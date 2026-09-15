"""Search a provisional SO-101 start registration across real UMI episodes.

The recorded oracle is evaluated before the learned policy.  A candidate is
useful only when its sampled oracle chunks are IK-reachable while the inferred
upright snack case is on the table, visible in the initial wrist-camera view,
and grasped around the middle of its body from the side.  Both the horizontal
approach direction and the orthogonal horizontal jaw-closing direction are
explicit task-frame constraints.  This is an offline MuJoCo search, never a
physical robot calibration or motor-safety approval.
"""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import sys
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import mujoco
import numpy as np

from umi.relative_robot_preflight import (
    FixedActionPolicy,
    arm_ranges,
    episode_anchors,
    evaluate_chunk,
    real_limits,
)
from tools.umi_mujoco import (
    DEFAULT_CONFIG,
    DEFAULT_SCENE,
    MujocoIK,
    build_model,
    load_config,
)
from tools.run_umi_regression import is_shared_gpu_server
from umi.convert import invert_gap_curve
from umi.ik import matrix_to_quat, quat_to_matrix
from umi.relative_dataset import vector_to_transform


AI_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REAL_CONFIG = AI_ROOT / "configs" / "real" / "so101_ver1.json"
DEFAULT_REGISTRATION = (
    AI_ROOT / "configs" / "real" / "umi_so101_registration_provisional.json"
)


class NumpyRelativeDataset:
    """Load only geometry arrays; registration search does not need images."""

    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        index = json.loads((self.root / "dataset.json").read_text(encoding="utf-8"))
        self.rate_hz = float(index["rate_hz"])
        self.episodes = []
        for episode_id in index["episodes"]:
            path = self.root / f"{episode_id}.npz"
            with np.load(path, allow_pickle=False) as stored:
                action = stored["action"].copy()
                proprio = stored["proprio"].copy()
            self.episodes.append({
                "id": str(episode_id), "action": action, "proprio": proprio,
            })

    @property
    def episode_ids(self) -> list[str]:
        return [str(episode["id"]) for episode in self.episodes]


def _sample_rows(length: int, count: int) -> np.ndarray:
    return np.unique(np.linspace(0, length - 1, min(length, count), dtype=int))


def _contact_row(gaps: np.ndarray) -> int:
    """Choose the first near-closed row, not an arbitrary late plateau row."""
    values = np.asarray(gaps, dtype=float)
    if values.ndim != 1 or not len(values) or not np.isfinite(values).all():
        raise ValueError("episode gaps must be a finite non-empty vector")
    opened = float(np.percentile(values, 90))
    closed = float(np.min(values))
    threshold = closed + 0.10 * max(0.0, opened - closed)
    reached = np.flatnonzero(values <= threshold)
    return int(reached[0]) if len(reached) else int(np.argmin(values))


def _mean_contact_rotation(dataset: NumpyRelativeDataset,
                           episode_indices: list[int]) -> np.ndarray:
    """Average start-to-contact rotations on SO(3), independent of base registration."""
    rotations = []
    for episode_index in episode_indices:
        episode = dataset.episodes[episode_index]
        gaps = np.asarray(episode["proprio"][:, -1, 9], dtype=float)
        contact = _contact_row(gaps)
        pose = np.eye(4)
        for row in range(contact):
            pose = pose @ vector_to_transform(episode["action"][row, 0])
        rotations.append(pose[:3, :3])
    summed = np.sum(rotations, axis=0)
    u, _, vt = np.linalg.svd(summed)
    correction = np.eye(3)
    correction[2, 2] = np.linalg.det(u @ vt)
    return u @ correction @ vt


