"""URDF 파서 self-check — 관절 순서·한계·그리퍼 판정 (S15P21A103-43)."""
from so101_orchestrator import robot_model as rm

# so101.urdf.xacro 를 펼친 형태의 최소 URDF (팔 2 + 그리퍼 + fixed 1)
URDF = """<robot name="so101">
  <link name="base_link"/>
  <joint name="base_fixed" type="fixed">
    <parent link="base_link"/><child link="a"/>
  </joint>
  <joint name="shoulder_pan" type="revolute">
    <parent link="a"/><child link="b"/><axis xyz="0 0 1"/>
    <limit effort="10" velocity="10" lower="-1.92" upper="1.92"/>
  </joint>
  <joint name="wrist_roll" type="revolute">
    <parent link="b"/><child link="c"/><axis xyz="0 0 1"/>
    <limit effort="10" velocity="10" lower="-2.74" upper="2.84"/>
  </joint>
  <joint name="gripper" type="prismatic">
    <parent link="c"/><child link="d"/><axis xyz="0 0 1"/>
    <limit effort="30" velocity="0.04" lower="0.0" upper="0.09"/>
  </joint>
</robot>"""


def test_parse_joints_order_limits_gripper():
    js = rm.parse_joints(URDF)
    assert [j[0] for j in js] == ["shoulder_pan", "wrist_roll", "gripper"]  # fixed 제외, 문서순
    assert js[0] == ("shoulder_pan", -1.92, 1.92, False)
    assert js[2] == ("gripper", 0.0, 0.09, True)                            # prismatic → 그리퍼


def test_parse_joints_continuous_no_limit():
    urdf = """<robot name="r"><joint name="spin" type="continuous">
      <parent link="a"/><child link="b"/><axis xyz="0 0 1"/></joint></robot>"""
    js = rm.parse_joints(urdf)
    assert js == [("spin", None, None, False)]
