#!/usr/bin/env python3
"""Show or set a servo's position-loop parameters (EEPROM). Backs up before every write.

  python3 pid.py show                      # P/D/I, min start force, dead band of all six
  python3 pid.py set 2 --i 3               # change only I on servo 2 (P, D unchanged)
  python3 pid.py set 2 --p 40 --d 40 --i 2
  python3 pid.py set 2 --min-force 24      # minimum start force (reg 24-25), helps stiction near target

Refuses while the servo is moving, reports a non-zero status, or is at/above 60 C.
Backups: tuning/pid_backups/<time>_j<id>.json (values before the write)."""
import argparse
from datetime import datetime
import json
from pathlib import Path

from common import ALL_IDS, ST3215Bus, name_of, retry_eintr
from servo_params import read_params

P_GAIN, D_GAIN, I_GAIN = 21, 22, 23
MIN_START_FORCE = 24          # 2 bytes
EEPROM_LOCK = 55
BACKUP_DIR = Path(__file__).resolve().parent / "tuning/pid_backups"


def show(bus, ids) -> None:
    print(f"{'id':>2} {'joint':13} {'P':>3} {'D':>3} {'I':>3} {'minF':>4} {'dead':>5} {'trq':>3} {'degC':>4} {'st':>2}")
    for servo_id in ids:
        p = read_params(bus, servo_id)
        print(f"{servo_id:>2} {name_of(servo_id):13} {p['P']:>3} {p['D']:>3} {p['I']:>3} {p['min_start_force']:>4} "
              f"{p['dead_band_cw_ccw'][0]}/{p['dead_band_cw_ccw'][1]:<3} {'on' if p['torque_enabled'] else 'off':>3} "
              f"{p['temperature_c']:>4} {p['status']:>2}")


def write_eeprom(bus, servo_id, address, payload: bytes) -> None:
    retry_eintr(lambda: bus._request(servo_id, bus.WRITE, bytes((EEPROM_LOCK, 0))))
    try:
        retry_eintr(lambda: bus._request(servo_id, bus.WRITE, bytes((address,)) + payload))
    finally:
        retry_eintr(lambda: bus._request(servo_id, bus.WRITE, bytes((EEPROM_LOCK, 1))))


def set_params(bus, servo_id, p, d, i, min_force) -> None:
    before = read_params(bus, servo_id)
    if before["torque_enabled"] and bus.read_diagnostics(servo_id)["moving"]:
        raise SystemExit("servo is moving; wait until it stops")
    if before["status"] != 0:
        raise SystemExit(f"servo status {before['status']} != 0; not writing")
    if before["temperature_c"] >= 60:
        raise SystemExit(f"servo is {before['temperature_c']} C; cool below 60 C first")
    target = {"P": before["P"] if p is None else p, "D": before["D"] if d is None else d,
              "I": before["I"] if i is None else i,
              "min_start_force": before["min_start_force"] if min_force is None else min_force}
    if target == {k: before[k] for k in target}:
        print("nothing to change:", json.dumps(target))
        return
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = BACKUP_DIR / f"{stamp}_j{servo_id}.json"
    backup.write_text(json.dumps({"servo_id": servo_id, "joint": name_of(servo_id), "before": before,
                                  "target": target}, indent=2), encoding="utf-8")
    write_eeprom(bus, servo_id, P_GAIN, bytes((target["P"], target["D"], target["I"])))
    if target["min_start_force"] != before["min_start_force"]:
        value = target["min_start_force"]
        write_eeprom(bus, servo_id, MIN_START_FORCE, bytes((value & 0xFF, value >> 8)))
    after = read_params(bus, servo_id)
    got = {k: after[k] for k in target}
    if got != target:
        raise SystemExit(f"verification failed: wanted {target}, servo reports {got} (backup {backup.name})")
    print(f"servo {servo_id} {name_of(servo_id)}: {json.dumps({k: before[k] for k in target})} -> {json.dumps(got)}")
    print(f"backup {backup}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", default="/dev/ttyTHS1")
    sub = parser.add_subparsers(dest="command", required=True)
    s = sub.add_parser("show")
    s.add_argument("--ids", type=int, nargs="+", default=list(ALL_IDS))
    w = sub.add_parser("set")
    w.add_argument("servo_id", type=int)
    w.add_argument("--p", type=int)
    w.add_argument("--d", type=int)
    w.add_argument("--i", type=int)
    w.add_argument("--min-force", type=int, dest="min_force")
    args = parser.parse_args()
    if args.command == "set":
        if args.servo_id not in ALL_IDS:
            parser.error(f"servo must be one of {ALL_IDS}")
        for name, value, upper in (("p", args.p, 254), ("d", args.d, 254), ("i", args.i, 254),
                                   ("min-force", args.min_force, 1000)):
            if value is not None and not 0 <= value <= upper:
                parser.error(f"--{name} must be 0..{upper}")
        if args.p == 0:
            parser.error("--p 0 would disable position control")
        if all(v is None for v in (args.p, args.d, args.i, args.min_force)):
            parser.error("give at least one of --p --d --i --min-force")
    with ST3215Bus(args.port) as bus:
        if args.command == "show":
            show(bus, args.ids)
        else:
            set_params(bus, args.servo_id, args.p, args.d, args.i, args.min_force)


if __name__ == "__main__":
    main()