def _candidate_starts(current: np.ndarray, ranges: np.ndarray, *, count: int,
                      seed: int, ik: MujocoIK,
                      initial_gap_m: float,
                      targeted_start_rotation: np.ndarray) -> tuple[list[np.ndarray], int]:
    if count <= 0:
        raise ValueError("candidate count must be positive")
    lo, hi = ranges[:, 0], ranges[:, 1]
    margin = 0.08 * (hi - lo)
    lower, upper = lo + margin, hi - margin
    centre = np.clip(np.asarray(current, dtype=float), lower, upper)
    rng = np.random.default_rng(seed)
    candidates = [centre]

    # Random joint configurations almost never land in the task's required side
    # approach orientation. Build candidates around the declared task-frame
    # approach and jaw axes. Jaw sign is irrelevant for parallel jaws and is
    # handled by the wrist-roll symmetry; approach sign is not interchangeable.
    rotation = np.asarray(targeted_start_rotation, dtype=float)
    target_count = max(0, int(round((count - 1) * 0.65)))
    xs = np.linspace(0.10, 0.32, 4)
    ys = np.linspace(-0.20, 0.20, 5)
    # The standing case centre is 38.35 mm above the table and the configured
    # grasp offset is only a few millimetres.  The old 0.08--0.14 m range could
    # select a pose whose pad merely clipped the case's top edge.  Include the
    # case-centre height directly; collision and IK checks reject unsafe seeds.
    zs = np.linspace(0.035, 0.10, 6)
    targets = [(float(x), float(y), float(z), rotation)
               for z in zs for y in ys for x in xs]
    rng.shuffle(targets)
    for x, y, z, rotation in targets:
        target_xyz = np.asarray([x, y, z])
        target_quat = matrix_to_quat(rotation)
        solution = ik.solve(target_xyz, target_quat, initial_gap_m, q_init=None)
        # A parallel jaw is unchanged by 180 degrees about the approach axis,
        # but the attached wrist camera is not.  The generic IK intentionally
        # folds that symmetry.  For registration candidate generation, test
        # both wrist-roll equivalents and retain the one whose canonical +Y
        # points in the requested (directed) up direction.
        directed_solutions = [solution]
        for delta in (np.pi, -np.pi):
            seed_q = np.asarray(solution.q_rad, dtype=float).copy()
            seed_q[4] += delta
            if ranges[4, 0] <= seed_q[4] <= ranges[4, 1]:
                directed_solutions.append(ik.solve(
                    target_xyz, target_quat, initial_gap_m, q_init=seed_q))

        def directed_score(value: Any) -> tuple[float, float, float]:
            _, achieved_quat = ik.forward_pose(value.q_rad)
            achieved_rotation = quat_to_matrix(achieved_quat)
            return (
                float(np.dot(achieved_rotation[:, 1], rotation[:, 1])),
                -float(value.pos_error_m),
                -float(value.axis_error_deg),
            )

        solution = max(directed_solutions, key=directed_score)
        _, achieved_quat = ik.forward_pose(solution.q_rad)
        achieved_rotation = quat_to_matrix(achieved_quat)
        directed_up_error = float(np.degrees(np.arccos(np.clip(
            np.dot(achieved_rotation[:, 1], rotation[:, 1]), -1.0, 1.0))))
        arm = np.asarray(solution.q_rad[:5], dtype=float)
        valid = bool(
            solution.converged
            and solution.pos_error_m <= 0.005
            and solution.axis_error_deg <= 5.0
            and directed_up_error <= 5.0
            and np.all(arm >= ranges[:, 0])
            and np.all(arm <= ranges[:, 1])
        )
        if valid and all(np.linalg.norm(arm - value) > 1e-3
                         for value in candidates):
            candidates.append(arm)
        if len(candidates) >= 1 + target_count:
            break

    targeted_count = len(candidates) - 1
    local_count = max(0, int(round((count - len(candidates)) * 0.65)))
    sigma = 0.10 * (hi - lo)
    for _ in range(local_count):
        candidates.append(np.clip(centre + rng.normal(0.0, sigma), lower, upper))
    while len(candidates) < count:
        candidates.append(rng.uniform(lower, upper))
    return candidates, targeted_count


def _collision_counts(model: Any, data: Any) -> tuple[int, int]:
    object_geom = mujoco.mj_name2id(
        model, mujoco.mjtObj.mjOBJ_GEOM, "target_object_geom")
    table_geom = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_GEOM, "table")
    table_robot = self_collision = 0
    for index in range(data.ncon):
        geom1 = int(data.contact[index].geom1)
        geom2 = int(data.contact[index].geom2)
        body1 = int(model.geom_bodyid[geom1])
        body2 = int(model.geom_bodyid[geom2])
        if table_geom in (geom1, geom2):
            other = geom2 if geom1 == table_geom else geom1
            if other != object_geom and int(model.geom_bodyid[other]) != 0:
                table_robot += 1
        if (body1 != 0 and body2 != 0 and body1 != body2
                and object_geom not in (geom1, geom2)):
            self_collision += 1
    return table_robot, self_collision


def _camera_metrics(model: Any, data: Any,
                    object_xyz: np.ndarray) -> dict[str, Any]:
    camera_id = mujoco.mj_name2id(
        model, mujoco.mjtObj.mjOBJ_CAMERA, "cam_wrist")
    camera_xyz = data.cam_xpos[camera_id].copy()
    camera_rotation = data.cam_xmat[camera_id].reshape(3, 3)
    local = camera_rotation.T @ (object_xyz - camera_xyz)
    depth = -float(local[2])
    if depth <= 1e-9:
        return {"visible": False, "centre_error_norm": None,
                "depth_m": depth, "object_camera_xyz_m": local.tolist(),
                "camera_world_xyz_m": camera_xyz.tolist()}
    half_fov = np.tan(np.deg2rad(float(model.cam_fovy[camera_id])) / 2.0)
    uv = np.asarray([local[0], local[1]]) / (depth * half_fov)
    centre_error = float(np.linalg.norm(uv))
    # The requirement here is only that the object appears in the initial view.
    # A 0.30 m far-depth cutoff incorrectly rejected centred objects that were
    # still inside the camera frustum.  Keep a near clearance, but let the
    # frustum test decide the far limit.
    visible = bool(np.max(np.abs(uv)) < 0.85 and centre_error <= 0.60
                   and depth >= 0.12)
    return {"visible": visible, "centre_error_norm": centre_error,
            "depth_m": depth, "object_camera_xyz_m": local.tolist(),
            "camera_world_xyz_m": camera_xyz.tolist()}


