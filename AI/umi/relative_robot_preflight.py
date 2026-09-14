"""Torch-free robot geometry helpers for relative UMI action chunks."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np

from tools.umi_mujoco import MujocoArmAdapter, MujocoIK, eef_pose_from_joints
from umi.arm_preflight import ArmSolution, prepare_arm_commands
from umi.convert import invert_gap_curve
from umi.ik import quat_to_matrix
from umi.relative_dataset import vector_to_transform
from umi.timing import required_time_scale


class RecordingArmAdapter:
    """Record IK residuals while retaining the checked five-joint boundary."""

    def __init__(self, solver: MujocoIK, gap_curve: Any) -> None:
        self.delegate = MujocoArmAdapter(solver, gap_curve)
        self.solutions: list[ArmSolution] = []

    def solve(self, target_pose: np.ndarray, *, seed_rad: np.ndarray,
              gripper_width_m: float) -> ArmSolution:
        solution = self.delegate.solve(
            target_pose, seed_rad=seed_rad,
            gripper_width_m=gripper_width_m)
        self.solutions.append(solution)
        return solution


class FixedActionPolicy:
    """Expose a recorded ground-truth chunk through the policy boundary."""

    def __init__(self, action: np.ndarray) -> None:
        self.action = np.asarray(action, dtype=np.float32).copy()

    def predict_action(self, observation: Any) -> dict[str, np.ndarray]:
        del observation
        return {"action_pred": self.action.copy()}


def real_limits(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    required = (
        "limits_rad", "max_gap_m", "max_speed_rad_s", "max_accel_rad_s2",
        "max_gap_speed_m_s", "max_gap_accel_m_s2",
    )
    missing = [key for key in required if key not in payload]
    if missing:
        raise ValueError(f"real robot config is missing {missing}")
    return payload


def arm_ranges(real: dict[str, Any]) -> np.ndarray:
    order = [
        "shoulder_pan", "shoulder_lift", "elbow_flex", "wrist_flex",
        "wrist_roll",
    ]
    ranges = np.asarray([real["limits_rad"][name] for name in order], dtype=float)
    if ranges.shape != (5, 2) or not np.isfinite(ranges).all():
        raise ValueError("real limits_rad must contain five finite [lo, hi] rows")
    return ranges


def matrix_from_fk(model: Any, solver: MujocoIK,
                   q_rad: np.ndarray) -> np.ndarray:
    pos, quat = eef_pose_from_joints(
        model, solver.data, q_rad, solver.pinch)
    result = np.eye(4)
    result[:3, :3] = quat_to_matrix(quat)
    result[:3, 3] = pos
    return result


def episode_anchors(episode: dict[str, Any], *, model: Any, ik: MujocoIK,
                    curve: Any, arm_start: np.ndarray,
                    ranges: np.ndarray) -> tuple[list[np.ndarray],
                                                  list[np.ndarray | None]]:
    """Reconstruct every current pose and its continuous recorded-oracle seed."""
    gaps = np.asarray(episode["proprio"][:, -1, 9], dtype=float)
    first_q = np.r_[arm_start, invert_gap_curve(float(gaps[0]), curve)]
    poses = [matrix_from_fk(model, ik, first_q)]
    arms: list[np.ndarray | None] = [arm_start.copy()]
    seed = arm_start.copy()
    adapter = MujocoArmAdapter(ik, curve)
    for row in range(1, len(gaps)):
        pose = poses[-1] @ vector_to_transform(episode["action"][row - 1, 0])
        poses.append(pose)
        solution = adapter.solve(
            pose, seed_rad=seed, gripper_width_m=float(gaps[row]))
        valid = bool(
            solution.converged
            and solution.position_error_m <= 0.005
            and solution.axis_error_deg <= 5.0
            and solution.roll_error_deg <= 5.0
            and np.all(solution.positions_rad >= ranges[:, 0])
            and np.all(solution.positions_rad <= ranges[:, 1])
        )
        if valid:
            seed = solution.positions_rad.copy()
            arms.append(seed.copy())
        else:
            arms.append(None)
    return poses, arms


def evaluate_chunk(policy: Any, observation: Any, *,
                   ik: MujocoIK, curve: Any,
                   current_pose: np.ndarray, arm_current: np.ndarray,
                   current_gap: float,
                   ranges: np.ndarray, gap_max: float, dt: float,
                   max_arm_speed: float, max_arm_accel: float,
                   max_gap_speed: float, max_gap_accel: float) -> dict[str, Any]:
    """Separate range/IK geometry from timestamp-derived command dynamics."""
    adapter = RecordingArmAdapter(ik, curve)
    geometric_arm_step = ranges[:, 1] - ranges[:, 0]
    try:
        commands = prepare_arm_commands(
            policy, observation,
            t_current=current_pose,
            arm_current_rad=arm_current,
            gripper_current_m=current_gap,
            solver=adapter,
            ranges_rad=ranges,
            gap_range_m=[0.0, gap_max],
            max_step_rad=geometric_arm_step,
            max_gap_step_m=gap_max,
            max_position_error_m=0.005,
            max_axis_error_deg=5.0,
            max_roll_error_deg=5.0,
        )
    except ValueError as error:
        message = str(error)
        if "gripper" in message:
            reason = "gripper_range"
        elif "arm range" in message:
            reason = "arm_range"
        elif "IK" in message:
            reason = "ik_residual_or_convergence"
        elif "rotation_6d" in message:
            reason = "invalid_rotation_prediction"
        else:
            reason = "invalid_prediction_or_contract"
        return {"result": "geometry_reject", "reason": reason,
                "detail": message}

    arm = np.vstack([arm_current, *[
        value.arm_positions_rad for value in commands]])
    gap = np.asarray([current_gap, *[
        value.gripper_width_m for value in commands]])
    arm_velocity = np.diff(arm, axis=0) / dt
    gap_velocity = np.diff(gap) / dt
    arm_acceleration = np.diff(arm_velocity, axis=0) / dt
    gap_acceleration = np.diff(gap_velocity) / dt
    arm_speed = float(np.abs(arm_velocity).max(initial=0.0))
    arm_accel = float(np.abs(arm_acceleration).max(initial=0.0))
    gap_speed = float(np.abs(gap_velocity).max(initial=0.0))
    gap_accel = float(np.abs(gap_acceleration).max(initial=0.0))
    time_scale = required_time_scale(
        arm_speed=arm_speed, arm_accel=arm_accel,
        gap_speed=gap_speed, gap_accel=gap_accel,
        max_arm_speed=max_arm_speed, max_arm_accel=max_arm_accel,
        max_gap_speed=max_gap_speed, max_gap_accel=max_gap_accel,
    )
    violations = []
    if arm_speed > max_arm_speed + 1e-9:
        violations.append("arm_velocity")
    if arm_accel > max_arm_accel + 1e-9:
        violations.append("arm_acceleration")
    if gap_speed > max_gap_speed + 1e-9:
        violations.append("gap_velocity")
    if gap_accel > max_gap_accel + 1e-9:
        violations.append("gap_acceleration")
    return {
        "result": "accepted" if not violations else "dynamic_reject",
        "violations": violations,
        "position_errors": [
            float(value.position_error_m) for value in adapter.solutions],
        "axis_errors": [float(value.axis_error_deg) for value in adapter.solutions],
        "roll_errors": [float(value.roll_error_deg) for value in adapter.solutions],
        "arm_speed_peak_rad_s": arm_speed,
        "arm_accel_peak_rad_s2": arm_accel,
        "gap_speed_peak_m_s": gap_speed,
        "gap_accel_peak_m_s2": gap_accel,
        "required_time_scale": time_scale,
    }
