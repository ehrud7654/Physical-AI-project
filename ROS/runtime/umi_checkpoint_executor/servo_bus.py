"""Minimal ST3215 UART protocol used by the checkpoint executor."""
from __future__ import annotations

import time
from collections.abc import Iterable, Mapping

import serial


class ServoError(RuntimeError):
    pass


class ST3215Bus:
    PING = 0x01
    READ = 0x02
    WRITE = 0x03
    SYNC_WRITE = 0x83
    BROADCAST_ID = 0xFE

    TORQUE_ENABLE = 40
    ACCELERATION = 41
    GOAL_POSITION = 42
    GOAL_SPEED = 46
    TORQUE_LIMIT = 48
    PRESENT_POSITION = 56
    PRESENT_SPEED = 58
    PRESENT_LOAD = 60
    PRESENT_VOLTAGE = 62
    PRESENT_TEMPERATURE = 63
    STATUS = 65
    MOVING = 66
    PRESENT_CURRENT = 69

    def __init__(self, port: str = "/dev/ttyTHS1", baudrate: int = 1_000_000):
        self.serial = serial.Serial(port, baudrate, timeout=0.2)
        self.request_retries = 0
        self.request_failures = 0

    def close(self) -> None:
        self.serial.close()

    def __enter__(self) -> "ST3215Bus":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    @staticmethod
    def _packet(servo_id: int, instruction: int, parameters: bytes = b"") -> bytes:
        body = bytes((servo_id, len(parameters) + 2, instruction)) + parameters
        return b"\xff\xff" + body + bytes(((~sum(body)) & 0xFF,))

    def _read_exact(self, size: int) -> bytes:
        data = self.serial.read(size)
        if len(data) != size:
            raise ServoError(f"UART response timeout: expected {size}, got {len(data)} bytes")
        return data

    def _read_status(self, expected_id: int) -> bytes:
        if self._read_exact(2) != b"\xff\xff":
            raise ServoError("invalid response header")
        servo_id, length = self._read_exact(2)
        payload = self._read_exact(length)
        if servo_id != expected_id:
            raise ServoError(f"unexpected servo ID: {servo_id}")
        if ((servo_id + length + sum(payload)) & 0xFF) != 0xFF:
            raise ServoError("invalid response checksum")
        error = payload[0]
        if error:
            raise ServoError(f"servo {servo_id} error: 0x{error:02x}")
        return payload[1:-1]

    def _request(self, servo_id: int, instruction: int, parameters: bytes = b"") -> bytes:
        packet = self._packet(servo_id, instruction, parameters)
        last_error: ServoError | None = None
        for _ in range(3):
            self.serial.reset_input_buffer()
            self.serial.write(packet)
            self.serial.flush()
            try:
                return self._read_status(servo_id)
            except ServoError as error:
                self.request_retries += 1
                last_error = error
                time.sleep(0.01)
        self.request_failures += 1
        raise ServoError(f"servo {servo_id} failed after 3 attempts: {last_error}") from last_error

    @staticmethod
    def _check_id(servo_id: int) -> None:
        if not 0 <= servo_id <= 253:
            raise ValueError("servo_id must be 0..253")

    @staticmethod
    def _motion_data(position: int, speed: int, acceleration: int) -> bytes:
        if not 0 <= position <= 4095:
            raise ValueError("position must be 0..4095")
        if not 0 <= speed <= 32767:
            raise ValueError("speed must be 0..32767")
        if not 0 <= acceleration <= 150:
            raise ValueError("acceleration must be 0..150")
        return bytes((
            acceleration,
            position & 0xFF, position >> 8,
            0, 0,
            speed & 0xFF, speed >> 8,
        ))

    def ping(self, servo_id: int) -> bool:
        self._check_id(servo_id)
        self._request(servo_id, self.PING)
        return True

    def read_position(self, servo_id: int) -> int:
        self._check_id(servo_id)
        data = self._request(servo_id, self.READ, bytes((self.PRESENT_POSITION, 2)))
        if len(data) != 2:
            raise ServoError(f"invalid position response from servo {servo_id}")
        return data[0] | (data[1] << 8)

    @staticmethod
    def _decode_signed_15(value: int) -> int:
        return -(value & 0x7FFF) if value & 0x8000 else value

    def read_position_speed(self, servo_id: int) -> tuple[int, int]:
        self._check_id(servo_id)
        data = self._request(servo_id, self.READ, bytes((self.PRESENT_POSITION, 4)))
        if len(data) != 4:
            raise ServoError(f"invalid position/speed response from servo {servo_id}")
        return data[0] | (data[1] << 8), self._decode_signed_15(data[2] | (data[3] << 8))

    def read_motion_state(self, servo_id: int) -> tuple[int, int, bool]:
        self._check_id(servo_id)
        length = self.MOVING - self.PRESENT_POSITION + 1
        data = self._request(servo_id, self.READ, bytes((self.PRESENT_POSITION, length)))
        if len(data) != length:
            raise ServoError(f"invalid motion-state response from servo {servo_id}")
        position = data[0] | (data[1] << 8)
        speed = self._decode_signed_15(data[2] | (data[3] << 8))
        return position, speed, bool(data[self.MOVING - self.PRESENT_POSITION])

    @classmethod
    def _decode_diagnostics(cls, data: bytes) -> dict[str, int | bool]:
        first = cls.TORQUE_ENABLE
        expected = cls.PRESENT_CURRENT + 2 - first
        if len(data) != expected:
            raise ServoError(f"invalid diagnostic response: expected {expected}, got {len(data)}")

        def word(address: int) -> int:
            offset = address - first
            return data[offset] | (data[offset + 1] << 8)

        load = word(cls.PRESENT_LOAD)
        if load & (1 << 10):
            load = -(load & ~(1 << 10))
        return {
            "torque_enabled": bool(data[cls.TORQUE_ENABLE - first]),
            "acceleration_register": data[cls.ACCELERATION - first],
            "goal_tick": word(cls.GOAL_POSITION),
            "goal_speed_raw": word(cls.GOAL_SPEED),
            "torque_limit_raw": word(cls.TORQUE_LIMIT),
            "tick": word(cls.PRESENT_POSITION),
            "present_speed_raw": cls._decode_signed_15(word(cls.PRESENT_SPEED)),
            "present_load_raw": load,
            "voltage_raw": data[cls.PRESENT_VOLTAGE - first],
            "temperature_c": data[cls.PRESENT_TEMPERATURE - first],
            "status_raw": data[cls.STATUS - first],
            "moving": bool(data[cls.MOVING - first]),
            "present_current_raw": cls._decode_signed_15(word(cls.PRESENT_CURRENT)),
        }

    def read_diagnostics(self, servo_id: int) -> dict[str, int | bool]:
        self._check_id(servo_id)
        length = self.PRESENT_CURRENT + 2 - self.TORQUE_ENABLE
        data = self._request(servo_id, self.READ, bytes((self.TORQUE_ENABLE, length)))
        return self._decode_diagnostics(data)

    def read_positions(self, servo_ids: Iterable[int] = range(1, 7)) -> dict[int, int]:
        return {servo_id: self.read_position(servo_id) for servo_id in servo_ids}

    def read_positions_speeds(
        self, servo_ids: Iterable[int] = range(1, 7)
    ) -> dict[int, tuple[int, int]]:
        return {servo_id: self.read_position_speed(servo_id) for servo_id in servo_ids}

    def write_position(
        self,
        servo_id: int,
        position: int,
        speed: int = 300,
        acceleration: int = 10,
    ) -> None:
        self._check_id(servo_id)
        data = self._motion_data(position, speed, acceleration)
        self._request(servo_id, self.WRITE, bytes((self.ACCELERATION,)) + data)

    def write_positions(
        self,
        positions: Mapping[int, int],
        speed: int = 300,
        acceleration: int = 10,
    ) -> None:
        if not positions:
            return
        parameters = bytearray((self.ACCELERATION, 7))
        for servo_id, position in positions.items():
            self._check_id(servo_id)
            parameters.append(servo_id)
            parameters.extend(self._motion_data(position, speed, acceleration))
        self.serial.write(self._packet(self.BROADCAST_ID, self.SYNC_WRITE, bytes(parameters)))
        self.serial.flush()

    def set_torque(self, servo_id: int, enabled: bool) -> None:
        self._check_id(servo_id)
        self._request(servo_id, self.WRITE, bytes((self.TORQUE_ENABLE, int(enabled))))

    def set_torque_all(self, enabled: bool, servo_ids: Iterable[int] = range(1, 7)) -> None:
        for servo_id in servo_ids:
            self.set_torque(servo_id, enabled)


def self_check() -> None:
    assert ST3215Bus._packet(1, ST3215Bus.PING) == bytes.fromhex("ffff010201fb")
    assert ST3215Bus._motion_data(203, 300, 10) == bytes.fromhex("0acb0000002c01")
    raw = bytearray(ST3215Bus.PRESENT_CURRENT + 2 - ST3215Bus.TORQUE_ENABLE)
    raw[0:10] = bytes.fromhex("010a870b00002c01e803")
    raw[16:24] = bytes.fromhex("ac0b648064047c25")
    raw[25:27] = bytes.fromhex("0201")
    raw[29:31] = bytes.fromhex("0400")
    diagnostic = ST3215Bus._decode_diagnostics(bytes(raw))
    assert diagnostic["goal_tick"] == 2951
    assert diagnostic["tick"] == 2988
    assert diagnostic["present_speed_raw"] == -100
    assert diagnostic["present_load_raw"] == -100
    assert diagnostic["voltage_raw"] == 124
    assert diagnostic["temperature_c"] == 37
    assert diagnostic["status_raw"] == 2
    assert diagnostic["moving"] is True
    assert diagnostic["present_current_raw"] == 4


if __name__ == "__main__":
    self_check()
    print("SERVO_BUS_SELF_CHECK_OK")
