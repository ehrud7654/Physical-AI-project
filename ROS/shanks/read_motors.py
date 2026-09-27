#!/usr/bin/env python3
"""Read-only SO-101 servo monitor for the Jetson. Never writes torque, goals, or IDs.

  python3 read_motors.py                 # one snapshot of ID 1..6
  python3 read_motors.py --watch 10      # 10 Hz table until Ctrl+C
  python3 read_motors.py --watch 15 --log ticks.jsonl
  python3 read_motors.py --ids 1 2 3     # subset
  python3 read_motors.py --self-test     # conversions only, no bus

Reuses the existing bus driver and joint calibration from ~/Workspace/so101_speed_measure
(ST3215Bus over /dev/ttyTHS1 at 1 Mbps; ZERO_TICKS/DIRECTIONS confirmed on 2026-09-15)."""
from __future__ import annotations

import argparse
import json
import math
import sys
import time
from pathlib import Path

from common import (ARM_IDS, GRIPPER_ID, GRIPPER_CLOSED_TICK, GRIPPER_OPEN_TICK, GRIPPER_OPEN_WIDTH_M,
                    JOINT_NAMES, ST3215Bus, ServoError, VOLTAGE_PER_RAW, gripper_tick_to_width_m,
                    joint_tick_to_rad, retry_eintr)


def read_servo(bus: ST3215Bus, servo_id: int) -> dict:
    """One diagnostics transaction (registers 40..70) with EINTR retry, as in tick_logger.py."""
    return retry_eintr(lambda: bus.read_diagnostics(servo_id))


def snapshot(bus: ST3215Bus, ids: tuple[int, ...]) -> dict:
    record = {"t_mono": time.monotonic(), "t_wall": time.time(), "servos": {}}
    for servo_id in ids:
        try:
            d = read_servo(bus, servo_id)
        except ServoError as error:
            record["servos"][servo_id] = {"present": False, "error": str(error).split(":")[-1].strip()}
            continue
        entry = {
            "present": True,
            "tick": d["tick"],
            "goal_tick": d["goal_tick"],
            "speed_raw": d["present_speed_raw"],
            "moving": d["moving"],
            "load_raw": d["present_load_raw"],
            "current_raw": d["present_current_raw"],
            "voltage_v": round(d["voltage_raw"] * VOLTAGE_PER_RAW, 1),
            "temperature_c": d["temperature_c"],
            "torque_enabled": d["torque_enabled"],
            "status_raw": d["status_raw"],
        }
        if servo_id in ARM_IDS:
            entry["name"] = JOINT_NAMES[servo_id - 1]
            entry["rad"] = round(joint_tick_to_rad(servo_id, d["tick"]), 4)
            entry["deg"] = round(math.degrees(entry["rad"]), 2)
        elif servo_id == GRIPPER_ID:
            entry["name"] = "gripper"
            entry["width_m"] = round(gripper_tick_to_width_m(d["tick"]), 4)
        record["servos"][servo_id] = entry
    return record


def format_table(record: dict) -> str:
    lines = [f"{'id':>2} {'joint':13} {'tick':>5} {'value':>10} {'spd':>5} {'mov':>3} {'load':>5} "
             f"{'V':>5} {'degC':>4} {'trq':>3} {'st':>2}"]
    for servo_id, e in record["servos"].items():
        if not e["present"]:
            lines.append(f"{servo_id:>2} {'(no reply)':13} {e['error']}")
            continue
        value = f"{e['deg']:8.2f}deg" if "deg" in e else f"{e['width_m'] * 1000:7.1f} mm"
        lines.append(f"{servo_id:>2} {e['name']:13} {e['tick']:5} {value:>10} {e['speed_raw']:5} "
                     f"{'Y' if e['moving'] else '.':>3} {e['load_raw']:5} {e['voltage_v']:5.1f} "
                     f"{e['temperature_c']:4} {'on' if e['torque_enabled'] else 'off':>3} {e['status_raw']:2}")
    return "\n".join(lines)


def self_test() -> None:
    assert abs(joint_tick_to_rad(2, 2048)) < 1e-9          # shoulder_lift zero
    assert abs(joint_tick_to_rad(1, 1990)) < 1e-9          # shoulder_pan zero
    assert abs(joint_tick_to_rad(1, 1990 + 1024) - math.pi / 2) < 1e-6
    assert abs(gripper_tick_to_width_m(GRIPPER_CLOSED_TICK)) < 1e-9
    assert abs(gripper_tick_to_width_m(GRIPPER_OPEN_TICK) - GRIPPER_OPEN_WIDTH_M) < 1e-9
    assert abs(gripper_tick_to_width_m((GRIPPER_CLOSED_TICK + GRIPPER_OPEN_TICK) / 2) - 0.045) < 1e-9
    print("SELF_TEST_OK")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", type=int, nargs="+", default=list(ARM_IDS) + [GRIPPER_ID])
    parser.add_argument("--port", default="/dev/ttyTHS1")
    parser.add_argument("--baud", type=int, default=1_000_000)
    parser.add_argument("--watch", type=float, metavar="HZ", help="repeat at HZ until Ctrl+C")
    parser.add_argument("--log", type=Path, help="append one JSON line per snapshot")
    parser.add_argument("--json", action="store_true", help="print JSON instead of a table")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    ids = tuple(args.ids)
    log = args.log.open("a", encoding="utf-8") if args.log else None
    with ST3215Bus(args.port, args.baud) as bus:
        period = 1.0 / args.watch if args.watch else None
        count = 0
        try:
            while True:
                started = time.monotonic()
                record = snapshot(bus, ids)
                count += 1
                if log:
                    log.write(json.dumps(record) + "\n")
                    log.flush()
                text = json.dumps(record) if args.json else format_table(record)
                if period and not args.json:
                    print(f"\033[2J\033[H{time.strftime('%H:%M:%S')}  #{count}  Ctrl+C to stop\n{text}", flush=True)
                else:
                    print(text, flush=True)
                if period is None:
                    break
                time.sleep(max(0.0, period - (time.monotonic() - started)))
        except KeyboardInterrupt:
            pass
        finally:
            if log:
                log.close()
                print(f"\n{count} snapshots -> {args.log}", file=sys.stderr)


if __name__ == "__main__":
    main()
