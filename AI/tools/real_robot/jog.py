"""Jog one STS3215 servo on the SO-101 bus in small interpolated steps, or print status.
SO-101 버스의 서보 하나를 작은 보간 스텝으로 움직이거나 상태를 찍는다.

사용 (젯슨, 실행기가 포트를 잡고 있지 않을 때만)
  python3 ~/jog.py status            6축 현재위치·목표·토크·온도
  python3 ~/jog.py 1 +40             1번(pan) 을 +40틱 (≈3.5°)
  python3 ~/jog.py 2 -20             2번(lift) 을 -20틱
  python3 ~/jog.py 6 =400            6번(그리퍼) 을 절대 400틱 (130=90mm 열림, 1763=닫힘)
  python3 ~/jog.py selftest          패킷·체크섬·에코 제거 정답 검사

안전
  - 토크가 켜져 있으면 '현재 목표 레지스터'에서 출발한다 (중력 처짐 위치에서 출발하면 한 번 더 처진다).
  - 토크가 꺼져 있으면 goal := present 를 쓰고 되읽어 5틱 이내일 때만 토크를 켠다 (safe_on 과 같은 규칙).
  - 상대 이동은 한 번에 |N| <= 120틱. 넘으면 거부.
  - 온도 > 50°C 이면 움직이지 않는다.
"""
from __future__ import annotations

import sys
import time

PORT, BAUD = "/dev/ttyTHS1", 1_000_000
ADDR_TORQUE, ADDR_GOAL, ADDR_POS, ADDR_TEMP = 40, 42, 56, 63
MAX_REL, STEP, DT, TEMP_MAX = 120, 6, 0.03, 50
GRIP_OPEN, GRIP_CLOSED = 130, 1763


def checksum(body: list[int]) -> int:
    """Feetech checksum over ID..params.
    ID 부터 파라미터까지의 합의 비트반전 하위 1바이트."""
    return (~sum(body)) & 0xFF


def packet(sid: int, instr: int, params: list[int]) -> bytes:
    """Build an instruction packet.
    명령 패킷을 만든다."""
    body = [sid, len(params) + 2, instr, *params]
    return bytes([0xFF, 0xFF, *body, checksum(body)])


def parse_status(buf: bytes, req: bytes, sid: int) -> bytes:
    """Strip an echoed request if present, then return the data bytes of the status packet.
    반이중 에코가 있으면 요청 바이트를 걷어내고 응답 데이터만 돌려준다."""
    if buf.startswith(req):
        buf = buf[len(req):]
    i = buf.find(bytes([0xFF, 0xFF, sid]))
    if i < 0 or len(buf) < i + 6:
        raise IOError(f"id{sid} 응답 없음 ({buf.hex()})")
    ln, err = buf[i + 3], buf[i + 4]
    pkt = buf[i: i + 4 + ln]
    if len(pkt) < 4 + ln or checksum(list(pkt[2:-1])) != pkt[-1]:
        raise IOError(f"id{sid} 체크섬 불일치 ({buf.hex()})")
    if err:
        raise IOError(f"id{sid} 서보 에러 0x{err:02x}")
    return bytes(pkt[5:-1])


class Bus:
    """Minimal STS3215 bus over pyserial.
    pyserial 위 최소 STS3215 버스."""

    def __init__(self) -> None:
        import serial

        self.s = serial.Serial(PORT, BAUD, timeout=0.02)

    def _xfer(self, req: bytes, sid: int, want: int) -> bytes:
        for _ in range(3):
            self.s.reset_input_buffer()
            self.s.write(req)
            self.s.flush()
            time.sleep(0.004)
            buf = self.s.read(len(req) + 6 + want)
            try:
                return parse_status(buf, req, sid)
            except IOError as e:
                last = e
        raise last

    def read(self, sid: int, addr: int, n: int) -> int:
        d = self._xfer(packet(sid, 0x02, [addr, n]), sid, n)
        return d[0] if n == 1 else d[0] | (d[1] << 8)

    def write(self, sid: int, addr: int, val: int, n: int) -> None:
        data = [val & 0xFF] if n == 1 else [val & 0xFF, (val >> 8) & 0xFF]
        self._xfer(packet(sid, 0x03, [addr, *data]), sid, 0)


