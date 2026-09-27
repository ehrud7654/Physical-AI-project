"""Portable UMI TorchScript policy loading, preprocessing, and observation encoding."""
import argparse
import json
import math
import time
from pathlib import Path

import cv2
import numpy as np
import torch
from diffusers import DDIMScheduler

from .camera_stream import UsbCamera


IDENTITY_ROT6D = np.array([1, 0, 0, 0, 1, 0], dtype=np.float32)


def preprocess(bgr):
    if bgr.dtype != np.uint8 or bgr.shape != (1080, 1920, 3):
        raise ValueError(f"expected uint8 1080x1920 BGR frame, got {bgr.shape} {bgr.dtype}")
    rgb = cv2.cvtColor(cv2.rotate(bgr, cv2.ROTATE_180), cv2.COLOR_BGR2RGB)
    width = math.ceil(224 / 1080 * 1920)
    rgb = cv2.resize(rgb, (width, 224), interpolation=cv2.INTER_AREA)
    left = (width - 224) // 2
    return rgb[:, left:left + 224]


def mat_to_rot6d(mat):
    return np.asarray(mat)[:2, :].reshape(6).astype(np.float32)


def make_observation(samples, episode_start, device):
    last_inv = np.linalg.inv(samples[-1]["pose"])
    start_inv = np.linalg.inv(episode_start)
    rel_last = [last_inv @ sample["pose"] for sample in samples]
    rel_start = [start_inv @ sample["pose"] for sample in samples]
    return {
        "camera0_rgb": torch.from_numpy(
            np.stack([sample["rgb"] for sample in samples]).transpose(0, 3, 1, 2).copy()
        ).unsqueeze(0).to(device).float() / 255,
        "robot0_eef_pos": torch.from_numpy(
            np.stack([pose[:3, 3] for pose in rel_last]).astype(np.float32)
        ).unsqueeze(0).to(device),
        "robot0_eef_rot_axis_angle": torch.from_numpy(
            np.stack([mat_to_rot6d(pose[:3, :3]) for pose in rel_last])
        ).unsqueeze(0).to(device),
        "robot0_eef_rot_axis_angle_wrt_start": torch.from_numpy(
            np.stack([mat_to_rot6d(pose[:3, :3]) for pose in rel_start])
        ).unsqueeze(0).to(device),
        "robot0_gripper_width": torch.tensor(
            [[[sample["gripper_width_m"]] for sample in samples]],
            dtype=torch.float32,
            device=device,
        ),
    }


def validate_action(action, expected_shape):
    if action.shape != expected_shape or not np.isfinite(action).all():
        raise ValueError(f"invalid action shape/value: {action.shape}")
    if np.any(np.linalg.norm(action[:, 3:6], axis=1) < 1e-6):
        raise ValueError("degenerate rot6d action")


class PortablePolicy:
    def __init__(self, model):
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA unavailable")
        self.metadata = json.loads((model / "metadata.json").read_text())
        action_meta = self.metadata["shape_meta"]["action"]
        self.action_shape = (int(action_meta["horizon"]), int(action_meta["shape"][0]))
        if (self.metadata["status"] != "provided_checkpoint"
                or self.action_shape[0] < 5 or self.action_shape[1] != 10):
            raise ValueError("unexpected model metadata")
        self.device = torch.device("cuda")
        self.encoder = torch.jit.load(str(model / "encoder.pt"), map_location=self.device).eval()
        self.denoiser = torch.jit.load(str(model / "denoiser.pt"), map_location=self.device).eval()
        self.scheduler = DDIMScheduler.from_config(self.metadata["scheduler"])
        self.scale = torch.tensor(self.metadata["action_scale"], device=self.device)
        self.offset = torch.tensor(self.metadata["action_offset"], device=self.device)

    def predict(self, obs):
        with torch.inference_mode():
            torch.cuda.synchronize()
            started = time.perf_counter()
            condition = self.encoder(obs)
            action = torch.randn((1, *self.action_shape), device=self.device)
            self.scheduler.set_timesteps(self.metadata["inference_steps"], device=self.device)
            for timestep in self.scheduler.timesteps:
                action = self.scheduler.step(
                    self.denoiser(action, timestep, condition), timestep, action
                ).prev_sample
            action = ((action - self.offset) / self.scale)[0].cpu().numpy()
            torch.cuda.synchronize()
        validate_action(action, self.action_shape)
        return action, (time.perf_counter() - started) * 1000


def main(args):
    if not 0 <= args.gripper_width_m <= 0.09:
        raise ValueError("gripper width must be 0..0.09 m")
    if args.out.exists():
        raise FileExistsError(args.out)

    policy = PortablePolicy(args.model)
    if policy.metadata.get("robot_execution_enabled", False):
        raise ValueError("unexpected model metadata")

    camera = UsbCamera(args.port)
    try:
        frames, frame_info = [], []
        while len(frames) < 2:
            bgr, info = camera.read_frame()
            frames.append(preprocess(bgr))
            frame_info.append({k: int(info[k]) for k in ("sequence", "sensor_timestamp_ns", "received_monotonic_ns")})
            if len(frames) < 2:
                time.sleep(0.1)
    finally:
        camera.release()

    identity = np.tile(IDENTITY_ROT6D, (2, 1))
    device = policy.device
    obs = {
        "camera0_rgb": torch.from_numpy(np.stack(frames).transpose(0, 3, 1, 2).copy()).unsqueeze(0).to(device).float() / 255,
        "robot0_eef_pos": torch.zeros((1, 2, 3), device=device),
        "robot0_eef_rot_axis_angle": torch.from_numpy(identity).unsqueeze(0).to(device),
        "robot0_eef_rot_axis_angle_wrt_start": torch.from_numpy(identity).unsqueeze(0).to(device),
        "robot0_gripper_width": torch.full((1, 2, 1), args.gripper_width_m, device=device),
    }

    action, latency_ms = policy.predict(obs)
    args.out.mkdir(parents=True)
    cv2.imwrite(str(args.out / "policy_input.jpg"), cv2.cvtColor(frames[-1], cv2.COLOR_RGB2BGR))
    np.save(args.out / "relative_action.npy", action)
    report = {
        "status": "PASS",
        "prediction_only": True,
        "motor_commands_sent": 0,
        "camera": "S22 Camera2 ID 2, 1920x1080, rotated 180 degrees",
        "preprocess": "official UMI aspect-preserving resize plus centered 224x224 crop",
        "state_assumption": "stationary at episode start; relative position zero and rotation identity",
        "gripper_width_m": args.gripper_width_m,
        "frame_info": frame_info,
        "sensor_interval_ms": (frame_info[1]["sensor_timestamp_ns"] - frame_info[0]["sensor_timestamp_ns"]) / 1e6,
        "latency_ms": latency_ms,
        "action_shape": list(action.shape),
        "max_relative_translation_mm": float(np.linalg.norm(action[:, :3], axis=1).max() * 1000),
        "predicted_gripper_range_mm": [float(action[:, 9].min() * 1000), float(action[:, 9].max() * 1000)],
    }
    (args.out / "report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--gripper-width-m", type=float, required=True)
    main(parser.parse_args())
