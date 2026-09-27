"""EXECUTE_TASK step 해석 — BE JetsonCommand.Step 계약 (S15P21A103-43).

BE는 step 트리를 flat 리스트(parentStepId)로 보내고 totalSteps 만 펼쳐 센다
(ExecutionService.countExecutableSteps). 펼치기는 여기서 — 같은 규칙으로 세야
우리 currentStep 이 BE 범위 검사(≤ totalSteps)에 안 걸린다.

MOTOR step parameters = {"motor": "J1".."J6", "angle": 값} (BE TaskService.validateMotor).
J{n} = URDF 움직이는 관절 n-1번째. 팔(revolute)은 angle=도→rad, 그리퍼(prismatic)는
angle=손가락 간격[m] 그대로. (BE 계약: 그리퍼 단위 = 미터. 키 이름은 angle 공용.)
  ⚠️ 그리퍼가 정말 J6·angle 필드로 미터를 보내는지 은찬 최종 확인 필요.
"""
from __future__ import annotations

import math

STEP_SKILL = "SKILL"
STEP_MOTOR = "MOTOR"
STEP_REPEAT = "REPEAT"

# actionSpace 는 오케스트레이터가 검증하지 않는다 — 형식은 tgz 번들을 실행하는
# 런타임(policy_runtime: metadata shape_meta·rot6d 검증)만 안다. 여긴 배달·무결성만.
COMMAND_TOPIC = "/forward_position_controller/commands"  # so101_policy 와 동일 토픽


def expand_steps(steps: list[dict], parent_id=None) -> list[dict]:
    """flat step 리스트 → 실행 순서의 leaf(SKILL/MOTOR) 리스트.
    REPEAT 는 자식을 repeatCount 번 반복. 개수 = BE countExecutableSteps."""
    out: list[dict] = []
    children = sorted((s for s in steps if s.get("parentStepId") == parent_id),
                      key=lambda s: s.get("order", 0))
    for s in children:
        if s.get("type") == STEP_REPEAT:
            out.extend(expand_steps(steps, s.get("stepId")) * int(s.get("repeatCount") or 1))
        else:
            out.append(s)
    return out


def motor_index(motor, n_joints: int) -> int:
    """'J1'..'J{n}' → 0..n-1. 형식·범위 아니면 ValueError."""
    if not (isinstance(motor, str) and motor[:1] == "J" and motor[1:].isdigit()):
        raise ValueError(f"motor 이름 형식 오류: {motor!r} (J1..J{n_joints})")
    i = int(motor[1:]) - 1
    if not 0 <= i < n_joints:
        raise ValueError(f"motor 범위 초과: {motor} (J1..J{n_joints})")
    return i


def motor_to_command(params: dict, joints: list[tuple[str, float, float, bool]],
                     current: list[float]) -> list[float]:
    """MOTOR parameters → 관절 명령 벡터(팔 rad, 그리퍼 m). 지정 관절만 바꾸고 나머지 유지.
    팔(revolute) = 도→rad, 그리퍼(prismatic) = 미터 그대로. 둘 다 URDF 한계로 clamp."""
    i = motor_index(params.get("motor"), len(joints))
    val = params.get("angle")
    if isinstance(val, bool) or not isinstance(val, (int, float)):
        raise ValueError(f"angle 숫자 아님: {val!r}")
    _, lo, hi, is_gripper = joints[i]
    target = float(val) if is_gripper else math.radians(float(val))   # 그리퍼는 미터 직접
    if lo is not None:
        target = max(lo, target)
    if hi is not None:
        target = min(hi, target)
    cmd = list(current)
    cmd[i] = target
    return cmd
