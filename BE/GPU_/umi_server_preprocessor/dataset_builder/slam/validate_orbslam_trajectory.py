import argparse
import csv
import json
import math
import os
from pathlib import Path


# Catastrophic relocalisation-jump guard. These are deliberately loose bounds,
# not claims about normal hand motion. Override only through the container
# environment and record the resulting values in the report.
MAX_TRANSLATION_STEP_M = float(os.environ.get("UMI_MAX_POSE_STEP_M", "0.25"))
MAX_ROTATION_STEP_DEG = float(os.environ.get("UMI_MAX_POSE_ROTATION_STEP_DEG", "45.0"))


def _quaternion_angle_deg(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if left_norm == 0 or right_norm == 0:
        return float("inf")
    dot = abs(sum(a * b for a, b in zip(left, right)) / (left_norm * right_norm))
    return math.degrees(2.0 * math.acos(max(-1.0, min(1.0, dot))))


def validate(csv_path: Path) -> dict:
    with csv_path.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    if not rows:
        raise ValueError("trajectory CSV is empty")

    parsed = []
    for row in rows:
        parsed.append(
            {
                "frame": int(row["frame_idx"]),
                "time": float(row["timestamp"]),
                "tracked": row["state"] == "2" and row["is_lost"].lower() == "false",
                "keyframe": row["is_keyframe"].lower() == "true",
                "position": tuple(float(row[name]) for name in ("x", "y", "z")),
                "quaternion": tuple(float(row[name]) for name in ("q_x", "q_y", "q_z", "q_w")),
            }
        )

    tracked = [row for row in parsed if row["tracked"]]
    first = tracked[0] if tracked else None
    last = tracked[-1] if tracked else None
    after_init = [row for row in parsed if first and row["frame"] >= first["frame"]]
    loss_episodes = 0
    in_loss = False
    for row in after_init:
        if not row["tracked"] and not in_loss:
            loss_episodes += 1
            in_loss = True
        elif row["tracked"]:
            in_loss = False

    path_length = 0.0
    pose_steps = []
    for previous, current in zip(parsed, parsed[1:]):
        if previous["tracked"] and current["tracked"] and current["frame"] == previous["frame"] + 1:
            translation = math.dist(previous["position"], current["position"])
            rotation = _quaternion_angle_deg(previous["quaternion"], current["quaternion"])
            path_length += translation
            pose_steps.append({
                "from_frame": previous["frame"],
                "to_frame": current["frame"],
                "translation_m": translation,
                "rotation_deg": rotation,
            })

    finite = all(
        math.isfinite(value)
        for row in tracked
        for value in (*row["position"], *row["quaternion"])
    )
    contiguous = all(current["frame"] == previous["frame"] + 1 for previous, current in zip(parsed, parsed[1:]))
    monotonic_time = all(current["time"] >= previous["time"] for previous, current in zip(parsed, parsed[1:]))
    axes = list(zip(*(row["position"] for row in tracked))) if tracked else []
    extents = [max(axis) - min(axis) for axis in axes] if axes else []
    errors = []
    if not tracked:
        errors.append("no tracked frames")
    if not finite:
        errors.append("non-finite tracked pose")
    if not contiguous:
        errors.append("frame indices are not contiguous")
    if not monotonic_time:
        errors.append("timestamps are not monotonic")
    if last and last["frame"] != parsed[-1]["frame"]:
        errors.append("tracking was lost at the end")
    max_translation = max(pose_steps, key=lambda item: item["translation_m"], default=None)
    max_rotation = max(pose_steps, key=lambda item: item["rotation_deg"], default=None)
    if max_translation and max_translation["translation_m"] > MAX_TRANSLATION_STEP_M:
        errors.append(
            f"pose translation jump {max_translation['translation_m']:.6f} m > "
            f"{MAX_TRANSLATION_STEP_M:.6f} m at frames "
            f"{max_translation['from_frame']}->{max_translation['to_frame']}"
        )
    if max_rotation and max_rotation["rotation_deg"] > MAX_ROTATION_STEP_DEG:
        errors.append(
            f"pose rotation jump {max_rotation['rotation_deg']:.3f} deg > "
            f"{MAX_ROTATION_STEP_DEG:.3f} deg at frames "
            f"{max_rotation['from_frame']}->{max_rotation['to_frame']}"
        )

    return {
        "status": "pass" if not errors else "fail",
        "errors": errors,
        "frames": len(parsed),
        "duration_s": parsed[-1]["time"] - parsed[0]["time"],
        "tracked_frames": len(tracked),
        "first_tracked_frame": first["frame"] if first else None,
        "first_tracked_time_s": first["time"] if first else None,
        "tracked_ratio_after_initialization": len(tracked) / len(after_init) if after_init else 0.0,
        "loss_episodes_after_initialization": loss_episodes,
        "keyframes": sum(row["keyframe"] for row in parsed),
        "path_length_m": path_length,
        "position_extent_m": extents,
        "first_tracked_pose": ({
            "frame": first["frame"],
            "timestamp_s": first["time"],
            "position_m": list(first["position"]),
            "quaternion_xyzw": list(first["quaternion"]),
        } if first else None),
        "pose_continuity": {
            "status": "pass" if not any("pose " in error and " jump " in error for error in errors) else "fail",
            "checked_consecutive_tracked_pairs": len(pose_steps),
            "maximum_translation_step_m": max_translation["translation_m"] if max_translation else None,
            "maximum_translation_step_frames": (
                [max_translation["from_frame"], max_translation["to_frame"]] if max_translation else None
            ),
            "maximum_rotation_step_deg": max_rotation["rotation_deg"] if max_rotation else None,
            "maximum_rotation_step_frames": (
                [max_rotation["from_frame"], max_rotation["to_frame"]] if max_rotation else None
            ),
            "translation_limit_m": MAX_TRANSLATION_STEP_M,
            "rotation_limit_deg": MAX_ROTATION_STEP_DEG,
            "gate_semantics": "provisional catastrophic relocalisation-jump guard",
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate an ORB-SLAM3 camera trajectory CSV.")
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = validate(args.csv)
    text = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")
    raise SystemExit(report["status"] != "pass")


if __name__ == "__main__":
    main()
