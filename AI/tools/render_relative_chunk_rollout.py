"""Camera-matched MuJoCo diagnostic for a relative UMI policy.

This is not a hardware validation or a deployment rollout.  The policy sees
only wrist RGB history and relative proprio history.  Simulator object state is
used exclusively for scoring and the debug overlay.
"""

from __future__ import annotations

import argparse
import copy
from collections import deque
from contextlib import nullcontext
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import mujoco
import numpy as np
from PIL import Image, ImageDraw

from sim.mujoco.build_scene import load_config, normalize
from sim.mujoco.env import MujocoPickEnv
from tools.run_umi_regression import is_shared_gpu_server
from tools.umi_mujoco import (
    MujocoArmAdapter,
    MujocoIK,
    apply_kinematic_grasp,
    gap_from_angle,
)
from umi.arm_preflight import prepare_arm_commands
from umi.convert import invert_gap_curve
from umi.relative_dataset import relative_vector
from umi.relative_robot_preflight import (
    FixedActionPolicy,
    arm_ranges,
    matrix_from_fk,
    real_limits,
)
from umi.timing import uniform_time_schedule


AI_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = AI_ROOT / "configs" / "so101.yaml"
DEFAULT_REAL_CONFIG = AI_ROOT / "configs" / "real" / "so101_ver1.json"
TABLETOP_MOTION_LIFT_CUTOFF_M = 0.005
MAX_DIAGNOSTIC_OBJECT_OFFSET_M = 0.03


def _registration(path: Path) -> dict:
    payload = json.loads(path.read_text(encoding="utf-8"))
    allowed = {
        "PROVISIONAL_NOT_PHYSICAL_CALIBRATION",
        "KINEMATIC_REGISTRATION_CANDIDATE_CAMERA_AND_DYNAMICS_UNVERIFIED",
        "KINEMATIC_REGISTRATION_CANDIDATE_NOT_DYNAMIC_READY",
        "DIAGNOSTIC_COLLISION_PROXY_CANDIDATE_NOT_PHYSICAL_VALIDATION",
        "DIAGNOSTIC_CANONICAL_REPLAY_NOT_REGISTRATION",
        "REJECTED_DIAGNOSTIC_CANONICAL_REPLAY",
    }
    if payload.get("status") not in allowed:
        raise ValueError("registration must be an explicitly provisional candidate")
    arm = np.asarray(payload.get("start_arm_rad"), dtype=float)
    obj = np.asarray(payload.get("object_xyz_m"), dtype=float)
    size = np.asarray(payload.get("object_size_m"), dtype=float)
    table_half_xy = np.asarray(payload.get("table_half_size_xy_m"), dtype=float)
    if (arm.shape != (5,) or obj.shape != (3,) or size.shape != (3,)
            or not np.isfinite(arm).all() or not np.isfinite(obj).all()
            or not np.isfinite(size).all() or np.any(size <= 0)
            or table_half_xy.shape != (2,)
            or not np.isfinite(table_half_xy).all()
            or np.any(table_half_xy <= 0)):
        raise ValueError(
            "registration needs finite start_arm_rad[5], object xyz/size[3], "
            "and table_half_size_xy_m[2]")
    return {**payload, "start_arm_rad": arm, "object_xyz_m": obj,
            "object_size_m": size, "table_half_size_xy_m": table_half_xy}


def _episode_object_xyz(registration: dict, episode_id: str) -> np.ndarray:
    selected = registration.get("selected", {})
    for row in selected.get("per_episode", []):
        if str(row.get("episode")) == episode_id:
            value = np.asarray(row.get("object_xyz_m"), dtype=float)
            if value.shape == (3,) and np.isfinite(value).all():
                return value
    if episode_id == str(registration.get("representative_episode")):
        return np.asarray(registration["object_xyz_m"], dtype=float)
    raise ValueError(f"registration has no object position for {episode_id}")


