"""Offline robot-time preflight for a trained relative UMI action-chunk policy.

This tool never writes to a motor.  It runs real validation observations through
the checkpoint, anchors every predicted chunk at one explicitly registered
SO-101 pose, then applies the production decoding boundary and MuJoCo IK.  The
result separates geometric rejection from commanded velocity/acceleration
rejection before any camera-matched rollout is attempted.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
import sys
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np

from data.relative_chunk_dataset import RelativeChunkDataset
from policy.relative_chunk_bc import RelativeChunkBCPolicy
from tools.umi_mujoco import (
    DEFAULT_CONFIG,
    DEFAULT_SCENE,
    MujocoArmAdapter,
    MujocoIK,
    build_model,
    eef_pose_from_joints,
    load_config,
)
from umi.arm_preflight import ArmSolution, prepare_arm_commands
from umi.convert import invert_gap_curve
from umi.ik import quat_to_matrix


AI_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REAL_CONFIG = AI_ROOT / "configs" / "real" / "so101_ver1.json"
DEFAULT_REGISTRATION = (
    AI_ROOT / "configs" / "real" / "umi_so101_registration_provisional.json"
)


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


def _matrix_from_fk(model: Any, solver: MujocoIK, q_rad: np.ndarray) -> np.ndarray:
    pos, quat = eef_pose_from_joints(
        model, solver.data, q_rad, solver.pinch)
    result = np.eye(4)
    result[:3, :3] = quat_to_matrix(quat)
    result[:3, 3] = pos
    return result


def _registration(path: Path) -> tuple[np.ndarray, np.ndarray, dict[str, Any]]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("status") != "PROVISIONAL_NOT_PHYSICAL_CALIBRATION":
        raise ValueError(
            "registration must explicitly declare "
            "PROVISIONAL_NOT_PHYSICAL_CALIBRATION")
    arm = np.asarray(payload.get("start_arm_rad"), dtype=float)
    object_xyz = np.asarray(payload.get("object_xyz_m"), dtype=float)
    if (arm.shape != (5,) or object_xyz.shape != (3,)
            or not np.isfinite(arm).all() or not np.isfinite(object_xyz).all()):
        raise ValueError("registration must contain finite start_arm_rad[5] and object_xyz_m[3]")
    return arm, object_xyz, payload


def _real_limits(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    required = (
        "limits_rad", "max_gap_m", "max_speed_rad_s", "max_accel_rad_s2",
        "max_gap_speed_m_s", "max_gap_accel_m_s2",
    )
    missing = [key for key in required if key not in payload]
    if missing:
        raise ValueError(f"real robot config is missing {missing}")
    return payload


def _ranges(real: dict[str, Any]) -> np.ndarray:
    order = [
        "shoulder_pan", "shoulder_lift", "elbow_flex", "wrist_flex",
        "wrist_roll",
    ]
    ranges = np.asarray([real["limits_rad"][name] for name in order], dtype=float)
    if ranges.shape != (5, 2) or not np.isfinite(ranges).all():
        raise ValueError("real limits_rad must contain five finite [lo, hi] rows")
    return ranges


def _balanced_indices(dataset: RelativeChunkDataset, episode_ids: list[str],
                      count: int, seed: int) -> list[int]:
    wanted = set(episode_ids)
    groups = [
        indices for episode, indices in zip(
            dataset.episode_ids, dataset.episode_sample_indices)
        if episode in wanted
    ]
    if not groups:
        raise ValueError("none of the requested episodes exists in the dataset")
    rng = np.random.default_rng(seed)
    each = max(1, int(np.ceil(count / len(groups))))
    selected: list[int] = []
    for indices in groups:
        take = min(each, len(indices))
        selected.extend(int(value) for value in rng.choice(indices, take, replace=False))
    rng.shuffle(selected)
    return selected[:count]


def _reason(error: ValueError) -> str:
    message = str(error)
    if "gripper" in message:
        return "gripper_range"
    if "arm range" in message:
        return "arm_range"
    if "IK" in message:
        return "ik_residual_or_convergence"
    if "rotation_6d" in message:
        return "invalid_rotation_prediction"
    return "invalid_prediction_or_contract"


def _percentiles(values: list[float]) -> list[float | None]:
    if not values:
        return [None, None, None, None]
    return [float(value) for value in np.percentile(values, [0, 50, 95, 100])]


def _empty_stats() -> dict[str, Any]:
    return {
        "geometry_accepted": 0,
        "dynamic_accepted": 0,
        "rejects": Counter(),
        "position_errors": [],
        "axis_errors": [],
        "roll_errors": [],
        "arm_speed_peaks": [],
        "arm_accel_peaks": [],
        "gap_speed_peaks": [],
        "gap_accel_peaks": [],
        "examples": [],
    }


def _evaluate_chunk(policy: Any, observation: dict[str, np.ndarray], *,
                    model: Any, ik: MujocoIK, curve: Any,
                    arm_start: np.ndarray, current_gap: float,
                    ranges: np.ndarray, gap_max: float, dt: float,
                    max_arm_speed: float, max_arm_accel: float,
                    max_gap_speed: float, max_gap_accel: float) -> dict[str, Any]:
    q_start = np.r_[arm_start, invert_gap_curve(current_gap, curve)]
    current_pose = _matrix_from_fk(model, ik, q_start)
    adapter = RecordingArmAdapter(ik, curve)
    # Geometry and commanded dynamics are separate questions.  Use the complete
    # range span as the jump budget here: any two in-range arm values can pass,
    # while an out-of-range IK result is still rejected.  Real speed and
    # acceleration limits are applied below from the timestamp-derived dt.
    geometric_arm_step = ranges[:, 1] - ranges[:, 0]
    try:
        commands = prepare_arm_commands(
            policy, observation,
            t_current=current_pose,
            arm_current_rad=arm_start,
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
        return {
            "result": "geometry_reject", "reason": _reason(error),
            "detail": str(error),
        }

    arm = np.vstack([arm_start, *[value.arm_positions_rad for value in commands]])
    gap = np.asarray([current_gap, *[value.gripper_width_m for value in commands]])
    arm_velocity = np.diff(arm, axis=0) / dt
    gap_velocity = np.diff(gap) / dt
    arm_acceleration = np.diff(arm_velocity, axis=0) / dt
    gap_acceleration = np.diff(gap_velocity) / dt
    arm_speed = float(np.abs(arm_velocity).max(initial=0.0))
    arm_accel = float(np.abs(arm_acceleration).max(initial=0.0))
    gap_speed = float(np.abs(gap_velocity).max(initial=0.0))
    gap_accel = float(np.abs(gap_acceleration).max(initial=0.0))
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
        "position_errors": [float(value.position_error_m) for value in adapter.solutions],
        "axis_errors": [float(value.axis_error_deg) for value in adapter.solutions],
        "roll_errors": [float(value.roll_error_deg) for value in adapter.solutions],
        "arm_speed_peak_rad_s": arm_speed,
        "arm_accel_peak_rad_s2": arm_accel,
        "gap_speed_peak_m_s": gap_speed,
        "gap_accel_peak_m_s2": gap_accel,
    }


def _accumulate(stats: dict[str, Any], outcome: dict[str, Any],
                identity: dict[str, Any]) -> None:
    if outcome["result"] == "geometry_reject":
        stats["rejects"][outcome["reason"]] += 1
    else:
        stats["geometry_accepted"] += 1
        stats["position_errors"].extend(outcome["position_errors"])
        stats["axis_errors"].extend(outcome["axis_errors"])
        stats["roll_errors"].extend(outcome["roll_errors"])
        peak_keys = {
            "arm_speed_peak_rad_s": "arm_speed_peaks",
            "arm_accel_peak_rad_s2": "arm_accel_peaks",
            "gap_speed_peak_m_s": "gap_speed_peaks",
            "gap_accel_peak_m_s2": "gap_accel_peaks",
        }
        for source, destination in peak_keys.items():
            stats[destination].append(outcome[source])
        if outcome["violations"]:
            stats["rejects"].update(outcome["violations"])
        else:
            stats["dynamic_accepted"] += 1
    if len(stats["examples"]) < 8:
        stats["examples"].append({**identity, **{
            key: value for key, value in outcome.items()
            if key not in ("position_errors", "axis_errors", "roll_errors")
        }})


def _summary(stats: dict[str, Any], total: int) -> dict[str, Any]:
    return {
        "geometry": {
            "accepted_chunks": stats["geometry_accepted"],
            "accepted_fraction": stats["geometry_accepted"] / total if total else 0.0,
            "position_error_m_p0_p50_p95_max": _percentiles(stats["position_errors"]),
            "axis_error_deg_p0_p50_p95_max": _percentiles(stats["axis_errors"]),
            "roll_error_deg_p0_p50_p95_max": _percentiles(stats["roll_errors"]),
        },
        "dynamics": {
            "accepted_chunks": stats["dynamic_accepted"],
            "accepted_fraction": stats["dynamic_accepted"] / total if total else 0.0,
            "arm_speed_peak_rad_s_p0_p50_p95_max": _percentiles(
                stats["arm_speed_peaks"]),
            "arm_accel_peak_rad_s2_p0_p50_p95_max": _percentiles(
                stats["arm_accel_peaks"]),
            "gap_speed_peak_m_s_p0_p50_p95_max": _percentiles(
                stats["gap_speed_peaks"]),
            "gap_accel_peak_m_s2_p0_p50_p95_max": _percentiles(
                stats["gap_accel_peaks"]),
        },
        "rejections": dict(sorted(stats["rejects"].items())),
        "examples": stats["examples"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--policy-ckpt", type=Path, required=True)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--samples", type=int, default=200)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--scene", type=Path, default=DEFAULT_SCENE)
    parser.add_argument("--real-config", type=Path, default=DEFAULT_REAL_CONFIG)
    parser.add_argument("--registration", type=Path, default=DEFAULT_REGISTRATION)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.samples <= 0:
        raise SystemExit("--samples must be positive")

    policy = RelativeChunkBCPolicy(args.policy_ckpt, args.device)
    data_config = policy.meta["config"]["data"]
    dataset = RelativeChunkDataset(
        args.data, image_mean=float(data_config["image_mean"]),
        image_std=float(data_config["image_std"]))
    if float(policy.meta["rate_hz"]) != float(dataset.rate_hz):
        raise ValueError("checkpoint and dataset rates differ")
    val_episodes = [str(value) for value in policy.meta.get("val_episodes", [])]
    if not val_episodes:
        raise ValueError("checkpoint has no recorded validation episode split")
    indices = _balanced_indices(
        dataset, val_episodes, min(args.samples, len(dataset)), args.seed)

    cfg = load_config(args.config)
    model = build_model(cfg, args.scene)
    ik = MujocoIK(model, cfg)
    arm_start, object_xyz, registration = _registration(args.registration)
    real = _real_limits(args.real_config)
    ranges = _ranges(real)
    if np.any(arm_start < ranges[:, 0]) or np.any(arm_start > ranges[:, 1]):
        raise ValueError("registered start pose exceeds real arm limits")

    rate_hz = float(dataset.rate_hz)
    dt = 1.0 / rate_hz
    max_arm_speed = float(real["max_speed_rad_s"])
    max_arm_accel = float(real["max_accel_rad_s2"])
    max_gap_speed = float(real["max_gap_speed_m_s"])
    max_gap_accel = float(real["max_gap_accel_m_s2"])
    max_gap = float(real["max_gap_m"])
    curve = cfg["grasp"]["gap_curve"]

    stats = {"policy_prediction": _empty_stats(), "recorded_oracle": _empty_stats()}

    for sample_index in indices:
        episode_index, row = dataset.index[sample_index]
        episode = dataset.episodes[episode_index]
        image = episode["image"][row]
        proprio = episode["proprio"][row]
        current_gap = float(proprio[-1, 9])
        observation = {"image": image.copy(), "proprio": proprio.copy()}
        identity = {
            "episode": str(episode["id"]), "row": int(row),
            "source_row": int(episode["source_row"][row]),
        }
        candidates = {
            "policy_prediction": policy,
            "recorded_oracle": FixedActionPolicy(episode["action"][row]),
        }
        for name, candidate in candidates.items():
            outcome = _evaluate_chunk(
                candidate, observation,
                model=model, ik=ik, curve=curve,
                arm_start=arm_start, current_gap=current_gap,
                ranges=ranges, gap_max=max_gap, dt=dt,
                max_arm_speed=max_arm_speed, max_arm_accel=max_arm_accel,
                max_gap_speed=max_gap_speed, max_gap_accel=max_gap_accel,
            )
            _accumulate(stats[name], outcome, identity)

    total = len(indices)
    result = {
        "status": "OFFLINE_PREFLIGHT_NOT_ROLLOUT_NOT_HARDWARE_VALIDATION",
        "dataset": str(args.data),
        "checkpoint": str(args.policy_ckpt),
        "samples": total,
        "sampling": "checkpoint_validation_episodes_balanced",
        "validation_episodes": val_episodes,
        "anchor": {
            "registration": str(args.registration),
            "registration_status": registration["status"],
            "start_arm_rad": arm_start.tolist(),
            "object_xyz_m_for_next_render_stage": object_xyz.tolist(),
            "note": (
                "Each independent prediction is anchored at the same feasible "
                "SO-101 pose. Object contact and closed-loop visual feedback are "
                "not evaluated here."
            ),
        },
        "limits": {
            "rate_hz": rate_hz,
            "max_arm_speed_rad_s": max_arm_speed,
            "max_arm_accel_rad_s2": max_arm_accel,
            "max_gap_speed_m_s": max_gap_speed,
            "max_gap_accel_m_s2": max_gap_accel,
            "max_position_error_m": 0.005,
            "max_axis_error_deg": 5.0,
            "max_roll_error_deg": 5.0,
            "geometry_jump_budget": (
                "full in-range span; velocity and acceleration are evaluated "
                "separately from source timestamps"
            ),
        },
        "comparison": {
            "policy_prediction": _summary(stats["policy_prediction"], total),
            "recorded_oracle": _summary(stats["recorded_oracle"], total),
            "interpretation": (
                "If the recorded oracle also fails, fix registration, IK or timing "
                "before blaming the learned policy. If the oracle passes but the "
                "policy fails, the learned output is the bottleneck."
            ),
        },
        "next_gate": (
            "Only after geometry and commanded dynamics are measured should a "
            "camera-matched MuJoCo diagnostic video be interpreted."
        ),
    }
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    print(rendered)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(rendered + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
