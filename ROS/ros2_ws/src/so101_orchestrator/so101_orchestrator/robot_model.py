"""URDF(/robot_description)에서 관절 순서·한계를 읽는다 — 단일 출처 (S15P21A103-43).

AI so101.yaml 대신 ROS 정본(URDF)을 쓴다. robot_state_publisher 가 /robot_description
토픽에 latched(transient_local)로 URDF 전문을 발행하므로 파일 경로가 필요 없다.

관절 순서 = URDF <joint> 문서 순서(fixed 제외) = forward_position_controller 명령 벡터 순서.
  ⚠️ 이 순서는 so101_bringup 컨트롤러 yaml 의 joints 순서와 일치해야 한다(같은 패키지에서 관리).
그리퍼 = prismatic (단위 미터, 손가락 간격). 팔 = revolute (라디안).
"""
from __future__ import annotations

import xml.etree.ElementTree as ET

_MOVABLE = ("revolute", "prismatic", "continuous")


def parse_joints(urdf_xml: str) -> list[tuple[str, float, float, bool]]:
    """URDF 문자열 → [(name, lower, upper, is_gripper)] 문서 순서. 움직이는 관절만.
    continuous(한계 없음)는 lower=upper=None. is_gripper = prismatic(간격 미터)."""
    root = ET.fromstring(urdf_xml)
    out: list[tuple[str, float, float, bool]] = []
    for j in root.findall("joint"):
        jtype = j.get("type")
        if jtype not in _MOVABLE:
            continue
        limit = j.find("limit")
        lo = hi = None
        if limit is not None:
            lo = float(limit.get("lower")) if limit.get("lower") is not None else None
            hi = float(limit.get("upper")) if limit.get("upper") is not None else None
        out.append((j.get("name"), lo, hi, jtype == "prismatic"))
    return out
