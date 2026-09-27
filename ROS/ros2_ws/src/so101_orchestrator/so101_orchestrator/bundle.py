"""배포 모델 번들(tgz) 압축 해제 (S15P21A103-46).

BE 가 배포하는 아티팩트는 raw ckpt 가 아니라 **TorchScript export 번들 tgz** 다.
런타임이 요구하는 필수 4개: encoder.pt · denoiser.pt · metadata.json · dataset.report.json
"""
from __future__ import annotations

import tarfile
from pathlib import Path

REQUIRED = ("encoder.pt", "denoiser.pt", "metadata.json")
RECOMMENDED = ("dataset.report.json",)   # 런타임 full 실행(--move) 시 provenance 검증에 필요


def _safe_members(tar: tarfile.TarFile, dest: Path):
    """tgz 안의 경로가 dest 밖으로 나가지 않게 (path traversal 차단)."""
    dest = dest.resolve()
    for member in tar.getmembers():
        target = (dest / member.name).resolve()
        if not str(target).startswith(str(dest)):
            raise ValueError(f"번들에 상위 경로 항목: {member.name}")
        if member.issym() or member.islnk():
            raise ValueError(f"번들에 링크 항목: {member.name}")
        yield member


def extract_bundle(tgz: Path, dest: Path) -> Path:
    """tgz 를 dest 에 풀고, 필수 파일이 있는 디렉터리를 반환.
    tgz 가 파일을 최상위에 담았든 한 겹 디렉터리로 감쌌든 모두 처리."""
    dest.mkdir(parents=True, exist_ok=True)
    with tarfile.open(tgz, "r:*") as tar:
        tar.extractall(dest, members=_safe_members(tar, dest))
    return locate_model_dir(dest)


def locate_model_dir(root: Path) -> Path:
    """필수 파일이 있는 실제 모델 디렉터리(최상위 또는 한 겹 아래)."""
    if all((root / name).is_file() for name in REQUIRED):
        return root
    for child in sorted(p for p in root.iterdir() if p.is_dir()):
        if all((child / name).is_file() for name in REQUIRED):
            return child
    raise FileNotFoundError(
        f"번들에 필수 파일 없음({', '.join(REQUIRED)}): {root}")


def missing_recommended(model_dir: Path) -> list[str]:
    """있으면 좋은데 빠진 것(경고용)."""
    return [n for n in RECOMMENDED if not (model_dir / n).is_file()]
