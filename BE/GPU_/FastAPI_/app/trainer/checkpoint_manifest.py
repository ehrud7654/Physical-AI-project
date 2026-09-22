import json
import os
from pathlib import Path
from typing import Any


REQUIRED_FIELDS = {
    "architecture", "framework", "frameworkVersion", "contractVersion",
    "robotProfileId", "actionSpace", "actionSpec", "runtimeSpec", "nParams",
    "controlRateHz", "cameraNames", "inputSchema", "outputSchema",
}


def build_metadata(
    request: dict[str, Any], checkpoint: Path | None = None, run_directory: Path | None = None
) -> dict[str, Any]:
    """Use the trainer-produced manifest as the source of truth when available.

    Environment metadata remains a compatibility fallback until every trainer emits
    ``checkpoint_manifest.json``. GPU Spring and EC2 receive the selected JSON without
    reshaping it.
    """
    generated = _load_generated_manifest(checkpoint, run_directory)
    if generated is not None:
        _validate(generated)
        return generated

    print(
        "trainer metadata manifest가 없어 환경변수 호환 metadata를 사용합니다.",
        flush=True,
    )
    fallback = {
        "architecture": os.getenv("MODEL_ARCHITECTURE", "DIFFUSION_UNET_TIMM_UMI"),
        "framework": os.getenv("MODEL_FRAMEWORK", "PyTorch"),
        "frameworkVersion": os.getenv(
            "MODEL_FRAMEWORK_VERSION", "umi-d095ba9590df789df5189eea5ee7e431689038a6"
        ),
        "contractVersion": os.getenv("MODEL_CONTRACT_VERSION", "umi-policy-v1"),
        "robotProfileId": os.getenv(
            "ROBOT_PROFILE_ID", "52000000-0000-4000-8000-000000000001"
        ),
        "actionSpace": os.getenv("MODEL_ACTION_SPACE", "EEF_RELATIVE_ROT6D"),
        "actionSpec": json.loads(os.getenv(
            "MODEL_ACTION_SPEC_JSON",
            '{"schemaVersion":1,"dim":10,"horizon":16,"nActionSteps":8,"rateHz":10,"layout":["dx","dy","dz","r00","r01","r02","r10","r11","r12","gap_m"],"rotation":"ROTATION_MATRIX_ROWS_0_1","compose":"T_next = T_cur @ A_relative","gapUnit":"m","gapRange":[0,0.09]}'
        )),
        "runtimeSpec": json.loads(os.getenv(
            "MODEL_RUNTIME_SPEC_JSON",
            '{"consumerMustConvert":true,"convertedDim":7,"convertedLayout":["x","y","z","rx","ry","rz","gap_m"],"convertedRotation":"AXIS_ANGLE_ROTVEC","convertedFrame":"ABSOLUTE","execSlice":{"startInclusive":1,"endExclusive":5},"execSliceAppliesTo":"raw","actionPointRateHz":10,"reobserveRateHz":2.5}'
        )),
        "nParams": int(os.getenv("MODEL_PARAMETER_COUNT", "1")),
        "controlRateHz": int(os.getenv("MODEL_CONTROL_RATE_HZ", "10")),
        "cameraNames": json.loads(os.getenv("MODEL_CAMERA_NAMES_JSON", '["camera0_rgb"]')),
        "inputSchema": json.loads(os.getenv(
            "MODEL_INPUT_SCHEMA_JSON",
            '{"camera0_rgb":{"shape":[2,3,224,224]},"robot0_eef_pos":{"shape":[2,3]},"robot0_eef_rot_axis_angle":{"shape":[2,6]},"robot0_eef_rot_axis_angle_wrt_start":{"shape":[2,6]},"robot0_gripper_width":{"shape":[2,1]}}',
        )),
        "outputSchema": json.loads(os.getenv("MODEL_OUTPUT_SCHEMA_JSON", '{"actionShape":[16,10]}')),
    }
    _validate(fallback)
    return fallback


def _load_generated_manifest(
    checkpoint: Path | None, run_directory: Path | None
) -> dict[str, Any] | None:
    candidates: list[Path] = []
    if checkpoint is not None:
        candidates.extend([
            checkpoint.with_suffix(".metadata.json"),
            checkpoint.parent / "checkpoint_manifest.json",
        ])
    if run_directory is not None:
        candidates.extend([
            run_directory / "checkpoint_manifest.json",
            run_directory / "model_metadata.json",
        ])
    for candidate in candidates:
        if not candidate.is_file() or candidate.is_symlink():
            continue
        payload = json.loads(candidate.read_text(encoding="utf-8"))
        metadata = payload.get("metadata", payload)
        if not isinstance(metadata, dict):
            raise ValueError(f"학습 산출 metadata 형식이 올바르지 않습니다: {candidate}")
        print(f"trainer metadata manifest 사용: {candidate}", flush=True)
        return metadata
    return None


def _validate(metadata: dict[str, Any]) -> None:
    missing = sorted(REQUIRED_FIELDS - metadata.keys())
    if missing:
        raise ValueError(f"학습 산출 metadata 필드가 누락되었습니다: {', '.join(missing)}")

