#!/usr/bin/env python3
"""Torque on/off for SO-101 servos.

  python3 torque.py on                 # all six: goal := current, then torque on (no jump)
  python3 torque.py on --ids 1 2 3
  python3 torque.py off --ids 6        # gripper only
  python3 torque.py off --all          # the arm goes limp: HOLD IT before running

`on` always writes goal = current position first so a stale goal register cannot move the arm."""
import argparse
import json

from common import ALL_IDS, ST3215Bus, hold_current, read_ticks, retry_eintr


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("state", choices=("on", "off"))
    parser.add_argument("--ids", type=int, nargs="+", help="servo IDs (default: all six for `on`)")
    parser.add_argument("--all", action="store_true", help="required to switch all six off")
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()
    if args.ids:
        ids = tuple(args.ids)
    elif args.state == "on" or args.all:
        ids = ALL_IDS
    else:
        parser.error("`off` needs --ids or --all (the arm goes limp; hold it first)")
    with ST3215Bus(args.port) as bus:
        if args.state == "on":
            held = hold_current(bus, ids)
            print("torque ON, holding", json.dumps(held))
        else:
            for servo_id in ids:
                retry_eintr(lambda: bus.set_torque(servo_id, False))
            print("torque OFF on", list(ids), "- ticks now", json.dumps(read_ticks(bus, ids)))
        state = {i: bus.read_diagnostics(i)["torque_enabled"] for i in ids}
        print("torque_enabled", json.dumps(state))


if __name__ == "__main__":
    main()
