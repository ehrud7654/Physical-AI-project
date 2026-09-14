"""Local-only camera-matched MuJoCo diagnostic for a relative UMI policy.

This is not a hardware validation or a deployment rollout.  The policy sees
only wrist RGB history and relative proprio history.  Simulator object state is
used exclusively for scoring and the debug overlay.
"""

from __future__ import annotations

import argparse
import copy
from collections import deque
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import mujoco
import numpy as np
from PIL import Image, ImageDraw

from policy.relative_chunk_bc import RelativeChunkBCPolicy
from sim.mujoco.build_scene import load_config, normalize
from sim.mujoco.env import MujocoPickEnv
from tools.run_umi_regression import is_shared_gpu_server
from tools.umi_mujoco import MujocoArmAdapter, MujocoIK, gap_from_angle
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


def _registration(path: Path) -> dict:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("status") != "PROVISIONAL_NOT_PHYSICAL_CALIBRATION":
        raise ValueError("registration must be explicitly provisional")
    arm = np.asarray(payload.get("start_arm_rad"), dtype=float)
    obj = np.asarray(payload.get("object_xyz_m"), dtype=float)
    size = np.asarray(payload.get("object_size_m"), dtype=float)
    if (arm.shape != (5,) or obj.shape != (3,) or size.shape != (3,)
            or not np.isfinite(arm).all() or not np.isfinite(obj).all()
            or not np.isfinite(size).all() or np.any(size <= 0)):
        raise ValueError("registration needs finite start_arm_rad[5], object xyz/size[3]")
    return {**payload, "start_arm_rad": arm, "object_xyz_m": obj,
            "object_size_m": size}


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
    if is_shared_gpu_server():
        raise SystemExit(
            "Shared GPU server is training-only; run MuJoCo rollout locally")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--policy-ckpt", type=Path, required=True)
    parser.add_argument("--registration", type=Path, required=True)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--observation-source", choices=("dataset", "simulation"),
                        default="dataset")
    parser.add_argument("--oracle", action="store_true",
                        help="replay recorded relative targets instead of policy output")
    parser.add_argument("--episode",
                        help="override the registration's representative episode")
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
    parser.add_argument("--tracking-time-margin", type=float, default=1.0,
                        help="simulation-only multiplier after required dynamic retiming")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--real-config", type=Path, default=DEFAULT_REAL_CONFIG)
    parser.add_argument("--out", type=Path, required=True,
                        help="output .gif; a JSON report is written beside it")
    args = parser.parse_args()
    if args.device != "cpu":
        raise SystemExit("this local diagnostic currently requires --device cpu")
    if args.out.suffix.lower() != ".gif":
        raise SystemExit("--out must end in .gif")
    report_path = args.out.with_suffix(".json")
    if args.out.exists() or report_path.exists():
        raise SystemExit("output already exists")
    if (args.cycles <= 0 or args.execute_steps <= 0 or args.capture_every <= 0
            or not np.isfinite(args.tracking_time_margin)
            or args.tracking_time_margin < 1.0):
        raise SystemExit("cycles, execute-steps and capture-every must be positive")

    registration = _registration(args.registration)
    representative = str(args.episode or registration["representative_episode"])
    object_xyz = _episode_object_xyz(registration, representative)
    episode = _episode(args.data, representative)
    initial_gap = float(episode["proprio"][0, -1, 9])
    policy = RelativeChunkBCPolicy(args.policy_ckpt, device="cpu")
    if int(policy.meta["observation_horizon"]) != 2:
        raise ValueError("diagnostic currently requires observation_horizon=2")
    action_horizon = int(policy.meta["action_horizon"])
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
    cfg["task"]["object"]["half_size_m"] = (
        registration["object_size_m"] / 2.0).tolist()
    cfg["task"]["object"]["init_pos"] = object_xyz.tolist()
    real = real_limits(args.real_config)
    ranges = arm_ranges(real)
    start_arm = registration["start_arm_rad"]
    if np.any(start_arm < ranges[:, 0]) or np.any(start_arm > ranges[:, 1]):
        raise ValueError("registered start arm exceeds real limits")
    curve = cfg["grasp"]["gap_curve"]
    initial_q = np.r_[start_arm, invert_gap_curve(initial_gap, curve)]
    object_xy = tuple(float(value) for value in object_xyz[:2])
    source_period = 1.0 / float(policy.meta["rate_hz"])

    frames: list[np.ndarray] = []
    time_scales: list[float] = []
    periods: list[float] = []
    closest_xy = float("inf")
    closest_3d = float("inf")
    first_contact_cycle = None
    geometry_error = None
    final_oracle_tail_commands = 0
    executed_commands = 0
    simulation_ticks = 0
    max_tracking_error = 0.0
    max_endpoint_tracking_error = 0.0
    cycle_trace: list[dict[str, object]] = []

    with MujocoPickEnv(
        cfg, render=True, object_jitter_m=0.0,
        max_ticks=max(1, args.cycles * args.execute_steps * 20),
    ) as env:
        observation = env.reset(seed=0, object_xy=object_xy, initial_q_rad=initial_q)
        ik = MujocoIK(env.model, cfg)
        solver = MujocoArmAdapter(ik, curve)
        current_q = env.joint_positions()
        current_pose = matrix_from_fk(env.model, ik, current_q)
        current_gap = gap_from_angle(float(current_q[5]), curve)
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
        with mujoco.Renderer(env.model, height=448, width=448) as renderer:
            initial_xy, initial_3d = env.pinch_to_object_m()
            closest_xy, closest_3d = initial_xy, initial_3d
            initial_policy_image = (
                episode["image"][0, -1]
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
                min(args.cycles, len(episode["action"]))
                if args.observation_source == "dataset" or args.oracle
                else args.cycles
            )
            for cycle in range(cycle_count):
                row = min(cycle, len(episode["action"]) - 1)
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
                base_candidate = (
                    FixedActionPolicy(episode["action"][row])
                    if args.oracle else policy
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
                for command in executed_cycle_commands:
                    raw_target = np.r_[
                        command.arm_positions_rad,
                        invert_gap_curve(command.gripper_width_m, curve),
                    ]
                    action = normalize(raw_target, cfg, clip=True)
                    ticks = max(1, int(np.ceil(
                        command_period * env.control_rate_hz)))
                    for _ in range(ticks):
                        observation = env.step(action)
                        simulation_ticks += 1
                        max_tracking_error = max(
                            max_tracking_error,
                            float(np.max(np.abs(
                                env.joint_positions()[:5]
                                - command.arm_positions_rad))))
                        xy, d3 = env.pinch_to_object_m()
                        closest_xy = min(closest_xy, xy)
                        closest_3d = min(closest_3d, d3)
                        if first_contact_cycle is None and env.jaw_contacts() > 0:
                            first_contact_cycle = cycle
                        if simulation_ticks % args.capture_every == 0:
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
                    "jaw_contacts": env.jaw_contacts(),
                })
                images.append(observation.images["cam_wrist"].copy())
                poses.append(current_pose.copy())
                gaps.append(current_gap)
                if env.is_success():
                    break

        result = {
            "status": "LOCAL_CAMERA_MATCHED_DIAGNOSTIC_NOT_HARDWARE_VALIDATION",
            "dataset": str(args.data),
            "checkpoint": str(args.policy_ckpt),
            "mode": "recorded_oracle" if args.oracle else "learned_policy",
            "observation_source": args.observation_source,
            "closed_loop_visual_feedback": (
                args.observation_source == "simulation" and not args.oracle),
            "registration": str(args.registration),
            "representative_episode": representative,
            "object_xyz_m": object_xyz.tolist(),
            "initial_gap_m": initial_gap,
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
            "closest_pinch_xy_m": closest_xy,
            "closest_pinch_3d_m": closest_3d,
            "first_contact_cycle": first_contact_cycle,
            "lift_height_m": env.lift_height(),
            "jaw_contacts_final": env.jaw_contacts(),
            "success": env.is_success(),
            "max_arm_tracking_error_rad": max_tracking_error,
            "max_endpoint_arm_tracking_error_rad": max_endpoint_tracking_error,
            "cycle_trace": cycle_trace,
            "policy_input": (
                "recorded target chunk; policy bypassed"
                if args.oracle else
                f"{args.observation_source} RGB + relative proprio history only"),
            "privileged_state_use": "scoring and debug overlay only",
            "timing": (
                "same predicted waypoints; robot-time timestamps uniformly stretched; "
                "source dataset timestamps and labels unchanged"),
        }
    _save_gif(frames, args.out, fps=max(1, round(
        float(cfg["control"]["rate_hz"]) / args.capture_every)))
    report_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"video: {args.out}")
    print(f"report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
