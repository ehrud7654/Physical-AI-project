"""Search a provisional SO-101 start registration across real UMI episodes.

The recorded oracle is evaluated before the learned policy.  A candidate is
useful only when its sampled oracle chunks are IK-reachable while the inferred
upright snack case is on the table, visible in the initial wrist-camera view,
and approached with horizontal jaw/approach axes.  This is an offline MuJoCo
search, never a physical robot calibration or motor-safety approval.
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
    eef_pose_from_joints,
    load_config,
)
from umi.convert import invert_gap_curve
from umi.ik import quat_to_matrix


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


def _candidate_starts(current: np.ndarray, ranges: np.ndarray, *, count: int,
                      seed: int) -> list[np.ndarray]:
    if count <= 0:
        raise ValueError("candidate count must be positive")
    lo, hi = ranges[:, 0], ranges[:, 1]
    margin = 0.08 * (hi - lo)
    lower, upper = lo + margin, hi - margin
    centre = np.clip(np.asarray(current, dtype=float), lower, upper)
    rng = np.random.default_rng(seed)
    candidates = [centre]
    local_count = max(0, int(round((count - 1) * 0.75)))
    sigma = 0.10 * (hi - lo)
    for _ in range(local_count):
        candidates.append(np.clip(centre + rng.normal(0.0, sigma), lower, upper))
    while len(candidates) < count:
        candidates.append(rng.uniform(lower, upper))
    return candidates


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
    visible = bool(np.max(np.abs(uv)) < 0.85 and centre_error <= 0.60
                   and 0.12 <= depth <= 0.30)
    return {"visible": visible, "centre_error_norm": centre_error,
            "depth_m": depth, "object_camera_xyz_m": local.tolist(),
            "camera_world_xyz_m": camera_xyz.tolist()}


def _mean(rows: list[bool]) -> float:
    return float(np.mean(rows)) if rows else 0.0


def _evaluate_candidate(*, candidate_id: int, arm_start: np.ndarray,
                        dataset: NumpyRelativeDataset,
                        episode_indices: list[int], samples_per_episode: int,
                        model: Any, ik: MujocoIK, cfg: dict[str, Any],
                        ranges: np.ndarray, real: dict[str, Any],
                        object_height_m: float) -> dict[str, Any]:
    curve = cfg["grasp"]["gap_curve"]
    data = mujoco.MjData(model)
    first_episode = dataset.episodes[episode_indices[0]]
    first_gap = float(first_episode["proprio"][0, -1, 9])
    q_start = np.r_[arm_start, invert_gap_curve(first_gap, curve)]
    data.qpos[:6] = q_start
    mujoco.mj_forward(model, data)
    start_table_contacts, start_self_contacts = _collision_counts(model, data)
    start_pos, start_quat = eef_pose_from_joints(model, data, q_start, ik.pinch)
    start_pose = np.eye(4)
    start_pose[:3, :3] = quat_to_matrix(start_quat)
    start_pose[:3, 3] = start_pos

    table = cfg["task"]["table"]
    table_top = float(table["pos"][2]) + float(table["half_size_m"][2])
    object_z = table_top + object_height_m / 2.0
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
        camera_clear = float(data.cam_xpos[
            mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_CAMERA, "cam_wrist"), 2]
        ) >= object_z + object_height_m / 2.0 + 0.03
        jaw_vertical = abs(float(contact_pose[2, 0]))
        approach_vertical = abs(float(contact_pose[2, 2]))
        horizontal_grasp = bool(jaw_vertical <= 0.25 and approach_vertical <= 0.35)
        half_xy = np.asarray(table["half_size_m"][:2], dtype=float)
        table_xy = np.asarray(table["pos"][:2], dtype=float)
        object_on_table = bool(np.all(np.abs(object_xyz[:2] - table_xy) <= half_xy - 0.02))
        grasp_height_error = abs(float(contact_pose[2, 3]) - object_z)

        contact_collision_free = False
        if arms[contact] is not None:
            data.qpos[:6] = np.r_[
                arms[contact], invert_gap_curve(float(gaps[contact]), curve)]
            mujoco.mj_forward(model, data)
            table_contacts, self_contacts = _collision_counts(model, data)
            contact_collision_free = table_contacts == 0 and self_contacts == 0
        else:
            table_contacts = self_contacts = None

        scene_ok = bool(
            start_table_contacts == 0 and start_self_contacts == 0
            and camera["visible"] and camera_clear and horizontal_grasp
            and object_on_table and grasp_height_error <= 0.02
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
            "camera_above_object_clearance": camera_clear,
            "jaw_vertical_abs": jaw_vertical,
            "approach_vertical_abs": approach_vertical,
            "horizontal_side_grasp": horizontal_grasp,
            "object_on_table": object_on_table,
            "grasp_height_error_m": grasp_height_error,
            "contact_table_contacts": table_contacts,
            "contact_self_contacts": self_contacts,
            "scene_constraints_ok": scene_ok,
        })

    scene_fraction = _mean([
        bool(report["scene_constraints_ok"]) for report in episode_reports])
    representative_pool = [
        report for report in episode_reports if report["scene_constraints_ok"]]
    if not representative_pool:
        representative_pool = episode_reports
    representative = min(
        representative_pool,
        key=lambda report: (
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
        "representative_episode": representative["episode"],
        "representative_object_xyz_m": representative["object_xyz_m"],
        "per_episode": episode_reports,
    }


def main() -> int:
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
    parser.add_argument("--seed", type=int, default=20260914)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if (args.candidates <= 0 or args.samples_per_episode <= 0
            or args.screen_episodes <= 0 or args.finalists <= 0
            or args.finalists > args.candidates):
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
    candidates = _candidate_starts(
        current, ranges, count=args.candidates, seed=args.seed)
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
        )
        screen_reports.append(report)
        print(
            f"screen {candidate_id + 1:3d}/{len(candidates)} "
            f"combined={report['combined_accepted_fraction']:.1%} "
            f"geometry={report['oracle_geometry_accepted_fraction']:.1%} "
            f"scene={report['scene_constraint_episode_fraction']:.1%}",
            flush=True,
        )

    def rank(report: dict[str, Any]) -> tuple[Any, ...]:
        return (
        report["combined_accepted_fraction"],
        report["oracle_geometry_accepted_fraction"],
        report["anchor_accepted_fraction"],
        report["scene_constraint_episode_fraction"],
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
        )
        reports.append(report)
        print(
            f"final {finalist_index + 1:2d}/{len(finalist_ids)} "
            f"candidate={candidate_id} "
            f"combined={report['combined_accepted_fraction']:.1%} "
            f"geometry={report['oracle_geometry_accepted_fraction']:.1%} "
            f"scene={report['scene_constraint_episode_fraction']:.1%}",
            flush=True,
        )
    reports.sort(key=rank, reverse=True)
    selected = reports[0]
    result = {
        "status": "PROVISIONAL_NOT_PHYSICAL_CALIBRATION",
        "source": source,
        "purpose": (
            "Multi-episode recorded-oracle registration search for offline IK "
            "preflight and camera-matched MuJoCo diagnostics only"
        ),
        "start_arm_rad": selected["start_arm_rad"],
        "object_xyz_m": selected["representative_object_xyz_m"],
        "object_size_m": object_size.tolist(),
        "representative_episode": selected["representative_episode"],
        "selection": {
            "validation_episodes": validation_ids,
            "candidates": args.candidates,
            "screen_episodes": [dataset.episode_ids[index]
                                for index in screen_indices],
            "finalists": args.finalists,
            "samples_per_episode": args.samples_per_episode,
            "seed": args.seed,
            "ranking": (
                "combined oracle geometry+scene fraction, then oracle geometry, "
                "anchor reachability and scene constraints"
            ),
        },
        "screened_candidates": [{
            key: report[key] for key in (
                "candidate_id", "start_arm_rad", "anchor_accepted_fraction",
                "oracle_geometry_accepted_fraction",
                "combined_accepted_fraction",
                "scene_constraint_episode_fraction",
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
        ],
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8")
    print(json.dumps({
        "selected_candidate": selected["candidate_id"],
        "start_arm_rad": selected["start_arm_rad"],
        "representative_episode": selected["representative_episode"],
        "object_xyz_m": selected["representative_object_xyz_m"],
        "combined_accepted_fraction": selected["combined_accepted_fraction"],
        "oracle_geometry_accepted_fraction": selected[
            "oracle_geometry_accepted_fraction"],
        "scene_constraint_episode_fraction": selected[
            "scene_constraint_episode_fraction"],
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
