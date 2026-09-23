"""command-stream SSE 파싱 + 토큰 발급 + 재시도 견고화 self-check (S15P21A103-51)."""
import httpx
import pytest

from so101_orchestrator import be_client as bc
from so101_orchestrator.be_client import BEClient
from so101_orchestrator.orchestrator_node import OrchestratorNode   # import만, Node 생성 X


def test_parse_sse_extracts_commands():
    lines = [
        ": connected",                                             # 주석 무시
        "event: robot.command",
        'data: {"type": "EXECUTE_TASK", "commandId": "c1"}',
        "",                                                        # 이벤트 경계
        ": ping",                                                  # keepalive 무시
        "event: robot.command",
        'data: {"type": "APPLY_DEPLOYMENT", "commandId": "c2"}',
        "",
    ]
    got: list[dict] = []
    BEClient._parse_sse(iter(lines), got.append, lambda: False)
    assert [c["type"] for c in got] == ["EXECUTE_TASK", "APPLY_DEPLOYMENT"]
    assert got[0]["commandId"] == "c1"


def test_parse_sse_ignores_non_command_events():
    lines = [
        "event: message",
        'data: {"foo": 1}',
        "",
    ]
    got: list[dict] = []
    BEClient._parse_sse(iter(lines), got.append, lambda: False)
    assert got == []            # robot.command 아니면 무시


def test_fetch_device_token(monkeypatch):
    """올바른 바디로 POST 하고 accessToken 저장·expiresIn 반환."""
    seen = {}

    def handler(req: httpx.Request) -> httpx.Response:
        import json as _j
        seen["url"] = str(req.url)
        seen["json"] = _j.loads(req.content)
        return httpx.Response(200, json={"data": {"accessToken": "jwt-abc",
                                                  "tokenType": "Bearer", "expiresIn": 900}})

    monkeypatch.setattr(httpx, "post",
                        lambda url, **kw: httpx.Client(
                            transport=httpx.MockTransport(handler)).post(url, **kw))
    c = BEClient("http://be", "dev-1")
    expires = c.fetch_device_token("s3cr3t")
    assert expires == 900
    assert c.token == "jwt-abc"
    assert seen["json"] == {"deviceId": "dev-1", "deviceSecret": "s3cr3t"}
    assert seen["url"].endswith("/api/v1/device-auth/token")


def test_fetch_device_token_rejects_bad_secret(monkeypatch):
    """401 이면 예외 — 토큰은 안 바뀜."""
    def handler(req: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"error": {"code": "INVALID_CREDENTIALS"}})

    monkeypatch.setattr(httpx, "post",
                        lambda url, **kw: httpx.Client(
                            transport=httpx.MockTransport(handler)).post(url, **kw))
    c = BEClient("http://be", "dev-1")
    with pytest.raises(httpx.HTTPStatusError):
        c.fetch_device_token("wrong")
    assert c.token is None


def test_request_reauth_on_401(monkeypatch):
    """401 → reauth 후 새 토큰으로 1회 재시도."""
    auth_seen = []
    responses = [httpx.Response(401), httpx.Response(200, json={"data": {"ok": 1}})]

    def fake_request(method, url, **kw):
        auth_seen.append(kw.get("headers", {}).get("Authorization"))
        return responses.pop(0)

    monkeypatch.setattr(httpx, "request", fake_request)
    c = BEClient("http://be", "d1", token="old")
    reauthed = []
    c.reauth = lambda: (setattr(c, "token", "new"), reauthed.append(True))
    r = c._request("GET", "http://be/x")
    assert r.status_code == 200
    assert reauthed == [True]
    assert auth_seen == ["Bearer old", "Bearer new"]   # 재발급 후 새 토큰으로 재시도


def test_patch_retries_on_5xx(monkeypatch):
    """5xx는 동일 reportId·sequence·payload로 재시도."""
    seq = [httpx.Response(503), httpx.Response(503), httpx.Response(200)]
    bodies = []

    def fake_request(method, url, **kw):
        bodies.append(kw.get("json"))
        return seq.pop(0)

    monkeypatch.setattr(httpx, "request", fake_request)
    monkeypatch.setattr(bc.time, "sleep", lambda *_a, **_k: None)   # 대기 스킵
    c = BEClient("http://be", "d1", token="t")
    ok = c._patch("/robots/me/executions/e1",
                  {"reportId": "r", "sequence": 1, "status": "RUNNING"}, "실행")
    assert ok is True
    assert len(bodies) == 3
    assert all(b["reportId"] == "r" and b["sequence"] == 1 for b in bodies)


