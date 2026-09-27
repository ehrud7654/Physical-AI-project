#!/usr/bin/env python3
"""IK-driven grasp in the table frame (mm; see kinematics.TABLE). `go` MOVES THE ARM.

  python3 grasp.py where                       # current TCP in table mm from the encoders (FK check with a ruler)
  python3 grasp.py plan 410 350 80             # joints/ticks for the grasp + waypoints, no motion (works on the PC)
  python3 grasp.py plan 410 350 80 --tilt 60   # approach 60 deg from vertical, leaning toward the arm
  python3 grasp.py go 410 350 80 --tilt 60     # open -> pregrasp -> grasp -> close -> lift 100 mm -> hold 3 s
  python3 grasp.py open                        # release

Every motion goes through move.py, so its guards apply per waypoint (E-STOP, tick range, no reply,
plan table, y/N). Before moving, the joint-space path between waypoints is sampled with FK and refused
if the wrist or TCP would dip below --min-z above the table."""
from __future__ import annotations

import argparse
import math
import subprocess
import sys
import time
from pathlib import Path

import numpy as np

import kinematics as K

HERE = Path(__file__).resolve().parent
ZERO_TICKS, DIRECTIONS, RAD_PER_TICK = (1990, 2048, 2048, 2048, 2126), (1, 1, 1, 1, 1), 2 * math.pi / 4096
GRIPPER_CLOSED_TICK, GRIPPER_OPEN_TICK, GRIPPER_OPEN_WIDTH_M = 1763, 130, 0.09
try:  # on the Jetson take the driver's calibration; on the PC fall back to the same values copied above
    sys.path.insert(0, str(Path.home() / "Workspace/so101_speed_measure"))
    from robot_control_5dof import ZERO_TICKS, DIRECTIONS, RAD_PER_TICK  # type: ignore  # noqa: F811
except ImportError:
    pass


def rad_to_tick(q) -> list[int]:
    return [int(round(z + d * qi / RAD_PER_TICK)) for z, d, qi in zip(ZERO_TICKS, DIRECTIONS, q)]


def tick_to_rad(ticks) -> np.ndarray:
    return np.array([(t - z) * d * RAD_PER_TICK for z, d, t in zip(ZERO_TICKS, DIRECTIONS, ticks)])


def width_to_tick(width_m: float) -> int:
    ratio = min(max(width_m / GRIPPER_OPEN_WIDTH_M, 0.0), 1.0)
    return int(round(GRIPPER_CLOSED_TICK - ratio * (GRIPPER_CLOSED_TICK - GRIPPER_OPEN_TICK)))


def approach_dirs(target_mm, tilt_deg: float, finger_deg: float | None):
    """Approach = down, tilted `tilt_deg` toward the arm (radially from the J1 axis). Fingers default
    to the horizontal tangent so a standing cylinder is gripped across; --finger-deg overrides (angle from +x)."""
    jx, jy = K.TABLE["j1_axis_xy_mm"]
    radial = np.array([target_mm[0] - jx, target_mm[1] - jy, 0.0])
    radial /= max(np.linalg.norm(radial), 1e-9)
    t = math.radians(tilt_deg)
    approach = np.array([0, 0, -math.cos(t)]) + radial * math.sin(t)
    if finger_deg is None:
        finger = np.array([-radial[1], radial[0], 0.0])
    else:
        finger = np.array([math.cos(math.radians(finger_deg)), math.sin(math.radians(finger_deg)), 0.0])
    return approach, finger


def solve(target_mm, approach, finger, q0=None):
    to_base = lambda v: None if v is None else K.dir_base_from_table(v)  # noqa: E731
    q, err, ok = K.ik(K.base_from_table(target_mm), to_base(approach), to_base(finger), q0=q0)
    if not ok:
        raise SystemExit(f"IK cannot reach {np.round(target_mm, 1)} with that approach (best {err:.1f} mm); "
                         "move the target, change --tilt, or lower it")
    return q


def waypoints(target_mm, tilt: float, finger_deg, pre_mm: float, lift_mm: float):
    target = np.asarray(target_mm, float)
    approach, finger = approach_dirs(target, tilt, finger_deg)
    q_grasp = solve(target, approach, finger)
    q_pre = solve(target - approach * pre_mm, approach, finger, q0=q_grasp)
    q_lift = solve(target + (0, 0, lift_mm), None, None, q0=q_grasp)  # position only: nearest joints, tilt may change
    return [("pregrasp", q_pre), ("grasp", q_grasp), ("lift", q_lift)]


def path_min_z(q_from, q_to, steps: int = 25) -> float:
    """Lowest table-frame z (mm) of the wrist_roll origin and TCP along a straight joint-space move."""
    lowest = math.inf
    for s in np.linspace(0, 1, steps):
        q = np.asarray(q_from) + (np.asarray(q_to) - np.asarray(q_from)) * s
        t = np.eye(4)
        for tf, qi in zip(K._JOINT_TFS, q):
            t = t @ tf @ K._rz(qi)
        lowest = min(lowest, K.table_from_base(t[:3, 3])[2], K.table_from_base((t @ K._TCP_TF)[:3, 3])[2])
    return lowest


def describe(name: str, q) -> str:
    tcp = K.fk(q)
    p = K.table_from_base(tcp[:3, 3])
    deg = " ".join(f"{math.degrees(v):7.1f}" for v in q)
    return f"{name:9} deg[{deg}]  ticks{rad_to_tick(q)}  TCP({p[0]:.0f},{p[1]:.0f},{p[2]:.0f})"


