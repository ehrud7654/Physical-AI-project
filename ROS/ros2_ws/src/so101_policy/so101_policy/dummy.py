"""더미 정책 — ckpt 없이 배관·데모용 사인파.

각 관절을 제 범위의 중앙(0) 기준으로 부드럽게 흔든다. 진폭은 범위의 일부라
안전하고, 팔이 눈에 보이게 움직여서 30Hz 루프·발행·FakeBus 추종을 확인한다.
"""
from __future__ import annotations

import math

PERIOD_S = 6.0      # 한 주기
AMPLITUDE = 0.25    # 각 관절 범위의 25% 만 사용 (안전)


def dummy_command(t: float, joints: list[tuple[str, float, float]]) -> list[float]:
    """t[초] 에서 관절각 6개[rad]. joints = [(name, lo, hi)] so101.yaml 순서."""
    cmd = []
    for i, (_name, lo, hi) in enumerate(joints):
        # 캘리브 중앙(0) 기준으로 흔든다. 비대칭 범위라도 안 벗어나게
        # 진폭 = 양쪽 여유 중 작은 쪽 * 비율.
        half = min(abs(lo), abs(hi)) * AMPLITUDE
        phase = 2 * math.pi * (t / PERIOD_S + i / len(joints))  # 관절마다 위상 어긋
        cmd.append(half * math.sin(phase))
    return cmd
