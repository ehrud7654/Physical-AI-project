"""Let the policy release the gripper after a squeeze latch (runtime_jt).
조임 래치 뒤에도 정책이 '열어라'를 내면 그리퍼를 풀어 준다.

Why / 왜 (2026-09-25 r4 실측):
  patch_runtime_jt_squeeze 는 한 번 수렴하면 held 를 단조 유지한다. 자체검사에
  "정책이 열어도 안 열림" 이 PASS 항목으로 들어 있다 — 즉 놓기가 설계상 막혀 있다.
  r4 는 파지·리프트(+103mm)·운반까지 갔고 20사이클 제한에서 끝났다 (정책 예측 폭 40.8mm, 아직 놓기 전).
  사이클을 늘리면 상자 위에서 정책이 열라고 해도 안 열린다.

Rule / 규칙:
  latch 이후 policy_tick <= held - squeeze - release_ticks 가 confirm 연속 청크 → 해제.
  해제 뒤에는 이번 실행에서 다시 조이지 않는다 (열린 폭에서 재수렴해 145틱 닫히는 것 방지).
  기본 --gripper-release-ticks 0 = 현행 동작 그대로.

Requires patch_runtime_jt_squeeze applied first. Idempotent. runtime_jt only.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MARK_CTRL = "[JT-PATCH gripper-release]"
MARK_CLI = "[JT-PATCH gripper-release-args]"
OLD_LINE = "_converged = _votes >= _sq_need or _held is not None"

NEW_TEMPLATE = '''{i}# {mark} latch 뒤 정책이 충분히 열면 confirm 연속으로 해제. 기본 0 = off
{i}_rel_n = int(getattr(args, "gripper_release_ticks", 0))
{i}_rel_votes = int(getattr(args, "_rel_votes", 0))
{i}if _rel_n and _held is not None and _p_tick <= _held - _sq_n - _rel_n:
{i}    _rel_votes += 1
{i}else:
{i}    _rel_votes = 0
{i}args._rel_votes = _rel_votes
{i}if _rel_n and _held is not None and _rel_votes >= _sq_need:
{i}    _held = None
{i}    args._sq_held = None
{i}    _votes = 0
{i}    args._sq_votes = 0
{i}    args._sq_released = True
{i}_released = bool(getattr(args, "_sq_released", False))
{i}report["gripper_release"] = {{"release_ticks": _rel_n, "votes": _rel_votes, "released": _released}}
{i}_converged = (_votes >= _sq_need or _held is not None) and not _released
'''

CLI_TEMPLATE = (
    '{indent}# {mark}\n'
    '{indent}{recv}.add_argument("--gripper-release-ticks", type=int, default=0,\n'
    '{indent}    help="after a squeeze latch, release when the policy opens this many ticks beyond its converged tick")\n'
)


class PatchError(RuntimeError):
    """Anchor missing or ambiguous. Refuse rather than write a guessed patch."""


def patch_controller(text: str) -> tuple[str, str]:
    if MARK_CTRL in text:
        return text, "= 이미 적용됨"
    lines = text.splitlines(keepends=True)
    hits = [k for k, line in enumerate(lines) if line.strip() == OLD_LINE]
    if len(hits) != 1:
        raise PatchError(f"squeeze 줄 발견 {len(hits)} / 기대 1 (squeeze 패치 먼저)")
    k = hits[0]
    indent = lines[k][: len(lines[k]) - len(lines[k].lstrip())]
    lines[k] = NEW_TEMPLATE.format(i=indent, mark=MARK_CTRL)
    return "".join(lines), "+ release 블록으로 교체"


def patch_cli(text: str) -> tuple[str, str]:
    if MARK_CLI in text:
        return text, "= 이미 적용됨"
    pattern = re.compile(r'^(\s*)([A-Za-z_][\w.]*)\.add_argument\("--gripper-squeeze-ticks"')
    lines = text.splitlines(keepends=True)
    hits = [(k, m) for k, line in enumerate(lines) if (m := pattern.match(line))]
    if len(hits) != 1:
        raise PatchError(f'cli 앵커 "--gripper-squeeze-ticks" 발견 {len(hits)} / 기대 1')
    k, m = hits[0]
    lines.insert(k, CLI_TEMPLATE.format(indent=m.group(1), recv=m.group(2), mark=MARK_CLI))
    return "".join(lines), "+ --gripper-release-ticks 추가"


def apply_to(root: Path) -> int:
    changed = 0
    for name, fn in (("robot_controller.py", patch_controller), ("cli.py", patch_cli)):
        path = root / name
        if not path.is_file():
            raise PatchError(f"대상 없음 {path}")
        before = path.read_text(encoding="utf-8")
        after, note = fn(before)
        if after != before:
            compile(after, str(path), "exec")
            path.write_text(after, encoding="utf-8")
            changed += 1
        print(f"  {note}  ({name})")
    print(f"변경 {changed} / 대상 2")
    return 0


# ---------------- self-test: run the REAL patched block, not a mirror ----------------

def _squeeze_block() -> str:
    """The block exactly as patch_runtime_jt_squeeze inserts it (fixture).
    squeeze 패치가 넣는 블록 원문 (고정 입력)."""
    import importlib.util

    here = Path(__file__).with_name("patch_runtime_jt_squeeze.py")
    if here.is_file():
        spec = importlib.util.spec_from_file_location("sq", here)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod.CTRL_BLOCK
    raise PatchError(f"자체검사에 {here} 필요")


def _run(block: str, ticks: list[int], release: int, squeeze: int = 145) -> list[int]:
    """Execute the patched block chunk by chunk; return the commanded gripper tick per chunk.
    패치된 블록을 청크마다 실제 실행해 명령 틱을 돌려준다."""
    import textwrap
    from types import SimpleNamespace

    body = textwrap.dedent(block)
    common = SimpleNamespace(GRIPPER_ID=6, tick_limits=lambda _i: (130, 1763))
    args = SimpleNamespace(gripper_squeeze_ticks=squeeze, gripper_squeeze_tolerance_ticks=20,
                           gripper_squeeze_confirm=2, enable_gripper=True, gripper_release_ticks=release)
    out = []
    for p in ticks:
        cands = [{"predicted_gripper_tick": p, "gripper_tick": p}]
        ns = {"args": args, "common": common, "candidates": cands, "report": {}}
        exec(compile(body, "<block>", "exec"), ns)
        out.append(cands[0]["gripper_tick"])
    return out


def _selftest() -> int:
    checks: list[tuple[str, bool]] = []
    sq = _squeeze_block()
    patched = "def f(args, common, candidates, report):\n" + sq
    new, _ = patch_controller(patched)
    checks.append(("교체 후 컴파일", _compiles(new)))
    again, note = patch_controller(new)
    checks.append(("controller 멱등", again == new and note.startswith("=")))
    blk = new.split("\n", 1)[1]
    blk_old = sq

    # r4 모양: 접근 → 1023~1067 수렴 → 운반 중 ±20 흔들림 → 상자 위에서 열기(≈75mm → tick 403)
    seq = [330, 500, 700, 900, 1023, 1030, 1040, 1067, 1050, 1060, 1023, 1045, 403, 403, 403, 410]
    old = _run(blk_old, seq, 0)
    got = _run(blk, seq, 250)
    off = _run(blk, seq, 0)
    checks.append(("release 0 = 기존 동작과 동일", off == old))
    checks.append(("기존 동작은 안 열린다 (문제 재현)", old[-1] > 1100))
    checks.append(("접근 구간 무개입", got[:4] == seq[:4]))
    held = max(got[4:12])
    checks.append(("운반 중 조임 유지", min(got[6:12]) >= 1023 + 145 and held == 1067 + 145))
    checks.append(("열기 1청크째는 아직 유지 (confirm 2)", got[12] == held))
    checks.append(("열기 2청크째 해제 → 정책 틱", got[13] == 403))
    checks.append(("해제 뒤 재조임 없음", got[14:] == seq[14:]))
    # 운반 중 한 번 튀는 값에는 안 풀린다
    spike = [900, 1023, 1030, 1040, 700, 1040, 1035]
    s = _run(blk, spike, 250)
    checks.append(("단발 튐 무시", s[4] == s[3] and s[5] >= s[3]))
    # 폭이 조금 넓어진 예측(임계 안쪽)에는 안 풀린다
    near = [1023, 1030, 1040, 900, 900, 900]
    n = _run(blk, near, 250)
    checks.append(("임계 안쪽 무해제", all(v >= 1040 + 145 for v in n[3:])))

    cli = ('def build_parser():\n'
           '    parser = argparse.ArgumentParser()\n'
           '    parser.add_argument("--gripper-squeeze-ticks", type=int, default=0,\n'
           '        help="x")\n')
    c, _ = patch_cli(cli)
    checks.append(("cli 삽입", MARK_CLI in c and _compiles(c)))
    d, note = patch_cli(c)
    checks.append(("cli 멱등", d == c and note.startswith("=")))
    for name, fn, bad in (("판별: squeeze 미적용 거부", patch_controller, "def f():\n    pass\n"),
                          ("판별: cli 앵커 없음", patch_cli, "parser = 1\n"),
                          ("판별: 줄 중복 거부", patch_controller, patched + sq)):
        try:
            fn(bad)
            checks.append((name, False))
        except PatchError:
            checks.append((name, True))

    ok = sum(1 for _, p in checks if p)
    for name, passed in checks:
        print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
    print(f"자체검사 통과 {ok} / 전체 {len(checks)}")
    return 0 if ok == len(checks) else 1


def _compiles(source: str) -> bool:
    try:
        compile(source, "<patched>", "exec")
        return True
    except SyntaxError:
        return False


def main() -> int:
    ap = argparse.ArgumentParser(description="runtime_jt 조임 해제 패치")
    ap.add_argument("root", nargs="?")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest or not a.root:
        return _selftest()
    if _selftest() != 0:
        print("FAIL: 자체검사 실패 — 적용하지 않는다", file=sys.stderr)
        return 1
    try:
        return apply_to(Path(a.root))
    except PatchError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
