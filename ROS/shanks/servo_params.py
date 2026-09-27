#!/usr/bin/env python3
"""Read-only dump of the STS3215 control parameters that decide tracking stiffness.

  python3 servo_params.py            # all six
  python3 servo_params.py --ids 2

Registers: 16-17 max torque (EEPROM, 1000 = 100 %), 21 P, 22 D, 23 I, 24-25 minimum start force,
26/27 CW/CCW dead band, 34-35 protection current, 48-49 torque limit (RAM), plus live load/current."""
import argparse

from common import ALL_IDS, ST3215Bus, ServoError, name_of


def read_params(bus: ST3215Bus, servo_id: int) -> dict:
    raw = bus._request(servo_id, bus.READ, bytes((16, 2)))
    max_torque = raw[0] | (raw[1] << 8)
    p, d, i, force_lo, force_hi, dead_cw, dead_ccw = bus._request(servo_id, bus.READ, bytes((21, 7)))
    raw = bus._request(servo_id, bus.READ, bytes((34, 2)))
    diag = bus.read_diagnostics(servo_id)
    return {"P": p, "D": d, "I": i, "min_start_force": force_lo | (force_hi << 8),
            "dead_band_cw_ccw": (dead_cw, dead_ccw), "max_torque_eeprom": max_torque,
            "torque_limit_ram": diag["torque_limit_raw"], "protection_current": raw[0] | (raw[1] << 8),
            "goal_tick": diag["goal_tick"], "tick": diag["tick"], "error_ticks": diag["tick"] - diag["goal_tick"],
            "load": diag["present_load_raw"], "current": diag["present_current_raw"],
            "voltage_v": diag["voltage_raw"] / 10, "temperature_c": diag["temperature_c"],
            "torque_enabled": diag["torque_enabled"], "status": diag["status_raw"]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", type=int, nargs="+", default=list(ALL_IDS))
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()
    with ST3215Bus(args.port) as bus:
        for servo_id in args.ids:
            try:
                params = read_params(bus, servo_id)
            except ServoError as error:
                print(f"{servo_id} {name_of(servo_id):13} (no reply) {error}")
                continue
            print(f"{servo_id} {name_of(servo_id):13} " + "  ".join(f"{k}={v}" for k, v in params.items()))


if __name__ == "__main__":
    main()
