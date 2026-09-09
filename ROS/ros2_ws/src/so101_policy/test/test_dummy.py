"""dummy_command 가 관절 범위를 절대 안 벗어나는지 (안전) self-check."""
from so101_policy.dummy import dummy_command

# so101.yaml 순서·범위 축약본 (테스트용 고정값)
JOINTS = [
    ("shoulder_pan", -1.92, 1.92),
    ("shoulder_lift", -1.745, 1.745),
    ("elbow_flex", -1.69, 1.69),
    ("wrist_flex", -1.658, 1.658),
    ("wrist_roll", -2.744, 2.841),
    ("gripper", -0.175, 1.745),
]


def test_within_range():
    for k in range(600):          # 20초, 30Hz 스윕
        t = k / 30.0
        cmd = dummy_command(t, JOINTS)
        assert len(cmd) == len(JOINTS)
        for val, (_n, lo, hi) in zip(cmd, JOINTS):
            assert lo <= val <= hi, f"범위 이탈: {val} ∉ [{lo},{hi}]"
