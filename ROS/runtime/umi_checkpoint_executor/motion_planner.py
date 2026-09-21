"""Convert a policy action to SO-101 IK candidates without motor I/O."""
import argparse
import importlib
import json
import sys
from pathlib import Path

import numpy as np


def joint_margin_ticks(q, limits, rad_per_tick):
    limits = np.asarray(limits, dtype=float)
    q = np.asarray(q, dtype=float)
    margin_rad = np.minimum(q - limits[:, 0], limits[:, 1] - q)
    return np.floor(margin_rad / rad_per_tick).astype(int).tolist()


def pose10d_to_mat(pose):
    a1, a2 = pose[3:6], pose[6:9]
    b1 = a1 / np.linalg.norm(a1)
    b2 = a2 - np.dot(b1, a2) * b1
    b2 /= np.linalg.norm(b2)
    out = np.eye(4)
    out[:3, :3] = np.stack((b1, b2, np.cross(b1, b2)))
    out[:3, 3] = pose[:3]
    return out


def tick_delta_allowed(delta, limit):
    return limit == 0 or abs(delta) <= limit


def apply_gripper_latch(candidates, current_tick, latch, offset_ticks):
    """Latch the final policy close target plus offset until a clear open command."""
    if not candidates or offset_ticks <= 0:
        return None

    policy_tick = candidates[-1]["predicted_gripper_tick"]
    threshold = offset_ticks
    newly_latched = False
    if latch is not None and policy_tick <= latch["policy_tick"] - threshold:
        latch = None
    elif latch is not None and policy_tick > latch["policy_tick"]:
        latch = {"policy_tick": policy_tick, "target_tick": policy_tick + offset_ticks}
    elif latch is None and policy_tick >= current_tick + threshold:
        latch = {"policy_tick": policy_tick, "target_tick": policy_tick + offset_ticks}
        newly_latched = True

    held_indices = {len(candidates) - 1} if newly_latched else range(len(candidates)) if latch else ()
    previous_tick = current_tick
    for index, candidate in enumerate(candidates):
        held = index in held_indices
        if held:
            candidate["gripper_tick"] = latch["target_tick"]
        candidate["gripper_latched"] = held
        candidate["gripper_close_offset_ticks"] = offset_ticks if held else 0
        candidate["gripper_delta"] = candidate["gripper_tick"] - previous_tick
        previous_tick = candidate["gripper_tick"]
    return latch


def plan_action(action, state, common, grasp, kinematics, args):
    previous_q = state["q"]
    previous_ticks = [state["ticks"][i] for i in common.ARM_IDS]
    previous_gripper = state["ticks"][common.GRIPPER_ID]
    candidates = []
    for action_index, row in enumerate(action[1:5], start=1):
        target = state["pose"] @ pose10d_to_mat(row[:9])
        target_table = kinematics.table_from_base(target[:3, 3])
        q, residual_mm, ik_ok = kinematics.ik(
            target[:3, 3], target[:3, 2], target[:3, 0], q0=previous_q
        )
        arm_ticks = grasp.rad_to_tick(q)
        arm_delta = [b - a for a, b in zip(previous_ticks, arm_ticks)]
        predicted_gripper_tick = grasp.width_to_tick(float(row[9]))
        gripper_tick = predicted_gripper_tick
        gripper_delta = gripper_tick - previous_gripper
        path_min_z_mm = float(grasp.path_min_z(previous_q, q))
        margin_ticks = joint_margin_ticks(q, kinematics.RAD_LIMITS, grasp.RAD_PER_TICK)
        approach_error_deg = float(np.degrees(np.arccos(np.clip(
            np.dot(kinematics.fk(q)[:3, 2], target[:3, 2]), -1, 1
        ))))
        workspace_ok = bool(
            args.workspace_x[0] <= target_table[0] <= args.workspace_x[1]
            and args.workspace_y[0] <= target_table[1] <= args.workspace_y[1]
            and args.workspace_z[0] <= target_table[2] <= args.workspace_z[1]
        )
        limits_ok = all(
            common.tick_limits(i)[0] <= tick <= common.tick_limits(i)[1]
            for i, tick in zip(common.ARM_IDS, arm_ticks)
        ) and common.tick_limits(common.GRIPPER_ID)[0] <= gripper_tick <= common.tick_limits(common.GRIPPER_ID)[1]
        # Diagnostics do not block execution. The motor-control boundary clamps
        # each target to the calibrated per-servo tick range.
        safe = True
        candidates.append({
            "action_index": action_index,
            "target_table_mm": np.round(target_table, 2).tolist(),
            "predicted_gripper_mm": float(row[9] * 1000),
            "predicted_gripper_tick": predicted_gripper_tick,
            "gripper_latched": False,
            "gripper_close_offset_ticks": 0,
            "arm_ticks": arm_ticks,
            "arm_delta": arm_delta,
            "gripper_tick": gripper_tick,
            "gripper_delta": gripper_delta,
            "ik_ok": bool(ik_ok),
            "ik_residual_mm": float(residual_mm),
            "approach_error_deg": approach_error_deg,
            "joint_margin_ticks": margin_ticks,
            "minimum_joint_margin_ticks": min(margin_ticks),
            "path_min_z_mm": path_min_z_mm,
            "workspace_ok": workspace_ok,
            "limits_ok": limits_ok,
            "safe": safe,
        })
        previous_q, previous_ticks, previous_gripper = q, arm_ticks, gripper_tick
    return candidates