def test_patch_no_retry_on_4xx(monkeypatch):
    """409 STATE_CONFLICT 등 4xx는 재시도 없이 즉시 종료."""
    calls = []

    def fake_request(method, url, **kw):
        calls.append(1)
        return httpx.Response(409, json={"error": {"code": "STATE_CONFLICT"}})

    monkeypatch.setattr(httpx, "request", fake_request)
    monkeypatch.setattr(bc.time, "sleep", lambda *_a, **_k: None)
    c = BEClient("http://be", "d1", token="t")
    ok = c._patch("/x", {"status": "SUCCEEDED"}, "배포")
    assert ok is False
    assert len(calls) == 1


# ── orchestrator_node 순수 헬퍼 (rclpy init 불필요) ─────────────────────
def test_clock_drift_parses_z_suffix():
    """'Z'·나노초 ISO 파싱 OK, 미래 시각은 +drift, 쓰레기는 None."""
    from datetime import datetime, timedelta, timezone
    future = (datetime.now(timezone.utc) + timedelta(seconds=120)).strftime(
        "%Y-%m-%dT%H:%M:%S.123456789Z")
    d = OrchestratorNode._clock_drift_sec(future)
    assert d is not None and 110 < d < 130
    assert abs(OrchestratorNode._clock_drift_sec(
        datetime.now(timezone.utc).isoformat())) < 5
    assert OrchestratorNode._clock_drift_sec("garbage") is None
    assert OrchestratorNode._clock_drift_sec("") is None


def test_find_model_url():
    m = {"models": [{"skillVersionId": "a", "downloadUrl": "u-a"},
                    {"skillVersionId": "b", "downloadUrl": "u-b"}]}
    assert OrchestratorNode._find_model_url(m, "b") == "u-b"
    assert OrchestratorNode._find_model_url(m, "zzz") is None
    assert OrchestratorNode._find_model_url({}, "a") is None


def test_verify_size(tmp_path):
    p = tmp_path / "x.ckpt"
    p.write_bytes(b"12345")
    assert OrchestratorNode._verify_size(str(p), 5) is True
    assert OrchestratorNode._verify_size(str(p), "5") is True      # 문자열 숫자도 허용
    assert OrchestratorNode._verify_size(str(p), 6) is False
    assert OrchestratorNode._verify_size(str(p), None) is True     # 서버가 안 주면 스킵


def _cancel_fake(active, done):
    """Node 생성 없이 _cancel_execution 을 부를 fake self. 보낸 report status 를 수집."""
    from types import SimpleNamespace
    sent = []
    fake = SimpleNamespace(
        _active_exec=active,
        _done_execs=set(done),
        _cancel=SimpleNamespace(set=lambda: None),
        _stop_engines=lambda: None,
        _report_id=lambda: "r",
        _next_seq=lambda _k: 1,
        _be=SimpleNamespace(report_execution=lambda *a, **k: sent.append(k.get("status", a))),
        get_logger=lambda: SimpleNamespace(info=lambda *a: None),
    )
    return fake, sent


def test_cancel_ignores_finished_execution():
    """이미 종료된 실행에 늦게 온 CANCEL 은 재보고하지 않는다 —
    안 그러면 종료 상태 위에 CANCELED 가 덮여 BE 가 '작업중' 에 멈춘다."""
    fake, sent = _cancel_fake(active=None, done={"exec-1"})
    OrchestratorNode._cancel_execution(fake, {"executionId": "exec-1"})
    assert sent == []                       # 종료된 실행 → 아무것도 안 보냄


def test_cancel_reports_unknown_execution():
    """실행 중도 아니고 종료 기록도 없는 exec 의 CANCEL 은 한 번 CANCELED 로 보고한다."""
    fake, sent = _cancel_fake(active=None, done=set())
    OrchestratorNode._cancel_execution(fake, {"executionId": "exec-2"})
    assert len(sent) == 1