def _geom_vertical_interval(model: Any, data: Any,
                            geom_name: str) -> tuple[float, float, float]:
    """World-Z interval and centre of an oriented box collision pad."""
    geom_id = mujoco.mj_name2id(
        model, mujoco.mjtObj.mjOBJ_GEOM, geom_name)
    if geom_id < 0:
        raise ValueError(f"required gripper pad is missing: {geom_name}")
    if int(model.geom_type[geom_id]) != int(mujoco.mjtGeom.mjGEOM_BOX):
        raise ValueError(f"gripper pad must be a box: {geom_name}")
    centre_z = float(data.geom_xpos[geom_id, 2])
    rotation = data.geom_xmat[geom_id].reshape(3, 3)
    half_extent_z = float(
        np.dot(np.abs(rotation[2]), model.geom_size[geom_id, :3]))
    return centre_z - half_extent_z, centre_z + half_extent_z, centre_z


def _mean(rows: list[bool]) -> float:
    return float(np.mean(rows)) if rows else 0.0


def _evaluate_candidate(*, candidate_id: int, arm_start: np.ndarray,
                        dataset: NumpyRelativeDataset,
                        episode_indices: list[int], samples_per_episode: int,
                        model: Any, ik: MujocoIK, cfg: dict[str, Any],
                        ranges: np.ndarray, real: dict[str, Any],
                        object_height_m: float,
                        pad_center_margin_m: float,
                        desired_world_jaw_axis: np.ndarray,
                        jaw_yaw_tolerance_deg: float,
                        desired_world_approach_axis: np.ndarray,
                        approach_axis_tolerance_deg: float,
                        desired_world_up_axis: np.ndarray,
                        up_axis_tolerance_deg: float,
                        body_midheight_tolerance_m: float) -> dict[str, Any]:
    curve = cfg["grasp"]["gap_curve"]
    data = mujoco.MjData(model)
    first_episode = dataset.episodes[episode_indices[0]]
    first_gap = float(first_episode["proprio"][0, -1, 9])
    q_start = np.r_[arm_start, invert_gap_curve(first_gap, curve)]
    data.qpos[:6] = q_start
    mujoco.mj_forward(model, data)
    start_table_contacts, start_self_contacts = _collision_counts(model, data)
    start_pos, start_quat = ik.forward_pose(q_start)
    start_pose = np.eye(4)
    start_pose[:3, :3] = quat_to_matrix(start_quat)
    start_pose[:3, 3] = start_pos

    table = cfg["task"]["table"]
    table_top = float(table["pos"][2]) + float(table["half_size_m"][2])
    object_z = table_top + object_height_m / 2.0
    object_bottom = table_top
    object_top = table_top + object_height_m
    episode_reports: list[dict[str, Any]] = []
    total_samples = anchor_accepted = geometry_accepted = combined_accepted = 0
    geometry_rejections: Counter[str] = Counter()

    for episode_index in episode_indices:
        episode = dataset.episodes[episode_index]
        gaps = np.asarray(episode["proprio"][:, -1, 9], dtype=float)
        rows = _sample_rows(len(gaps), samples_per_episode)
        poses, arms = episode_anchors(
            episode, model=model, ik=ik, curve=curve,
            arm_start=arm_start, ranges=ranges)
        contact = _contact_row(gaps)
        contact_pose = poses[contact]
        object_xyz = np.asarray(
            [contact_pose[0, 3], contact_pose[1, 3], object_z], dtype=float)

        data.qpos[:6] = q_start
        mujoco.mj_forward(model, data)
        camera = _camera_metrics(model, data, object_xyz)
        camera_world_z = float(data.cam_xpos[
            mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_CAMERA, "cam_wrist"), 2]
        )
        camera_above_table = camera_world_z >= table_top
        jaw_vertical = abs(float(contact_pose[2, 0]))
        jaw_axis = np.asarray(contact_pose[:3, 0], dtype=float)
        approach_axis = np.asarray(contact_pose[:3, 2], dtype=float)
        jaw_alignment = abs(float(np.dot(
            jaw_axis, desired_world_jaw_axis)))
        jaw_yaw_error = float(np.degrees(np.arccos(np.clip(
            jaw_alignment, -1.0, 1.0))))
        jaw_yaw_aligned = jaw_yaw_error <= jaw_yaw_tolerance_deg
        approach_alignment = float(np.dot(
            approach_axis, desired_world_approach_axis))
        approach_axis_error = float(np.degrees(np.arccos(np.clip(
            approach_alignment, -1.0, 1.0))))
        approach_axis_aligned = (
            approach_axis_error <= approach_axis_tolerance_deg)
        up_axis = np.asarray(contact_pose[:3, 1], dtype=float)
        up_axis_error = float(np.degrees(np.arccos(np.clip(
            np.dot(up_axis, desired_world_up_axis), -1.0, 1.0))))
        up_axis_aligned = up_axis_error <= up_axis_tolerance_deg
        side_body_grasp = bool(
            jaw_vertical <= 0.25
            and abs(float(approach_axis[2])) <= 0.25
            and approach_axis_aligned
            and up_axis_aligned
        )
        half_xy = np.asarray(table["half_size_m"][:2], dtype=float)
        table_xy = np.asarray(table["pos"][:2], dtype=float)
        object_on_table = bool(np.all(np.abs(object_xyz[:2] - table_xy) <= half_xy - 0.02))
        grasp_height_error = abs(float(contact_pose[2, 3]) - object_z)
        pinch_z = float(contact_pose[2, 3])
        body_midheight_aligned = (
            grasp_height_error <= body_midheight_tolerance_m)

        contact_ik_available = arms[contact] is not None
        contact_collision_free = False
        pad_vertical_overlaps = {"pad_fixed": 0.0, "pad_moving": 0.0}
        pad_center_offsets = {"pad_fixed": None, "pad_moving": None}
        object_center_inside_both_pads = False
        if contact_ik_available:
            data.qpos[:6] = np.r_[
                arms[contact], invert_gap_curve(float(gaps[contact]), curve)]
            mujoco.mj_forward(model, data)
            table_contacts, self_contacts = _collision_counts(model, data)
            contact_collision_free = table_contacts == 0 and self_contacts == 0
            pad_centered = []
            for pad_name in pad_vertical_overlaps:
                low, high, centre = _geom_vertical_interval(
                    model, data, pad_name)
                pad_vertical_overlaps[pad_name] = max(
                    0.0, min(high, object_top) - max(low, object_bottom))
                pad_center_offsets[pad_name] = centre - object_z
                pad_centered.append(
                    low + pad_center_margin_m <= object_z
                    <= high - pad_center_margin_m)
            object_center_inside_both_pads = all(pad_centered)
        else:
            table_contacts = self_contacts = None

        contact_collision_free = bool(contact_collision_free)
        start_collision_free = bool(
            start_table_contacts == 0 and start_self_contacts == 0)
        scene_components = (
            start_collision_free,
            bool(camera["visible"]),
            camera_above_table,
            side_body_grasp,
            jaw_yaw_aligned,
            up_axis_aligned,
            object_on_table,
            body_midheight_aligned,
            object_center_inside_both_pads,
            contact_collision_free,
        )
        scene_component_fraction = float(np.mean(scene_components))
        scene_ok = bool(
            start_collision_free
            and camera["visible"] and camera_above_table and side_body_grasp
            and jaw_yaw_aligned and up_axis_aligned
            and object_on_table and body_midheight_aligned
            and object_center_inside_both_pads
            and contact_collision_free
        )
        episode_anchor = episode_geometry = episode_combined = 0
        for row in rows:
            total_samples += 1
            arm_current = arms[int(row)]
            if arm_current is None:
                continue
            anchor_accepted += 1
            episode_anchor += 1
            outcome = evaluate_chunk(
                FixedActionPolicy(episode["action"][int(row)]),
                {},
                ik=ik, curve=curve, current_pose=poses[int(row)],
                arm_current=arm_current,
                current_gap=float(gaps[int(row)]), ranges=ranges,
                gap_max=float(real["max_gap_m"]), dt=1.0 / dataset.rate_hz,
                max_arm_speed=float(real["max_speed_rad_s"]),
                max_arm_accel=float(real["max_accel_rad_s2"]),
                max_gap_speed=float(real["max_gap_speed_m_s"]),
                max_gap_accel=float(real["max_gap_accel_m_s2"]),
            )
            if outcome["result"] == "geometry_reject":
                geometry_rejections[outcome["reason"]] += 1
                continue
            geometry_accepted += 1
            episode_geometry += 1
            if scene_ok:
                combined_accepted += 1
                episode_combined += 1

        episode_reports.append({
            "episode": str(episode["id"]),
            "sampled_rows": int(len(rows)),
            "anchor_accepted": episode_anchor,
            "oracle_geometry_accepted": episode_geometry,
            "combined_accepted": episode_combined,
            "contact_row": contact,
            "object_xyz_m": object_xyz.tolist(),
            "first_view": camera,
            "camera_above_table": camera_above_table,
            "jaw_vertical_abs": jaw_vertical,
            "approach_vertical_abs": abs(float(approach_axis[2])),
            "approach_axis_world": approach_axis.tolist(),
            "desired_world_approach_axis": desired_world_approach_axis.tolist(),
            "approach_axis_error_deg": approach_axis_error,
            "approach_axis_tolerance_deg": approach_axis_tolerance_deg,
            "approach_axis_aligned": approach_axis_aligned,
            "up_axis_world": up_axis.tolist(),
            "desired_world_up_axis": desired_world_up_axis.tolist(),
            "up_axis_error_deg": up_axis_error,
            "up_axis_tolerance_deg": up_axis_tolerance_deg,
            "up_axis_aligned": up_axis_aligned,
            "upright_body_side_grasp": side_body_grasp,
            "jaw_axis_world": jaw_axis.tolist(),
            "desired_world_jaw_axis": desired_world_jaw_axis.tolist(),
            "jaw_yaw_error_deg": jaw_yaw_error,
            "jaw_yaw_tolerance_deg": jaw_yaw_tolerance_deg,
            "jaw_yaw_aligned": jaw_yaw_aligned,
            "object_on_table": object_on_table,
            "contact_pinch_z_m": pinch_z,
            "grasp_height_error_m": grasp_height_error,
            "body_midheight_tolerance_m": body_midheight_tolerance_m,
            "body_midheight_aligned": body_midheight_aligned,
            "contact_ik_available": contact_ik_available,
            "pad_vertical_overlap_m": pad_vertical_overlaps,
            "pad_center_z_offset_m": pad_center_offsets,
            "pad_center_margin_m": pad_center_margin_m,
            "object_center_inside_both_pads_with_margin": (
                object_center_inside_both_pads),
            "contact_table_contacts": table_contacts,
            "contact_self_contacts": self_contacts,
            "scene_component_fraction": scene_component_fraction,
            "scene_constraints_ok": scene_ok,
        })

    scene_fraction = _mean([
        bool(report["scene_constraints_ok"]) for report in episode_reports])
    scene_component_fraction = float(np.mean([
        report["scene_component_fraction"] for report in episode_reports]))
    side_grasp_fraction = _mean([
        bool(report["upright_body_side_grasp"])
        for report in episode_reports])
    approach_aligned_fraction = _mean([
        bool(report["approach_axis_aligned"])
        for report in episode_reports])
    yaw_aligned_fraction = _mean([
        bool(report["jaw_yaw_aligned"]) for report in episode_reports])
    up_aligned_fraction = _mean([
        bool(report["up_axis_aligned"]) for report in episode_reports])
    visible_fraction = _mean([
        bool(report["first_view"]["visible"]) for report in episode_reports])
    centered_fraction = _mean([
        bool(report["object_center_inside_both_pads_with_margin"])
        for report in episode_reports])
    contact_ik_reports = [
        report for report in episode_reports if report["contact_ik_available"]]
    contact_ik_fraction = len(contact_ik_reports) / len(episode_reports)
    centered_given_contact_ik = _mean([
        bool(report["object_center_inside_both_pads_with_margin"])
        for report in contact_ik_reports])
    collision_free_given_contact_ik = _mean([
        report["contact_table_contacts"] == 0
        and report["contact_self_contacts"] == 0
        for report in contact_ik_reports])
    # Prefer an episode that passes both the scene contract and at least one
    # sampled full-chunk geometry check. Choosing only by camera centring can
    # produce a neat view whose recorded tail is not executable.
    representative_pool = [
        report for report in episode_reports
        if report["scene_constraints_ok"] and report["combined_accepted"] > 0]
    if not representative_pool:
        representative_pool = [
            report for report in episode_reports if report["scene_constraints_ok"]]
    if not representative_pool:
        representative_pool = episode_reports
    representative = min(
        representative_pool,
        key=lambda report: (
            -report["combined_accepted"],
            -report["oracle_geometry_accepted"],
            report["first_view"]["centre_error_norm"]
            if report["first_view"]["centre_error_norm"] is not None else 1e9,
            report["episode"],
        ),
    )
    return {
        "candidate_id": candidate_id,
        "start_arm_rad": arm_start.tolist(),
        "start_pose": start_pose.tolist(),
        "start_table_contacts": start_table_contacts,
        "start_self_contacts": start_self_contacts,
        "sampled_chunks": total_samples,
        "anchor_accepted": anchor_accepted,
        "anchor_accepted_fraction": anchor_accepted / total_samples,
        "oracle_geometry_accepted": geometry_accepted,
        "oracle_geometry_accepted_fraction": geometry_accepted / total_samples,
        "oracle_geometry_rejections": dict(sorted(geometry_rejections.items())),
        "combined_accepted": combined_accepted,
        "combined_accepted_fraction": combined_accepted / total_samples,
        "scene_constraint_episode_fraction": scene_fraction,
        "scene_component_fraction": scene_component_fraction,
        "side_grasp_episode_fraction": side_grasp_fraction,
        "approach_axis_aligned_episode_fraction": approach_aligned_fraction,
        "jaw_yaw_aligned_episode_fraction": yaw_aligned_fraction,
        "up_axis_aligned_episode_fraction": up_aligned_fraction,
        "initial_object_visible_episode_fraction": visible_fraction,
        "pad_centered_episode_fraction": centered_fraction,
        "contact_ik_available_episode_fraction": contact_ik_fraction,
        "pad_centered_given_contact_ik_fraction": centered_given_contact_ik,
        "collision_free_given_contact_ik_fraction": (
            collision_free_given_contact_ik),
        "representative_episode": representative["episode"],
        "representative_object_xyz_m": representative["object_xyz_m"],
        "per_episode": episode_reports,
    }