def full_chunk_safe(candidates):
    return len(candidates) == 4 and all(candidate["safe"] for candidate in candidates)


def main(args):
    sys.path.insert(0, str(args.shanks_dir.resolve()))
    kinematics = importlib.import_module("kinematics")
    grasp = importlib.import_module("grasp")

    action = np.load(args.action, allow_pickle=False)
    if action.ndim != 2 or action.shape[0] < 5 or action.shape[1] != 10 or not np.isfinite(action).all():
        raise ValueError(f"expected finite (N>=5, 10) action, got {action.shape}")

    q_current = grasp.tick_to_rad(args.ticks)
    limits = np.asarray(kinematics.RAD_LIMITS)
    current_in_limits = bool(np.all((q_current >= limits[:, 0]) & (q_current <= limits[:, 1])))
    current_margin = joint_margin_ticks(q_current, limits, grasp.RAD_PER_TICK)
    base_current = kinematics.fk(q_current)
    previous_q, previous_ticks = q_current, list(args.ticks)
    candidates = []

    for index, row in enumerate(action[1:5], start=1):
        target = base_current @ pose10d_to_mat(row[:9])
        q, residual_mm, ik_ok = kinematics.ik(
            target[:3, 3], target[:3, 2], target[:3, 0], q0=previous_q
        )
        ticks = grasp.rad_to_tick(q)
        delta = [b - a for a, b in zip(previous_ticks, ticks)]
        min_z_mm = grasp.path_min_z(previous_q, q)
        margin_ticks = joint_margin_ticks(q, limits, grasp.RAD_PER_TICK)
        approach_error_deg = float(np.degrees(np.arccos(np.clip(
            np.dot(kinematics.fk(q)[:3, 2], target[:3, 2]), -1, 1
        ))))
        safe = True
        candidates.append({
            "action_index": index,
            "target_table_mm": np.round(kinematics.table_from_base(target[:3, 3]), 2).tolist(),
            "predicted_gripper_mm": float(row[9] * 1000),
            "ik_ok": bool(ik_ok),
            "ik_residual_mm": float(residual_mm),
            "approach_error_deg": approach_error_deg,
            "planned_ticks": ticks,
            "delta_ticks": delta,
            "max_abs_delta_ticks": max(map(abs, delta)),
            "joint_margin_ticks": margin_ticks,
            "minimum_joint_margin_ticks": min(margin_ticks),
            "path_min_z_mm": float(min_z_mm),
            "safe": safe,
        })
        previous_q, previous_ticks = q, ticks

    report = {
        "status": "PASS_DRY_RUN",
        "motor_commands_sent": 0,
        "calibration": "UNVALIDATED_CAD_CAMERA_TCP",
        "current_ticks": list(args.ticks),
        "current_joint_within_urdf_limits": current_in_limits,
        "current_joint_margin_ticks": current_margin,
        "minimum_required_joint_margin_ticks": args.min_joint_margin_ticks,
        "current_tcp_table_mm": np.round(kinematics.table_from_base(base_current[:3, 3]), 2).tolist(),
        "candidates": candidates,
    }
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--action", type=Path, required=True)
    parser.add_argument("--shanks-dir", type=Path, required=True)
    parser.add_argument("--ticks", type=int, nargs=5, required=True)
    parser.add_argument("--max-delta", type=int, default=300)
    parser.add_argument("--min-joint-margin-ticks", type=int, default=0)
    parser.add_argument("--min-z-mm", type=float, default=15)
    parser.add_argument("--out", type=Path)
    main(parser.parse_args())
