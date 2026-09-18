"""step 해석 self-check — REPEAT 펼치기·J매핑·도→rad·그리퍼 폭 (S15P21A103-43)."""
import math

import pytest

from so101_orchestrator import steps as st

# so101.yaml 형태 (name, lo, hi) — 테스트용 단순 범위
J = [("shoulder_pan", -1.0, 1.0), ("shoulder_lift", -1.0, 1.0), ("elbow_flex", -1.0, 1.0),
     ("wrist_flex", -1.0, 1.0), ("wrist_roll", -1.0, 1.0), ("gripper", 0.0, 2.0)]


def _s(sid, typ, order, parent=None, repeat=None):
    return {"stepId": sid, "parentStepId": parent, "order": order, "type": typ,
            "repeatCount": repeat, "parameters": {}}


def test_expand_repeat_matches_be_count():
    """루트 A, REPEAT×2(자식 B,C), 루트 D → A B C B C D = 6 (BE countExecutableSteps 동일)."""
    steps = [_s("A", "MOTOR", 1), _s("R", "REPEAT", 2, repeat=2),
             _s("C", "SKILL", 2, parent="R"), _s("B", "MOTOR", 1, parent="R"),
             _s("D", "MOTOR", 3)]
    plan = st.expand_steps(steps)
    assert [s["stepId"] for s in plan] == ["A", "B", "C", "B", "C", "D"]


def test_expand_nested_repeat_and_null_count():
    steps = [_s("R1", "REPEAT", 1, repeat=2), _s("R2", "REPEAT", 1, parent="R1", repeat=3),
             _s("X", "MOTOR", 1, parent="R2"), _s("R3", "REPEAT", 2, repeat=None),
             _s("Y", "SKILL", 1, parent="R3")]
    plan = st.expand_steps(steps)
    assert len(plan) == 2 * 3 + 1                      # repeatCount None → 1회
    assert st.expand_steps([]) == []


def test_motor_index():
    assert st.motor_index("J1", 6) == 0
    assert st.motor_index("J6", 6) == 5
    for bad in ("J7", "J0", "X1", "J", 1, None):
        with pytest.raises(ValueError):
            st.motor_index(bad, 6)


def test_motor_to_command_arm_deg_to_rad_and_clamp():
    cur = [0.1] * 6
    out = st.motor_to_command({"motor": "J2", "angle": 45}, J, cur, 0.09)
    assert out[1] == pytest.approx(math.radians(45))
    assert out[0] == 0.1 and out[2:] == [0.1] * 4          # 나머지 유지
    assert cur == [0.1] * 6                                 # 입력 불변
    assert st.motor_to_command({"motor": "J1", "angle": 179}, J, cur, 0.09)[0] == 1.0  # clamp hi
    assert st.motor_to_command({"motor": "J1", "angle": -179}, J, cur, 0.09)[0] == -1.0


def test_motor_to_command_gripper_to_width():
    cur = [0.0] * 6
    hi_deg = math.degrees(2.0)
    assert st.motor_to_command({"motor": "J6", "angle": hi_deg}, J, cur, 0.09)[5] == \
        pytest.approx(0.09)                                 # 상한 → 최대 폭
    assert st.motor_to_command({"motor": "J6", "angle": 0}, J, cur, 0.09)[5] == 0.0
    assert st.motor_to_command({"motor": "J6", "angle": math.degrees(1.0)}, J, cur,
                               0.09)[5] == pytest.approx(0.045)


def test_motor_to_command_rejects_bad_angle():
    for bad in ({"motor": "J1"}, {"motor": "J1", "angle": "45"}, {"motor": "J1", "angle": True}):
        with pytest.raises(ValueError):
            st.motor_to_command(bad, J, [0.0] * 6, 0.09)


def test_action_spaces_match_be_enum():
    assert st.ACTION_SPACES == {"JOINT_ABSOLUTE", "JOINT_DELTA"}
