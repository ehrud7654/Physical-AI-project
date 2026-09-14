"""BE(제어 평면) 클라이언트 — 젯슨은 아웃바운드 클라이언트다 (S15P21A103-116).

BE 계약 v1.3.0 / 실제 Jetson 컨트롤러 구현 기준:
  명령 수신  GET  /api/v1/robots/me/command-stream        SSE, event=robot.command
  heartbeat POST /api/v1/devices/{deviceId}/heartbeat
  배포 조회  GET  /api/v1/robots/me/deployments/pending    → Manifest | 204
  manifest  POST /api/v1/robots/me/deployments/{id}/manifest  (URL 만료 재발급)
  배포 리포트 PATCH /api/v1/robots/me/deployments/{id}      (reportId·sequence)
  실행 리포트 PATCH /api/v1/robots/me/executions/{id}       (reportId·sequence)
  모델 다운로드 GET <S3 presigned downloadUrl>              (Authorization 안 붙임)

인증: JWT Bearer (DEVICE-05 device access token). 응답은 ApiResponse{data} 래퍼.
FastAPI 같은 서버 프레임워크 없음 — httpx 클라이언트만.
"""
from __future__ import annotations

import json
import time
from typing import Callable

try:
    import httpx
except ImportError:  # 빌드는 되게, 런타임에만 요구
    httpx = None


# 배포 상태 (DeploymentStatus)
DEPLOY_PENDING = "PENDING"
DEPLOY_DOWNLOADING = "DOWNLOADING"
DEPLOY_VERIFYING = "VERIFYING"
DEPLOY_APPLYING = "APPLYING"
DEPLOY_SUCCEEDED = "SUCCEEDED"
DEPLOY_FAILED = "FAILED"
DEPLOY_CANCELED = "CANCELED"

# 실행 상태 (RobotExecutionStatus)
EXEC_STARTING = "STARTING"
EXEC_RUNNING = "RUNNING"
EXEC_SUCCEEDED = "SUCCEEDED"
EXEC_FAILED = "FAILED"
EXEC_CANCELED = "CANCELED"

# 명령 타입 (JetsonCommand.type)
CMD_APPLY_DEPLOYMENT = "APPLY_DEPLOYMENT"
CMD_EXECUTE_TASK = "EXECUTE_TASK"
CMD_CANCEL_EXECUTION = "CANCEL_EXECUTION"

_EVENT_COMMAND = "robot.command"


