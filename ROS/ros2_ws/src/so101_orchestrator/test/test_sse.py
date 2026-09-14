"""command-stream SSE 파싱 + 토큰 발급 self-check."""
import httpx
import pytest

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
