"""Run a local multi-episode dynamic gate for recorded relative UMI targets.

This tool never loads a learned policy.  It replays recorded target chunks in
MuJoCo without rendering, then aggregates contact, lift, IK and displacement
outcomes for the registration's scene-valid episodes.  Passing this diagnostic
is necessary before learned-policy rollout, but is not hardware validation.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import subprocess
import sys
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.run_umi_regression import is_shared_gpu_server


AI_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = AI_ROOT / "configs" / "so101.yaml"
DEFAULT_REAL_CONFIG = AI_ROOT / "configs" / "real" / "so101_ver1.json"
RENDER_TOOL = AI_ROOT / "tools" / "render_relative_chunk_rollout.py"


def _scene_valid_episodes(registration: dict[str, Any]) -> list[str]:
    selected = registration.get("selected")
    if not isinstance(selected, dict):
        raise ValueError("registration has no selected candidate report")
    rows = selected.get("per_episode")
    if not isinstance(rows, list):
        raise ValueError("registration has no per-episode scene report")
    episodes = [
        str(row["episode"])
        for row in rows
        if isinstance(row, dict) and row.get("scene_constraints_ok") is True
    ]
    if not episodes:
        raise ValueError("registration has no scene-valid episodes to gate")
    return episodes


def _classification(report: dict[str, Any]) -> str:
    if report.get("success") is True:
        return "success"
    contacted = report.get("first_contact_cycle") is not None
    geometry_error = report.get("geometry_error")
    if geometry_error:
        return "ik_reject_after_contact" if contacted else "ik_reject_before_contact"
    if not contacted:
        return "no_contact"
    final_lift = float(report.get("lift_height_m", 0.0))
    min_lift = float(report.get("min_lift_height_m", min(0.0, final_lift)))
    max_lift = float(report.get("max_lift_height_m", max(0.0, final_lift)))
    if min_lift <= -0.01:
        return "object_displaced_or_tipped"
    if max_lift > 0.005:
        return "partial_lift"
    return "contact_without_lift"


def _finite_nonnegative(name: str, value: float) -> float:
    number = float(value)
    if not math.isfinite(number) or number < 0.0:
        raise ValueError(f"{name} must be finite and non-negative")
    return number


def main() -> int:
    if is_shared_gpu_server():
        raise SystemExit(
            "Shared GPU server is training-only; run the dynamic oracle gate locally")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--registration", type=Path, required=True)
    parser.add_argument("--episodes", nargs="*",
                        help="explicit episode ids; default is every scene-valid episode")
    parser.add_argument("--cycles", type=int, default=30)
    parser.add_argument("--tracking-time-margin", type=float, default=1.0)
    parser.add_argument("--grip-preload-m", type=float, default=0.001)
    parser.add_argument("--grip-preload-activation-gap-m", type=float, default=0.055)
    parser.add_argument("--min-success-fraction", type=float, default=0.8)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--real-config", type=Path, default=DEFAULT_REAL_CONFIG)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    if args.out.suffix.lower() != ".json":
        raise SystemExit("--out must end in .json")
    if args.out.exists():
        raise SystemExit(f"output already exists: {args.out}")
    if args.cycles <= 0:
        raise SystemExit("--cycles must be positive")
    preload = _finite_nonnegative("grip-preload-m", args.grip_preload_m)
    activation = _finite_nonnegative(
        "grip-preload-activation-gap-m", args.grip_preload_activation_gap_m)
    margin = float(args.tracking_time_margin)
    threshold = float(args.min_success_fraction)
    if not math.isfinite(margin) or margin < 1.0:
        raise SystemExit("--tracking-time-margin must be finite and >= 1")
    if not math.isfinite(threshold) or not 0.0 <= threshold <= 1.0:
        raise SystemExit("--min-success-fraction must be within [0, 1]")

    registration_path = args.registration.resolve()
    registration = json.loads(registration_path.read_text(encoding="utf-8"))
    if registration.get("status") != "PROVISIONAL_NOT_PHYSICAL_CALIBRATION":
        raise ValueError("registration must be explicitly provisional")
    eligible = _scene_valid_episodes(registration)
    episodes = list(args.episodes) if args.episodes else eligible
    unknown = sorted(set(episodes) - set(eligible))
    if unknown:
        raise ValueError(
            "explicit episodes are not scene-valid in this registration: "
            + ", ".join(unknown))
    if len(set(episodes)) != len(episodes):
        raise ValueError("episode ids must be unique")

    report_dir = (
        args.out.parent / f"{args.out.stem}_episodes"
    ).resolve()
    if report_dir.exists():
        raise SystemExit(f"episode report directory already exists: {report_dir}")
    report_dir.mkdir(parents=True, exist_ok=False)

    rows: list[dict[str, Any]] = []
    for index, episode in enumerate(episodes, start=1):
        pseudo_video = report_dir / f"{episode}.gif"
        command = [
            sys.executable,
            str(RENDER_TOOL),
            "--data", str(args.data.resolve()),
            "--registration", str(registration_path),
            "--device", "cpu",
            "--observation-source", "dataset",
            "--oracle",
            "--episode", episode,
            "--cycles", str(args.cycles),
            "--execute-steps", "1",
            "--validation-horizon-steps", "1",
            "--execute-final-oracle-tail",
            "--no-video",
            "--capture-every", "1000000",
            "--tracking-time-margin", str(margin),
            "--grip-preload-m", str(preload),
            "--grip-preload-activation-gap-m", str(activation),
            "--config", str(args.config.resolve()),
            "--real-config", str(args.real_config.resolve()),
            "--out", str(pseudo_video),
        ]
        completed = subprocess.run(
            command, cwd=AI_ROOT, capture_output=True, text=True, check=False)
        report_path = pseudo_video.with_suffix(".json")
        if completed.returncode != 0 or not report_path.is_file():
            row = {
                "episode": episode,
                "classification": "runner_error",
                "returncode": completed.returncode,
                "stderr_tail": completed.stderr[-2000:],
                "stdout_tail": completed.stdout[-2000:],
            }
        else:
            report = json.loads(report_path.read_text(encoding="utf-8"))
            row = {
                "episode": episode,
                "classification": _classification(report),
                "success": bool(report["success"]),
                "first_contact_cycle": report["first_contact_cycle"],
                "closest_pinch_xy_m": report["closest_pinch_xy_m"],
                "final_lift_height_m": report["lift_height_m"],
                "max_lift_height_m": report["max_lift_height_m"],
                "min_lift_height_m": report["min_lift_height_m"],
                "jaw_contacts_final": report["jaw_contacts_final"],
                "geometry_error": report["geometry_error"],
                "episode_report": str(report_path),
            }
        rows.append(row)
        print(
            f"{index}/{len(episodes)} {episode}: {row['classification']}",
            flush=True,
        )

    successes = sum(row.get("success") is True for row in rows)
    contacts = sum(row.get("first_contact_cycle") is not None for row in rows)
    runner_errors = sum(row["classification"] == "runner_error" for row in rows)
    success_fraction = successes / len(rows)
    passed = runner_errors == 0 and success_fraction >= threshold
    result = {
        "status": (
            "PASS_LOCAL_DYNAMIC_ORACLE_GATE"
            if passed else "FAIL_LOCAL_DYNAMIC_ORACLE_GATE"
        ),
        "purpose": "recorded-oracle dynamic registration gate; not policy or hardware validation",
        "dataset": str(args.data),
        "registration": str(args.registration),
        "eligible_scene_valid_episodes": eligible,
        "evaluated_episodes": episodes,
        "episodes": len(rows),
        "contacts": contacts,
        "successes": successes,
        "success_fraction": success_fraction,
        "required_success_fraction": threshold,
        "runner_errors": runner_errors,
        "simulation_grip_preload_m": preload,
        "simulation_grip_preload_activation_gap_m": activation,
        "simulation_tracking_time_margin": margin,
        "classification_counts": {
            label: sum(row["classification"] == label for row in rows)
            for label in sorted({row["classification"] for row in rows})
        },
        "per_episode": rows,
        "limitations": [
            "Recorded target chunks are replayed; no learned policy is loaded.",
            "The one-millimetre preload is a MuJoCo-only contact diagnostic, not hardware calibration.",
            "Passing does not approve real motor commands or establish physical workspace calibration.",
        ],
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if passed else 2


if __name__ == "__main__":
    raise SystemExit(main())
