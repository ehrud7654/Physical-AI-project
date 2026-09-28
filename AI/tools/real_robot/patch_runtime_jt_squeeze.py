"""Add a one-shot final squeeze to the runtime_jt gripper command.
runtime_jt 그리퍼 명령에 1회성 최종 조임을 추가한다.

Why / 왜 (2026-09-22 실측):
  --gripper-close-offset-ticks adds N ticks to an ABSOLUTE gripper target, and the
  policy anchors its next command on the resulting observation. So the offset
  compounds: the gripper closes N ticks every cycle regardless of distance.
  Measured with N=91: policy commanded only +10 ticks per cycle while the gripper
  went 73.9 -> 35.3 mm in five cycles, finishing 27 mm before the object was reached.
  오프셋은 절대 목표에 더해지고 정책이 그 결과 관측에 앵커하므로 매 사이클 복리로 닫힌다.

  What is actually needed is a single extra close AFTER the policy has stopped
  closing, because the policy converges ~5 mm wider than the object.
  필요한 것은 정책이 더 이상 닫지 않게 된 뒤의 1회 추가 조임이다.

Rule / 규칙:
  policy_tick = candidates[-1]["predicted_gripper_tick"]
  |policy_tick - previous| <= tolerance for `confirm` consecutive chunks -> converged
  then held = max(held, policy_tick + squeeze_ticks), monotone, applied to every waypoint.

Defaults keep today's behaviour exactly (--gripper-squeeze-ticks 0).
기본값 0 이면 현행 동작과 동일하다.

Idempotent. runtime_jt only.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MARK_CTRL = "[JT-PATCH gripper-squeeze]"
MARK_CLI = "[JT-PATCH gripper-squeeze-args]"
CTRL_ANCHOR = "with common.ST3215Bus(args.motor_port) as bus:"

CTRL_BLOCK = '''
        # {mark} 정책이 더 닫지 않게 되면 1회만 추가로 조인다. 기본 0 = off
        if getattr(args, "gripper_squeeze_ticks", 0) and args.enable_gripper and candidates:
            _sq_n = int(args.gripper_squeeze_ticks)
            _sq_tol = int(getattr(args, "gripper_squeeze_tolerance_ticks", 20))
            _sq_need = int(getattr(args, "gripper_squeeze_confirm", 2))
            _sq_lo, _sq_hi = common.tick_limits(common.GRIPPER_ID)
            _p_tick = int(candidates[-1]["predicted_gripper_tick"])
            _prev = getattr(args, "_sq_prev", None)
            _votes = int(getattr(args, "_sq_votes", 0))
            _held = getattr(args, "_sq_held", None)
            if _prev is not None and abs(_p_tick - _prev) <= _sq_tol:
                _votes += 1
            else:
                _votes = 0
            args._sq_prev = _p_tick
            args._sq_votes = _votes
            _converged = _votes >= _sq_need or _held is not None
            if _converged:
                _want = min(max(_p_tick + _sq_n, _sq_lo), _sq_hi)
                _held = _want if _held is None else max(_held, _want)
                args._sq_held = _held
                for _c in candidates:
                    _c["gripper_tick"] = int(_held)
            report["gripper_squeeze"] = {{
                "policy_tick": _p_tick, "votes": _votes, "confirm": _sq_need,
                "tolerance_ticks": _sq_tol, "squeeze_ticks": _sq_n,
                "converged": bool(_converged), "held_tick": _held,
                "commanded_ticks": [int(_c["gripper_tick"]) for _c in candidates],
            }}
'''.format(mark=MARK_CTRL)

CLI_TEMPLATE = (
    '{indent}# {mark}\n'
    '{indent}{recv}.add_argument("--gripper-squeeze-ticks", type=int, default=0,\n'
    '{indent}    help="extra closing ticks applied ONCE after the policy gripper command converges")\n'
    '{indent}{recv}.add_argument("--gripper-squeeze-tolerance-ticks", type=int, default=20,\n'
    '{indent}    help="per-chunk policy gripper change counted as converged")\n'
    '{indent}{recv}.add_argument("--gripper-squeeze-confirm", type=int, default=2,\n'
    '{indent}    help="consecutive converged chunks required before squeezing")\n'
)


class PatchError(RuntimeError):
    """Anchor missing or ambiguous. Refuse rather than write a guessed patch."""


def patch_controller(text: str) -> tuple[str, str]:
    if MARK_CTRL in text:
        return text, "= 이미 적용됨"
    lines = text.splitlines(keepends=True)
    hits = [i for i, line in enumerate(lines) if CTRL_ANCHOR in line]
    if len(hits) != 1:
        raise PatchError(f"controller 앵커 발견 {len(hits)} / 기대 1")
    lines.insert(hits[0] + 1, CTRL_BLOCK.lstrip("\n"))
    return "".join(lines), "+ squeeze 블록 삽입"


def patch_cli(text: str) -> tuple[str, str]:
    if MARK_CLI in text:
        return text, "= 이미 적용됨"
    pattern = re.compile(r'^(\s*)([A-Za-z_][\w.]*)\.add_argument\(\s*"--enable-gripper"')
    lines = text.splitlines(keepends=True)
    hits = [(i, m) for i, line in enumerate(lines) if (m := pattern.match(line))]
    if len(hits) != 1:
        raise PatchError(f'cli 앵커 "--enable-gripper" 발견 {len(hits)} / 기대 1')
    index, match = hits[0]
    lines.insert(index, CLI_TEMPLATE.format(indent=match.group(1), recv=match.group(2), mark=MARK_CLI))
    return "".join(lines), "+ --gripper-squeeze-* 3종 추가"


def apply_to(root: Path) -> int:
    changed = 0
    for name, fn in (("robot_controller.py", patch_controller), ("cli.py", patch_cli)):
        path = root / name
        if not path.is_file():
            raise PatchError(f"대상 없음 {path}")
        before = path.read_text(encoding="utf-8")
        after, note = fn(before)
        if after != before:
            path.write_text(after, encoding="utf-8")
            changed += 1
        print(f"  {note}  ({name})")
    print(f"변경 {changed} / 대상 2")
    return 0


def _simulate(policy_ticks, squeeze=91, tol=20, confirm=2, lo=130, hi=1763):
    """Mirror of the patched logic, for the self-test. / 자체검사용 동일 로직."""
    prev, votes, held, out = None, 0, None, []
    for p in policy_ticks:
        votes = votes + 1 if prev is not None and abs(p - prev) <= tol else 0
        prev = p
        if votes >= confirm or held is not None:
            held = max(held or 0, min(max(p + squeeze, lo), hi))
            out.append(held)
        else:
            out.append(p)
    return out


def _selftest() -> int:
    checks: list[tuple[str, bool]] = []
    # 접근 중 정책이 계속 닫는다 -> 건드리지 않는다
    approach = [330, 431, 531, 631, 863, 1030]
    got = _simulate(approach)
    checks.append(("접근 구간 무개입", got == approach))
    # 수렴 후에만 1회 조인다 (오늘 실측 모양: 982 근처에서 멈춤)
    conv = [863, 975, 982, 984, 983]
    got = _simulate(conv)
    # 975 에서 votes=0, 982 에서 votes=1, 984 에서 votes=2 -> 여기서 발동
    checks.append(("수렴 전 무개입", got[:3] == [863, 975, 982]))
    checks.append(("2연속 수렴 후 발동", got[3] == 984 + 91))
    checks.append(("발동 후 단조 유지", all(b >= a for a, b in zip(got[3:], got[4:]))))
    reopen = _simulate([900, 905, 903, 870])
    checks.append(("발동 시점", reopen[2] == 903 + 91))
    checks.append(("정책이 열어도 안 열림", reopen[3] == reopen[2]))
    checks.append(("상한 클램프", _simulate([1700, 1702, 1701])[-1] == 1763))
    checks.append(("한 번도 수렴 안 하면 무개입",
                   _simulate([200, 400, 600, 900, 1200]) == [200, 400, 600, 900, 1200]))

    ctrl = ('def execute_chunk(candidates, common, args, report):\n'
            '    trace = []\n'
            '    with common.ST3215Bus(args.motor_port) as bus:\n'
            '        for candidate in candidates:\n'
            '            pass\n')
    cli = ('def build_parser():\n'
           '    parser = argparse.ArgumentParser()\n'
           '    parser.add_argument("--enable-gripper", action="store_true")\n')
    a, _ = patch_controller(ctrl)
    checks.append(("controller 삽입", MARK_CTRL in a and _compiles(a)))
    b, note = patch_controller(a)
    checks.append(("controller 멱등", b == a and note.startswith("=")))
    c, _ = patch_cli(cli)
    checks.append(("cli 삽입", MARK_CLI in c and _compiles(c)))
    d, note = patch_cli(c)
    checks.append(("cli 멱등", d == c and note.startswith("=")))
    for name, fn, bad in (("판별: controller 앵커 없음", patch_controller, "def f():\n    pass\n"),
                          ("판별: cli 앵커 없음", patch_cli, "parser = 1\n"),
                          ("판별: 앵커 중복", patch_controller, ctrl + ctrl)):
        try:
            fn(bad); checks.append((name, False))
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
    ap = argparse.ArgumentParser(description="runtime_jt 최종 조임 패치")
    ap.add_argument("root", nargs="?")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest or not args.root:
        return _selftest()
    try:
        return apply_to(Path(args.root))
    except PatchError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
