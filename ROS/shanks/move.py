#!/usr/bin/env python3
"""Move one or more servos to target ticks. THIS MOVES THE ARM.

  python3 move.py 1 2000                 # servo 1 -> tick 2000
  python3 move.py 2 900 4 1000           # two servos in one Sync Write
  python3 move.py 6 130                  # gripper fully open
  python3 move.py 1 2400 --max-delta 600 # allow a bigger step than the default 300 ticks
  python3 move.py 1 2000 --speed 60 --yes

Guards, all of which refuse instead of clamping: E-STOP flag, tick outside the joint's
URDF/measured range, step larger than --max-delta (300 ticks = 26 deg), servo not replying.
Torque is enabled with goal = current position before the move so nothing jumps."""
import argparse
import time

SETTLE_TICKS = 60  # ~5 deg; the arm servos sag this much under load until the PID tune lands

from common import (ALL_IDS, ST3215Bus, ServoError, describe_tick, hold_current, name_of, read_ticks,
                    refuse_if_estopped, retry_eintr, tick_limits)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("pairs", type=int, nargs="+", metavar="ID TICK")
    parser.add_argument("--speed", type=int, default=100, help="raw goal speed 1..32767 (100 is slow)")
    parser.add_argument("--acc", type=int, default=10, help="raw acceleration 1..150")
    parser.add_argument("--max-delta", type=int, default=300, help="largest allowed |target - current| in ticks")
    parser.add_argument("--tolerance", type=int, default=15, help="ticks counted as arrived")
    parser.add_argument("--timeout", type=float, default=8.0)
    parser.add_argument("--yes", "-y", action="store_true", help="skip the confirmation prompt")
    parser.add_argument("--measured", action="store_true",
                        help="skip the URDF range check for ticks previously read from this arm (pose.py); "
                             "the real rest pose (J2 849, J4 877) sits outside the URDF limits")
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()
    if len(args.pairs) % 2:
        parser.error("arguments must be ID TICK pairs")
    targets = dict(zip(args.pairs[0::2], args.pairs[1::2]))
    refuse_if_estopped()
    for servo_id, tick in targets.items():
        if not 0 <= tick <= 4095:
            parser.error(f"tick {tick} outside 0..4095")
        if servo_id not in ALL_IDS:
            parser.error(f"servo {servo_id} is not one of {ALL_IDS}")
        lower, upper = tick_limits(servo_id)
        if not args.measured and not lower <= tick <= upper:
            parser.error(f"servo {servo_id} ({name_of(servo_id)}): tick {tick} outside allowed {lower}..{upper}")

    with ST3215Bus(args.port) as bus:
        current = read_ticks(bus, tuple(targets))
        for servo_id, tick in targets.items():
            now = current[servo_id]
            if now is None:
                raise SystemExit(f"servo {servo_id} does not reply; not moving anything")
            if abs(tick - now) > args.max_delta:
                raise SystemExit(f"servo {servo_id}: |{tick} - {now}| = {abs(tick - now)} > --max-delta {args.max_delta}")
        print("plan:")
        for servo_id, tick in targets.items():
            now = current[servo_id]
            print(f"  {servo_id} {name_of(servo_id):13} {now:5} ({describe_tick(servo_id, now)})  ->  "
                  f"{tick:5} ({describe_tick(servo_id, tick)})  delta {tick - now:+d}")
        print(f"  speed {args.speed}  acc {args.acc}  timeout {args.timeout}s")
        if not args.yes and input("move? [y/N] ").strip().lower() != "y":
            print("cancelled; nothing written")
            return

        hold_current(bus, tuple(targets), speed=args.speed, acceleration=args.acc)  # torque on, no jump
        retry_eintr(lambda: bus.write_positions(targets, speed=args.speed, acceleration=args.acc))
        started = time.monotonic()
        stopped_since = None
        try:
            while True:
                diag = {i: retry_eintr(lambda i=i: bus.read_diagnostics(i)) for i in targets}
                now = {i: d["tick"] for i, d in diag.items()}
                remaining = {i: targets[i] - now[i] for i in targets}
                print("  " + "  ".join(f"{i}:{now[i]}" for i in targets), flush=True)
                if all(abs(r) <= args.tolerance for r in remaining.values()):
                    print(f"arrived in {time.monotonic() - started:.2f}s")
                    break
                # Gravity load and stiction leave the arm servos 20-40 ticks short of the goal with the
                # current P gains (2026-09-18 elbow: goal 2669, rests at 2700). Once every servo reports
                # not moving for 1 s, accept a residual up to SETTLE_TICKS instead of waiting for the timeout.
                elapsed = time.monotonic() - started
                if elapsed > 0.5 and not any(d["moving"] for d in diag.values()):
                    stopped_since = stopped_since or time.monotonic()
                    if time.monotonic() - stopped_since > 1.0:
                        worst = max(abs(r) for r in remaining.values())
                        if worst <= SETTLE_TICKS:
                            print(f"settled in {elapsed:.2f}s with residual {remaining} ticks (<= {SETTLE_TICKS})")
                            break
                        raise SystemExit(f"servos stopped {worst} ticks short: remaining {remaining}; holding current position")
                else:
                    stopped_since = None
                if elapsed > args.timeout:
                    raise SystemExit(f"timeout: remaining {remaining}; holding current position")
                time.sleep(0.1)
        except KeyboardInterrupt:
            held = hold_current(bus, tuple(targets), speed=args.speed, acceleration=args.acc)
            raise SystemExit(f"interrupted; holding {held}")
        except ServoError as error:
            raise SystemExit(f"bus error during move: {error}")


if __name__ == "__main__":
    main()
