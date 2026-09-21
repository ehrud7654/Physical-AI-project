"""RuntimeExecutor 통합 self-check — 실물(팔·카메라·CUDA) 없이 (S15P21A103-46).

fake_runtime.py 가 진짜 런타임의 I/O 계약(pretty JSON stdout · MOVE stdin · SIGINT)을
흉내낸다. 여기서 검증하는 것:
  - 여러 줄 pretty JSON report 수집
  - --move 시 stdin "MOVE" 자동 입력
  - 취소 → SIGINT → hold 후 정상 종료
  - 실패 exit code 와 오류 문자열 전달
  - SIGINT 무시 시 SIGKILL 에스컬레이션
"""
import sys
import threading
import time
from pathlib import Path

from so101_orchestrator.runtime_executor import RuntimeExecutor

STUB = Path(__file__).parent / "fake_runtime.py"


class _Log:
    def info(self, *_a):
        pass

    def warn(self, *_a):
        pass

    def error(self, *_a):
        pass


class _Node:
    def get_logger(self):
        return _Log()


def _executor(tmp_path, *stub_args):
    ex = RuntimeExecutor(_Node(), runtime_root=str(tmp_path))
    ex.python = Path(sys.executable)          # 실물 venv 대신 현재 인터프리터
    ex.launch = [str(STUB)]                   # 진짜 런타임 대신 스텁
    ex.shanks = tmp_path                      # preflight 통과용(존재하면 됨)
    ex.extra_args = list(stub_args)
    ex.stop_grace_s = 1.0
    return ex


def test_collects_pretty_json_reports_and_move(tmp_path):
    """--move 로 MOVE 입력이 먹고, 여러 줄 JSON report 가 사이클마다 수집된다."""
    ex = _executor(tmp_path)
    got = []
    code, err = ex.run(tmp_path / "model", tmp_path / "out", move=True, cycles=2,
                       on_report=got.append, should_cancel=lambda: False)
    assert code == 0 and err is None
    assert [r["cycle"] for r in got] == [0, 1]          # 두 사이클 다 파싱됨
    assert all(r["status"] == "PASS" for r in got)
    assert got[0]["candidates"][0]["action_index"] == 1  # 중첩 구조까지 온전
    # 스텁은 MOVE 를 못 받으면 1 로 끝난다 → code 0 자체가 stdin 자동입력이 먹었다는 증거


def test_cancel_sends_sigint_and_exits_cleanly(tmp_path):
    """무한 사이클을 취소하면 SIGINT 로 hold 후 0 으로 끝난다."""
    ex = _executor(tmp_path, "--cycle-sec", "0.1")
    got = []
    cancel = threading.Event()

    def on_report(rep):
        got.append(rep)
        cancel.set()                                     # 첫 report 직후 취소

    started = time.time()
    code, _ = ex.run(tmp_path / "m", tmp_path / "o", move=False, cycles=0,
                     on_report=on_report, should_cancel=cancel.is_set)
    assert code == 0
    assert len(got) >= 1
    assert time.time() - started < 10                    # 무한 루프가 실제로 끊겼다


def test_failure_exit_code_and_error(tmp_path):
    """런타임이 실패 종료하면 exit code 와 오류 문자열이 올라온다."""
    ex = _executor(tmp_path, "--fail-at", "0")
    code, err = ex.run(tmp_path / "m", tmp_path / "o", move=False, cycles=3,
                       on_report=lambda _r: None, should_cancel=lambda: False)
    assert code == 1
    assert err and "fake failure" in err


def test_sigkill_escalation_when_sigint_ignored(tmp_path):
    """SIGINT 를 씹는 런타임은 유예 후 SIGKILL 로 정리된다(고아 방지의 마지막 수단)."""
    ex = _executor(tmp_path, "--ignore-sigint", "--cycle-sec", "0.1")
    cancel = threading.Event()

    def on_report(_rep):
        cancel.set()

    started = time.time()
    code, _ = ex.run(tmp_path / "m", tmp_path / "o", move=False, cycles=0,
                     on_report=on_report, should_cancel=cancel.is_set)
    elapsed = time.time() - started
    assert code != 0                                     # SIGKILL 된 프로세스
    assert elapsed < 10                                  # 유예(1s) 후 즉시 정리
