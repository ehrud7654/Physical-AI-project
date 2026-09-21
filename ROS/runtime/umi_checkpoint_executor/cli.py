#!/usr/bin/env python3
"""Run an exported UMI checkpoint through camera, policy, IK, and SO-101 control."""
from __future__ import annotations

import argparse
import importlib
import json
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace

import cv2
import numpy as np

from .calibration import (
    load_camera_tcp_config,
    validate_camera_tcp_config,
    validate_model_calibration,
)
from .camera_stream import UsbCamera, capture_history, observation_interval_ok
from .motion_planner import (
    apply_gripper_latch,
    full_chunk_safe,
    joint_margin_ticks,
    plan_action,
    pose10d_to_mat,
    tick_delta_allowed,
)
from .policy_runtime import PortablePolicy, make_observation, mat_to_rot6d, preprocess, validate_action
from .robot_controller import (
    clamp_targets,
    execute_chunk,
    hold_current,
    motion_ids,
    read_robot,
    read_ticks_with_retry,
)


def self_check():
    identity = np.eye(4)
    assert np.allclose(mat_to_rot6d(identity[:3, :3]), [1, 0, 0, 0, 1, 0])
    pose = np.array([0.01, -0.02, 0.03, 1, 0, 0, 0, 1, 0], dtype=float)
    assert np.allclose(pose10d_to_mat(pose)[:3, 3], pose[:3])
    assert full_chunk_safe([{"safe": True}] * 4)
    assert not full_chunk_safe([{"safe": True}, {"safe": False}, {"safe": True}, {"safe": True}])
    assert observation_interval_ok(100, 100, 15)
    assert not observation_interval_ok(133, 100, 15)
    assert tick_delta_allowed(4095, 0)
    assert not tick_delta_allowed(171, 170)
    assert joint_margin_ticks([0], [(-1, 1)], 0.1) == [10]
    close = [
        {"predicted_gripper_tick": tick, "gripper_tick": tick, "gripper_delta": 0}
        for tick in (130, 200, 300, 400)
    ]
    latch = apply_gripper_latch(close, current_tick=130, latch=None, offset_ticks=50)
    assert latch == {"policy_tick": 400, "target_tick": 450}
    assert [x["gripper_tick"] for x in close] == [130, 200, 300, 450]
    held = [
        {"predicted_gripper_tick": 400, "gripper_tick": 400, "gripper_delta": 0}
        for _ in range(4)
    ]
    latch = apply_gripper_latch(held, current_tick=450, latch=latch, offset_ticks=50)
    assert [x["gripper_tick"] for x in held] == [450] * 4
    opened = [
        {"predicted_gripper_tick": 130, "gripper_tick": 130, "gripper_delta": 0}
        for _ in range(4)
    ]
    assert apply_gripper_latch(opened, current_tick=450, latch=latch, offset_ticks=50) is None
    assert [x["gripper_tick"] for x in opened] == [130] * 4
    unchecked_action = np.zeros((5, 10), dtype=float)
    unchecked_action[:, 0] = 10.0
    unchecked_action[:, 3] = 1.0
    unchecked_action[:, 7] = 1.0
    unchecked_action[:, 9] = 10.0
    validate_action(unchecked_action, (5, 10))

    batches = iter(({1: 1000, 2: None}, {2: 2000}))
    retry_common = SimpleNamespace(
        ALL_IDS=(1, 2),
        read_ticks=lambda _bus, _ids=None: next(batches),
    )
    assert read_ticks_with_retry(retry_common, None, attempts=2) == {1: 1000, 2: 2000}

    calibration = {
        "status": "physically_validated",
        "physical_deployment_allowed": True,
        "image_orientation_contract": "upright_v1",
        "T_camera_lens": identity.tolist(),
        "T_lens_tcp": identity.tolist(),
        "T_camera_tcp": identity.tolist(),
    }
    validate_camera_tcp_config(calibration)
    calibration["physical_deployment_allowed"] = False
    try:
        validate_camera_tcp_config(calibration)
    except ValueError:
        pass
    else:
        raise AssertionError("unapproved camera-TCP config accepted")

    with tempfile.TemporaryDirectory() as directory:
        model = Path(directory)
        calibration_ref = {"sha256": "camera-fixture"}
        (model / "dataset.report.json").write_text(json.dumps({
            "training_input_status": "ready",
            "physical_deployment_ready": True,
            "dataset_sha256": "dataset-fixture",
            "config_sha256": {"camera_tcp": "camera-fixture"},
        }), encoding="utf-8")
        assert validate_model_calibration(model, calibration_ref)["dataset_sha256"] == "dataset-fixture"
        calibration_ref["sha256"] = "wrong"
        try:
            validate_model_calibration(model, calibration_ref)
        except ValueError:
            pass
        else:
            raise AssertionError("model/config calibration mismatch accepted")

    class FakeBus:
        def __init__(self, follows=True):
            self.ticks = {i: 1000 for i in range(1, 7)}
            self.writes = []
            self.follows = follows

        def __enter__(self):
            return self

        def __exit__(self, *_):
            pass

        def read_diagnostics(self, servo_id):
            return {"tick": self.ticks[servo_id], "torque_enabled": True}

        def write_positions(self, targets, speed, acceleration):
            self.writes.append((dict(targets), speed, acceleration))
            if self.follows or len(self.writes) == 4:
                self.ticks.update(targets)

    fake_bus = FakeBus()
    fake_common = SimpleNamespace(
        ARM_IDS=(1, 2, 3, 4, 5),
        ALL_IDS=(1, 2, 3, 4, 5, 6),
        GRIPPER_ID=6,
        ST3215Bus=lambda _port: fake_bus,
        retry_eintr=lambda call: call(),
        refuse_if_estopped=lambda: None,
        tick_limits=lambda _servo_id: (0, 4095),
    )
    assert clamp_targets(fake_common, {1: -1, 2: 5000}) == {1: 0, 2: 4095}
    fake_args = SimpleNamespace(
        enable_gripper=False,
        motor_port="fixture",
        action_interval_ms=1,
        max_live_arm_delta=300,
        max_live_gripper_delta=700,
        speed=30,
        acceleration=10,
        settle_tolerance=15,
        settle_residual=60,
        settle_timeout=1,
    )
    fake_candidates = [
        {"action_index": i, "arm_ticks": [1000 + i] * 5, "gripper_tick": 1000}
        for i in range(1, 5)
    ]
    fake_report = {"motor_commands_attempted": 0, "motor_commands_sent": 0, "executed_waypoints": 0}
    execute_chunk(fake_candidates, fake_common, fake_args, fake_report)
    assert fake_report["motor_commands_sent"] == 4 and len(fake_bus.writes) == 4
    assert fake_report["settle_status"] == "arrived"
    assert all(6 not in targets for targets, _, _ in fake_bus.writes)

    lagging_bus = FakeBus(follows=False)
    fake_common.ST3215Bus = lambda _port: lagging_bus
    lagging_candidates = [
        {"action_index": i, "arm_ticks": [1000 + delta] * 5, "gripper_tick": 1000}
        for i, delta in enumerate((100, 170, 240, 300), start=1)
    ]
    lagging_report = {"motor_commands_attempted": 0, "motor_commands_sent": 0, "executed_waypoints": 0}
    execute_chunk(lagging_candidates, fake_common, fake_args, lagging_report)
    assert lagging_report["motor_commands_sent"] == 4 and len(lagging_bus.writes) == 4
    assert lagging_report["settle_status"] == "arrived"

    gripper_bus = FakeBus(follows=False)
    fake_common.ST3215Bus = lambda _port: gripper_bus
    fake_args.enable_gripper = True
    fake_args.settle_timeout = 0
    gripper_candidates = [
        {"action_index": i, "arm_ticks": [1000 + i] * 5, "gripper_tick": 1000 + delta}
        for i, delta in enumerate((100, 300, 550, 700), start=1)
    ]
    gripper_report = {"motor_commands_attempted": 0, "motor_commands_sent": 0, "executed_waypoints": 0}
    execute_chunk(gripper_candidates, fake_common, fake_args, gripper_report)
    assert gripper_report["motor_commands_sent"] == 4 and len(gripper_bus.writes) == 4
    assert gripper_report["settle_status"] == "skipped"
    print("UMI_CHECKPOINT_EXECUTOR_SELF_CHECK_OK")


