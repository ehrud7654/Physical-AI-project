#!/usr/bin/env python3
"""Save the current servo ticks under a name and move back to them later. `go` MOVES THE ARM.

  python3 pose.py save ready          # read all 6 ticks -> poses.json["ready"]
  python3 pose.py list
  python3 pose.py go ready            # move.py with every saved tick (its plan table + y/N apply)
  python3 pose.py go ready --arm      # arm only, leave the gripper

Poses live in ~/Shanks/poses.json next to this file."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

POSES = Path(__file__).resolve().with_name("poses.json")


def load() -> dict:
    return json.loads(POSES.read_text()) if POSES.exists() else {}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("cmd", choices=("save", "list", "go"))
    parser.add_argument("name", nargs="?")
    parser.add_argument("--arm", action="store_true", help="go: move joints 1-5 only")
    parser.add_argument("--speed", type=int, default=300)
    parser.add_argument("--yes", "-y", action="store_true")
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()
    poses = load()

    if args.cmd == "list":
        for name, ticks in poses.items():
            print(f"{name:12} {ticks}")
        return
    if not args.name:
        parser.error("save/go need a pose name")

    if args.cmd == "save":
        from common import ST3215Bus, read_ticks
        with ST3215Bus(args.port) as bus:
            ticks = read_ticks(bus)
        if any(t is None for t in ticks.values()):
            raise SystemExit(f"a servo does not reply, not saving: {ticks}")
        poses[args.name] = [ticks[i] for i in range(1, 7)]
        POSES.write_text(json.dumps(poses, indent=1))
        print(f"saved {args.name}: {poses[args.name]} -> {POSES}")
        return

    if args.name not in poses:
        raise SystemExit(f"no pose {args.name!r}; have {list(poses)}")
    ids = range(1, 6) if args.arm else range(1, 7)
    pairs = [str(x) for i in ids for x in (i, poses[args.name][i - 1])]
    cmd = [sys.executable, str(POSES.with_name("move.py")), *pairs, "--speed", str(args.speed),
           "--max-delta", "4096", "--timeout", "90", "--tolerance", "20"] + (["--yes"] if args.yes else [])
    sys.exit(subprocess.run(cmd).returncode)


if __name__ == "__main__":
    main()
