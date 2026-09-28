"""Retry corrupted servo replies more often, without touching servo error-flag handling.
깨진 서보 응답(체크섬·헤더·타임아웃)만 더 재시도한다. 서보 에러 플래그 처리는 그대로.

Why / 왜 (2026-09-26 실측):
  새 2번 모터 응답만 깨진다. 토크 OFF 20초 긴 읽기 2번 13/111 실패, 3~6번 0/444.
  설정 주소 0~39 는 3번과 ID 외 동일. 3회 재시도로는 청크당 요청 수백 개 중 하나가
  3연속 깨져 실행이 죽는다 (r13 c0).

Rule / 규칙:
  - 서보가 보낸 에러 플래그(0x20 과부하 등)는 지금과 똑같이 3회 뒤 예외.
  - 통신 손상(체크섬·헤더·ID·타임아웃)만 JT_COMM_RETRIES 회까지 재시도.
  - JT_COMM_RETRIES 기본 3 = 현행 동작과 동일.

Idempotent. runtime_jt/umi_checkpoint_executor/servo_bus.py only.
"""
from __future__ import annotations

import sys
from pathlib import Path

MARK = "[JT-PATCH comm-retry]"

A_CLASS = "class ServoError(RuntimeError):\n    pass\n"
B_CLASS = (
    "class ServoError(RuntimeError):\n    pass\n\n\n"
    f"class ServoFlagError(ServoError):\n    \"\"\"{MARK} error byte reported by the servo itself.\"\"\"\n"
)
A_FLAG = '            raise ServoError(f"servo {servo_id} error: 0x{error:02x}")\n'
B_FLAG = '            raise ServoFlagError(f"servo {servo_id} error: 0x{error:02x}")\n'
A_IMPORT = "import time\n"
B_IMPORT = "import os\nimport time\n"
A_LOOP = '''        for _ in range(3):
            self.serial.reset_input_buffer()
            self.serial.write(packet)
            self.serial.flush()
            try:
                return self._read_status(servo_id)
            except ServoError as error:
                self.request_retries += 1
                last_error = error
                time.sleep(0.01)
        self.request_failures += 1
        raise ServoError(f"servo {servo_id} failed after 3 attempts: {last_error}") from last_error
'''
B_LOOP = f'''        # {MARK} 플래그 에러는 3회 그대로, 통신 손상만 JT_COMM_RETRIES 회까지 (기본 3 = 현행)
        comm_max = max(3, int(os.environ.get("JT_COMM_RETRIES", "3")))
        flag_attempts = comm_attempts = 0
        while True:
            self.serial.reset_input_buffer()
            self.serial.write(packet)
            self.serial.flush()
            try:
                return self._read_status(servo_id)
            except ServoFlagError as error:
                flag_attempts += 1
                last_error = error
            except ServoError as error:
                comm_attempts += 1
                last_error = error
            self.request_retries += 1
            if flag_attempts >= 3 or flag_attempts + comm_attempts >= comm_max:
                break
            time.sleep(0.01)
        self.request_failures += 1
        raise ServoError(f"servo {{servo_id}} failed after {{flag_attempts + comm_attempts}} attempts: {{last_error}}") from last_error
'''


class PatchError(RuntimeError):
    """Anchor missing or ambiguous."""


def patch(text: str) -> tuple[str, str]:
    if MARK in text:
        return text, "= 이미 적용됨"
    for name, a in (("class", A_CLASS), ("flag", A_FLAG), ("import", A_IMPORT), ("loop", A_LOOP)):
        if text.count(a) != 1:
            raise PatchError(f"앵커 {name} 발견 {text.count(a)} / 기대 1")
    text = text.replace(A_CLASS, B_CLASS).replace(A_FLAG, B_FLAG).replace(A_LOOP, B_LOOP)
    text = text.replace(A_IMPORT, B_IMPORT, 1)
    return text, "+ comm-retry 적용"


