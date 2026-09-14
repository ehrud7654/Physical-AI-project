"""command-stream SSE 파싱 self-check — robot.command 이벤트만 뽑고 주석은 무시."""
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
