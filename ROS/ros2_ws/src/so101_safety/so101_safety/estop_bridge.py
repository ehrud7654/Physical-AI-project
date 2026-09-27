"""
Estop 브리지 (S15P21A103-49) — /estop을 실제 토크 off로 연결.

safety 노드가 /estop을 발행해도 ros2_control엔 소비자가 없어 팔이 안 멈춘다.
(옛 so101_driver가 /estop을 받아 토크 off 했는데 ros2_control 전환 때 삭제됨.)
이 노드가 그 소비자다. /estop=true 면 **두 경로 모두** 친다:

  1) ros2_control 경로 — controller_manager 로 하드웨어(SO101System) deactivate
     → SO101System::on_deactivate → set_torque(false) → 토크 off
  2) 파일 플래그 경로 — /tmp/umi_ESTOP 생성 (S15P21A103-46)
     UMI 런타임이 모터를 직접 잡는 모드에선 ros2_control 이 안 떠 있다.
     런타임은 매 청크·settle 루프마다 shanks/common.refuse_if_estopped() 로 이 파일을
     확인하고 SystemExit 한다 → 포트 점유와 무관하게 정지시킬 수 있는 유일한 수단.

해제는 자동으로 하지 않는다. shanks 주석대로 "팔을 확인한 뒤에만" 사람이 지운다.

⚠️ 소프트 estop이라 소프트웨어가 살아있어야 동작(best-effort). 실물 최종 안전은
   하드웨어 estop(서보 전원 차단)이다. 이건 보조 계층.
"""
from __future__ import annotations

from pathlib import Path

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
        # shanks/common.py ESTOP_FLAG 와 반드시 동일해야 런타임이 인식한다.
        self.declare_parameter("estop_flag", "/tmp/umi_ESTOP")
        self._hw = self.get_parameter("hardware_component").value
        self._flag = Path(self.get_parameter("estop_flag").value)

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
        self.get_logger().error(f"ESTOP 수신 → {self._hw} deactivate + 플래그 {self._flag}")

        # 1) 파일 플래그 먼저 — 런타임이 모터를 잡고 있는 경우 이게 유일한 정지 수단이고,
        #    서비스 대기(최대 2초)보다 빨라야 한다.
        try:
            self._flag.parent.mkdir(parents=True, exist_ok=True)
            self._flag.touch()
            self.get_logger().error(f"ESTOP 플래그 생성 — 런타임 정지 (해제는 팔 확인 후 수동 삭제)")
        except Exception as e:  # noqa: BLE001
            self.get_logger().fatal(f"ESTOP 플래그 생성 실패: {e}")

        # 2) ros2_control 경로 (bringup 이 떠 있을 때만 가능)
        if not self._cli.service_is_ready() and not self._cli.wait_for_service(timeout_sec=2.0):
            self.get_logger().warn(
                f"{SET_STATE_SRV} 없음 — ros2_control 미기동(런타임 모드로 보임). "
                f"플래그 경로로 정지 시도됨")
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
