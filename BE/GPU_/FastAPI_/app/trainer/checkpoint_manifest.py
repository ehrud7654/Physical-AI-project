import json
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

    The trainer-produced manifest is mandatory because nParams must be calculated from
    the instantiated model. GPU Spring and EC2 receive this JSON without reshaping it.
    """
    generated = _load_generated_manifest(checkpoint, run_directory)
    if generated is not None:
        _validate(generated)
        return generated

    raise ValueError(
        "trainer metadata manifest가 없습니다. 실제 모델에서 계산한 nParams를 포함한 "
        "checkpoint_manifest.json 또는 model_metadata.json이 필요합니다."
    )


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
    n_params = metadata.get("nParams")
    if isinstance(n_params, bool) or not isinstance(n_params, int) or n_params <= 1:
        raise ValueError("nParams는 CKPT에서 계산한 1보다 큰 정수여야 합니다.")

