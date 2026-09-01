"""BE(제어 평면)와의 통신 — 젯슨은 클라이언트다.

명령(BE→젯슨): SSE 스트림을 아웃바운드로 열어 받는다. (젯슨이 NAT 뒤라 아웃바운드)
상태(젯슨→BE): HTTP POST.

FastAPI 같은 서버 프레임워크는 여기 없다. httpx 클라이언트만 쓴다.

⚠️ 엔드포인트 경로·이벤트 필드·인증은 BE와 미확정(S15P21A103-115, 24).
   docs/deploy_control_interface_draft.md 의 제안을 기본값으로 두고, 합의되면 여기만 고친다.
"""

from __future__ import annotations

import json
import time
from typing import Callable

try:
    import httpx
except ImportError:  # 빌드는 되게, 런타임에만 요구
    httpx = None


class BEClient:
    """BE 제어 평면 클라이언트. SSE 수신 + 상태 POST."""

    def __init__(self, base_url: str, device_id: str, *, token: str | None = None,
                 connect_timeout: float = 10.0, reconnect_backoff: float = 3.0):
        self.base_url = base_url.rstrip("/")
        self.device_id = device_id
        self.token = token  # TODO(BE): 인증 방식 확정 (§7-1)
        self.connect_timeout = connect_timeout
        self.reconnect_backoff = reconnect_backoff

    # --- 헤더 ---------------------------------------------------------------
    def _headers(self) -> dict[str, str]:
        h = {"X-Device-Id": self.device_id}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h

    # --- 명령 수신 (SSE, BE→젯슨) -------------------------------------------
    def stream_commands(self, on_event: Callable[[str, dict], None],
                        should_stop: Callable[[], bool]) -> None:
        """SSE 스트림을 열고 이벤트마다 on_event(event_type, data)를 호출한다.
        연결이 끊기면 should_stop()이 True가 될 때까지 재연결한다.

        이 메서드는 블로킹이므로 **별도 스레드**에서 돌린다.

        SSE 포맷 가정 (BE와 확정 필요):
            event: <type>\n
            data: <json>\n
            \n
        엔드포인트: GET {base}/api/deploy/stream?device_id=...  (TODO: BE 확정)
        """
        if httpx is None:
            raise RuntimeError("httpx 미설치: pip install httpx")

        url = f"{self.base_url}/api/deploy/stream"  # TODO(BE): 경로 확정
        params = {"device_id": self.device_id}

        while not should_stop():
            try:
                with httpx.Client(timeout=httpx.Timeout(self.connect_timeout, read=None)) as client:
                    with client.stream("GET", url, params=params, headers=self._headers()) as resp:
                        resp.raise_for_status()
                        self._parse_sse(resp.iter_lines(), on_event, should_stop)
            except Exception as e:  # noqa: BLE001 — 어떤 오류든 재연결로 흡수
                if should_stop():
                    break
                # TODO: 로깅은 노드 쪽 logger로 넘기는 게 나음
                print(f"[BEClient] SSE 연결 오류, {self.reconnect_backoff}s 후 재시도: {e}")
                time.sleep(self.reconnect_backoff)

    @staticmethod
    def _parse_sse(lines, on_event: Callable[[str, dict], None],
                   should_stop: Callable[[], bool]) -> None:
        """SSE 라인 스트림을 event/data 블록으로 파싱한다."""
        event_type = "message"
        data_buf: list[str] = []
        for raw in lines:
            if should_stop():
                return
            line = raw.rstrip("\r")
            if line == "":  # 빈 줄 = 이벤트 경계
                if data_buf:
                    payload = "\n".join(data_buf)
                    try:
                        data = json.loads(payload) if payload else {}
                    except json.JSONDecodeError:
                        data = {"_raw": payload}
                    on_event(event_type, data)
                event_type = "message"
                data_buf = []
            elif line.startswith(":"):
                continue  # 주석/keepalive
            elif line.startswith("event:"):
                event_type = line[len("event:"):].strip()
            elif line.startswith("data:"):
                data_buf.append(line[len("data:"):].strip())

    # --- 상태 전송 (POST, 젯슨→BE) ------------------------------------------
    def post_status(self, status_type: str, **fields) -> bool:
        """상태 한 건을 BE로 POST. 성공하면 True.
        엔드포인트: POST {base}/api/deploy/status  (TODO: BE 확정)
        """
        if httpx is None:
            raise RuntimeError("httpx 미설치: pip install httpx")

        payload = {"device_id": self.device_id, "type": status_type, **fields}
        # ts 는 노드 쪽에서 채워 넣는 게 낫다(시계 일원화). 여기선 fields로 받음.
        url = f"{self.base_url}/api/deploy/status"  # TODO(BE): 경로 확정
        try:
            r = httpx.post(url, json=payload, headers=self._headers(), timeout=10.0)
            return 200 <= r.status_code < 300
        except Exception as e:  # noqa: BLE001
            print(f"[BEClient] 상태 POST 실패({status_type}): {e}")
            return False

    # --- ckpt 다운로드 (EC2 경유, S3는 BE가 처리) ---------------------------
    def download_ckpt(self, url: str, dest_path: str) -> None:
        """ckpt 를 EC2 엔드포인트에서 받아 dest_path 에 저장한다.
        무결성(sha256) 검증은 호출자(정책 로드 쪽)가 한다.
        url 은 load_ckpt 이벤트가 준 EC2 엔드포인트(프리사인드 아님, EC2가 S3 연동).
        """
        if httpx is None:
            raise RuntimeError("httpx 미설치: pip install httpx")
        with httpx.stream("GET", url, headers=self._headers(), timeout=None) as r:
            r.raise_for_status()
            with open(dest_path, "wb") as f:
                for chunk in r.iter_bytes():
                    f.write(chunk)
