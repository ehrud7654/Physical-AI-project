"""BE(제어 평면) 클라이언트 — 젯슨은 아웃바운드 클라이언트다 (S15P21A103-116).

BE 계약 v1.5.0 / 실제 Jetson 컨트롤러 구현 기준:
  토큰 발급  POST /api/v1/device-auth/token               {deviceId, deviceSecret} → JWT (900s)
  명령 수신  GET  /api/v1/robots/me/command-stream        SSE, event=robot.command
  heartbeat POST /api/v1/devices/{deviceId}/heartbeat
  배포 조회  GET  /api/v1/robots/me/deployments/pending    → Manifest | 204
  manifest  POST /api/v1/robots/me/deployments/{id}/manifest  (URL 만료 재발급)
  배포 리포트 PATCH /api/v1/robots/me/deployments/{id}      (reportId·sequence)
  실행 리포트 PATCH /api/v1/robots/me/executions/{id}       (reportId·sequence)
  현재 배포  GET  /api/v1/robots/me/deployment/current     (재시작 복구, JetsonStateController)
  활성 스킬  GET  /api/v1/robots/me/skills/active
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
                 connect_timeout: float = 10.0, reconnect_backoff: float = 3.0,
                 max_reconnect_backoff: float = 30.0):
        self.api = base_url.rstrip("/") + "/api/v1"
        self.device_id = device_id
        self.token = token
        self.connect_timeout = connect_timeout
        self.reconnect_backoff = reconnect_backoff
        self.max_reconnect_backoff = max_reconnect_backoff
        # 401 시 토큰 재발급 콜백(오케스트레이터가 secret으로 주입). 없으면 재시도 안 함.
        self.reauth: Callable[[], None] | None = None

    def _headers(self) -> dict[str, str]:
        h = {}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    @staticmethod
    def _require_httpx():
        if httpx is None:
            raise RuntimeError("httpx 미설치: pip install httpx")

    # ── 토큰 발급 (DEVICE-05, 공개 경로) ────────────────────────────────
    def fetch_device_token(self, device_secret: str) -> int:
        """POST device-auth/token 으로 JWT 발급받아 self.token 갱신. expiresIn(초) 반환.
        공개 경로 — Bearer 없이 deviceSecret 로 인증. 실패(401 INVALID_CREDENTIALS 등)면 예외."""
        self._require_httpx()
        url = f"{self.api}/device-auth/token"
        r = httpx.post(url, json={"deviceId": self.device_id, "deviceSecret": device_secret},
                       timeout=10.0)
        r.raise_for_status()
        data = self._unwrap(r)
        self.token = data["accessToken"]
        return int(data.get("expiresIn", 900))

    @staticmethod
    def _unwrap(resp) -> dict:
        """ApiResponse{data} 래퍼를 벗긴다. data 없으면 원문."""
        body = resp.json()
        return body.get("data", body) if isinstance(body, dict) else body

    def _request(self, method: str, url: str, **kw):
        """인증 요청. 401이면 reauth(토큰 재발급) 후 1회 재시도. httpx.Response 반환."""
        self._require_httpx()
        r = httpx.request(method, url, headers=self._headers(), **kw)
        if r.status_code == 401 and self.reauth:
            try:
                self.reauth()
            except Exception as e:  # noqa: BLE001 — 재발급 실패면 원래 401 반환
                print(f"[BEClient] 401 토큰 재발급 실패: {e}")
                return r
            r = httpx.request(method, url, headers=self._headers(), **kw)
        return r

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
        backoff = self.reconnect_backoff

        while not should_stop():
            # Accept-Encoding: identity — gzip 이면 httpx 가 스트림을 통째로 버퍼링해서
            #   SSE 이벤트가 안 흘러온다(curl 은 gzip 요청 안 해서 됐던 것). 압축 끔.
            headers = {**self._headers(), "Accept": "text/event-stream",
                       "Accept-Encoding": "identity", "Cache-Control": "no-cache"}
            try:
                timeout = httpx.Timeout(self.connect_timeout, read=None)
                with httpx.Client(timeout=timeout, http2=False) as client:
                    with client.stream("GET", url, headers=headers) as resp:
                        if resp.status_code == 401 and self.reauth:
                            self.reauth()          # 토큰 만료 → 재발급 후 재연결
                        resp.raise_for_status()
                        backoff = self.reconnect_backoff   # 연결 성공 → 백오프 리셋
                        print("[BEClient] command-stream 연결됨")
                        # iter_lines 는 이벤트 경계(빈 줄)를 안 넘겨줄 때가 있어 직접 줄 분리.
                        self._parse_sse(self._iter_lines(resp), on_command, should_stop)
            except Exception as e:  # noqa: BLE001 — 어떤 오류든 재연결로 흡수
                if should_stop():
                    break
                print(f"[BEClient] command-stream 오류, {backoff:.0f}s 후 재시도: {e}")
                time.sleep(backoff)
                backoff = min(backoff * 2, self.max_reconnect_backoff)  # 지수 백오프

    @staticmethod
    def _iter_lines(resp):
        """SSE 바이트를 직접 \\n 으로 쪼개 **빈 줄 포함** 모든 줄을 넘긴다.
        httpx iter_lines 가 이벤트 경계(빈 줄)를 안 넘길 때가 있어 직접 처리."""
        buf = ""
        for chunk in resp.iter_text():
            buf += chunk
            while "\n" in buf:
                line, buf = buf.split("\n", 1)
                yield line

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
            r = self._request("POST", url, json=body, timeout=10.0)
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
        r = self._request("GET", url, timeout=15.0)
        if r.status_code == 204:
            return None
        r.raise_for_status()
        return self._unwrap(r)

    def renew_manifest(self, deployment_id: str) -> dict:
        """만료된 S3 URL 재발급 (JETSON-02)."""
        url = f"{self.api}/robots/me/deployments/{deployment_id}/manifest"
        r = self._request("POST", url, timeout=15.0)
        r.raise_for_status()
        return self._unwrap(r)

    def get_current_deployment(self) -> dict | None:
        """GET 현재 적용 배포 {deploymentId, taskVersionId, steps, skills, appliedAt}. 없으면 None."""
        r = self._request("GET", f"{self.api}/robots/me/deployment/current", timeout=15.0)
        if r.status_code in (204, 404):
            return None
        r.raise_for_status()
        return self._unwrap(r)

    def get_active_skills(self) -> list:
        """GET 활성 SkillVersion 목록 [{skillId, skillKey, skillVersionId, version, status, ...}]."""
        r = self._request("GET", f"{self.api}/robots/me/skills/active", timeout=15.0)
        r.raise_for_status()
        return self._unwrap(r) or []

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

    def _patch(self, path: str, body: dict, what: str,
               *, retries: int = 3, backoff: float = 1.0) -> bool:
        """PATCH 리포트. 같은 body(동일 reportId·sequence·payload)로 재시도 →
        서버가 멱등 처리. 5xx·네트워크만 재시도, 4xx(409 STATE_CONFLICT 등)는 즉시 종료."""
        url = f"{self.api}{path}"
        for attempt in range(1, retries + 1):
            try:
                r = self._request("PATCH", url, json=body, timeout=10.0)
                if 200 <= r.status_code < 300:
                    return True
                if 400 <= r.status_code < 500:   # 거부는 재시도 무의미
                    print(f"[BEClient] {what} 리포트 거부 {r.status_code}: {body.get('status')}")
                    return False
                print(f"[BEClient] {what} 리포트 {r.status_code}({attempt}/{retries})")
            except Exception as e:  # noqa: BLE001 — 네트워크 오류만 재시도
                print(f"[BEClient] {what} 리포트 실패({attempt}/{retries}): {e}")
            if attempt < retries:
                time.sleep(backoff * attempt)
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
