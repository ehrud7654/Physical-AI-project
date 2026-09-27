"""Shared servo access and calibration for the Shanks test tools.

Reuses the existing bus driver and arm calibration in ~/Workspace/so101_speed_measure."""
from __future__ import annotations

import math
import sys
import time
from pathlib import Path

SPEED_MEASURE = Path.home() / "Workspace/so101_speed_measure"
sys.path.insert(0, str(SPEED_MEASURE))
try:
    from servo_control import ST3215Bus, ServoError  # noqa: E402,F401
    from robot_control_5dof import JOINT_NAMES, RAD_LIMITS, ZERO_TICKS, DIRECTIONS, RAD_PER_TICK, joint_tick_to_rad  # noqa: E402,F401
except ImportError as error:  # pragma: no cover
    raise SystemExit(f"cannot import the servo driver from {SPEED_MEASURE}: {error}")

ARM_IDS = (1, 2, 3, 4, 5)
GRIPPER_ID = 6
ALL_IDS = ARM_IDS + (GRIPPER_ID,)
ESTOP_FLAG = Path("/tmp/umi_ESTOP")  # created by estop.py; every mover refuses while it exists

# Gripper servo 6 replaced 2026-09-17: measured 1763 fully closed (hand-held, torque off) and
# 130 fully open. The old BACKEND_INTERFACE.md values (1720 / 70) belong to the previous servo.
# ponytail: linear tick->width with 0.09 m nominal opening; replace with a measured table if needed.
GRIPPER_CLOSED_TICK, GRIPPER_OPEN_TICK, GRIPPER_OPEN_WIDTH_M = 1763, 130, 0.09
VOLTAGE_PER_RAW = 0.1  # register 62 is in 0.1 V units

# Ticks read from this arm at rest that lie outside the URDF limits; the allowed range is widened to include
# them. J2 852 (2026-09-17), 849 (2026-09-18); J4 877 (2026-09-18 rest, -103 deg vs URDF -95).
MEASURED_TICKS = {2: 849, 4: 877}


def name_of(servo_id: int) -> str:
    return JOINT_NAMES[servo_id - 1] if servo_id in ARM_IDS else "gripper" if servo_id == GRIPPER_ID else f"id{servo_id}"


def tick_limits(servo_id: int) -> tuple[int, int]:
    """Allowed encoder range: URDF joint limits for the arm, measured end stops for the gripper."""
    if servo_id in ARM_IDS:
        index = servo_id - 1
        lower, upper = RAD_LIMITS[index]
        ticks = sorted(ZERO_TICKS[index] + DIRECTIONS[index] * r / RAD_PER_TICK for r in (lower, upper))
        lo, hi = max(0, math.ceil(ticks[0])), min(4095, math.floor(ticks[1]))
        seen = MEASURED_TICKS.get(servo_id, lo)
        return min(lo, seen), max(hi, seen)
    if servo_id == GRIPPER_ID:
        return min(GRIPPER_OPEN_TICK, GRIPPER_CLOSED_TICK), max(GRIPPER_OPEN_TICK, GRIPPER_CLOSED_TICK)
    raise ValueError(f"unknown servo id {servo_id}")


def gripper_tick_to_width_m(tick: int) -> float:
    ratio = (GRIPPER_CLOSED_TICK - tick) / (GRIPPER_CLOSED_TICK - GRIPPER_OPEN_TICK)
    return ratio * GRIPPER_OPEN_WIDTH_M


def describe_tick(servo_id: int, tick: int) -> str:
    if servo_id in ARM_IDS:
        return f"{math.degrees(joint_tick_to_rad(servo_id, tick)):.2f} deg"
    if servo_id == GRIPPER_ID:
        return f"{gripper_tick_to_width_m(tick) * 1000:.1f} mm"
    return ""


def retry_eintr(call, tries: int = 4):
    """UART writes can be interrupted by signals (termios EINTR); retry briefly like tick_logger.py."""
    for attempt in range(tries):
        try:
            return call()
        except OSError:
            if attempt == tries - 1:
                raise
            time.sleep(0.005)
    raise AssertionError("unreachable")


def read_ticks(bus: ST3215Bus, ids=ALL_IDS) -> dict[int, int | None]:
    ticks = {}
    for servo_id in ids:
        try:
            ticks[servo_id] = retry_eintr(lambda: bus.read_position(servo_id))
        except ServoError:
            ticks[servo_id] = None
    return ticks


def hold_current(bus: ST3215Bus, ids, speed: int = 100, acceleration: int = 10) -> dict[int, int]:
    """Goal := current position, then torque on. Enabling torque with a stale goal would make the servo jump."""
    ticks = {i: t for i, t in read_ticks(bus, ids).items() if t is not None}
    if ticks:
        retry_eintr(lambda: bus.write_positions(ticks, speed=speed, acceleration=acceleration))
        for servo_id in ticks:
            retry_eintr(lambda: bus.set_torque(servo_id, True))
    return ticks


def refuse_if_estopped() -> None:
    if ESTOP_FLAG.exists():
        raise SystemExit(f"E-STOP flag {ESTOP_FLAG} exists; remove it only after checking the arm")
