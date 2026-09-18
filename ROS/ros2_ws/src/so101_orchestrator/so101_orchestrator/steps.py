"""EXECUTE_TASK step 해석 — BE JetsonCommand.Step 계약 (S15P21A103-43).

BE는 step 트리를 flat 리스트(parentStepId)로 보내고 totalSteps 만 펼쳐 센다
(ExecutionService.countExecutableSteps). 펼치기는 여기서 — 같은 규칙으로 세야
우리 currentStep 이 BE 범위 검사(≤ totalSteps)에 안 걸린다.

MOTOR step parameters = {"motor": "J1".."J6", "angle": 도} (TaskService.validateMotor).
J{n} = so101.yaml 관절 index n-1. 도→rad, 그리퍼(마지막 관절)는 폭[m]으로 변환.
"""
from __future__ import annotations

import math

STEP_SKILL = "SKILL"
STEP_MOTOR = "MOTOR"
STEP_REPEAT = "REPEAT"

ACTION_SPACES = {"JOINT_ABSOLUTE", "JOINT_DELTA"}       # BE model.entity.ActionSpace
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


def motor_to_command(params: dict, joints: list[tuple[str, float, float]],
                     current: list[float], max_width_m: float) -> list[float]:
    """MOTOR parameters → 6축 명령(팔 rad, 그리퍼 m). 지정 관절만 바꾸고 나머지는 current 유지.
    도→rad 후 so101.yaml 범위로 clamp. 그리퍼(마지막)는 rad 범위를 [0, max_width_m]에 선형 매핑.
    ponytail: 그리퍼 각도→폭 선형 가정 — 실측 캘리브레이션 나오면 교체."""
    i = motor_index(params.get("motor"), len(joints))
    angle = params.get("angle")
    if isinstance(angle, bool) or not isinstance(angle, (int, float)):
        raise ValueError(f"angle 숫자 아님: {angle!r}")
    _, lo, hi = joints[i]
    rad = min(hi, max(lo, math.radians(float(angle))))
    cmd = list(current)
    cmd[i] = (rad - lo) / (hi - lo) * max_width_m if i == len(joints) - 1 else rad
    return cmd