def _selftest(src: Path) -> int:
    """Run the REAL patched ST3215Bus against a scripted fake serial.
    패치된 실제 ST3215Bus 를 가짜 시리얼로 돌린다."""
    import importlib.util
    import os
    import tempfile
    import types

    checks: list[tuple[str, bool]] = []
    new, _ = patch(src.read_text(encoding="utf-8"))
    again, note = patch(new)
    checks.append(("멱등", again == new and note.startswith("=")))
    tmp = Path(tempfile.mkdtemp()) / "sb.py"
    tmp.write_text(new, encoding="utf-8")
    sys.modules.setdefault("serial", types.SimpleNamespace(Serial=None))
    spec = importlib.util.spec_from_file_location("sb_patched", tmp)
    sb = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sb)

    def status(err: int, good_ck: bool = True) -> bytes:
        body = bytes((2, 4, err, 0x00, 0x08))
        ck = (~sum(body)) & 0xFF
        return b"\xff\xff" + body + bytes((ck if good_ck else ck ^ 0x55,))

    class Fake:
        def __init__(self, replies):
            self.replies, self.buf, self.writes = list(replies), b"", 0
        def reset_input_buffer(self): self.buf = b""
        def write(self, _p):
            self.writes += 1
            self.buf = self.replies.pop(0) if self.replies else b""
        def flush(self): pass
        def read(self, n):
            out, self.buf = self.buf[:n], self.buf[n:]
            return out

    def run(replies, env):
        os.environ["JT_COMM_RETRIES"] = env
        bus = sb.ST3215Bus.__new__(sb.ST3215Bus)
        bus.serial, bus.request_retries, bus.request_failures = Fake(replies), 0, 0
        sb.time.sleep = lambda _s: None
        try:
            bus._request(2, 0x02, bytes((56, 2)))
            return "ok", bus.serial.writes
        except sb.ServoError as e:
            return str(e), bus.serial.writes

    r, w = run([status(0, False)] * 3, "3")
    checks.append(("기본3: 체크섬 3연속 → 3회 뒤 예외 (현행)", "failed after 3 attempts" in r and w == 3))
    r, w = run([status(0, False)] * 5 + [status(0)], "8")
    checks.append(("8: 체크섬 5연속 뒤 정상 → 성공", r == "ok" and w == 6))
    r, w = run([status(0x20)] * 10, "8")
    checks.append(("8: 0x20 과부하는 여전히 3회 뒤 예외", "0x20" in r and w == 3))
    r, w = run([status(0x20)] * 10, "3")
    checks.append(("기본3: 0x20 3회 뒤 예외 (현행)", "0x20" in r and w == 3))
    r, w = run([status(0, False)] * 20, "8")
    checks.append(("8: 계속 깨지면 8회 뒤 예외", "failed after 8 attempts" in r and w == 8))
    try:
        patch("x = 1\n")
        checks.append(("판별: 앵커 없으면 거부", False))
    except PatchError:
        checks.append(("판별: 앵커 없으면 거부", True))
    ok = sum(v for _, v in checks)
    for n, v in checks:
        print(f"  [{'PASS' if v else 'FAIL'}] {n}")
    print(f"자체검사 통과 {ok} / 전체 {len(checks)}")
    return 0 if ok == len(checks) else 1


def main() -> int:
    if len(sys.argv) != 2:
        print("사용: patch_runtime_jt_comm_retry.py runtime_jt/umi_checkpoint_executor")
        return 2
    path = Path(sys.argv[1]) / "servo_bus.py"
    if _selftest(path) != 0:
        print("FAIL: 자체검사 실패 — 적용하지 않는다", file=sys.stderr)
        return 1
    before = path.read_text(encoding="utf-8")
    try:
        after, note = patch(before)
    except PatchError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        return 2
    if after != before:
        compile(after, str(path), "exec")
        path.write_text(after, encoding="utf-8")
    print(f"  {note}  (servo_bus.py)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
