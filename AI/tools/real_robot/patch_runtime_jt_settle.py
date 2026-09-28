"""Add a residual re-command loop to the runtime_jt settle stage.
runtime_jt settle 단계에 잔차 재명령 루프를 추가한다.

Why / 왜:
  A P-controlled servo under gravity load settles with a steady-state error.
  Measured 2026-09-22: elbow -21..-30 ticks, wrist_flex -13 ticks every cycle,
  which composes into -12..-18 mm of TCP z per chunk and accumulates because the
  next chunk is anchored on the drooped observation.
  중력 부하 아래 P 제어 서보는 정상편차를 남긴다. 2026-09-22 실측으로 청크당
  TCP z -12~-18mm 이고, 다음 청크가 처진 관측을 원점으로 삼아 누적된다.

  Servo load at settle was 189/1000 max, so torque is NOT saturated: pushing the
  setpoint past the target by the measured residual should cancel the error.
  정착 시 부하 최대 189/1000 으로 포화가 아니다. 목표를 잔차만큼 더 밀면 상쇄된다.

Defaults keep today's behaviour exactly (--settle-retries 0), so the patched build
is comparable against the unpatched runs.
기본값은 현행 동작 그대로(0회)라 패치 전후 비교가 흔들리지 않는다.

Idempotent. Only touches runtime_jt (our fork), never runtime (현석 소유).
멱등. runtime_jt 만 건드린다.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MARK_CTRL = "[JT-PATCH settle-recommand]"
MARK_CLI = "[JT-PATCH settle-recommand-args]"

CTRL_ANCHOR = 'report["settle_status"] = settle_status'

CTRL_BLOCK = '''
        # {mark} 정상편차를 목표 이동으로 상쇄한다. 기본 0회 = 현행 동작
        recommand_log = []
        _retries = int(getattr(args, "settle_retries", 0) or 0)
        _gain = float(getattr(args, "settle_correction_gain", 1.0) or 1.0)
        if args.settle_timeout > 0 and _retries > 0:
            for _attempt in range(1, _retries + 1):
                _worst = max(map(abs, remaining.values()))
                if _worst <= args.settle_tolerance:
                    break
                corrected = clamp_targets(common, {{
                    servo_id: int(round(final_targets[servo_id] + remaining[servo_id] * _gain))
                    for servo_id in ids
                }})
                common.retry_eintr(
                    lambda: bus.write_positions(
                        corrected, speed=args.speed, acceleration=args.acceleration
                    )
                )
                time.sleep(0.4)
                _wait_started = time.monotonic()
                while True:
                    common.refuse_if_estopped()
                    diagnostics = {{
                        servo_id: common.retry_eintr(
                            lambda servo_id=servo_id: bus.read_diagnostics(servo_id)
                        )
                        for servo_id in ids
                    }}
                    live_ticks = {{
                        servo_id: int(value["tick"]) for servo_id, value in diagnostics.items()
                    }}
                    if not any(value.get("moving", False) for value in diagnostics.values()):
                        break
                    if time.monotonic() - _wait_started > max(1.0, args.settle_timeout):
                        break
                    time.sleep(0.1)
                remaining = {{
                    servo_id: final_targets[servo_id] - live_ticks[servo_id] for servo_id in ids
                }}
                recommand_log.append({{
                    "attempt": _attempt,
                    "gain": _gain,
                    "correction_ticks": {{
                        servo_id: corrected[servo_id] - final_targets[servo_id]
                        for servo_id in ids
                    }},
                    "sent_targets": corrected,
                    "remaining_after": remaining,
                    "worst_after": max(map(abs, remaining.values())),
                    "load_raw": {{
                        servo_id: int(diagnostics[servo_id].get("present_load_raw", -1))
                        for servo_id in ids
                    }},
                }})
        report["settle_recommand"] = recommand_log
        report["settle_recommand_attempts"] = len(recommand_log)
'''.format(mark=MARK_CTRL)

CLI_TEMPLATE = (
    '{indent}# {mark}\n'
    '{indent}{recv}.add_argument("--settle-retries", type=int, default=0,\n'
    '{indent}    help="0 keeps current behaviour; N re-commands the residual up to N times")\n'
    '{indent}{recv}.add_argument("--settle-correction-gain", type=float, default=1.0,\n'
    '{indent}    help="fraction of the measured residual added to the goal on each re-command")\n'
)


class PatchError(RuntimeError):
    """Anchor missing or ambiguous. Refuse rather than write a guessed patch."""


def patch_controller(text: str) -> tuple[str, str]:
    if MARK_CTRL in text:
        return text, "= 이미 적용됨"
    hits = [i for i, line in enumerate(text.splitlines()) if CTRL_ANCHOR in line]
    if len(hits) != 1:
        raise PatchError(f"controller 앵커 {CTRL_ANCHOR!r} 발견 {len(hits)} / 기대 1")
    lines = text.splitlines(keepends=True)
    lines.insert(hits[0], CTRL_BLOCK.lstrip("\n"))
    return "".join(lines), "+ settle 재명령 블록 삽입"


def patch_cli(text: str) -> tuple[str, str]:
    if MARK_CLI in text:
        return text, "= 이미 적용됨"
    pattern = re.compile(r'^(\s*)([A-Za-z_][\w.]*)\.add_argument\(\s*"--settle-timeout"')
    lines = text.splitlines(keepends=True)
    hits = [(i, m) for i, line in enumerate(lines) if (m := pattern.match(line))]
    if len(hits) != 1:
        raise PatchError(f'cli 앵커 "--settle-timeout" add_argument 발견 {len(hits)} / 기대 1')
    index, match = hits[0]
    block = CLI_TEMPLATE.format(indent=match.group(1), recv=match.group(2), mark=MARK_CLI)
    lines.insert(index, block)
    return "".join(lines), "+ --settle-retries / --settle-correction-gain 추가"


def apply(root: Path) -> int:
    targets = {
        root / "robot_controller.py": patch_controller,
        root / "cli.py": patch_cli,
    }
    changed = 0
    for path, fn in targets.items():
        if not path.is_file():
            raise PatchError(f"대상 없음 {path}")
        before = path.read_text(encoding="utf-8")
        after, note = fn(before)
        if after != before:
            path.write_text(after, encoding="utf-8")
            changed += 1
        print(f"  {note}  ({path.name})")
    print(f"변경 {changed} / 대상 {len(targets)}")
    return 0


def _selftest() -> int:
    ctrl = (
        "def execute_chunk(candidates, common, args, report):\n"
        "    with common.ST3215Bus(args.motor_port) as bus:\n"
        "        pass\n"
        '        report["settle_status"] = settle_status\n'
        '        report["final_ticks"] = live_ticks\n'
    )
    cli = (
        "def build_parser():\n"
        "    parser = argparse.ArgumentParser()\n"
        '    parser.add_argument("--settle-tolerance", type=int, default=15)\n'
        '    parser.add_argument("--settle-timeout", type=float, default=0,\n'
        '                        help="0 skips final-target waiting")\n'
    )
    checks: list[tuple[str, bool]] = []
    a, _ = patch_controller(ctrl)
    checks.append(("controller 마커 삽입", MARK_CTRL in a))
    checks.append(("앵커 앞에 삽입", a.index(MARK_CTRL) < a.index(CTRL_ANCHOR)))
    checks.append(("컴파일 가능", _compiles(a)))
    b, note = patch_controller(a)
    checks.append(("controller 멱등", b == a and note.startswith("=")))

    c, _ = patch_cli(cli)
    checks.append(("cli 마커 삽입", MARK_CLI in c))
    checks.append(("수신자 이름 재사용", 'parser.add_argument("--settle-retries"' in c))
    checks.append(("cli 컴파일 가능", _compiles(c)))
    d, note = patch_cli(c)
    checks.append(("cli 멱등", d == c and note.startswith("=")))

    for name, fn, bad in (
        ("판별: controller 앵커 없음", patch_controller, "def f():\n    pass\n"),
        ("판별: cli 앵커 없음", patch_cli, "parser = 1\n"),
        ("판별: controller 앵커 중복", patch_controller, ctrl + ctrl),
    ):
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
    ap = argparse.ArgumentParser(description="runtime_jt settle 재명령 패치")
    ap.add_argument("root", nargs="?", help="runtime_jt/umi_checkpoint_executor 경로")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest or not args.root:
        return _selftest()
    try:
        return apply(Path(args.root))
    except PatchError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