def run_move(pairs: dict[int, int], speed: int, yes: bool, max_delta: int = 4096, timeout: float = 90.0) -> None:
    # speed 100 moved ~160 ticks/s on 2026-09-18; a folded->pregrasp wrist swing of ~1600 ticks needs ~10 s,
    # so move.py's default 8 s timeout is too short here (it then holds position and aborts the sequence).
    # tolerance 20: the servo does not move for a ~16 tick error (stiction), move.py default 15 never "arrives".
    cmd = [sys.executable, str(HERE / "move.py"), *map(str, (x for kv in pairs.items() for x in kv)),
           "--speed", str(speed), "--max-delta", str(max_delta), "--timeout", str(timeout), "--tolerance", "20"] + (["--yes"] if yes else [])
    if subprocess.run(cmd).returncode:
        raise SystemExit("move.py refused or failed; stopping the sequence here")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("cmd", choices=("where", "plan", "go", "open"))
    parser.add_argument("xyz", type=float, nargs="*", help="target TCP in table mm (plan/go)")
    parser.add_argument("--tilt", type=float, default=45.0, help="approach angle from vertical toward the arm, deg")
    parser.add_argument("--finger-deg", type=float, help="finger opening axis angle from table +x (default: tangent)")
    parser.add_argument("--pre", type=float, default=80.0, help="pregrasp retreat along the approach, mm")
    parser.add_argument("--lift", type=float, default=100.0, help="lift height after closing, mm")
    parser.add_argument("--close-mm", type=float, default=30.0, help="gripper width command when closing (potato 40 mm)")
    parser.add_argument("--hold", type=float, default=3.0, help="seconds to hold after lifting")
    parser.add_argument("--min-z", type=float, default=15.0, help="refuse if wrist/TCP path dips below this, mm")
    parser.add_argument("--speed", type=int, default=300, help="raw goal speed; 300 is the URDF ros2_control default, 100 is the slow test value")
    parser.add_argument("--yes", "-y", action="store_true", help="skip every confirmation (move.py included)")
    parser.add_argument("--port", default="/dev/ttyTHS1")
    args = parser.parse_args()

    if args.cmd in ("plan", "go"):
        if len(args.xyz) != 3:
            parser.error("plan/go need X Y Z in table mm")
        wps = waypoints(args.xyz, args.tilt, args.finger_deg, args.pre, args.lift)
        print(f"target {args.xyz} tilt {args.tilt} deg  open {GRIPPER_OPEN_TICK}  close {width_to_tick(args.close_mm / 1000)} ({args.close_mm} mm)")
        for name, q in wps:
            print("  " + describe(name, q))
        for (a, qa), (b, qb) in zip(wps, wps[1:]):
            z = path_min_z(qa, qb)
            print(f"  path {a}->{b}: lowest wrist/TCP z {z:.0f} mm")
            if z < args.min_z and b != "grasp":
                raise SystemExit(f"path {a}->{b} dips to {z:.0f} mm (< --min-z {args.min_z}); refusing")
        if args.cmd == "plan":
            return

    from common import ST3215Bus, read_ticks, refuse_if_estopped  # Jetson only
    refuse_if_estopped()
    with ST3215Bus(args.port) as bus:
        ticks = read_ticks(bus)
    if any(ticks[i] is None for i in range(1, 7)):
        raise SystemExit(f"a servo does not reply: {ticks}")
    q_now = tick_to_rad([ticks[i] for i in range(1, 6)])
    tcp = K.fk(q_now)
    p, a = K.table_from_base(tcp[:3, 3]), tcp[:3, 2]
    print(f"now: ticks {[ticks[i] for i in range(1, 7)]}  deg {[round(math.degrees(v), 1) for v in q_now]}")
    print(f"     TCP table (mm) x={p[0]:.1f} y={p[1]:.1f} z={p[2]:.1f}  approach(base) {np.round(a, 2)}  gripper {ticks[6]}")
    if args.cmd == "where":
        return
    if args.cmd == "open":
        run_move({6: GRIPPER_OPEN_TICK}, args.speed, args.yes)
        return

    z = path_min_z(q_now, wps[0][1])
    print(f"  path now->pregrasp: lowest wrist/TCP z {z:.0f} mm")
    if z < args.min_z:
        raise SystemExit(f"path from the current pose dips to {z:.0f} mm; move the arm to a raised pose first")
    if not args.yes and input("run the whole sequence (each step still asks)? [y/N] ").strip().lower() != "y":
        return
    arm = lambda q: dict(zip(range(1, 6), rad_to_tick(q)))  # noqa: E731
    run_move({6: GRIPPER_OPEN_TICK}, args.speed, args.yes)
    run_move(arm(wps[0][1]), args.speed, args.yes)
    run_move(arm(wps[1][1]), args.speed, args.yes)
    run_move({6: width_to_tick(args.close_mm / 1000)}, args.speed, args.yes)
    time.sleep(0.5)
    run_move(arm(wps[2][1]), args.speed, args.yes)
    print(f"holding {args.hold}s")
    time.sleep(args.hold)
    print("done; arm is holding the lift pose. `python3 grasp.py open` releases")


if __name__ == "__main__":
    main()