def _diagnostic_object_xyz(
        registration: dict, episode_id: str,
        offset_xy_m: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Apply a bounded object-only shift without moving the robot start."""
    base = _episode_object_xyz(registration, episode_id)
    offset = np.asarray(offset_xy_m, dtype=float)
    if (offset.shape != (2,) or not np.isfinite(offset).all()
            or np.any(np.abs(offset) > MAX_DIAGNOSTIC_OBJECT_OFFSET_M)):
        raise ValueError(
            "diagnostic object XY offset must be finite and no larger than "
            f"{MAX_DIAGNOSTIC_OBJECT_OFFSET_M:.3f}m per axis")
    shifted = base.copy()
    shifted[:2] += offset
    return base, shifted


def _episode_approach_start(
        registration: dict, episode_id: str) -> tuple[int, np.ndarray, float]:
    """Read one predeclared static-gate start; never search at rollout time."""
    selected = registration.get("selected", {})
    for row in selected.get("per_episode", []):
        if str(row.get("episode")) != episode_id:
            continue
        start_row = int(row.get("approach_start_row", -1))
        arm = np.asarray(row.get("approach_start_arm_rad"), dtype=float)
        gap = float(row.get("approach_start_executed_gap_m", float("nan")))
        if (row.get("scene_constraints_ok") is not True or start_row < 0
                or arm.shape != (5,) or not np.isfinite(arm).all()
                or not np.isfinite(gap) or gap < 0.0):
            raise ValueError(
                f"registration has no valid fixed approach start for {episode_id}")
        return start_row, arm, gap
    raise ValueError(f"registration has no per-episode row for {episode_id}")


def _stable_side_grasp_success(
        *, mujoco_success: bool, pre_lift_xy_displacement_m: float,
        xy_threshold_m: float, max_tilt_deg: float,
        tilt_threshold_deg: float) -> bool:
    """Score grasp stability without treating post-lift transport as a shove."""
    return bool(
        mujoco_success
        and pre_lift_xy_displacement_m < xy_threshold_m
        and max_tilt_deg < tilt_threshold_deg
    )


def _episode(data_root: Path, episode_id: str) -> dict[str, np.ndarray]:
    path = data_root / f"{episode_id}.npz"
    if not path.is_file():
        raise ValueError(f"representative episode is absent: {path}")
    with np.load(path, allow_pickle=False) as stored:
        episode = {key: stored[key] for key in stored.files}
    gap = float(episode["proprio"][0, -1, 9])
    if not np.isfinite(gap) or gap < 0:
        raise ValueError("representative initial gap is invalid")
    return episode


def _policy_observation(images: deque[np.ndarray], poses: deque[np.ndarray],
                        gaps: deque[float]) -> dict[str, np.ndarray]:
    if not (len(images) == len(poses) == len(gaps) == 2):
        raise ValueError("relative policy runtime requires two history samples")
    current = poses[-1]
    proprio = np.stack([
        relative_vector(current, pose, gap)
        for pose, gap in zip(poses, gaps)
    ]).astype(np.float32)
    return {"image": np.stack(images).astype(np.uint8), "proprio": proprio}


def _perturb_policy_images(
        image: np.ndarray, *, mode: str, rng: np.random.Generator,
        frozen: np.ndarray | None, noise_gray: float,
) -> tuple[np.ndarray, np.ndarray | None]:
    """Alter only policy RGB input; rendered/scored simulator state is untouched."""
    source = np.asarray(image)
    if source.dtype != np.uint8:
        raise ValueError("policy image perturbation requires uint8 input")
    if mode == "none":
        return source, frozen
    if mode == "blackout":
        return np.zeros_like(source), frozen
    if mode == "mean_fill":
        # Preserve each history frame's channel means while removing every
        # spatial cue.  This is a less extreme no-spatial-information control
        # than pure black, which is itself a severe OOD colour distribution.
        means = np.rint(source.mean(axis=(-2, -1), keepdims=True))
        return np.broadcast_to(means, source.shape).astype(np.uint8).copy(), frozen
    if mode == "noise":
        noisy = np.clip(
            source.astype(np.float32)
            + rng.normal(0.0, noise_gray, size=source.shape),
            0.0,
            255.0,
        ).astype(np.uint8)
        return noisy, frozen
    if mode == "freeze":
        if frozen is None:
            frozen = source.copy()
        return frozen.copy(), frozen
    raise ValueError(f"unsupported image perturbation: {mode}")


def _interpolated_joint_target(*, segment_start_q: np.ndarray,
                               target_arm_rad: np.ndarray,
                               segment_start_gap_m: float,
                               target_gap_m: float, alpha: float,
                               curve) -> np.ndarray:
    """Interpolate one scheduled motor target, including gap in metric space."""
    start = np.asarray(segment_start_q, dtype=float)
    target_arm = np.asarray(target_arm_rad, dtype=float)
    if (start.shape != (6,) or target_arm.shape != (5,)
            or not np.isfinite(start).all() or not np.isfinite(target_arm).all()
            or not np.isfinite(alpha) or not 0.0 < alpha <= 1.0):
        raise ValueError("invalid scheduled interpolation input")
    arm = start[:5] + (target_arm - start[:5]) * alpha
    gap = float(
        segment_start_gap_m
        + (target_gap_m - segment_start_gap_m) * alpha
    )
    return np.r_[arm, invert_gap_curve(gap, curve)]


class _ValidationPrefixPolicy:
    """Restrict validation to the commands this local diagnostic will execute.

    Production keeps whole-chunk validation.  This adapter exists only to
    distinguish an unreachable distant waypoint from the next command's local
    geometry during an offline MuJoCo diagnosis.
    """

    def __init__(self, policy, steps: int) -> None:
        self.policy = policy
        self.steps = int(steps)

    def predict_action(self, observation):
        output = self.policy.predict_action(observation)
        action = np.asarray(output["action_pred"])
        return {"action_pred": action[:self.steps].copy()}


class _GapPreloadPolicy:
    """Apply simulation-only under-closure to near-closed aperture targets.

    UMI measures aperture rather than contact force. A target equal to object
    width merely touches the MuJoCo body, so this diagnostic wrapper measures
    the runtime preload required by the simulated contact model without
    changing any stored training label.
    """

    def __init__(self, policy, *, preload_m: float,
                 activation_gap_m: float) -> None:
        self.policy = policy
        self.preload_m = float(preload_m)
        self.activation_gap_m = float(activation_gap_m)

    def predict_action(self, observation):
        output = self.policy.predict_action(observation)
        action = np.asarray(output["action_pred"]).copy()
        active = action[..., -1] <= self.activation_gap_m
        action[..., -1] = np.where(
            active,
            np.maximum(0.0, action[..., -1] - self.preload_m),
            action[..., -1],
        )
        return {"action_pred": action}


def _save_gif(frames: list[np.ndarray], path: Path, fps: int) -> None:
    if not frames:
        raise ValueError("cannot save an empty rollout")
    path.parent.mkdir(parents=True, exist_ok=True)
    images = [Image.fromarray(frame) for frame in frames]
    images[0].save(
        path, save_all=True, append_images=images[1:],
        duration=max(1, round(1000 / fps)), loop=0,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--policy-ckpt", type=Path,
                        help="required for learned-policy mode; unused by --oracle")
    parser.add_argument("--registration", type=Path, required=True)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--observation-source", choices=("dataset", "simulation"),
                        default="dataset")
    parser.add_argument(
        "--image-perturb",
        choices=("none", "noise", "blackout", "mean_fill", "freeze"),
        default="none",
        help="learned-policy inference only; alter RGB without changing state/scoring",
    )
    parser.add_argument("--image-noise-gray", type=float, default=96.0)
    parser.add_argument("--image-perturb-seed", type=int, default=0)
    parser.add_argument(
        "--object-offset-x-m", type=float, default=0.0,
        help=(
            "local diagnostic only: shift the simulated object in world X "
            "without shifting the registered robot approach start"
        ),
    )
    parser.add_argument(
        "--object-offset-y-m", type=float, default=0.0,
        help=(
            "local diagnostic only: shift the simulated object in world Y "
            "without shifting the registered robot approach start"
        ),
    )
    parser.add_argument("--oracle", action="store_true",
                        help="replay recorded relative targets instead of policy output")
    parser.add_argument("--episode",
                        help="override the registration's representative episode")
    parser.add_argument(
        "--use-registration-approach-start", action="store_true",
        help=(
            "start at the fixed contact-relative row, arm state and executed "
            "gap stored by the static diagnostic; applies equally to oracle "
            "and learned-policy diagnostics"
        ),
    )
    parser.add_argument("--cycles", type=int, default=30)
    parser.add_argument("--execute-steps", type=int, default=1)
    parser.add_argument(
        "--validation-horizon-steps", type=int,
        help=(
            "local diagnosis only: validate this prefix instead of the entire "
            "predicted chunk; must be >= --execute-steps"
        ),
    )
    parser.add_argument(
        "--execute-final-oracle-tail", action="store_true",
        help=(
            "recorded-oracle diagnosis only: on the final dataset row, validate "
            "and execute the whole remaining target chunk instead of dropping "
            "its future tail"
        ),
    )
    parser.add_argument("--capture-every", type=int, default=2)
    parser.add_argument(
        "--no-video", action="store_true",
        help=(
            "skip all camera rendering and write only the JSON report; intended "
            "for local multi-episode recorded-oracle gates"
        ),
    )
    parser.add_argument("--tracking-time-margin", type=float, default=1.0,
                        help="simulation-only multiplier after required dynamic retiming")
    parser.add_argument(
        "--grip-preload-m", type=float, default=0.0,
        help=(
            "simulation-only under-closure for near-closed gap targets; "
            "dataset labels remain unchanged"
        ),
    )
    parser.add_argument(
        "--grip-preload-activation-gap-m", type=float, default=0.055,
        help="apply grip preload only at or below this target gap",
    )
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument(
        "--kinematic-grasp", type=Path,
        help=(
            "override the registration's ver1 kinematic block; recorded-oracle "
            "dynamics require its explicit provisional collision proxy"
        ),
    )
    parser.add_argument("--real-config", type=Path, default=DEFAULT_REAL_CONFIG)
    parser.add_argument("--out", type=Path, required=True,
                        help="output .gif; a JSON report is written beside it")
    args = parser.parse_args()
    if is_shared_gpu_server() and not args.oracle:
        raise SystemExit(
            "Learned-policy inference is forbidden on the shared GPU server; "
            "use --oracle for recorded-target MuJoCo simulation")
    if not args.oracle and args.policy_ckpt is None:
        raise SystemExit("--policy-ckpt is required unless --oracle is used")
    if args.oracle and args.image_perturb != "none":
        raise SystemExit("--image-perturb is meaningless with --oracle")
    if args.device != "cpu":
        raise SystemExit("this local diagnostic currently requires --device cpu")
    if args.out.suffix.lower() != ".gif":
        raise SystemExit("--out must end in .gif")
    report_path = args.out.with_suffix(".json")
    if args.out.exists() or report_path.exists():
        raise SystemExit("output already exists")
    if (args.cycles <= 0 or args.execute_steps <= 0 or args.capture_every <= 0
            or not np.isfinite(args.tracking_time_margin)
            or args.tracking_time_margin < 1.0
            or not np.isfinite(args.grip_preload_m)
            or args.grip_preload_m < 0.0
            or not np.isfinite(args.grip_preload_activation_gap_m)
            or args.grip_preload_activation_gap_m <= 0.0
            or not np.isfinite(args.image_noise_gray)
            or args.image_noise_gray <= 0.0):
        raise SystemExit("cycles, execute-steps and capture-every must be positive")

    registration = _registration(args.registration)
    if (registration["status"] == "DIAGNOSTIC_CANONICAL_REPLAY_NOT_REGISTRATION"
            and not args.use_registration_approach_start):
        raise SystemExit(
            "diagnostic canonical replay requires "
            "--use-registration-approach-start")
    representative = str(args.episode or registration["representative_episode"])
    registration_object_xyz, object_xyz = _diagnostic_object_xyz(
        registration,
        representative,
        np.asarray([args.object_offset_x_m, args.object_offset_y_m], dtype=float),
    )
    episode = _episode(args.data, representative)
    start_row = 0
    start_arm = registration["start_arm_rad"]
    initial_gap = float(episode["proprio"][0, -1, 9])
    if args.use_registration_approach_start:
        start_row, start_arm, initial_gap = _episode_approach_start(
            registration, representative)
        if start_row >= len(episode["action"]):
            raise ValueError("fixed approach start is outside the episode")
    if args.oracle:
        dataset_meta = json.loads(
            (args.data / "dataset.json").read_text(encoding="utf-8"))
        runtime_meta = dataset_meta
        policy = None
    else:
        # Keep torch entirely outside recorded-oracle diagnostics.  Replaying
        # labels does not need a network or a GPU-capable Python environment.
        from policy.relative_chunk_bc import RelativeChunkBCPolicy
        policy = RelativeChunkBCPolicy(args.policy_ckpt, device="cpu")
        runtime_meta = policy.meta
    if int(runtime_meta["observation_horizon"]) != 2:
        raise ValueError("diagnostic currently requires observation_horizon=2")
    action_horizon = int(runtime_meta["action_horizon"])
    validation_horizon = (
        action_horizon if args.validation_horizon_steps is None
        else int(args.validation_horizon_steps)
    )
    if not args.execute_steps <= validation_horizon <= action_horizon:
        raise SystemExit(
            "validation-horizon-steps must be between execute-steps and the "
            "checkpoint action horizon"
        )
    if args.execute_final_oracle_tail and not args.oracle:
        raise SystemExit("--execute-final-oracle-tail requires --oracle")

    cfg = copy.deepcopy(load_config(args.config))
    kinematic_grasp = registration.get("kinematic_grasp")
    if args.kinematic_grasp is not None:
        kinematic_grasp = json.loads(
            args.kinematic_grasp.read_text(encoding="utf-8"))
    if kinematic_grasp is not None:
        apply_kinematic_grasp(cfg, kinematic_grasp)
    diagnostic_proxy_ready = bool(
        isinstance(kinematic_grasp, dict)
        and kinematic_grasp.get("collision_proxy", {}).get(
            "diagnostic_dynamic_ready") is True
    )
    if registration["status"].startswith("KINEMATIC_") and not diagnostic_proxy_ready:
        raise ValueError(
            "kinematic-only registration cannot run contact dynamics without "
            "an explicit provisional collision proxy"
        )
    cfg["task"]["object"]["half_size_m"] = (
        registration["object_size_m"] / 2.0).tolist()
    cfg["task"]["object"]["init_pos"] = object_xyz.tolist()
    cfg["task"]["table"]["half_size_m"][:2] = (
        registration["table_half_size_xy_m"].tolist())
    real = real_limits(args.real_config)
    ranges = arm_ranges(real)
    if np.any(start_arm < ranges[:, 0]) or np.any(start_arm > ranges[:, 1]):
        raise ValueError("registered start arm exceeds real limits")
    curve = cfg["grasp"]["gap_curve"]
    initial_q = np.r_[start_arm, invert_gap_curve(initial_gap, curve)]
    object_xy = tuple(float(value) for value in object_xyz[:2])
    source_period = 1.0 / float(runtime_meta["rate_hz"])

    frames: list[np.ndarray] = []
    time_scales: list[float] = []
    periods: list[float] = []
    closest_xy = float("inf")
    closest_3d = float("inf")
    first_contact_cycle = None
    first_bilateral_contact_cycle = None
    bilateral_contact_ticks = 0
    max_simultaneous_contact_pads = 0
    geometry_error = None
    final_oracle_tail_commands = 0
    executed_commands = 0
    simulation_ticks = 0
    max_tracking_error = 0.0
    max_endpoint_tracking_error = 0.0
    max_lift_height = 0.0
    min_lift_height = 0.0
    max_object_xy_displacement = 0.0
    max_pre_lift_object_xy_displacement = 0.0
    max_object_tilt_deg = 0.0
    cycle_trace: list[dict[str, object]] = []
    command_trace: list[dict[str, object]] = []
    perturb_rng = np.random.default_rng(args.image_perturb_seed)
    frozen_policy_images: np.ndarray | None = None

    with MujocoPickEnv(
        cfg, render=not args.no_video, object_jitter_m=0.0,
        max_ticks=max(1, args.cycles * args.execute_steps * 20),
    ) as env:
        observation = env.reset(seed=0, object_xy=object_xy, initial_q_rad=initial_q)
        ik = MujocoIK(env.model, cfg)
        solver = MujocoArmAdapter(ik, curve)
        current_q = env.joint_positions()
        current_pose = matrix_from_fk(env.model, ik, current_q)
        current_gap = gap_from_angle(float(current_q[5]), curve)
        initial_object_xyz = env.object_position()
        initial_object_up = env.object_rotation()[:, 2]
        object_xy_displacement_threshold = max(
            0.01, 0.5 * float(np.min(registration["object_size_m"][:2])))
        object_tilt_threshold_deg = 30.0
        images: deque[np.ndarray] = deque(
            [observation.images["cam_wrist"].copy()] * 2, maxlen=2)
        poses: deque[np.ndarray] = deque(
            [current_pose.copy()] * 2, maxlen=2)
        gaps: deque[float] = deque([current_gap] * 2, maxlen=2)

        debug_camera = mujoco.MjvCamera()
        debug_camera.type = mujoco.mjtCamera.mjCAMERA_FREE
        debug_camera.lookat[:] = object_xyz
        debug_camera.distance = 0.62
        debug_camera.azimuth = 135
        debug_camera.elevation = -25
        renderer_context = (
            nullcontext(None)
            if args.no_video else
            mujoco.Renderer(env.model, height=448, width=448)
        )
        with renderer_context as renderer:
            initial_xy, initial_3d = env.pinch_to_object_m()
            closest_xy, closest_3d = initial_xy, initial_3d
            if renderer is not None:
                initial_policy_image = (
                    episode["image"][start_row, -1]
                    if args.observation_source == "dataset"
                    else observation.images["cam_wrist"]
                )
                wrist = np.transpose(initial_policy_image, (1, 2, 0))
                wrist = np.asarray(Image.fromarray(wrist).resize((448, 448)))
                renderer.update_scene(env.data, camera=debug_camera)
                external = renderer.render().copy()
                canvas = Image.new("RGB", (896, 484), "white")
                canvas.paste(Image.fromarray(wrist), (0, 36))
                canvas.paste(Image.fromarray(external), (448, 36))
                ImageDraw.Draw(canvas).text(
                    (8, 8),
                    f"initial  xy={initial_xy*1000:.1f}mm "
                    f"3d={initial_3d*1000:.1f}mm contacts={env.jaw_contacts()}",
                    fill="black",
                )
                frames.append(np.asarray(canvas))
            cycle_count = (
                min(args.cycles, len(episode["action"]) - start_row)
                if args.observation_source == "dataset" or args.oracle
                else args.cycles
            )
            for cycle in range(cycle_count):
                row = min(start_row + cycle, len(episode["action"]) - 1)
                final_oracle_tail = bool(
                    args.execute_final_oracle_tail
                    and cycle == cycle_count - 1
                )
                runtime_obs = (
                    {
                        "image": episode["image"][row].copy(),
                        "proprio": episode["proprio"][row].copy(),
                    }
                    if args.observation_source == "dataset"
                    else _policy_observation(images, poses, gaps)
                )
                if not args.oracle:
                    perturbed, frozen_policy_images = _perturb_policy_images(
                        runtime_obs["image"],
                        mode=args.image_perturb,
                        rng=perturb_rng,
                        frozen=frozen_policy_images,
                        noise_gray=args.image_noise_gray,
                    )
                    runtime_obs["image"] = perturbed
                base_candidate = (
                    FixedActionPolicy(episode["action"][row])
                    if args.oracle else policy
                )
                if args.grip_preload_m > 0.0:
                    base_candidate = _GapPreloadPolicy(
                        base_candidate,
                        preload_m=args.grip_preload_m,
                        activation_gap_m=args.grip_preload_activation_gap_m,
                    )
                candidate = base_candidate
                if validation_horizon < action_horizon and not final_oracle_tail:
                    candidate = _ValidationPrefixPolicy(
                        candidate, validation_horizon)
                current_q = env.joint_positions()
                current_pose = matrix_from_fk(env.model, ik, current_q)
                current_gap = gap_from_angle(float(current_q[5]), curve)
                try:
                    commands = prepare_arm_commands(
                        candidate, runtime_obs,
                        t_current=current_pose,
                        arm_current_rad=current_q[:5],
                        gripper_current_m=current_gap,
                        solver=solver,
                        ranges_rad=ranges,
                        gap_range_m=[0.0, float(real["max_gap_m"])],
                        max_step_rad=ranges[:, 1] - ranges[:, 0],
                        max_gap_step_m=float(real["max_gap_m"]),
                        max_position_error_m=0.005,
                        max_axis_error_deg=5.0,
                        max_roll_error_deg=5.0,
                    )
                except ValueError as error:
                    geometry_error = str(error)
                    if not final_oracle_tail:
                        break
                    # Preserve the valid prefix instead of dropping the whole
                    # final recorded tail when a later waypoint leaves the
                    # five-DoF manifold.  This is oracle diagnosis only; learned
                    # policy execution keeps fail-closed whole-chunk validation.
                    commands = []
                    for prefix in range(1, action_horizon + 1):
                        try:
                            prefix_commands = prepare_arm_commands(
                                _ValidationPrefixPolicy(base_candidate, prefix),
                                runtime_obs,
                                t_current=current_pose,
                                arm_current_rad=current_q[:5],
                                gripper_current_m=current_gap,
                                solver=solver,
                                ranges_rad=ranges,
                                gap_range_m=[0.0, float(real["max_gap_m"])],
                                max_step_rad=ranges[:, 1] - ranges[:, 0],
                                max_gap_step_m=float(real["max_gap_m"]),
                                max_position_error_m=0.005,
                                max_axis_error_deg=5.0,
                                max_roll_error_deg=5.0,
                            )
                        except ValueError:
                            break
                        commands = prefix_commands
                    if not commands:
                        break

                arm_path = np.vstack([
                    current_q[:5],
                    *[command.arm_positions_rad for command in commands],
                ])
                gap_path = np.asarray([
                    current_gap,
                    *[command.gripper_width_m for command in commands],
                ])
                schedule = uniform_time_schedule(
                    arm_path, gap_path, source_period_s=source_period,
                    max_arm_speed=float(real["max_speed_rad_s"]),
                    max_arm_accel=float(real["max_accel_rad_s2"]),
                    max_gap_speed=float(real["max_gap_speed_m_s"]),
                    max_gap_accel=float(real["max_gap_accel_m_s2"]),
                )
                time_scales.append(schedule.time_scale)
                command_period = (
                    schedule.waypoint_time_s[1] * args.tracking_time_margin)
                periods.append(command_period)

                executed_cycle_commands = (
                    commands if final_oracle_tail
                    else commands[:args.execute_steps]
                )
                if final_oracle_tail:
                    final_oracle_tail_commands = len(executed_cycle_commands)
                for chunk_step, command in enumerate(executed_cycle_commands):
                    raw_target = np.r_[
                        command.arm_positions_rad,
                        invert_gap_curve(command.gripper_width_m, curve),
                    ]
                    target_pinch_xyz, target_pinch_quat = ik.forward_pose(
                        raw_target)
                    ticks = max(1, int(np.ceil(
                        command_period * env.control_rate_hz)))
                    segment_start_q = env.joint_positions()
                    segment_start_gap = gap_from_angle(
                        float(segment_start_q[5]), curve)
                    for tick_index in range(ticks):
                        alpha = (tick_index + 1) / ticks
                        interpolated_target = _interpolated_joint_target(
                            segment_start_q=segment_start_q,
                            target_arm_rad=command.arm_positions_rad,
                            segment_start_gap_m=segment_start_gap,
                            target_gap_m=command.gripper_width_m,
                            alpha=alpha, curve=curve,
                        )
                        interpolated_arm = interpolated_target[:5]
                        action = normalize(interpolated_target, cfg, clip=True)
                        observation = env.step(action)
                        simulation_ticks += 1
                        max_tracking_error = max(
                            max_tracking_error,
                            float(np.max(np.abs(
                                env.joint_positions()[:5]
                                - interpolated_arm))))
                        xy, d3 = env.pinch_to_object_m()
                        lift = env.lift_height()
                        object_xyz_now = env.object_position()
                        object_xy_displacement = float(np.linalg.norm(
                            object_xyz_now[:2] - initial_object_xyz[:2]))
                        object_up_now = env.object_rotation()[:, 2]
                        object_tilt_deg = float(np.degrees(np.arccos(np.clip(
                            np.dot(initial_object_up, object_up_now), -1.0, 1.0))))
                        closest_xy = min(closest_xy, xy)
                        closest_3d = min(closest_3d, d3)
                        max_lift_height = max(max_lift_height, lift)
                        min_lift_height = min(min_lift_height, lift)
                        max_object_xy_displacement = max(
                            max_object_xy_displacement, object_xy_displacement)
                        if lift <= TABLETOP_MOTION_LIFT_CUTOFF_M:
                            max_pre_lift_object_xy_displacement = max(
                                max_pre_lift_object_xy_displacement,
                                object_xy_displacement,
                            )
                        max_object_tilt_deg = max(
                            max_object_tilt_deg, object_tilt_deg)
                        if first_contact_cycle is None and env.jaw_contacts() > 0:
                            first_contact_cycle = cycle
                        contact_counts = env.jaw_contact_counts()
                        simultaneous_contact_pads = sum(
                            value > 0 for value in contact_counts.values())
                        max_simultaneous_contact_pads = max(
                            max_simultaneous_contact_pads,
                            simultaneous_contact_pads,
                        )
                        if env.has_bilateral_jaw_contact():
                            bilateral_contact_ticks += 1
                            if first_bilateral_contact_cycle is None:
                                first_bilateral_contact_cycle = cycle
                        if (renderer is not None
                                and simulation_ticks % args.capture_every == 0):
                            policy_image = runtime_obs["image"][-1]
                            wrist = np.transpose(policy_image, (1, 2, 0))
                            wrist = np.asarray(Image.fromarray(wrist).resize((448, 448)))
                            renderer.update_scene(env.data, camera=debug_camera)
                            external = renderer.render().copy()
                            canvas = Image.new("RGB", (896, 484), "white")
                            canvas.paste(Image.fromarray(wrist), (0, 36))
                            canvas.paste(Image.fromarray(external), (448, 36))
                            ImageDraw.Draw(canvas).text(
                                (8, 8),
                                f"cycle={cycle:02d} scale={schedule.time_scale:.2f} "
                                f"xy={xy*1000:.1f}mm lift={env.lift_height()*1000:.1f}mm "
                                f"contacts={env.jaw_contacts()}",
                                fill="black",
                            )
                            frames.append(np.asarray(canvas))
                    command_object_xyz = env.object_position()
                    command_object_xy_displacement = float(np.linalg.norm(
                        command_object_xyz[:2] - initial_object_xyz[:2]))
                    command_object_up = env.object_rotation()[:, 2]
                    command_object_tilt_deg = float(np.degrees(np.arccos(np.clip(
                        np.dot(initial_object_up, command_object_up), -1.0, 1.0))))
                    actual_pinch_xyz, actual_pinch_quat = ik.forward_pose(
                        env.joint_positions())
                    command_trace.append({
                        "cycle": cycle,
                        "dataset_row": row,
                        "chunk_step": chunk_step,
                        "target_arm_rad": command.arm_positions_rad.tolist(),
                        "actual_arm_rad": env.joint_positions()[:5].tolist(),
                        "target_pinch_xyz_m": np.asarray(
                            target_pinch_xyz, dtype=float).tolist(),
                        "target_pinch_quat_xyzw": np.asarray(
                            target_pinch_quat, dtype=float).tolist(),
                        "actual_pinch_xyz_m": np.asarray(
                            actual_pinch_xyz, dtype=float).tolist(),
                        "actual_pinch_quat_xyzw": np.asarray(
                            actual_pinch_quat, dtype=float).tolist(),
                        "target_pinch_minus_initial_object_m": (
                            np.asarray(target_pinch_xyz, dtype=float)
                            - initial_object_xyz).tolist(),
                        "target_gap_m": command.gripper_width_m,
                        "actual_gap_m": gap_from_angle(
                            float(env.joint_positions()[5]), curve),
                        "pinch_xy_m": xy,
                        "pinch_3d_m": d3,
                        "object_lift_m": lift,
                        "object_xyz_m": command_object_xyz.tolist(),
                        "object_xy_displacement_m": (
                            command_object_xy_displacement),
                        "object_tilt_deg": command_object_tilt_deg,
                        "jaw_contacts": env.jaw_contacts(),
                        "jaw_contact_counts": env.jaw_contact_counts(),
                        "bilateral_jaw_contact": (
                            env.has_bilateral_jaw_contact()),
                    })
                    executed_commands += 1
                    if env.is_success():
                        break
                current_q = env.joint_positions()
                current_pose = matrix_from_fk(env.model, ik, current_q)
                current_gap = gap_from_angle(float(current_q[5]), curve)
                last_command = executed_cycle_commands[-1]
                endpoint_error = float(np.max(np.abs(
                    current_q[:5] - last_command.arm_positions_rad)))
                max_endpoint_tracking_error = max(
                    max_endpoint_tracking_error, endpoint_error)
                cycle_xy, cycle_3d = env.pinch_to_object_m()
                cycle_object_xyz = env.object_position()
                cycle_object_xy_displacement = float(np.linalg.norm(
                    cycle_object_xyz[:2] - initial_object_xyz[:2]))
                cycle_object_up = env.object_rotation()[:, 2]
                cycle_object_tilt_deg = float(np.degrees(np.arccos(np.clip(
                    np.dot(initial_object_up, cycle_object_up), -1.0, 1.0))))
                cycle_trace.append({
                    "cycle": cycle,
                    "dataset_row": row,
                    "time_scale": schedule.time_scale,
                    "scheduled_period_s": command_period,
                    "target_arm_rad": last_command.arm_positions_rad.tolist(),
                    "actual_arm_rad": current_q[:5].tolist(),
                    "endpoint_arm_tracking_error_rad": endpoint_error,
                    "target_gap_m": last_command.gripper_width_m,
                    "actual_gap_m": current_gap,
                    "pinch_xy_m": cycle_xy,
                    "pinch_3d_m": cycle_3d,
                    "object_lift_m": env.lift_height(),
                    "object_xyz_m": cycle_object_xyz.tolist(),
                    "object_xy_displacement_m": cycle_object_xy_displacement,
                    "object_tilt_deg": cycle_object_tilt_deg,
                    "jaw_contacts": env.jaw_contacts(),
                    "jaw_contact_counts": env.jaw_contact_counts(),
                    "bilateral_jaw_contact": env.has_bilateral_jaw_contact(),
                })
                images.append(observation.images["cam_wrist"].copy())
                poses.append(current_pose.copy())
                gaps.append(current_gap)
                if env.is_success():
                    break

        result = {
            "status": "LOCAL_CAMERA_MATCHED_DIAGNOSTIC_NOT_HARDWARE_VALIDATION",
            "dataset": str(args.data),
            "checkpoint": (str(args.policy_ckpt)
                           if args.policy_ckpt is not None else None),
            "mode": "recorded_oracle" if args.oracle else "learned_policy",
            "video_generated": not args.no_video,
            "observation_source": args.observation_source,
            "closed_loop_visual_feedback": (
                args.observation_source == "simulation" and not args.oracle),
            "image_perturb": args.image_perturb,
            "image_noise_gray": (
                args.image_noise_gray if args.image_perturb == "noise" else None),
            "image_perturb_seed": (
                args.image_perturb_seed if args.image_perturb == "noise" else None),
            "registration": str(args.registration),
            "registration_status": registration["status"],
            "collision_model": (
                "provisional_ver1_parallel_jaw_proxy"
                if diagnostic_proxy_ready else "legacy_mujoco_gripper"
            ),
            "collision_model_is_measured": False if diagnostic_proxy_ready else None,
            "visible_gripper_model": (
                "handoff_ver1_parallel_jaw_meshes"
                if diagnostic_proxy_ready else "legacy_mujoco_rotary_jaw"
            ),
            "visual_collision_alignment": (
                "same_moving_finger_bodies"
                if diagnostic_proxy_ready else None
            ),
            "representative_episode": representative,
            "registration_object_xyz_m": registration_object_xyz.tolist(),
            "diagnostic_object_offset_xy_m": [
                float(args.object_offset_x_m), float(args.object_offset_y_m)],
            "robot_start_shifted_with_object": False,
            "object_xyz_m": object_xyz.tolist(),
            "initial_gap_m": initial_gap,
            "episode_start_row": start_row,
            "registration_approach_start_used": bool(
                args.use_registration_approach_start),
            "cycles_requested": args.cycles,
            "action_horizon_steps": action_horizon,
            "validation_horizon_steps": validation_horizon,
            "full_chunk_validation": validation_horizon == action_horizon,
            "final_oracle_tail_executed": args.execute_final_oracle_tail,
            "final_oracle_tail_commands": final_oracle_tail_commands,
            "executed_commands": executed_commands,
            "simulation_ticks": simulation_ticks,
            "geometry_error": geometry_error,
            "time_scale_p0_p50_p95_max": (
                [float(value) for value in np.percentile(time_scales, [0, 50, 95, 100])]
                if time_scales else [None, None, None, None]),
            "scheduled_period_s_p0_p50_p95_max": (
                [float(value) for value in np.percentile(periods, [0, 50, 95, 100])]
                if periods else [None, None, None, None]),
            "simulation_tracking_time_margin": args.tracking_time_margin,
            "simulation_waypoint_execution": (
                "linear arm-and-gap target interpolation across scheduled ticks"
            ),
            "simulation_grip_preload_m": args.grip_preload_m,
            "simulation_grip_preload_activation_gap_m": (
                args.grip_preload_activation_gap_m),
            "closest_pinch_xy_m": closest_xy,
            "closest_pinch_3d_m": closest_3d,
            "first_contact_cycle": first_contact_cycle,
            "first_bilateral_contact_cycle": first_bilateral_contact_cycle,
            "bilateral_contact_ticks": bilateral_contact_ticks,
            "max_simultaneous_contact_pads": max_simultaneous_contact_pads,
            "lift_height_m": env.lift_height(),
            "max_lift_height_m": max_lift_height,
            "min_lift_height_m": min_lift_height,
            "initial_object_xyz_m": initial_object_xyz.tolist(),
            "final_object_xyz_m": env.object_position().tolist(),
            "max_object_xy_displacement_m": max_object_xy_displacement,
            "max_pre_lift_object_xy_displacement_m": (
                max_pre_lift_object_xy_displacement),
            "tabletop_motion_lift_cutoff_m": TABLETOP_MOTION_LIFT_CUTOFF_M,
            "object_xy_displacement_threshold_m": (
                object_xy_displacement_threshold),
            "max_object_tilt_deg": max_object_tilt_deg,
            "object_tilt_threshold_deg": object_tilt_threshold_deg,
            "object_xy_motion_exceeded_threshold": (
                max_pre_lift_object_xy_displacement
                >= object_xy_displacement_threshold),
            "total_object_xy_motion_exceeded_threshold": (
                max_object_xy_displacement >= object_xy_displacement_threshold),
            "object_displaced": (
                env.jaw_contacts() == 0
                and max_pre_lift_object_xy_displacement
                >= object_xy_displacement_threshold),
            "object_tipped": max_object_tilt_deg >= object_tilt_threshold_deg,
            "jaw_contacts_final": env.jaw_contacts(),
            "jaw_contact_counts_final": env.jaw_contact_counts(),
            "bilateral_jaw_contact_final": env.has_bilateral_jaw_contact(),
            "success": env.is_success(),
            "stable_side_grasp_success": _stable_side_grasp_success(
                mujoco_success=env.is_success(),
                pre_lift_xy_displacement_m=(
                    max_pre_lift_object_xy_displacement),
                xy_threshold_m=object_xy_displacement_threshold,
                max_tilt_deg=max_object_tilt_deg,
                tilt_threshold_deg=object_tilt_threshold_deg,
            ),
            "max_arm_tracking_error_rad": max_tracking_error,
            "max_endpoint_arm_tracking_error_rad": max_endpoint_tracking_error,
            "cycle_trace": cycle_trace,
            "command_trace": command_trace,
            "policy_input": (
                "recorded target chunk; policy bypassed"
                if args.oracle else
                f"{args.observation_source} RGB + relative proprio history only"),
            "privileged_state_use": "scoring and debug overlay only",
            "timing": (
                "same predicted waypoints; robot-time timestamps uniformly stretched; "
                "source dataset timestamps and labels unchanged"),
        }
    if not args.no_video:
        _save_gif(frames, args.out, fps=max(1, round(
            float(cfg["control"]["rate_hz"]) / args.capture_every)))
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if not args.no_video:
        print(f"video: {args.out}")
    print(f"report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