def validate_args(args):
    if args.out.exists():
        raise FileExistsError(args.out)
    if (args.cycles < 0 or not 0 <= args.max_resamples <= 10
            or args.max_arm_delta < 0 or args.max_live_arm_delta < 0
            or (args.max_arm_delta and args.max_live_arm_delta
                and args.max_live_arm_delta < args.max_arm_delta)
            or args.max_gripper_delta < 0 or args.max_live_gripper_delta < 0
            or (args.max_gripper_delta and args.max_live_gripper_delta
                and args.max_live_gripper_delta < args.max_gripper_delta)
            or args.max_state_drift < 0
            or args.settle_tolerance <= 0 or args.settle_residual < args.settle_tolerance
            or args.settle_timeout < 0
            or args.min_joint_margin_ticks < 0):
        raise ValueError("cycles and delta limits must be non-negative/positive")
    if not 0 <= args.gripper_close_offset_ticks <= 4095:
        raise ValueError("gripper close offset must be 0..4095 ticks")
    if (args.observation_interval_ms <= 0 or args.observation_tolerance_ms <= 0
            or args.observation_tolerance_ms >= args.observation_interval_ms):
        raise ValueError("observation interval/tolerance is invalid")
    if not 1 <= args.speed <= 32767 or not 1 <= args.acceleration <= 150:
        raise ValueError("speed or acceleration is outside the supported range")
    if not 20 <= args.action_interval_ms <= 1000:
        raise ValueError("action interval must be 20..1000 ms")
    if not 0 < args.max_ik_error_mm <= 5:
        raise ValueError("IK tolerance is outside the supported range")
    if any(bounds[0] >= bounds[1] for bounds in (args.workspace_x, args.workspace_y, args.workspace_z)):
        raise ValueError("workspace bounds must be MIN MAX")


