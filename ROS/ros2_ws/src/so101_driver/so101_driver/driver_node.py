"""SO-101 드라이버 노드 — ROS 경계 래퍼 (S15P21A103-50).

받는다:
  /joint_command  (sensor_msgs/JointState, position=라디안)  → 모터 write
  /estop          (std_msgs/Bool)                            → true면 토크 off
넘긴다:
  /joint_states   (sensor_msgs/JointState, position=라디안)  → 30Hz, header.stamp=읽은 시각

하드웨어 접근은 HardwareInterface 로만. Feetech 구현은 팀원이 채우고,
그전엔 DummyHardware 로 노드가 돈다. driver_node 는 반대편을 몰라야 한다.

메모: /joint_command 는 지금 sensor_msgs/JointState 를 쓴다(108 커스텀 msg 확정 전이라
       바로 빌드/테스트 가능). 108에서 so101_msgs/JointCommand 확정되면 여기 타입만 교체.
"""

from __future__ import annotations

import rclpy
from rclpy.node import Node
from sensor_msgs.msg import JointState
from std_msgs.msg import Bool

from so101_driver.hardware_interface import DummyHardware

# so101.yaml 의 joint_order 와 일치해야 한다 (AI/configs/so101.yaml)
DEFAULT_JOINT_NAMES = [
    "shoulder_pan", "shoulder_lift", "elbow_flex",
    "wrist_flex", "wrist_roll", "gripper",
]


class DriverNode(Node):
    def __init__(self):
        super().__init__("so101_driver")

        self.declare_parameter("joint_names", DEFAULT_JOINT_NAMES)
        self.declare_parameter("control_rate_hz", 30.0)

        self._joint_names = list(self.get_parameter("joint_names").value)
        rate = float(self.get_parameter("control_rate_hz").value)
        n = len(self._joint_names)

        # TODO(팀원): DummyHardware → FeetechHardware(so101.yaml, 포트, 서보ID) 로 교체
        self._hw = DummyHardware(n)
        self._hw.set_torque(True)  # 기동 시 토크 on (estop 오면 off). 실물 안전 게이트는 49와 조율

        self._state_pub = self.create_publisher(JointState, "/joint_states", 10)
        self.create_subscription(JointState, "/joint_command", self._on_command, 10)
        self.create_subscription(Bool, "/estop", self._on_estop, 10)

        self._timer = self.create_timer(1.0 / rate, self._publish_state)

        self.get_logger().info(
            f"so101_driver 시작 — {n} joints @ {rate:.0f}Hz "
            f"(하드웨어: {type(self._hw).__name__})"
        )

    # --- 받기: 명령 → 모터 --------------------------------------------------
    def _on_command(self, msg: JointState):
        pos = self._reorder(msg)
        if pos is None:
            return
        try:
            self._hw.write_positions(pos)
        except Exception as e:  # noqa: BLE001
            self.get_logger().error(f"write_positions 실패: {e}")

    def _reorder(self, msg: JointState) -> list[float] | None:
        """명령 msg 를 joint_names 순서의 라디안 리스트로 정렬.
        name 이 있으면 매핑, 없으면 위치 순서 가정."""
        if msg.name:
            table = dict(zip(msg.name, msg.position))
            try:
                return [float(table[j]) for j in self._joint_names]
            except KeyError as e:
                self.get_logger().warn(f"명령에 관절 누락: {e}")
                return None
        if len(msg.position) != len(self._joint_names):
            self.get_logger().warn(
                f"명령 길이 불일치: {len(msg.position)} != {len(self._joint_names)}"
            )
            return None
        return [float(p) for p in msg.position]

    # --- 받기: estop --------------------------------------------------------
    def _on_estop(self, msg: Bool):
        if msg.data:
            self.get_logger().warn("ESTOP 수신 — 토크 off")
            self._hw.set_torque(False)

    # --- 넘기기: 관절 상태 (30Hz) ------------------------------------------
    def _publish_state(self):
        try:
            pos = self._hw.read_positions()
        except Exception as e:  # noqa: BLE001
            self.get_logger().error(f"read_positions 실패: {e}")
            return
        js = JointState()
        js.header.stamp = self.get_clock().now().to_msg()  # 읽은 시각
        js.name = self._joint_names
        js.position = [float(p) for p in pos]
        effort = self._hw.read_effort()
        if effort is not None:
            js.effort = [float(e) for e in effort]
        self._state_pub.publish(js)


def main(args=None):
    rclpy.init(args=args)
    node = DriverNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    main()
