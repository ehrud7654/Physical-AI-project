"""배포 오케스트레이터 노드 (S15P21A103-115).

BE(제어 평면) ↔ 젯슨 ROS(데이터 평면) 다리.
  - SSE로 BE 명령 수신 (별도 스레드) → 스레드 안전 큐 → 타이머에서 ROS로 처리
  - 정책 노드 lifecycle 제어 (load_ckpt/start/stop)
  - /estop 발행
  - 상태 HTTP POST (heartbeat 포함)

설계 메모:
  SSE는 블로킹이라 스레드에서 돌리고, rclpy 호출은 전부 메인(executor) 스레드에서만.
  그래서 SSE 스레드는 큐에 넣기만 하고, 실제 ROS 동작(서비스·publish)은 타이머 콜백에서 한다.
"""

from __future__ import annotations

import queue
import threading

import rclpy
from rclpy.node import Node
from std_msgs.msg import Bool

from so101_orchestrator.be_client import BEClient
from so101_orchestrator.policy_control import PolicyControl


class OrchestratorNode(Node):
    def __init__(self):
        super().__init__("so101_orchestrator")

        # --- 파라미터 (launch/CLI로 주입) ---
        self.declare_parameter("base_url", "http://localhost:8000")  # TODO(BE): 실제 EC2
        self.declare_parameter("device_id", "jetson-a103")
        self.declare_parameter("token", "")
        self.declare_parameter("heartbeat_sec", 5.0)
        self.declare_parameter("ckpt_dir", "/tmp/so101_ckpt")

        base_url = self.get_parameter("base_url").value
        device_id = self.get_parameter("device_id").value
        token = self.get_parameter("token").value or None
        hb = float(self.get_parameter("heartbeat_sec").value)
        self._ckpt_dir = self.get_parameter("ckpt_dir").value

        # --- 협력 객체 ---
        self._be = BEClient(base_url, device_id, token=token)
        self._policy = PolicyControl(self)

        # --- ROS 인터페이스 ---
        self._estop_pub = self.create_publisher(Bool, "/estop", 10)

        # --- SSE 스레드 → 큐 ---
        self._cmd_q: queue.Queue = queue.Queue()
        self._stop = threading.Event()
        self._sse_thread = threading.Thread(target=self._run_sse, daemon=True)
        self._sse_thread.start()

        # --- 타이머: 명령 처리 + heartbeat ---
        self.create_timer(0.1, self._drain_commands)   # 10Hz로 큐 비움
        self.create_timer(hb, self._send_heartbeat)

        self.get_logger().info(
            f"orchestrator 시작 — BE={base_url} device={device_id}"
        )

    # === SSE 스레드 ==========================================================
    def _run_sse(self):
        def on_event(event_type: str, data: dict):
            self._cmd_q.put((event_type, data))  # ROS 호출 안 함, 큐에만
        self._be.stream_commands(on_event, self._stop.is_set)

    # === 명령 처리 (메인 스레드) =============================================
    def _drain_commands(self):
        while True:
            try:
                event_type, data = self._cmd_q.get_nowait()
            except queue.Empty:
                return
            try:
                self._handle(event_type, data)
            except Exception as e:  # noqa: BLE001
                self.get_logger().error(f"명령 처리 실패 [{event_type}]: {e}")

    def _handle(self, event_type: str, data: dict):
        self.get_logger().info(f"BE 명령 수신: {event_type} {data}")
        if event_type == "load_ckpt":
            self._on_load_ckpt(data)
        elif event_type == "start_skill":
            self._on_start_skill(data)
        elif event_type == "stop":
            self._on_stop(data)
        elif event_type == "estop":
            self._on_estop(data)
        elif event_type in ("ping", "message"):
            pass  # keepalive
        else:
            self.get_logger().warn(f"알 수 없는 명령: {event_type}")

    def _on_load_ckpt(self, data: dict):
        # TODO(BE 계약): url(EC2 엔드포인트), sha256, version 필드 확정
        ckpt_id = data.get("ckpt_id", "?")
        # TODO: self._be.download_ckpt(data["url"], dest) → sha256 검증 → 정책에 경로 전달
        self._policy.configure()
        self._be.post_status("ckpt_ready", ckpt_id=ckpt_id)

    def _on_start_skill(self, data: dict):
        run_id = data.get("run_id")
        if self._policy.activate():
            self._be.post_status("skill_running", run_id=run_id,
                                 skill_id=data.get("skill_id"))

    def _on_stop(self, data: dict):
        run_id = data.get("run_id")
        self._policy.deactivate()
        self._be.post_status("skill_done", run_id=run_id, success=True)

    def _on_estop(self, data: dict):
        self._estop_pub.publish(Bool(data=True))
        self._policy.deactivate()
        self._be.post_status("estopped", reason=data.get("reason", "be_command"))

    # === heartbeat ===========================================================
    def _send_heartbeat(self):
        # TODO: 실제 cpu/mem/gpu_mem 수집 (이슈 42 — 8GB 감시)
        self._be.post_status("heartbeat", cpu=None, mem=None, gpu_mem=None)

    # === 종료 ================================================================
    def destroy_node(self):
        self._stop.set()
        return super().destroy_node()


def main(args=None):
    rclpy.init(args=args)
    node = OrchestratorNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    main()