def main(args):
    if args.self_check:
        self_check()
        return
    validate_args(args)
    calibration = load_camera_tcp_config(args.camera_tcp)

    sys.path.insert(0, str(args.shanks_dir.resolve()))
    common = importlib.import_module("common")
    grasp = importlib.import_module("grasp")
    kinematics = importlib.import_module("kinematics")
    common.refuse_if_estopped()

    model_provenance = validate_model_calibration(args.model, calibration)
    policy = PortablePolicy(args.model)
    required_torque_ids = motion_ids(common, args) if args.move else ()
    if args.move:
        read_robot(common, grasp, kinematics, args.motor_port, required_torque_ids)
        if input("Type MOVE to start checkpoint control: ").strip() != "MOVE":
            print("cancelled; no motor command sent")
            return

    args.out.mkdir(parents=True)
    log_path = args.out / "cycles.jsonl"
    camera = UsbCamera(args.camera_port)
    episode_start = None
    gripper_latch = None
    cycle = 0
    attempt = 0
    try:
        with log_path.open("w", encoding="utf-8") as log:
            while args.cycles == 0 or cycle < args.cycles:
                samples = capture_history(
                    camera,
                    lambda: read_robot(common, grasp, kinematics, args.motor_port),
                    preprocess,
                    args.observation_interval_ms,
                    args.observation_tolerance_ms,
                )
                episode_start = samples[0]["pose"].copy() if episode_start is None else episode_start
                observation = make_observation(samples, episode_start, policy.device)
                action, latency_ms = policy.predict(observation)
                candidates = plan_action(action, samples[-1], common, grasp, kinematics, args)
                if args.enable_gripper:
                    gripper_latch = apply_gripper_latch(
                        candidates,
                        samples[-1]["ticks"][common.GRIPPER_ID],
                        gripper_latch,
                        args.gripper_close_offset_ticks,
                    )
                live = read_robot(common, grasp, kinematics, args.motor_port, required_torque_ids)
                drift = [live["ticks"][i] - samples[-1]["ticks"][i] for i in common.ARM_IDS]
                chunk_safe = full_chunk_safe(candidates)
                safe = True
                report = {
                    "cycle": cycle,
                    "attempt": attempt,
                    "phase": "planned",
                    "status": "PASS" if safe else "REFUSED",
                    "motor_commands_sent": 0,
                    "motor_commands_attempted": 0,
                    "checkpoint_waypoints": 4,
                    "executable_waypoints": 4 if chunk_safe else 0,
                    "executed_waypoints": 0,
                    "camera_tcp": calibration,
                    "model_provenance": model_provenance,
                    "latency_ms": latency_ms,
                    "observation_interval_ms": (
                        samples[1]["frame"]["sensor_timestamp_ns"]
                        - samples[0]["frame"]["sensor_timestamp_ns"]
                    ) / 1e6,
                    "observation_interval_target_ms": args.observation_interval_ms,
                    "observation_interval_tolerance_ms": args.observation_tolerance_ms,
                    "action_interval_ms": args.action_interval_ms,
                    "maximum_live_arm_lag_ticks": args.max_live_arm_delta,
                    "maximum_live_gripper_lag_ticks": args.max_live_gripper_delta,
                    "gripper_close_offset_ticks": args.gripper_close_offset_ticks,
                    "gripper_latch": gripper_latch,
                    "observation_ticks": samples[-1]["ticks"],
                    "observation_joint_margin_ticks": joint_margin_ticks(
                        samples[-1]["q"], kinematics.RAD_LIMITS, grasp.RAD_PER_TICK
                    ),
                    "minimum_required_joint_margin_ticks": args.min_joint_margin_ticks,
                    "post_inference_drift_ticks": drift,
                    "candidates": candidates,
                }
                stem = f"cycle_{cycle:04d}_attempt_{attempt:02d}"
                np.save(args.out / f"action_{stem}.npy", action)
                cv2.imwrite(
                    str(args.out / f"input_{stem}.jpg"),
                    cv2.cvtColor(samples[-1]["rgb"], cv2.COLOR_RGB2BGR),
                )
                if not safe:
                    report["phase"] = "refused"
                    print(json.dumps(report, indent=2), flush=True)
                    log.write(json.dumps(report) + "\n")
                    log.flush()
                    if attempt < args.max_resamples:
                        attempt += 1
                        print(
                            f"unsafe sample; asking the checkpoint again ({attempt}/{args.max_resamples})",
                            flush=True,
                        )
                        continue
                    if args.move:
                        hold_current(common, args.motor_port, required_torque_ids)
                    raise RuntimeError("checkpoint action failed a safety gate after all resamples")
                if args.move:
                    print(json.dumps(report, indent=2), flush=True)
                    try:
                        execute_chunk(candidates, common, args, report)
                    except KeyboardInterrupt:
                        report["phase"] = "interrupted"
                        report["status"] = "INTERRUPTED"
                        log.write(json.dumps(report) + "\n")
                        log.flush()
                        raise
                    except Exception as error:
                        hold_current(common, args.motor_port, required_torque_ids)
                        report["phase"] = "execution_failed"
                        report["status"] = "EXECUTION_FAILED"
                        report["execution_error"] = str(error)
                        print(json.dumps(report, indent=2), flush=True)
                        log.write(json.dumps(report) + "\n")
                        log.flush()
                        raise
                    report["phase"] = "completed"
                else:
                    report["phase"] = "prediction_only"
                print(json.dumps(report, indent=2), flush=True)
                log.write(json.dumps(report) + "\n")
                log.flush()
                cycle += 1
                attempt = 0
    except KeyboardInterrupt:
        if args.move:
            hold_current(common, args.motor_port, required_torque_ids)
        print("stopped by Ctrl+C", flush=True)
    finally:
        camera.release()


