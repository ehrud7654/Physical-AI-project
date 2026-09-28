"""SO-101 JSON pose and torque CLI - TEMPORARY 5-MOTOR version (no gripper ID6).

Copy of robot_control.py with the gripper servo (ID 6) removed, for running while
only arm servos 1..5 are present. Arm behavior is identical; gripper commands are
dropped. The original robot_control.py is untouched.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any

from servo_control import ST3215Bus


JOINT_NAMES = (
    "shoulder_pan",
    "shoulder_lift",
    "elbow_flex",
    "wrist_flex",
    "wrist_roll",
)
ZERO_TICKS = (1990, 2048, 2048, 2048, 2126)
DIRECTIONS = (1, 1, 1, 1, 1)
RAD_LIMITS = (
    (-1.91986, 1.91986),
    (-1.74533, 1.74533),
    (-1.57079632679, 1.69),
    (-1.65806, 1.65806),
    (-2.743847297, 2.841206309),
)
RAD_PER_TICK = 2.0 * math.pi / 4096.0
SERVO_IDS = (1, 2, 3, 4, 5)  # gripper ID 6 removed (temporary 5-motor build)


def _nearest_tick(raw: float) -> int:
    return int(math.floor(raw + 0.5))


def joint_rad_to_tick(joint_id: int, value: float) -> int:
    if not 1 <= joint_id <= 5:
        raise ValueError("joint_id must be 1..5")
    index = joint_id - 1
    radians = float(value)
    lower, upper = RAD_LIMITS[index]
    if not math.isfinite(radians) or not lower <= radians <= upper:
        raise ValueError(f"{JOINT_NAMES[index]} must be within {lower}..{upper} rad")
    raw = ZERO_TICKS[index] + DIRECTIONS[index] * radians / RAD_PER_TICK
    if not 0.0 <= raw <= 4095.0:
        raise ValueError(f"{JOINT_NAMES[index]} converts outside tick 0..4095")
    return _nearest_tick(raw)


def joint_tick_to_rad(joint_id: int, tick: int) -> float:
    if not 1 <= joint_id <= 5:
        raise ValueError("joint_id must be 1..5")
    if not 0 <= tick <= 4095:
        raise ValueError("tick must be 0..4095")
    index = joint_id - 1
    return (tick - ZERO_TICKS[index]) * DIRECTIONS[index] * RAD_PER_TICK


def pose_to_ticks(joints_rad: list[float], gripper_width_m: float | None = None) -> dict[int, int]:
    if len(joints_rad) != 5:
        raise ValueError("joints_rad must contain exactly J1..J5")
    targets: dict[int, int] = {}
    for index, value in enumerate(joints_rad):
        targets[index + 1] = joint_rad_to_tick(index + 1, value)
    # gripper (ID 6) intentionally omitted in 5-motor build; gripper_width_m ignored.
    return targets


def send_pose(
    bus: ST3215Bus,
    joints_rad: list[float],
    gripper_width_m: float | None = None,
    speed: int = 300,
    acceleration: int = 10,
) -> dict[int, int]:
    targets = pose_to_ticks(joints_rad, gripper_width_m)
    bus.write_positions(targets, speed=speed, acceleration=acceleration)
    return targets


def read_pose(bus: ST3215Bus) -> dict[str, Any]:
    ticks = bus.read_positions(SERVO_IDS)
    joints_rad = [joint_tick_to_rad(index + 1, ticks[index + 1]) for index in range(5)]
    return {
        "joints_rad": joints_rad,
        "gripper_width_m": 0.0,  # placeholder: no gripper servo in 5-motor build
        "ticks": ticks,
    }


def set_torque(bus: ST3215Bus, enabled: bool) -> None:
    if enabled:
        targets = bus.read_positions(SERVO_IDS)
        bus.write_positions(targets, speed=300, acceleration=10)
        for servo_id, target in targets.items():
            if bus.read_diagnostics(servo_id)["goal_tick"] != target:
                raise RuntimeError(f"servo {servo_id}: hold target verification failed")
    bus.set_torque_all(enabled, SERVO_IDS)


def _load_pose(path: Path | None, inline_json: str | None) -> dict[str, Any]:
    if path is not None:
        with path.open(encoding="utf-8") as source:
            value = json.load(source)
    else:
        value = json.loads(inline_json or "")
    if not isinstance(value, dict):
        raise ValueError("pose JSON must be an object")
    if "joints_rad" not in value:
        raise ValueError("pose JSON requires joints_rad")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="/dev/ttyTHS1")
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--torque", choices=("on", "off"))
    action.add_argument("--pose", type=Path, metavar="JSON_FILE")
    action.add_argument("--json", dest="inline_json", metavar="JSON")
    action.add_argument("--read", action="store_true")
    parser.add_argument("--speed", type=int, default=300)
    parser.add_argument("--acceleration", type=int, default=10)
    args = parser.parse_args()

    try:
        with ST3215Bus(port=args.port) as bus:
            if args.torque is not None:
                set_torque(bus, args.torque == "on")
                result: dict[str, Any] = {"torque": args.torque, "ids": SERVO_IDS}
            elif args.read:
                result = read_pose(bus)
            else:
                pose = _load_pose(args.pose, args.inline_json)
                targets = send_pose(
                    bus,
                    pose["joints_rad"],
                    pose.get("gripper_width_m"),
                    speed=args.speed,
                    acceleration=args.acceleration,
                )
                result = {"accepted": True, "target_ticks": targets}
    except Exception as error:
        parser.exit(1, f"error: {error}\n")

    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
