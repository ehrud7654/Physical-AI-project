"""Train a diagnostic BC baseline on camera-centric relative action chunks."""

from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import random
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Subset
import yaml

from data.relative_chunk_dataset import RelativeChunkDataset
from policy.relative_chunk_bc import RelativeChunkBCNet
from umi.relative_dataset import ACTION_DIM, SCHEMA


DEFAULT_CONFIG = Path(__file__).resolve().parents[1] / "configs/train/relative_chunk_bc.yaml"


def split_by_episode(dataset: RelativeChunkDataset, fraction: float,
                     seed: int) -> tuple[list[int], list[int], list[str]]:
    if not 0 < fraction < 1 or len(dataset.episodes) < 2:
        raise ValueError("episode validation split requires at least two episodes")
    rng = np.random.default_rng(seed)
    order = rng.permutation(len(dataset.episodes))
    n_val = max(1, int(round(len(order) * fraction)))
    val_episodes = set(int(value) for value in order[:n_val])
    train, val = [], []
    for episode_index, indices in enumerate(dataset.episode_sample_indices):
        (val if episode_index in val_episodes else train).extend(indices)
    names = [dataset.episode_ids[index] for index in sorted(val_episodes)]
    if not train or not val:
        raise ValueError("empty train or validation split")
    return train, val, names


def run_epoch(model: nn.Module, loader: DataLoader, criterion: nn.Module,
              device: torch.device, mean: torch.Tensor, std: torch.Tensor,
              optimizer: torch.optim.Optimizer | None, grad_clip: float) -> float:
    model.train(optimizer is not None)
    total, count = 0.0, 0
    context = torch.enable_grad() if optimizer is not None else torch.no_grad()
    with context:
        for image, proprio, action in loader:
            image = image.to(device, non_blocking=True)
            proprio = proprio.to(device, non_blocking=True)
            target = (action.to(device, non_blocking=True) - mean) / std
            if optimizer is not None:
                optimizer.zero_grad(set_to_none=True)
            prediction = model(image, proprio)
            loss = criterion(prediction, target)
            if optimizer is not None:
                loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), grad_clip)
                optimizer.step()
            batch = image.shape[0]
            total += float(loss.detach()) * batch
            count += batch
    return total / count


def save(path: Path, model: RelativeChunkBCNet, meta: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"state_dict": model.state_dict(), "meta": meta}, path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--epochs", type=int)
    parser.add_argument("--batch-size", type=int)
    parser.add_argument("--seed", type=int)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    train_config = config["train"]
    epochs = int(args.epochs or train_config["epochs"])
    batch_size = int(args.batch_size or train_config["batch_size"])
    seed = int(train_config["seed"] if args.seed is None else args.seed)
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    device = torch.device(args.device)
    if device.type == "cuda" and not torch.cuda.is_available():
        raise SystemExit("CUDA requested but unavailable")

    dataset = RelativeChunkDataset(
        args.data, image_mean=float(config["data"]["image_mean"]),
        image_std=float(config["data"]["image_std"]))
    train_indices, val_indices, val_episodes = split_by_episode(
        dataset, float(train_config["val_fraction"]), seed)
    train_actions = torch.from_numpy(dataset.action_rows(train_indices))
    mean = train_actions.mean(dim=0)
    std = train_actions.std(dim=0)
    std = torch.where(std < 1e-6, torch.ones_like(std), std)
    trivial = float((((train_actions - mean) / std).abs()).mean())
    print(dataset.summary())
    print(f"split: train={len(train_indices)}, val={len(val_indices)}, "
          f"val episodes={len(val_episodes)}")
    print(f"target: ({dataset.action_horizon}, {ACTION_DIM}) relative SE(3)+gap; "
          "each future step uses the same current-pose anchor")
    print("source time is preserved; robot base alignment and IK are not training labels")
    print(f"mean-predictor standardised L1 baseline: {trivial:.5f}")

    generator = torch.Generator().manual_seed(seed)
    loaders = {
        "train": DataLoader(
            Subset(dataset, train_indices), batch_size=batch_size, shuffle=True,
            num_workers=int(train_config["num_workers"]), generator=generator),
        "val": DataLoader(
            Subset(dataset, val_indices), batch_size=batch_size, shuffle=False,
            num_workers=int(train_config["num_workers"])),
    }
    model = RelativeChunkBCNet(
        obs_horizon=dataset.obs_horizon, action_horizon=dataset.action_horizon,
        action_dim=ACTION_DIM, config=config).to(device)
    mean, std = mean.to(device), std.to(device)
    optimizer = torch.optim.AdamW(
        model.parameters(), lr=float(train_config["lr"]),
        weight_decay=float(train_config["weight_decay"]))
    criterion = nn.L1Loss()
    best, best_state, history = float("inf"), None, []
    started = time.perf_counter()
    for epoch in range(1, epochs + 1):
        train_loss = run_epoch(
            model, loaders["train"], criterion, device, mean, std, optimizer,
            float(train_config["grad_clip"]))
        val_loss = run_epoch(
            model, loaders["val"], criterion, device, mean, std, None,
            float(train_config["grad_clip"]))
        history.append({"epoch": epoch, "train": train_loss, "val": val_loss})
        marker = ""
        if val_loss < best:
            best = val_loss
            best_state = copy.deepcopy(model.state_dict())
            marker = " <- best val"
        print(f"epoch {epoch:3d}/{epochs} train {train_loss:.5f} "
              f"val {val_loss:.5f}{marker}")

    meta = {
        "policy": "relative_chunk_bc_diagnostic",
        "dataset_schema": SCHEMA,
        "trained_on": str(args.data),
        "observation_horizon": dataset.obs_horizon,
        "action_horizon": dataset.action_horizon,
        "action_dim": ACTION_DIM,
        "rate_hz": dataset.rate_hz,
        "target_mean": mean.detach().cpu().tolist(),
        "target_std": std.detach().cpu().tolist(),
        "config": config,
        "seed": seed,
        "epochs": epochs,
        "train_samples": len(train_indices),
        "val_samples": len(val_indices),
        "val_episodes": val_episodes,
        "mean_predictor_standardised_l1": trivial,
        "best_val_loss": best,
        "history": history,
        "elapsed_s": time.perf_counter() - started,
        "limitations": [
            "Diagnostic BC baseline; not ACT or Diffusion Policy.",
            "Camera-to-marker-midpoint extrinsic remains provisional.",
            "Held-out episodes are not a certified held-out object-position split.",
        ],
    }
    save(args.out, model, meta)
    best_path = args.out.with_name(args.out.stem + "_bestval" + args.out.suffix)
    if best_state is not None:
        model.load_state_dict(best_state)
        save(best_path, model, {**meta, "checkpoint_selection": "best_validation"})
    print(f"last checkpoint: {args.out}")
    print(f"best checkpoint: {best_path}")
    print("Loss is diagnostic only. Next: same-checkpoint image ablation, then "
          "robot-time IK and camera-matched rollout.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