def main() -> int:
    if is_shared_gpu_server():
        raise SystemExit(
            "Shared GPU server is training-only; run registration search locally")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--preflight", type=Path,
                        help="phase-aligned preflight JSON containing validation episodes")
    parser.add_argument("--episodes", nargs="*",
                        help="explicit episode ids; otherwise use --preflight or all episodes")
    parser.add_argument("--registration", type=Path, default=DEFAULT_REGISTRATION)
    parser.add_argument("--real-config", type=Path, default=DEFAULT_REAL_CONFIG)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--scene", type=Path, default=DEFAULT_SCENE)
    parser.add_argument("--candidates", type=int, default=64)
    parser.add_argument("--samples-per-episode", type=int, default=2)
    parser.add_argument("--screen-episodes", type=int, default=3,
                        help="episode count for the cheap first-stage screen")
    parser.add_argument("--finalists", type=int, default=4,
                        help="top candidates evaluated on every selected episode")
    parser.add_argument(
        "--pad-center-margin-m", type=float, default=0.005,
        help=(
            "required clearance between the inferred object centre and either "
            "end of the fixed pad (default: 5 mm)"
        ),
    )
    parser.add_argument("--seed", type=int, default=20260914)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if (args.candidates <= 0 or args.samples_per_episode <= 0
            or args.screen_episodes <= 0 or args.finalists <= 0
            or args.finalists > args.candidates
            or args.pad_center_margin_m < 0.0):
        raise SystemExit("candidate and sample counts must be positive")
    if args.out.exists():
        raise SystemExit(f"output already exists: {args.out}")

    source_registration = json.loads(
        args.registration.read_text(encoding="utf-8"))
    if source_registration.get("status") != "PROVISIONAL_NOT_PHYSICAL_CALIBRATION":
        raise ValueError("source registration is not explicitly provisional")
    current = np.asarray(source_registration["start_arm_rad"], dtype=float)
    object_size = np.asarray(source_registration["object_size_m"], dtype=float)
    if current.shape != (5,) or object_size.shape != (3,):
        raise ValueError("registration start_arm_rad/object_size_m shape mismatch")
    desired_jaw = np.asarray(
        source_registration.get("desired_world_jaw_axis"), dtype=float)
    if (desired_jaw.shape != (3,) or not np.isfinite(desired_jaw).all()
            or np.linalg.norm(desired_jaw[:2]) <= 1e-9
            or abs(float(desired_jaw[2])) > 1e-6):
        raise ValueError(
            "registration needs a finite horizontal desired_world_jaw_axis")
    desired_jaw = desired_jaw / np.linalg.norm(desired_jaw)
    jaw_yaw_tolerance = float(
        source_registration.get("jaw_yaw_tolerance_deg", 15.0))
    if not 0.0 <= jaw_yaw_tolerance < 90.0:
        raise ValueError("jaw_yaw_tolerance_deg must be within [0, 90)")
    desired_approach = np.asarray(
        source_registration.get("desired_world_approach_axis"), dtype=float)
    if (desired_approach.shape != (3,)
            or not np.isfinite(desired_approach).all()
            or np.linalg.norm(desired_approach[:2]) <= 1e-9
            or abs(float(desired_approach[2])) > 1e-6):
        raise ValueError(
            "registration needs a finite horizontal "
            "desired_world_approach_axis")
    desired_approach = desired_approach / np.linalg.norm(desired_approach)
    if abs(float(np.dot(desired_jaw, desired_approach))) > 1e-6:
        raise ValueError("desired jaw and approach axes must be orthogonal")
    approach_axis_tolerance = float(
        source_registration.get("approach_axis_tolerance_deg", 15.0))
    if not 0.0 <= approach_axis_tolerance < 90.0:
        raise ValueError("approach_axis_tolerance_deg must be within [0, 90)")
    desired_up = np.asarray(
        source_registration.get("desired_world_up_axis"), dtype=float)
    if (desired_up.shape != (3,) or not np.isfinite(desired_up).all()
            or np.linalg.norm(desired_up) <= 1e-9):
        raise ValueError("registration needs a finite desired_world_up_axis")
    desired_up = desired_up / np.linalg.norm(desired_up)
    if (abs(float(np.dot(desired_up, desired_jaw))) > 1e-6
            or abs(float(np.dot(desired_up, desired_approach))) > 1e-6):
        raise ValueError("desired up axis must be orthogonal to jaw and approach")
    up_axis_tolerance = float(
        source_registration.get("up_axis_tolerance_deg", 15.0))
    if not 0.0 <= up_axis_tolerance < 90.0:
        raise ValueError("up_axis_tolerance_deg must be within [0, 90)")
    body_midheight_tolerance = float(
        source_registration.get("body_midheight_tolerance_m", 0.012))
    if body_midheight_tolerance <= 0.0:
        raise ValueError("body_midheight_tolerance_m must be positive")

    dataset = NumpyRelativeDataset(args.data)
    if args.episodes:
        validation_ids = [str(value) for value in args.episodes]
        source = "explicit --episodes"
    elif args.preflight is not None:
        preflight = json.loads(args.preflight.read_text(encoding="utf-8"))
        validation_ids = [str(value) for value in preflight["validation_episodes"]]
        source = str(args.preflight)
    else:
        validation_ids = dataset.episode_ids
        source = "all dataset episodes"
    episode_by_id = {episode_id: index for index, episode_id
                     in enumerate(dataset.episode_ids)}
    missing = sorted(set(validation_ids) - set(episode_by_id))
    if missing:
        raise ValueError(f"validation episodes missing from dataset: {missing}")
    episode_indices = [episode_by_id[value] for value in validation_ids]

    mean_contact_rotation = _mean_contact_rotation(dataset, episode_indices)
    lateral = np.cross(desired_approach, desired_jaw)
    desired_contact_rotation = np.column_stack([
        desired_jaw, lateral, desired_approach])
    targeted_start_rotation = (
        desired_contact_rotation @ mean_contact_rotation.T)

    cfg = load_config(args.config)
    cfg["task"]["object"]["half_size_m"] = (object_size / 2.0).tolist()
    if "table_half_size_xy_m" in source_registration:
        cfg["task"]["table"]["half_size_m"][:2] = [
            float(value) for value in source_registration["table_half_size_xy_m"]]
    table_top = (float(cfg["task"]["table"]["pos"][2])
                 + float(cfg["task"]["table"]["half_size_m"][2]))
    cfg["task"]["object"]["init_pos"][2] = table_top + float(object_size[2]) / 2.0
    model = build_model(cfg, args.scene)
    ik = MujocoIK(model, cfg)
    real = real_limits(args.real_config)
    ranges = arm_ranges(real)
    first_selected_episode = dataset.episodes[episode_indices[0]]
    initial_gap = float(first_selected_episode["proprio"][0, -1, 9])
    candidates, targeted_candidate_count = _candidate_starts(
        current, ranges, count=args.candidates, seed=args.seed, ik=ik,
        initial_gap_m=initial_gap,
        targeted_start_rotation=targeted_start_rotation)
    print(
        f"candidate generation: side-grasp IK {targeted_candidate_count}, "
        f"random/local {len(candidates) - targeted_candidate_count - 1}, "
        "source registration 1",
        flush=True,
    )
    screen_count = min(args.screen_episodes, len(episode_indices))
    screen_positions = np.unique(np.linspace(
        0, len(episode_indices) - 1, screen_count, dtype=int))
    screen_indices = [episode_indices[int(value)] for value in screen_positions]
    screen_reports = []
    for candidate_id, arm in enumerate(candidates):
        report = _evaluate_candidate(
            candidate_id=candidate_id, arm_start=arm,
            dataset=dataset, episode_indices=screen_indices,
            samples_per_episode=1,
            model=model, ik=ik, cfg=cfg, ranges=ranges, real=real,
            object_height_m=float(object_size[2]),
            pad_center_margin_m=args.pad_center_margin_m,
            desired_world_jaw_axis=desired_jaw,
            jaw_yaw_tolerance_deg=jaw_yaw_tolerance,
            desired_world_approach_axis=desired_approach,
            approach_axis_tolerance_deg=approach_axis_tolerance,
            desired_world_up_axis=desired_up,
            up_axis_tolerance_deg=up_axis_tolerance,
            body_midheight_tolerance_m=body_midheight_tolerance,
        )
        screen_reports.append(report)
        print(
            f"screen {candidate_id + 1:3d}/{len(candidates)} "
            f"combined={report['combined_accepted_fraction']:.1%} "
            f"geometry={report['oracle_geometry_accepted_fraction']:.1%} "
            f"scene={report['scene_constraint_episode_fraction']:.1%} "
            f"side={report['side_grasp_episode_fraction']:.1%} "
            f"partial={report['scene_component_fraction']:.1%}",
            flush=True,
        )

    def rank(report: dict[str, Any]) -> tuple[Any, ...]:
        return (
            report["combined_accepted_fraction"],
            report["scene_constraint_episode_fraction"],
            report["pad_centered_episode_fraction"],
            report["contact_ik_available_episode_fraction"],
            report["pad_centered_given_contact_ik_fraction"],
            report["jaw_yaw_aligned_episode_fraction"],
            report["approach_axis_aligned_episode_fraction"],
            report["up_axis_aligned_episode_fraction"],
            report["side_grasp_episode_fraction"],
            report["scene_component_fraction"],
            report["oracle_geometry_accepted_fraction"],
            report["anchor_accepted_fraction"],
            -report["start_table_contacts"],
            -report["start_self_contacts"],
            -report["candidate_id"],
        )

    screen_reports.sort(key=rank, reverse=True)
    finalist_ids = [int(report["candidate_id"])
                    for report in screen_reports[:args.finalists]]
    reports = []
    for finalist_index, candidate_id in enumerate(finalist_ids):
        report = _evaluate_candidate(
            candidate_id=candidate_id, arm_start=candidates[candidate_id],
            dataset=dataset, episode_indices=episode_indices,
            samples_per_episode=args.samples_per_episode,
            model=model, ik=ik, cfg=cfg, ranges=ranges, real=real,
            object_height_m=float(object_size[2]),
            pad_center_margin_m=args.pad_center_margin_m,
            desired_world_jaw_axis=desired_jaw,
            jaw_yaw_tolerance_deg=jaw_yaw_tolerance,
            desired_world_approach_axis=desired_approach,
            approach_axis_tolerance_deg=approach_axis_tolerance,
            desired_world_up_axis=desired_up,
            up_axis_tolerance_deg=up_axis_tolerance,
            body_midheight_tolerance_m=body_midheight_tolerance,
        )
        reports.append(report)
        print(
            f"final {finalist_index + 1:2d}/{len(finalist_ids)} "
            f"candidate={candidate_id} "
            f"combined={report['combined_accepted_fraction']:.1%} "
            f"geometry={report['oracle_geometry_accepted_fraction']:.1%} "
            f"scene={report['scene_constraint_episode_fraction']:.1%} "
            f"side={report['side_grasp_episode_fraction']:.1%} "
            f"partial={report['scene_component_fraction']:.1%}",
            flush=True,
        )
    reports.sort(key=rank, reverse=True)
    selected = reports[0]
    accepted = bool(
        selected["combined_accepted_fraction"] > 0.0
        and selected["scene_constraint_episode_fraction"] > 0.0
    )
    result = {
        "status": (
            "PROVISIONAL_NOT_PHYSICAL_CALIBRATION"
            if accepted else "REJECTED_NO_VALID_REGISTRATION"
        ),
        "source": source,
        "purpose": (
            "Multi-episode recorded-oracle registration search for offline IK "
            "preflight and camera-matched MuJoCo diagnostics only"
        ),
        "start_arm_rad": selected["start_arm_rad"],
        "object_xyz_m": selected["representative_object_xyz_m"],
        "object_size_m": object_size.tolist(),
        "table_half_size_xy_m": [
            float(value) for value in cfg["task"]["table"]["half_size_m"][:2]
        ],
        "desired_world_jaw_axis": desired_jaw.tolist(),
        "jaw_yaw_tolerance_deg": jaw_yaw_tolerance,
        "desired_world_approach_axis": desired_approach.tolist(),
        "approach_axis_tolerance_deg": approach_axis_tolerance,
        "desired_world_up_axis": desired_up.tolist(),
        "up_axis_tolerance_deg": up_axis_tolerance,
        "body_midheight_tolerance_m": body_midheight_tolerance,
        "grasp_mode": source_registration.get("grasp_mode"),
        "axis_source": source_registration.get("axis_source"),
        "mean_start_to_contact_rotation": mean_contact_rotation.tolist(),
        "targeted_start_rotation": targeted_start_rotation.tolist(),
        "representative_episode": selected["representative_episode"],
        "selection": {
            "validation_episodes": validation_ids,
            "candidates": args.candidates,
            "side_grasp_ik_candidates": targeted_candidate_count,
            "screen_episodes": [dataset.episode_ids[index]
                                for index in screen_indices],
            "finalists": args.finalists,
            "samples_per_episode": args.samples_per_episode,
            "pad_center_margin_m": args.pad_center_margin_m,
            "seed": args.seed,
            "ranking": (
                "combined oracle geometry+scene fraction, exact scene fraction, "
                "object-centred pad grasp, side-approach task fidelity, partial scene "
                "constraints, oracle geometry and anchor reachability"
            ),
        },
        "screened_candidates": [{
            key: report[key] for key in (
                "candidate_id", "start_arm_rad", "anchor_accepted_fraction",
                "oracle_geometry_accepted_fraction",
                "combined_accepted_fraction",
                "scene_constraint_episode_fraction",
                "scene_component_fraction",
                "jaw_yaw_aligned_episode_fraction",
                "approach_axis_aligned_episode_fraction",
                "up_axis_aligned_episode_fraction",
                "side_grasp_episode_fraction",
                "initial_object_visible_episode_fraction",
                "pad_centered_episode_fraction",
                "contact_ik_available_episode_fraction",
                "pad_centered_given_contact_ik_fraction",
                "collision_free_given_contact_ik_fraction",
            )
        } for report in screen_reports],
        "selected": selected,
        "top10": reports[:10],
        "limitations": [
            "Search registration, not measured robot-base/workspace calibration.",
            "Object locations are inferred from each episode's near-closed pinch pose.",
            "Marker-midpoint hand-eye and object geometry remain provisional.",
            "Collision checks are kinematic MuJoCo screens, not hardware approval.",
            "Must not be used for real motor commands.",
            "A rejected status must not be passed to policy preflight.",
        ],
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    print(json.dumps({
        "status": result["status"],
        "selected_candidate": selected["candidate_id"],
        "start_arm_rad": selected["start_arm_rad"],
        "representative_episode": selected["representative_episode"],
        "object_xyz_m": selected["representative_object_xyz_m"],
        "combined_accepted_fraction": selected["combined_accepted_fraction"],
        "oracle_geometry_accepted_fraction": selected[
            "oracle_geometry_accepted_fraction"],
        "scene_constraint_episode_fraction": selected[
            "scene_constraint_episode_fraction"],
        "scene_component_fraction": selected["scene_component_fraction"],
        "jaw_yaw_aligned_episode_fraction": selected[
            "jaw_yaw_aligned_episode_fraction"],
        "approach_axis_aligned_episode_fraction": selected[
            "approach_axis_aligned_episode_fraction"],
        "up_axis_aligned_episode_fraction": selected[
            "up_axis_aligned_episode_fraction"],
        "side_grasp_episode_fraction": selected[
            "side_grasp_episode_fraction"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
