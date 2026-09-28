"""
더미 정책 — ckpt 없이 배관·데모용 사인파.

각 관절을 제 범위의 중앙(0) 기준으로 부드럽게 흔든다. 진폭은 범위의 일부라
안전하고, 팔이 눈에 보이게 움직여서 30Hz 루프·발행·FakeBus 추종을 확인한다.
"""
from __future__ import annotations

import math

PERIOD_S = 6.0      # 한 주기
AMPLITUDE = 0.25    # 팔: 각 관절 범위의 25% 만 사용 (안전)

# 그리퍼는 단위가 미터(width). 팔(rad)과 달라 별도 스윙 (AI 계약: 그리퍼만 미터).
GRIPPER_MID_M = 0.04    # 중앙 간격
GRIPPER_AMP_M = 0.03    # ±0.03 → 0.01~0.07m, [0,0.09] 안쪽


def dummy_command(t: float, joints: list[tuple[str, float, float]]) -> list[float]:
    """t[초] 에서 명령 6개. 팔=라디안, gripper=미터. joints=[(name,lo,hi)] so101.yaml 순서."""
    cmd = []
    for i, (name, lo, hi) in enumerate(joints):
        phase = 2 * math.pi * (t / PERIOD_S + i / len(joints))  # 관절마다 위상 어긋
        if name == "gripper":
            cmd.append(GRIPPER_MID_M + GRIPPER_AMP_M * math.sin(phase))   # 미터
        else:
            # 캘리브 중앙(0) 기준으로 흔든다. 비대칭 범위라도 안 벗어나게
            # 진폭 = 양쪽 여유 중 작은 쪽 * 비율.
            half = min(abs(lo), abs(hi)) * AMPLITUDE
            cmd.append(half * math.sin(phase))                            # 라디안
    return cmd