def status(bus: Bus) -> None:
    for sid in range(1, 7):
        try:
            print(f"  id{sid}  pos {bus.read(sid, ADDR_POS, 2):5d}  goal {bus.read(sid, ADDR_GOAL, 2):5d}"
                  f"  torque {bus.read(sid, ADDR_TORQUE, 1)}  temp {bus.read(sid, ADDR_TEMP, 1)}C")
        except IOError as e:
            print(f"  id{sid}  읽기 실패: {e}")


def jog(bus: Bus, sid: int, spec: str) -> None:
    temp = bus.read(sid, ADDR_TEMP, 1)
    if temp > TEMP_MAX:
        sys.exit(f"id{sid} 온도 {temp}C > {TEMP_MAX} — 움직이지 않는다")
    pos = bus.read(sid, ADDR_POS, 2)
    if bus.read(sid, ADDR_TORQUE, 1):
        start = bus.read(sid, ADDR_GOAL, 2)
    else:
        bus.write(sid, ADDR_GOAL, pos, 2)
        back = bus.read(sid, ADDR_GOAL, 2)
        if abs(back - pos) > 5:
            sys.exit(f"id{sid} goal 되읽기 {back} vs pos {pos} — 토크 안 켠다")
        bus.write(sid, ADDR_TORQUE, 1, 1)
        start = pos
        print(f"  id{sid} 토크 꺼져 있었음 → goal=pos {pos} 후 ON")
    if spec.startswith("="):
        target = int(spec[1:])
    else:
        d = int(spec)
        if abs(d) > MAX_REL:
            sys.exit(f"|{d}| > {MAX_REL} 틱 — 나눠서 움직여라")
        target = start + d
    lo, hi = (GRIP_OPEN, GRIP_CLOSED) if sid == 6 else (0, 4095)
    target = min(max(target, lo), hi)
    print(f"  id{sid}  pos {pos}  goal {start} → {target}  ({target - start:+d})")
    g = start
    while g != target:
        g += max(-STEP, min(STEP, target - g))
        bus.write(sid, ADDR_GOAL, g, 2)
        time.sleep(DT)
    time.sleep(0.6)
    end = bus.read(sid, ADDR_POS, 2)
    print(f"  도착 pos {end}  (목표 대비 {end - target:+d}틱)  temp {bus.read(sid, ADDR_TEMP, 1)}C")


def selftest() -> None:
    """Known answers: documented read-position packet, echo strip, bad checksum.
    정답을 아는 행 3개."""
    bad = 0
    req = packet(1, 0x02, [0x38, 2])
    bad += req.hex() != "ffff0104023802be"
    print(f"  read 패킷 {req.hex()} (기대 ffff0104023802be)")
    resp = bytes([0xFF, 0xFF, 1, 4, 0, 0x00, 0x08]) + bytes([checksum([1, 4, 0, 0x00, 0x08])])
    d = parse_status(req + resp, req, 1)
    bad += (d[0] | d[1] << 8) != 2048
    print(f"  에코+응답 → {d[0] | d[1] << 8} (기대 2048)")
    try:
        parse_status(resp[:-1] + b"\x00", req, 1)
        bad += 1
        print("  깨진 체크섬 통과 — FAIL")
    except IOError:
        print("  깨진 체크섬 거부 OK")
    if bad:
        sys.exit(f"자체검사 실패 {bad}건")
    print("  자체검사 3/3")


if __name__ == "__main__":
    selftest()
    if len(sys.argv) < 2 or sys.argv[1] == "selftest":
        sys.exit(0)
    b = Bus()
    if sys.argv[1] == "status":
        status(b)
    else:
        jog(b, int(sys.argv[1]), sys.argv[2])
        status(b)
