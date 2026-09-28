#!/usr/bin/env python3
"""Table of all step tests in tuning/tuning_log.jsonl, newest last (read-only)."""
import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--joint", type=int)
    parser.add_argument("--log", type=Path, default=Path(__file__).resolve().parent / "tuning/tuning_log.jsonl")
    args = parser.parse_args()
    if not args.log.exists():
        raise SystemExit(f"no log yet: {args.log}")
    print(f"{'time':15} {'J':>1} {'P':>3} {'D':>3} {'I':>3} {'dir':>4} {'err':>6} {'over':>4} {'settle':>6} {'rise':>5} {'jit':>5} note")
    for line in args.log.read_text(encoding="utf-8").splitlines():
        row = json.loads(line)
        if args.joint and row["servo_id"] != args.joint:
            continue
        for leg in row["legs"]:
            print(f"{row['time']:15} {row['servo_id']:>1} {row['pid']['P']:>3} {row['pid']['D']:>3} {row['pid']['I']:>3} "
                  f"{leg['direction'] + str(abs(leg['target'] - leg['start'])):>4} {leg['steady_error_ticks']:>+6.1f} "
                  f"{leg['overshoot_ticks']:>4} {str(leg['settle_time_s']):>6} {str(leg['rise_time_90pct_s']):>5} "
                  f"{leg['hold_jitter_std_ticks']:>5} {row['note']}")


if __name__ == "__main__":
    main()
