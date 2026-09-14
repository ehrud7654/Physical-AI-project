"""Robot-time scheduling helpers for relative UMI action chunks."""

from __future__ import annotations

import numpy as np


def required_time_scale(*, arm_speed: float, arm_accel: float,
                        gap_speed: float, gap_accel: float,
                        max_arm_speed: float, max_arm_accel: float,
                        max_gap_speed: float, max_gap_accel: float) -> float:
    """Return the uniform duration multiplier required by all dynamic limits.

    Uniformly stretching time by ``s`` divides velocity by ``s`` and
    acceleration by ``s**2``. Source timestamps and training labels remain
    untouched; this value belongs to the robot-time command scheduler.
    """
    values = np.asarray([
        arm_speed, arm_accel, gap_speed, gap_accel,
        max_arm_speed, max_arm_accel, max_gap_speed, max_gap_accel,
    ], dtype=float)
    if (not np.isfinite(values).all() or np.any(values[:4] < 0)
            or np.any(values[4:] <= 0)):
        raise ValueError("dynamic peaks must be non-negative and limits positive")
    return float(max(
        1.0,
        arm_speed / max_arm_speed,
        np.sqrt(arm_accel / max_arm_accel),
        gap_speed / max_gap_speed,
        np.sqrt(gap_accel / max_gap_accel),
    ))
