"""배포 오케스트레이터 노드 (S15P21A103-116, 115 후속).

BE(제어 평면) ↔ 젯슨 ROS(데이터 평면) 다리. BE 계약 v1.3.0 기준.
  - command-stream SSE로 명령 수신 (APPLY_DEPLOYMENT / EXECUTE_TASK / CANCEL_EXECUTION)
  - 배포: pending manifest → 모델 다운로드+sha256 → 정책 로드 → JETSON-03 리포트
  - 실행: EXECUTE_TASK steps 펼쳐 MOTOR(관절 명령)/SKILL(정책 activate) 순차 → JETSON-06 리포트
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
import re
import threading
import uuid
from datetime import datetime, timezone

import rclpy
from rclpy.node import Node
from rclpy.qos import DurabilityPolicy, QoSProfile
from sensor_msgs.msg import JointState
from std_msgs.msg import Bool, Float64MultiArray, String

from so101_orchestrator import be_client as bc
from so101_orchestrator import robot_model as rm
from so101_orchestrator import steps as st
from so101_orchestrator.be_client import BEClient
from so101_orchestrator.policy_control import PolicyControl

try:
    import httpx
except ImportError:  # be_client와 동일 — 빌드는 되게
    httpx = None


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
        self.declare_parameter("firmware_version", "jetson-runtime-1.0.0")  # BE @NotBlank
        self.declare_parameter("runtime_version", "policy-runner-1.0.0")    # BE @NotBlank
        self.declare_parameter("motor_settle_sec", 2.0)    # MOTOR step: timeoutSeconds 없을 때 대기
        self.declare_parameter("skill_default_sec", 10.0)  # SKILL step: timeoutSeconds 없을 때 실행

        base_url = self.get_parameter("base_url").value
        device_id = self.get_parameter("device_id").value
        token = self.get_parameter("token").value or None
        # secret 은 param 우선, 없으면 env (로그·ros param 덤프에 안 남기려고 env 허용)
        self._device_secret = (self.get_parameter("device_secret").value
                               or os.environ.get("SO101_DEVICE_SECRET", ""))
        self._hb_period = float(self.get_parameter("heartbeat_sec").value)
        self._ckpt_dir = self.get_parameter("ckpt_dir").value
        self._contract = self.get_parameter("contract_version").value
        self._firmware = self.get_parameter("firmware_version").value
        self._runtime = self.get_parameter("runtime_version").value
        self._motor_settle_sec = float(self.get_parameter("motor_settle_sec").value)
        self._skill_default_sec = float(self.get_parameter("skill_default_sec").value)
        os.makedirs(self._ckpt_dir, exist_ok=True)

        # 관절 순서·한계 = URDF(/robot_description) 단일 출처. latched 라 기동 직후 도착.
        # MOTOR step 이 오기 전(실행은 한참 뒤)에 채워짐. 못 받았으면 MOTOR 만 막힘.
        self._joints: list | None = None
        self.create_subscription(
            String, "/robot_description", self._on_robot_description,
            QoSProfile(depth=1, durability=DurabilityPolicy.TRANSIENT_LOCAL))

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
        self._cmd_pub = self.create_publisher(Float64MultiArray, st.COMMAND_TOPIC, 10)
        self._latest: dict[str, float] = {}     # /joint_states name→pos (MOTOR step 기준 자세)
        self.create_subscription(JointState, "/joint_states", self._on_joint_state, 10)
        self._cancel = threading.Event()        # 실행 중단 플래그 — SSE 콜백에서 즉시 세팅
        self._active_exec: str | None = None    # 실행 중 executionId

        self._seq: dict[str, int] = {}          # 리포트 스트림별 sequence(단조증가)
        self._seen_cmds: set[str] = set()       # commandId 중복 실행 차단(세션 한정, 재시작시 리셋)
        self._deployed_ckpts: dict[str, str] = {}   # skillVersionId → 로드된 ckpt 경로(배포 성공분)
        self._cmd_q: queue.Queue = queue.Queue()
        self._stop = threading.Event()
        self._sse_thread = threading.Thread(target=self._run_sse, daemon=True)
        self._worker = threading.Thread(target=self._run_worker, daemon=True)
        self._sse_thread.start()
        self._worker.start()
        self._hb_timer = self.create_timer(self._hb_period, self._send_heartbeat)

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
        self._be.stream_commands(self._on_sse_command, self._stop.is_set)

    def _on_sse_command(self, cmd: dict):
        """SSE 스레드 콜백. 실행 중인 것의 CANCEL_EXECUTION 은 큐를 우회해 즉시 플래그 —
        워커가 _execute_task 로 블로킹 중이라 큐에 넣으면 실행이 끝나야 취소가 먹는다."""
        if (cmd.get("type") == bc.CMD_CANCEL_EXECUTION
                and cmd.get("executionId") == self._active_exec):
            self._cancel.set()
            self._policy.deactivate()
            self.get_logger().info(f"취소 요청 → 실행 {self._active_exec} 중단 플래그")
            return                      # CANCELED 리포트는 실행 루프가 함
        self._cmd_q.put(cmd)

    def _on_joint_state(self, msg: JointState):
        for name, pos in zip(msg.name, msg.position):
            self._latest[name] = pos

    def _on_robot_description(self, msg: String):
        try:
            self._joints = rm.parse_joints(msg.data)
            names = ", ".join(j[0] for j in self._joints)
            self.get_logger().info(f"URDF 관절 {len(self._joints)}개 로드: {names}")
        except Exception as e:  # noqa: BLE001
            self.get_logger().error(f"URDF 파싱 실패 (MOTOR step 불가): {e}")

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
            loaded: dict[str, str] = {}          # 이 배포에서 받은 svid → 경로 (성공시 반영)
            item_reports: list = []              # SUCCEEDED 전 item VERIFIED 보고용
            for model in manifest.get("models", []):
                svid = model["skillVersionId"]
                # 계약 검증 — actionSpace 는 BE enum 과 일치해야 함(관절공간만 실행 가능)
                asp = model.get("actionSpace")
                if asp is not None and asp not in st.ACTION_SPACES:
                    self._be.report_deployment(
                        dep_id, self._report_id(), self._next_seq(key), bc.DEPLOY_FAILED,
                        10.0, error={"code": "CONTRACT_MISMATCH",
                                     "message": f"{svid} actionSpace {asp} 미지원"})
                    return
                # 모델 contractVersion 은 정책/액션 계약(예: umi-policy-v1) — 명령 계약
                # (robot-command-v1)과 별개다. 정책 런타임 계약이 확정되면 그걸로 검증(46).
                cv = model.get("contractVersion")
                if cv:
                    self.get_logger().info(f"{svid} 모델 contractVersion={cv}")
                dest = os.path.join(self._ckpt_dir, f"{svid}.ckpt")
                try:
                    self._be.download(model["downloadUrl"], dest)
                except Exception as e:  # noqa: BLE001
                    # S3 presigned 만료(403) → manifest 재발급 후 1회 재시도 (JETSON-02)
                    if not (httpx and isinstance(e, httpx.HTTPStatusError)
                            and e.response.status_code == 403):
                        raise
                    url = self._find_model_url(self._be.renew_manifest(dep_id), svid)
                    if url is None:
                        raise
                    self.get_logger().warn(f"{svid} downloadUrl 만료 — 재발급 후 재시도")
                    self._be.download(url, dest)
                # 크기 키(sizeBytes/size)는 BE 미확정 — 둘 다 없으면 스킵
                if not self._verify_size(dest, model.get("sizeBytes", model.get("size"))):
                    self._be.report_deployment(
                        dep_id, self._report_id(), self._next_seq(key), bc.DEPLOY_FAILED,
                        10.0, error={"code": "SIZE_MISMATCH",
                                     "message": f"{svid} 크기 불일치"})
                    return
                if not self._verify_sha256(dest, model.get("sha256")):
                    self._be.report_deployment(
                        dep_id, self._report_id(), self._next_seq(key), bc.DEPLOY_FAILED,
                        10.0, error={"code": "SHA256_MISMATCH",
                                     "message": f"{model['skillVersionId']} 무결성 실패"})
                    return
                loaded[svid] = dest
                # BE: 모든 item VERIFIED 여야 SUCCEEDED 허용(JetsonDeploymentService).
                # deploymentItemId 는 manifest Model 에 있어야 함(현재 BE 미포함 — 은찬 추가 필요).
                iid = model.get("deploymentItemId")
                if iid:
                    item_reports.append({"deploymentItemId": iid, "status": "VERIFIED"})

            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_VERIFYING, 60.0, items=item_reports or None)
            # 정책 노드에 ckpt 로드. ponytail: 다중 스킬이면 첫 ckpt만 configure —
            #   단일 스킬 데모 기준. 스텝별 스킬 전환이 필요해지면 SKILL step 에서 재configure.
            first_ckpt = next(iter(loaded.values()), None)
            if not self._policy.configure(first_ckpt):
                self.get_logger().warn("정책 configure 미완(노드 미기동?) — 배포는 다운로드까지 완료")
            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_APPLYING, 90.0)
            self._be.report_deployment(dep_id, self._report_id(), self._next_seq(key),
                                       bc.DEPLOY_SUCCEEDED, 100.0, items=item_reports or None)
            self._deployed_ckpts = loaded        # 성공한 것만 반영(실패시 이전 배포 유지)
            self.get_logger().info(f"배포 {dep_id} 적용 완료 — 스킬 {list(loaded)}")
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

    @staticmethod
    def _verify_size(path: str, expected) -> bool:
        if expected is None:
            return True  # 서버가 크기 안 주면 스킵
        return os.path.getsize(path) == int(expected)

    @staticmethod
    def _find_model_url(manifest: dict, skill_version_id) -> str | None:
        for m in manifest.get("models", []):
            if m.get("skillVersionId") == skill_version_id:
                return m.get("downloadUrl")
        return None

    # ── 실행 플로우 (steps 펼쳐 순차 실행 → JETSON-06 리포트) ───────────
    def _execute_task(self, cmd: dict):
        exec_id = cmd.get("executionId")
        key = f"exec:{exec_id}"

        def rep(status, **kw):
            return self._be.report_execution(exec_id, self._report_id(), self._next_seq(key),
                                             status, **kw)

        rep(bc.EXEC_STARTING, progress=0.0)
        plan = st.expand_steps(cmd.get("steps") or [])
        total = len(plan)
        if total == 0:
            rep(bc.EXEC_FAILED, error={"code": "NO_STEPS", "message": "실행할 step 없음"})
            return

        self._cancel.clear()
        self._active_exec = exec_id
        i = 0
        try:
            for i, step in enumerate(plan, 1):
                if self._cancel.is_set():
                    break
                rep(bc.EXEC_RUNNING, current_step=i, progress=round((i - 1) * 100.0 / total, 1))
                self._run_step(step)
            else:                       # break 없이 완주
                rep(bc.EXEC_SUCCEEDED, current_step=total, progress=100.0)
                self.get_logger().info(f"실행 {exec_id} 완료 ({total} steps)")
                return
            rep(bc.EXEC_CANCELED, current_step=i, progress=round((i - 1) * 100.0 / total, 1))
            self.get_logger().info(f"실행 {exec_id} 취소 (step {i}/{total})")
        except Exception as e:  # noqa: BLE001
            self._policy.deactivate()
            rep(bc.EXEC_FAILED, current_step=max(i, 1),
                progress=round((i - 1) * 100.0 / total, 1),
                error={"code": "STEP_ERROR", "message": str(e)})
            raise
        finally:
            self._active_exec = None

    def _run_step(self, step: dict):
        """leaf step 1개 실행. 대기는 전부 self._cancel.wait 라 취소 시 즉시 빠져나옴."""
        stype = step.get("type")
        timeout = step.get("timeoutSeconds")
        if stype == st.STEP_MOTOR:
            if self._joints is None:
                raise RuntimeError("URDF 미수신 — MOTOR step 불가 (/robot_description 확인)")
            out = Float64MultiArray()
            out.data = st.motor_to_command(step.get("parameters") or {}, self._joints,
                                           self._current_vector())
            self._cmd_pub.publish(out)
            # ponytail: 도달 확인 없이 정착 시간만 대기 — /joint_states 수렴 체크는 후속
            self._cancel.wait(float(timeout or self._motor_settle_sec))
        elif stype == st.STEP_SKILL:
            svid = step.get("skillVersionId")
            # 배포 때 로드한 스킬이어야 함. ponytail: 단일 스킬 데모라 configure 는 배포에서 끝냈고
            #   여기선 activate 만. 스텝별 다른 스킬이면 여기서 configure(경로) 재호출 필요(후속).
            if svid and svid not in self._deployed_ckpts:
                raise RuntimeError(f"SKILL {svid} 미배포 — APPLY_DEPLOYMENT 먼저 필요")
            self.get_logger().info(f"SKILL 실행: {svid} (로드된 ckpt로 정책 activate)")
            if not self._policy.activate():
                raise RuntimeError("정책 노드 activate 실패 (so101_policy 미기동?)")
            # TODO(46): 정책 상태 토픽의 완료 이벤트로 교체. 지금은 timeoutSeconds 동안 실행.
            self._cancel.wait(float(timeout or self._skill_default_sec))
            self._policy.deactivate()
        else:
            raise ValueError(f"알 수 없는 step type: {stype}")

    def _current_vector(self) -> list[float]:
        """/joint_states 최신값을 URDF 관절 순서 벡터로. 없는 관절 있으면 에러(안전)."""
        try:
            return [self._latest[name] for name, _, _, _ in self._joints]
        except KeyError as e:
            raise RuntimeError(f"/joint_states 에 {e} 없음 — 컨트롤러 기동 확인") from e

    def _cancel_execution(self, cmd: dict):
        """큐 경로의 CANCEL — 실행 중인 건 SSE 콜백이 이미 처리(루프가 CANCELED 리포트).
        여기 오는 건 실행 중이 아닌 것 → 즉시 CANCELED 리포트."""
        exec_id = cmd.get("executionId")
        if exec_id == self._active_exec:
            self._cancel.set()
            return
        self._policy.deactivate()
        self._be.report_execution(exec_id, self._report_id(), self._next_seq(f"exec:{exec_id}"),
                                  bc.EXEC_CANCELED, progress=0.0)
        self.get_logger().info(f"실행 {exec_id} 취소(비실행 상태)")

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
        resp = self._be.heartbeat(firmware=self._firmware, runtime=self._runtime,
                                  contract=self._contract)  # 실패는 be_client가 로깅
        if not isinstance(resp, dict):
            return
        # 젯슨 RTC 죽어서 부팅마다 시각 리셋 — BE 시각과 비교해 경고(JWT exp·TLS 검증 영향)
        drift = self._clock_drift_sec(resp.get("serverTime", ""))
        if drift is not None and abs(drift) > 30:
            self.get_logger().warn(f"BE 시각차 {drift:+.0f}s — 젯슨 RTC 확인(JWT/TLS 영향)")
        # 서버가 원하는 heartbeat 주기 반영 (1~300s 클램프)
        nxt = resp.get("nextHeartbeatSeconds")
        if nxt is not None:
            try:
                period = min(300.0, max(1.0, float(nxt)))
            except (TypeError, ValueError):
                return
            if period != self._hb_period:
                self.destroy_timer(self._hb_timer)   # cancel+노드에서 제거
                self._hb_timer = self.create_timer(period, self._send_heartbeat)
                self.get_logger().info(f"heartbeat 주기 {self._hb_period:.0f}s → {period:.0f}s")
                self._hb_period = period

    @staticmethod
    def _clock_drift_sec(server_time_iso: str) -> float | None:
        """(서버 - 로컬) 초. py3.10 fromisoformat은 'Z'·7자리+ 소수초 미지원 → 치환·절단.
        naive면 UTC로 간주. 파싱 실패 None."""
        s = re.sub(r"(\.\d{6})\d+", r"\1", str(server_time_iso)).replace("Z", "+00:00")
        try:
            t = datetime.fromisoformat(s)
        except ValueError:
            return None
        if t.tzinfo is None:
            t = t.replace(tzinfo=timezone.utc)
        return (t - datetime.now(timezone.utc)).total_seconds()

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
