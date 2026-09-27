#!/usr/bin/env python3
"""Print the current encoder tick of every servo (read-only).

  python3 ticks.py                # 1:1980 2:853 3:3108 4:990 5:2130 6:1763
  python3 ticks.py --watch 10     # refresh at 10 Hz until Ctrl+C
  python3 ticks.py --json         # {"1": 1980, ...}
  python3 ticks.py --ids 1 2"""
import argparse
import json
import time

from common import ALL_IDS, ST3215Bus, describe_tick, name_of, read_ticks


def line(ticks: dict) -> str:
    return "  ".join(f"{i}:{t if t is not None else '----'}" for i, t in ticks.items())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ids", type=int, nargs="+", default=list(ALL_IDS))
    parser.add_argument("--watch", type=float, metavar="HZ")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--verbose", "-v", action="store_true", help="one line per servo with name and deg/mm")
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()
    with ST3215Bus(args.port) as bus:
        try:
            while True:
                ticks = read_ticks(bus, tuple(args.ids))
                if args.json:
                    print(json.dumps(ticks), flush=True)
                elif args.verbose:
                    print("\n".join(f"{i} {name_of(i):13} {t if t is not None else '----':>5}  "
                                    f"{describe_tick(i, t) if t is not None else '(no reply)'}"
                                    for i, t in ticks.items()) + "\n", flush=True)
                else:
                    print(line(ticks), flush=True)
                if not args.watch:
                    break
                time.sleep(1.0 / args.watch)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