class BEClient:
    def __init__(self, base_url: str, device_id: str, *, token: str | None = None,
                 connect_timeout: float = 10.0, reconnect_backoff: float = 3.0):
        self.api = base_url.rstrip("/") + "/api/v1"
        self.device_id = device_id
        self.token = token
        self.connect_timeout = connect_timeout
        self.reconnect_backoff = reconnect_backoff

    def _headers(self) -> dict[str, str]:
        h = {}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    @staticmethod
    def _require_httpx():
        if httpx is None:
            raise RuntimeError("httpx 미설치: pip install httpx")

    @staticmethod
    def _unwrap(resp) -> dict:
        """ApiResponse{data} 래퍼를 벗긴다. data 없으면 원문."""
        body = resp.json()
        return body.get("data", body) if isinstance(body, dict) else body

    # ── 명령 수신 (SSE, BE→젯슨) ────────────────────────────────────────
    def stream_commands(self, on_command: Callable[[dict], None],
                        should_stop: Callable[[], bool]) -> None:
        """command-stream SSE를 열고 명령마다 on_command(cmd_dict)를 호출한다.
        블로킹 — **별도 스레드**에서 돌린다. 끊기면 should_stop 까지 재연결.

        서버 이벤트: event=robot.command, id=commandId, data=JetsonCommand(json).
        주석(: connected, : ping)은 무시.
        """
        self._require_httpx()
        url = f"{self.api}/robots/me/command-stream"
        headers = {**self._headers(), "Accept": "text/event-stream"}

        while not should_stop():
            try:
                timeout = httpx.Timeout(self.connect_timeout, read=None)
                with httpx.Client(timeout=timeout) as client:
                    with client.stream("GET", url, headers=headers) as resp:
                        resp.raise_for_status()
                        self._parse_sse(resp.iter_lines(), on_command, should_stop)
            except Exception as e:  # noqa: BLE001 — 어떤 오류든 재연결로 흡수
                if should_stop():
                    break
                print(f"[BEClient] command-stream 오류, {self.reconnect_backoff}s 후 재시도: {e}")
                time.sleep(self.reconnect_backoff)

    @staticmethod
    def _parse_sse(lines, on_command: Callable[[dict], None],
                   should_stop: Callable[[], bool]) -> None:
        event = "message"
        data_buf: list[str] = []
        for raw in lines:
            if should_stop():
                return
            line = raw.rstrip("\r")
            if line == "":                       # 이벤트 경계
                if data_buf and event == _EVENT_COMMAND:
                    payload = "\n".join(data_buf)
                    try:
                        on_command(json.loads(payload))
                    except json.JSONDecodeError:
                        print(f"[BEClient] 명령 JSON 파싱 실패: {payload[:120]}")
                event = "message"
                data_buf = []
            elif line.startswith(":"):
                continue                          # 주석/keepalive
            elif line.startswith("event:"):
                event = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data_buf.append(line[len("data:"):].strip())

    # ── heartbeat (젯슨→BE) ─────────────────────────────────────────────
    def heartbeat(self, *, firmware: str = "", runtime: str = "",
                  contract: str = "") -> dict | None:
        """POST heartbeat. 응답 {serverTime, nextHeartbeatSeconds} 반환(실패 None)."""
        self._require_httpx()
        url = f"{self.api}/devices/{self.device_id}/heartbeat"
        body = {"firmwareVersion": firmware, "runtimeVersion": runtime,
                "contractVersion": contract}
        try:
            r = httpx.post(url, json=body, headers=self._headers(), timeout=10.0)
            r.raise_for_status()
            return self._unwrap(r)
        except Exception as e:  # noqa: BLE001
            print(f"[BEClient] heartbeat 실패: {e}")
            return None

    # ── 배포 (젯슨→BE) ──────────────────────────────────────────────────
    def get_pending_deployment(self) -> dict | None:
        """GET pending manifest. 없으면 None(204)."""
        self._require_httpx()
        url = f"{self.api}/robots/me/deployments/pending"
        r = httpx.get(url, headers=self._headers(), timeout=15.0)
        if r.status_code == 204:
            return None
        r.raise_for_status()
        return self._unwrap(r)

    def renew_manifest(self, deployment_id: str) -> dict:
        """만료된 S3 URL 재발급 (JETSON-02)."""
        self._require_httpx()
        url = f"{self.api}/robots/me/deployments/{deployment_id}/manifest"
        r = httpx.post(url, headers=self._headers(), timeout=15.0)
        r.raise_for_status()
        return self._unwrap(r)

    def report_deployment(self, deployment_id: str, report_id: str, sequence: int,
                          status: str, progress: float, *, items: list | None = None,
                          error: dict | None = None) -> bool:
        """PATCH 배포 상태 (JETSON-03). reportId·sequence로 중복·역순 차단."""
        body = {"reportId": report_id, "sequence": sequence, "status": status,
                "progressPercent": progress}
        if items is not None:
            body["items"] = items
        if error is not None:
            body["error"] = error       # {code, message}
        return self._patch(f"/robots/me/deployments/{deployment_id}", body, "배포")

    # ── 실행 (젯슨→BE) ──────────────────────────────────────────────────
    def report_execution(self, execution_id: str, report_id: str, sequence: int,
                         status: str, *, current_step: int | None = None,
                         progress: float = 0.0, error: dict | None = None) -> bool:
        """PATCH 실행 상태 (JETSON-06). FAILED면 error 필수."""
        body = {"reportId": report_id, "sequence": sequence, "status": status,
                "progressPercent": progress}
        if current_step is not None:
            body["currentStep"] = current_step
        if error is not None:
            body["error"] = error       # {code, message, recoveryAction?}
        return self._patch(f"/robots/me/executions/{execution_id}", body, "실행")

    def _patch(self, path: str, body: dict, what: str) -> bool:
        self._require_httpx()
        try:
            r = httpx.patch(f"{self.api}{path}", json=body,
                            headers=self._headers(), timeout=10.0)
            return 200 <= r.status_code < 300
        except Exception as e:  # noqa: BLE001
            print(f"[BEClient] {what} 리포트 실패: {e}")
            return False

    # ── 모델 다운로드 (S3 presigned) ────────────────────────────────────
    def download(self, url: str, dest_path: str) -> None:
        """presigned S3 URL에서 받아 저장. Authorization 안 붙인다(presigned는 자기인증).
        sha256 검증은 호출자가."""
        self._require_httpx()
        with httpx.stream("GET", url, timeout=None) as r:  # 헤더 없음
            r.raise_for_status()
            with open(dest_path, "wb") as f:
                for chunk in r.iter_bytes():
                    f.write(chunk)
