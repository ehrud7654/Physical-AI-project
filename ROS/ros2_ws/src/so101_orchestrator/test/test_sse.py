"""command-stream SSE 파싱 + 토큰 발급 + 재시도 견고화 self-check (S15P21A103-51)."""
import httpx
import pytest

from so101_orchestrator import be_client as bc
from so101_orchestrator.be_client import BEClient


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
