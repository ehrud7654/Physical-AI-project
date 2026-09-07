"""하드웨어 경계 — 팀원(Feetech 구현)이 채우는 계약.

ROS 래퍼(driver_node)는 이 인터페이스만 호출한다. 실제 Feetech 시리얼·틱변환·
Sync Read/Write 는 이 인터페이스를 구현하는 쪽(팀원)의 몫이다.

★ 단위 규약: 이 경계에서 오가는 관절값은 **전부 라디안**이다.
  라디안 ↔ Feetech 틱(0~4095) 변환은 구현체 안에서, so101.yaml 규칙으로 한다.
  (선에는 생 SI 단위 — 정규화는 정책 노드가, 틱변환은 여기 구현체가.)
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable


@runtime_checkable
class HardwareInterface(Protocol):
    """드라이버가 하드웨어에 대해 알아도 되는 전부.

    구현체(예: FeetechHardware)는 정확히 이것만 만족시키면 되고,
    driver_node 는 반대편이 실물인지 더미인지 몰라도 된다.
    """

    def read_positions(self) -> list[float]:
        """현재 관절각을 라디안 리스트로. 순서 = joint_names.
        (Feetech: Sync Read Present Position → 틱 → 라디안)"""
        ...

    def write_positions(self, positions_rad: list[float]) -> None:
        """목표 관절각(라디안)을 모터에 쓴다. 순서 = joint_names.
        (Feetech: 라디안 → 틱 → Sync Write Goal Position)"""
        ...

    def set_torque(self, enabled: bool) -> None:
        """전 서보 토크 on/off. estop·기동에 사용.
        (Feetech: Torque Enable 레지스터)"""
        ...

    # 선택: 안전 노드(49)에 넘길 부하/온도 — 구현되면 driver_node가 활용
    def read_effort(self) -> list[float] | None:
        """현재 부하/전류 (없으면 None). 충돌·과부하 감지용."""
        ...


class DummyHardware:
    """하드웨어 없이 노드를 돌려보기 위한 더미 (팀원 구현 전 / CI용).

    마지막으로 명령받은 위치를 그대로 되돌려준다 (완벽 추종 가정).
    실물 도착하면 FeetechHardware 로 교체 — driver_node 는 안 바뀐다.
    """

    def __init__(self, n_joints: int):
        self._pos = [0.0] * n_joints
        self._torque = False

    def read_positions(self) -> list[float]:
        return list(self._pos)

    def write_positions(self, positions_rad: list[float]) -> None:
        if self._torque:
            self._pos = list(positions_rad)

    def set_torque(self, enabled: bool) -> None:
        self._torque = enabled

    def read_effort(self) -> list[float] | None:
        return None