def build_parser():
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--shanks-dir", type=Path, default=Path.home() / "Shanks")
    parser.add_argument("--camera-port", type=int, default=8765)
    parser.add_argument(
        "--camera-tcp",
        type=Path,
        default=repo_root / "final/02_umi_dataset_builder/configs/s22_camera_tcp.json",
    )
    parser.add_argument("--motor-port", default="/dev/ttyTHS1")
    parser.add_argument("--cycles", type=int, default=1, help="0 means run until Ctrl+C")
    parser.add_argument("--max-resamples", type=int, default=3)
    parser.add_argument("--move", action="store_true", help="send checked IK targets to the SO-101")
    parser.add_argument("--enable-gripper", action="store_true", help="also execute checkpoint gripper width")
    parser.add_argument(
        "--gripper-close-offset-ticks",
        type=int,
        default=0,
        help="add positive ticks to every checkpoint gripper target (default: 0)",
    )
    parser.add_argument("--speed", type=int, default=30)
    parser.add_argument("--acceleration", type=int, default=10)
    parser.add_argument("--action-interval-ms", type=float, default=100)
    parser.add_argument("--max-arm-delta", type=int, default=0)
    parser.add_argument("--max-live-arm-delta", type=int, default=0)
    parser.add_argument("--max-gripper-delta", type=int, default=0)
    parser.add_argument("--max-live-gripper-delta", type=int, default=0)
    parser.add_argument("--max-state-drift", type=int, default=0)
    parser.add_argument("--settle-tolerance", type=int, default=15)
    parser.add_argument("--settle-residual", type=int, default=60)
    parser.add_argument("--settle-timeout", type=float, default=0, help="0 skips final-target waiting")
    parser.add_argument(
        "--min-joint-margin-ticks",
        type=int,
        default=0,
        help="extra margin inside configured joint limits (default: 0)",
    )
    parser.add_argument("--max-ik-error-mm", type=float, default=0.5)
    parser.add_argument("--observation-interval-ms", type=float, default=100)
    parser.add_argument("--observation-tolerance-ms", type=float, default=15)
    parser.add_argument("--min-z-mm", type=float, default=15)
    parser.add_argument("--workspace-x", type=float, nargs=2, default=(250, 570), metavar=("MIN", "MAX"))
    parser.add_argument("--workspace-y", type=float, nargs=2, default=(180, 550), metavar=("MIN", "MAX"))
    parser.add_argument("--workspace-z", type=float, nargs=2, default=(15, 300), metavar=("MIN", "MAX"))
    parser.add_argument("--self-check", action="store_true")
    return parser


def run(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    if not args.self_check and (args.model is None or args.out is None):
        parser.error("--model and --out are required")
    main(args)


if __name__ == "__main__":
    run()
