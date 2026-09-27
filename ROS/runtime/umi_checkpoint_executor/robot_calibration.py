"""SO-101 servo IDs, installed-arm calibration, and tick conversions."""
from __future__ import annotations

import math


ARM_IDS = (1, 2, 3, 4, 5)
GRIPPER_ID = 6
ALL_IDS = ARM_IDS + (GRIPPER_ID,)
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

# Installed gripper measured 2026-09-17.
GRIPPER_CLOSED_TICK = 1763
GRIPPER_OPEN_TICK = 130
GRIPPER_OPEN_WIDTH_M = 0.09

# Encoder positions observed on this arm outside the nominal URDF limits.
MEASURED_TICKS = {2: 849, 4: 877}


def joint_tick_to_rad(joint_id: int, tick: int) -> float:
    if not 1 <= joint_id <= 5:
        raise ValueError("joint_id must be 1..5")
    if not 0 <= tick <= 4095:
        raise ValueError("tick must be 0..4095")
    index = joint_id - 1
    return (tick - ZERO_TICKS[index]) * DIRECTIONS[index] * RAD_PER_TICK


def rad_to_tick(q) -> list[int]:
    return [
        int(round(zero + direction * radians / RAD_PER_TICK))
        for zero, direction, radians in zip(ZERO_TICKS, DIRECTIONS, q)
    ]


def tick_to_rad(ticks):
    import numpy as np

    return np.array([
        (tick - zero) * direction * RAD_PER_TICK
        for zero, direction, tick in zip(ZERO_TICKS, DIRECTIONS, ticks)
    ])


def width_to_tick(width_m: float) -> int:
    ratio = min(max(float(width_m) / GRIPPER_OPEN_WIDTH_M, 0.0), 1.0)
    return int(round(GRIPPER_CLOSED_TICK - ratio * (GRIPPER_CLOSED_TICK - GRIPPER_OPEN_TICK)))


def gripper_tick_to_width_m(tick: int) -> float:
    ratio = (GRIPPER_CLOSED_TICK - tick) / (GRIPPER_CLOSED_TICK - GRIPPER_OPEN_TICK)
    return ratio * GRIPPER_OPEN_WIDTH_M


def tick_limits(servo_id: int) -> tuple[int, int]:
    if servo_id in ARM_IDS:
        index = servo_id - 1
        lower, upper = RAD_LIMITS[index]
        ticks = sorted(
            ZERO_TICKS[index] + DIRECTIONS[index] * radians / RAD_PER_TICK
            for radians in (lower, upper)
        )
        low, high = max(0, math.ceil(ticks[0])), min(4095, math.floor(ticks[1]))
        measured = MEASURED_TICKS.get(servo_id, low)
        return min(low, measured), max(high, measured)
    if servo_id == GRIPPER_ID:
        return min(GRIPPER_OPEN_TICK, GRIPPER_CLOSED_TICK), max(GRIPPER_OPEN_TICK, GRIPPER_CLOSED_TICK)
    raise ValueError(f"unknown servo id {servo_id}")


def self_check() -> None:
    assert rad_to_tick(tick_to_rad(ZERO_TICKS)) == list(ZERO_TICKS)
    assert width_to_tick(0.09) == GRIPPER_OPEN_TICK
    assert width_to_tick(0.0) == GRIPPER_CLOSED_TICK
    assert math.isclose(gripper_tick_to_width_m(GRIPPER_OPEN_TICK), 0.09)
    assert tick_limits(GRIPPER_ID) == (130, 1763)


if __name__ == "__main__":
    self_check()
    print("ROBOT_CALIBRATION_SELF_CHECK_OK")
