"""배포 오케스트레이터 노드 (S15P21A103-116, 115 후속).

BE(제어 평면) ↔ 젯슨 ROS(데이터 평면) 다리. BE 계약 v1.3.0 기준.
  - command-stream SSE로 명령 수신 (APPLY_DEPLOYMENT / EXECUTE_TASK / CANCEL_EXECUTION)
  - 배포: pending manifest → 모델 다운로드+sha256 → 정책 로드 → JETSON-03 리포트
  - 실행: 정책 lifecycle activate → JETSON-06 리포트
  - heartbeat POST (liveness)
  - /estop 발행 (BE estop 명령 시)

설계 메모:
  SSE·다운로드는 블로킹이라 워커 스레드에서. rclpy publish/서비스 call_async는
  스레드에서 호출해도 안전(발행·요청 생성). 무거운 HTTP가 executor를 막지 않게 분리.
"""
from __future__ import annotations

import hashlib
import os
import queue
import threading
import uuid

import rclpy
from rclpy.node import Node
from std_msgs.msg import Bool

from so101_orchestrator import be_client as bc
from so101_orchestrator.be_client import BEClient
from so101_orchestrator.policy_control import PolicyControl


class OrchestratorNode(Node):
    def __init__(self):
        super().__init__("so101_orchestrator")

        self.declare_parameter("base_url", "http://localhost:8080")  # EC2 BE
        self.declare_parameter("device_id", "51000000-0000-4000-8000-000000000002")
        self.declare_parameter("token", "")                          # 고정 JWT (없으면 secret 로 발급)
        self.declare_parameter("device_secret", "")                  # DEVICE-05 — 절대 소스/깃에 X
        self.declare_parameter("heartbeat_sec", 15.0)                # <20s (젯슨 offline 임계)
        self.declare_parameter("ckpt_dir", "/tmp/so101_ckpt")
        self.declare_parameter("contract_version", "robot-command-v1")

        base_url = self.get_parameter("base_url").value
        device_id = self.get_parameter("device_id").value
        token = self.get_parameter("token").value or None
        # secret 은 param 우선, 없으면 env (로그·ros param 덤프에 안 남기려고 env 허용)
        self._device_secret = (self.get_parameter("device_secret").value
                               or os.environ.get("SO101_DEVICE_SECRET", ""))
        hb = float(self.get_parameter("heartbeat_sec").value)
        self._ckpt_dir = self.get_parameter("ckpt_dir").value
        self._contract = self.get_parameter("contract_version").value
        os.makedirs(self._ckpt_dir, exist_ok=True)

        self._be = BEClient(base_url, device_id, token=token)
        # 401 시 재발급 훅 — secret 있을 때만. be_client가 모든 요청·SSE에서 이걸로 재시도.
        if self._device_secret:
            self._be.reauth = lambda: self._be.fetch_device_token(self._device_secret)
        # 고정 token 없으면 secret 으로 발급 + 주기 재발급 (900s 만료 전에).
        if not token and self._device_secret:
            period = self._renew_token(initial=True)
            self.create_timer(period, self._renew_token)
        elif not token:
            self.get_logger().warn("token·device_secret 둘 다 없음 — BE 인증 없이 시작(요청 401 예상)")
        self._policy = PolicyControl(self)
        self._estop_pub = self.create_publisher(Bool, "/estop", 10)

        self._seq: dict[str, int] = {}          # 리포트 스트림별 sequence(단조증가)
        self._seen_cmds: set[str] = set()       # commandId 중복 실행 차단(세션 한정, 재시작시 리셋)
        self._cmd_q: queue.Queue = queue.Queue()
        self._stop = threading.Event()
        self._sse_thread = threading.Thread(target=self._run_sse, daemon=True)
        self._worker = threading.Thread(target=self._run_worker, daemon=True)
        self._sse_thread.start()
        self._worker.start()
        self.create_timer(hb, self._send_heartbeat)

        self.get_logger().info(f"orchestrator 시작 — BE={base_url} device={device_id}")

    # ── 유틸 ───────────────────────────────────────────────────────────
    def _next_seq(self, key: str) -> int:
        self._seq[key] = self._seq.get(key, 0) + 1
        return self._seq[key]

    @staticmethod
    def _report_id() -> str:
        return uuid.uuid4().hex

    # ── SSE 스레드 → 큐 ────────────────────────────────────────────────
    def _run_sse(self):
        self._be.stream_commands(self._cmd_q.put, self._stop.is_set)

    # ── 워커 스레드: 명령 처리 (HTTP·다운로드 블로킹 OK) ────────────────
    def _run_worker(self):
        while not self._stop.is_set():
            try:
                cmd = self._cmd_q.get(timeout=0.5)
            except queue.Empty:
                continue
            try:
                self._handle(cmd)
            except Exception as e:  # noqa: BLE001
                self.get_logger().error(f"명령 처리 실패 {cmd.get('type')}: {e}")

    def _handle(self, cmd: dict):
        ctype = cmd.get("type")
        cid = cmd.get("commandId")
        # commandId 중복 실행 차단 — BE가 ACK 전까지 같은 명령 재전송하므로 필수.
        if cid is not None:
            if cid in self._seen_cmds:
                self.get_logger().info(f"중복 명령 무시: {ctype} (commandId={cid})")
                return
            self._seen_cmds.add(cid)   # ponytail: 세션 한정 무제한 set, 재시작시 리셋(journal은 후속)
        self.get_logger().info(f"BE 명령: {ctype} (commandId={cid})")
        if ctype == bc.CMD_APPLY_DEPLOYMENT:
            self._apply_deployment(cmd)
        elif ctype == bc.CMD_EXECUTE_TASK:
            self._execute_task(cmd)
        elif ctype == bc.CMD_CANCEL_EXECUTION:
            self._cancel_execution(cmd)
        else:
            self.get_logger().warn(f"알 수 없는 명령 타입: {ctype}")

    # ── 배포 플로우 (JETSON-01 조회 → 다운로드 → JETSON-03 리포트) ──────
    def _apply_deployment(self, cmd: dict):
        dep_id = cmd.get("deploymentId")
        manifest = self._be.get_pending_deployment()
        if not manifest:
            self.get_logger().warn("pending 배포 없음 — APPLY_DEPLOYMENT 무시")
            return
        dep_id = manifest.get("deploymentId", dep_id)
        key = f"dep:{dep_id}"

        try:
            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_DOWNLOADING, 10.0)
            for model in manifest.get("models", []):
                dest = os.path.join(self._ckpt_dir, f"{model['skillVersionId']}.ckpt")
                self._be.download(model["downloadUrl"], dest)
                if not self._verify_sha256(dest, model.get("sha256")):
                    self._be.report_deployment(
                        dep_id, self._report_id(), self._next_seq(key), bc.DEPLOY_FAILED,
                        10.0, error={"code": "SHA256_MISMATCH",
                                     "message": f"{model['skillVersionId']} 무결성 실패"})
                    return

            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_VERIFYING, 60.0)
            # 정책 노드에 ckpt 로드. ⚠️ TODO(46): configure에 ckpt 경로/manifest 메타 전달
            #   (현재 policy_control.configure는 lifecycle 전이만). 46 인터페이스 확정 후 배관.
            self._policy.configure()
            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_APPLYING, 90.0)
            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_SUCCEEDED, 100.0)
            self.get_logger().info(f"배포 {dep_id} 적용 완료")
        except Exception as e:  # noqa: BLE001
            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_FAILED, 0.0,
                                       error={"code": "DEPLOY_ERROR", "message": str(e)})
            raise

    @staticmethod
    def _verify_sha256(path: str, expected: str | None) -> bool:
        if not expected:
            return True  # 서버가 sha256 안 주면 검증 스킵
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        return h.hexdigest() == expected

    # ── 실행 플로우 (정책 activate → JETSON-06 리포트) ──────────────────
    def _execute_task(self, cmd: dict):
        exec_id = cmd.get("executionId")
        key = f"exec:{exec_id}"
        self._be.report_execution(exec_id, self._report_id(), self._next_seq(key),
                                   bc.EXEC_STARTING, progress=0.0)
        if not self._policy.activate():
            self._be.report_execution(
                exec_id, self._report_id(), self._next_seq(key), bc.EXEC_FAILED,
                progress=0.0, error={"code": "POLICY_ACTIVATE_FAILED",
                                     "message": "정책 노드 activate 실패"})
            return
        self._be.report_execution(exec_id, self._report_id(), self._next_seq(key),
                                   bc.EXEC_RUNNING, current_step=1, progress=5.0)
        # ⚠️ TODO(46): 실행 완료(SUCCEEDED/FAILED)·진행률은 정책 노드가 알려줘야 함.
        #   정책 노드 상태 인터페이스(토픽/서비스) 확정되면 구독→step 진행·완료 리포트.
        self.get_logger().info(f"실행 {exec_id} 시작 — 완료 리포트는 정책 노드 상태 연동 후(46)")

    def _cancel_execution(self, cmd: dict):
        exec_id = cmd.get("executionId")
        key = f"exec:{exec_id}"
        self._policy.deactivate()
        self._be.report_execution(exec_id, self._report_id(), self._next_seq(key),
                                   bc.EXEC_CANCELED, progress=0.0)
        self.get_logger().info(f"실행 {exec_id} 취소")

    # ── 토큰 발급/갱신 (DEVICE-05) ──────────────────────────────────────
    def _renew_token(self, initial: bool = False) -> float:
        """secret 으로 JWT 재발급. 만료(기본 900s)의 2/3 지점을 갱신 주기로 반환.
        실패해도 죽지 않음 — 다음 주기에 재시도(초기 실패는 60s 뒤)."""
        try:
            expires = self._be.fetch_device_token(self._device_secret)
            self.get_logger().info(f"device token 발급/갱신 OK ({expires}s 유효)")
            return max(60.0, expires * 2.0 / 3.0)
        except Exception as e:  # noqa: BLE001
            self.get_logger().error(f"token 발급 실패{' (계속 시도)' if initial else ''}: {e}")
            return 60.0

    # ── heartbeat ──────────────────────────────────────────────────────
    def _send_heartbeat(self):
        self._be.heartbeat(contract=self._contract)  # 실패는 be_client가 로깅

    # ── 종료 ───────────────────────────────────────────────────────────
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
        rclpy.try_shutdown()


if __name__ == "__main__":
    main()
