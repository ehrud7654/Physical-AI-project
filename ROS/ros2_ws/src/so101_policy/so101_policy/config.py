"""so101.yaml 로더 — 관절 순서·범위 단일 출처. 하드코딩 금지."""
from __future__ import annotations

import os

import yaml

# 배포 기본 경로 후보 (레포 구조 기준). config_path 파라미터로 덮어씀.
_CANDIDATES = [
    os.path.expanduser("~/S15P21A103/AI/configs/so101.yaml"),
    "/opt/so101/so101.yaml",
]


def _find_default() -> str:
    for p in _CANDIDATES:
        if os.path.exists(p):
            return p
    raise FileNotFoundError(
        f"so101.yaml 못 찾음. config_path 파라미터로 지정하세요. 탐색: {_CANDIDATES}")


def load_joints(path: str | None = None) -> list[tuple[str, float, float]]:
    """[(name, lo, hi)] 를 so101.yaml index 순서로 반환."""
    path = path or _find_default()
    with open(path) as f:
        data = yaml.safe_load(f)
    joints = sorted(data["joints"], key=lambda j: j["index"])
    return [(j["name"], j["range_rad"][0], j["range_rad"][1]) for j in joints]
