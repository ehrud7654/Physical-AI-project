#!/usr/bin/env python3
"""Step-response test for one servo: step +/-delta from the current tick, hold, return, hold.

  python3 step_test.py 2 +98            # J2: current -> current+98 -> back, 50 Hz log, metrics
  python3 step_test.py 5 +150 --hold 4
  python3 step_test.py 3 -100 --note "P40 D32 I0"

Per leg it reports steady error, overshoot, settle time (into +-10 ticks and staying there),
rise time to 90 %, and jitter while holding (std of the last second). Samples go to
tuning/<joint>_<timestamp>.jsonl and one summary line to tuning/tuning_log.jsonl.
Same guards as move.py; Ctrl+C holds the current position. THIS MOVES THE ARM."""
import argparse
from datetime import datetime
import json
from pathlib import Path
import statistics
import time

from common import (ALL_IDS, ST3215Bus, ServoError, describe_tick, hold_current, name_of, read_ticks,
                    refuse_if_estopped, retry_eintr, tick_limits)
from servo_params import read_params

SETTLE_BAND = 10


def record(bus, servo_id, seconds, rate, t0, samples):
    period = 1.0 / rate
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        started = time.monotonic()
        tick = retry_eintr(lambda: bus.read_position(servo_id))
        samples.append({"t": round(started - t0, 4), "tick": tick})
        time.sleep(max(0.0, period - (time.monotonic() - started)))


def leg_metrics(samples, start, target, t_cmd):
    """samples: dicts with t, tick recorded after the command at t_cmd."""
    ticks = [s["tick"] for s in samples]
    times = [s["t"] - t_cmd for s in samples]
    direction = 1 if target >= start else -1
    final = statistics.mean(ticks[-max(1, len(ticks) // 6):])          # last ~1/6 of the hold
    overshoot = max(0, max((t - target) * direction for t in ticks))
    settle = None
    for index, tick in enumerate(ticks):
        if all(abs(x - target) <= SETTLE_BAND for x in ticks[index:]):
            settle = times[index]
            break
    rise = next((times[i] for i, t in enumerate(ticks) if (t - start) * direction >= 0.9 * abs(target - start)), None)
    jitter = statistics.pstdev(ticks[-min(len(ticks), 50):])            # last ~1 s at 50 Hz
    return {"start": start, "target": target, "final_tick": round(final, 1),
            "steady_error_ticks": round(final - target, 1), "overshoot_ticks": int(overshoot),
            "settle_time_s": None if settle is None else round(settle, 3),
            "rise_time_90pct_s": None if rise is None else round(rise, 3),
            "hold_jitter_std_ticks": round(jitter, 2), "samples": len(ticks)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("servo_id", type=int)
    parser.add_argument("delta", type=int, help="signed step in ticks, e.g. +98 or -100")
    parser.add_argument("--speed", type=int, default=100)
    parser.add_argument("--acc", type=int, default=10)
    parser.add_argument("--hold", type=float, default=3.0, help="seconds recorded after each step")
    parser.add_argument("--rate", type=float, default=50.0)
    parser.add_argument("--max-delta", type=int, default=300)
    parser.add_argument("--no-return", action="store_true", help="only the forward leg")
    parser.add_argument("--note", default="", help="free text stored with the summary, e.g. PID being tried")
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent / "tuning")
    parser.add_argument("--yes", "-y", action="store_true")
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()
    refuse_if_estopped()
    if args.servo_id not in ALL_IDS:
        parser.error(f"servo must be one of {ALL_IDS}")
    if abs(args.delta) > args.max_delta or args.delta == 0:
        parser.error(f"|delta| must be 1..{args.max_delta}")

    with ST3215Bus(args.port) as bus:
        start = read_ticks(bus, (args.servo_id,))[args.servo_id]
        if start is None:
            raise SystemExit(f"servo {args.servo_id} does not reply")
        target = start + args.delta
        lower, upper = tick_limits(args.servo_id)
        if not lower <= target <= upper:
            raise SystemExit(f"target {target} outside allowed {lower}..{upper} for {name_of(args.servo_id)}")
        params = read_params(bus, args.servo_id)
        pid = {k: params[k] for k in ("P", "D", "I")}
        print(f"{args.servo_id} {name_of(args.servo_id)}  {start} ({describe_tick(args.servo_id, start)})"
              f"  ->  {target} ({describe_tick(args.servo_id, target)})  then back  |  PID {pid}"
              f"  speed {args.speed} acc {args.acc}  hold {args.hold}s")
        if not args.yes and input("run step test? [y/N] ").strip().lower() != "y":
            print("cancelled; nothing written")
            return

        args.out.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        samples_path = args.out / f"j{args.servo_id}_{stamp}.jsonl"
        legs = []
        t0 = time.monotonic()
        all_samples = []
        try:
            hold_current(bus, (args.servo_id,), speed=args.speed, acceleration=args.acc)
            record(bus, args.servo_id, 0.5, args.rate, t0, all_samples)
            plan = [(start, target)] if args.no_return else [(start, target), (target, start)]
            for leg_start, leg_target in plan:
                cmd_time = time.monotonic() - t0
                retry_eintr(lambda: bus.write_position(args.servo_id, leg_target, speed=args.speed,
                                                       acceleration=args.acc))
                leg_samples = []
                record(bus, args.servo_id, args.hold, args.rate, t0, leg_samples)
                all_samples.extend(leg_samples)
                actual_start = leg_samples[0]["tick"]
                metrics = leg_metrics(leg_samples, actual_start, leg_target, cmd_time)
                metrics["direction"] = "+" if leg_target > actual_start else "-"
                legs.append(metrics)
                print(f"  leg {metrics['direction']}{abs(leg_target - actual_start)}: final {metrics['final_tick']}  "
                      f"steady_err {metrics['steady_error_ticks']:+.1f}  overshoot {metrics['overshoot_ticks']}  "
                      f"settle {metrics['settle_time_s']}s  rise90 {metrics['rise_time_90pct_s']}s  "
                      f"jitter {metrics['hold_jitter_std_ticks']}", flush=True)
        except KeyboardInterrupt:
            held = hold_current(bus, (args.servo_id,), speed=args.speed, acceleration=args.acc)
            print(f"\ninterrupted; holding {held}")
        except ServoError as error:
            print(f"bus error: {error}")
        finally:
            with samples_path.open("w", encoding="utf-8") as stream:
                for sample in all_samples:
                    stream.write(json.dumps(sample) + "\n")
            summary = {"time": stamp, "servo_id": args.servo_id, "joint": name_of(args.servo_id),
                       "pid": pid, "torque_limit": params["torque_limit_ram"], "speed": args.speed,
                       "acc": args.acc, "delta": args.delta, "note": args.note, "legs": legs,
                       "samples_file": samples_path.name, "voltage_v": params["voltage_v"],
                       "temperature_c": params["temperature_c"]}
            with (args.out / "tuning_log.jsonl").open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(summary) + "\n")
            print(f"saved {samples_path.name}; summary appended to tuning_log.jsonl")


if __name__ == "__main__":
    main()
