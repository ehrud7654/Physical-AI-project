"""Estop 브리지 (S15P21A103-49) — /estop을 실제 토크 off로 연결.

safety 노드가 /estop을 발행해도 ros2_control엔 소비자가 없어 팔이 안 멈춘다.
(옛 so101_driver가 /estop을 받아 토크 off 했는데 ros2_control 전환 때 삭제됨.)
이 노드가 그 소비자다: /estop=true → controller_manager로 하드웨어(SO101System)를
deactivate → SO101System::on_deactivate → set_torque(false) → 토크 off.

⚠️ 소프트 estop이라 소프트웨어가 살아있어야 동작(best-effort). 실물 최종 안전은
   하드웨어 estop(서보 전원 차단)이다. 이건 보조 계층.
"""
from __future__ import annotations

import rclpy
from rclpy.node import Node
from rclpy.qos import DurabilityPolicy, QoSProfile
from std_msgs.msg import Bool

from controller_manager_msgs.srv import SetHardwareComponentState
from lifecycle_msgs.msg import State

SET_STATE_SRV = "/controller_manager/set_hardware_component_state"


class EstopBridge(Node):
    def __init__(self):
        super().__init__("so101_estop_bridge")
        self.declare_parameter("hardware_component", "SO101System")  # URDF <ros2_control name=>
        self._hw = self.get_parameter("hardware_component").value

        self._cli = self.create_client(SetHardwareComponentState, SET_STATE_SRV)
        # safety 노드가 래치(TRANSIENT_LOCAL)로 발행하므로 구독도 맞춘다 → 늦게 떠도 마지막 값 수신.
        latched = QoSProfile(depth=1, durability=DurabilityPolicy.TRANSIENT_LOCAL)
        self.create_subscription(Bool, "/estop", self._on_estop, latched)

        self._tripped = False
        self._cli.wait_for_service(timeout_sec=5.0)  # 미리 연결(있으면). 없으면 estop 때 재시도
        self.get_logger().info(f"estop 브리지 시작 — /estop → {self._hw} deactivate(토크 off)")

    def _on_estop(self, msg: Bool):
        if not msg.data or self._tripped:
            return
        self._tripped = True
        self.get_logger().error(f"ESTOP 수신 → {self._hw} deactivate 요청(토크 off)")

        if not self._cli.service_is_ready() and not self._cli.wait_for_service(timeout_sec=2.0):
            self.get_logger().fatal(
                f"{SET_STATE_SRV} 없음 — 토크 off 못 함! 하드웨어 estop(전원차단)으로 정지하라")
            return

        req = SetHardwareComponentState.Request()
        req.name = self._hw
        req.target_state = State(id=State.PRIMARY_STATE_INACTIVE, label="inactive")
        future = self._cli.call_async(req)
        future.add_done_callback(self._on_result)

    def _on_result(self, future):
        try:
            resp = future.result()
        except Exception as e:  # noqa: BLE001
            self.get_logger().fatal(f"deactivate 호출 실패 — 토크 off 확인 안 됨: {e}")
            return
        if getattr(resp, "ok", False):
            self.get_logger().error("하드웨어 deactivate 성공 — 토크 off 됨")
        else:
            self.get_logger().fatal("deactivate 응답 실패 — 토크 off 확인 안 됨! 전원차단 하라")


def main(args=None):
    rclpy.init(args=args)
    node = EstopBridge()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.try_shutdown()


if __name__ == "__main__":
    main()
