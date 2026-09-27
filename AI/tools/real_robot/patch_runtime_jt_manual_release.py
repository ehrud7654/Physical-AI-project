"""Open the gripper on a human signal (flag file) and keep it open for the rest of the run.
사람이 신호(플래그 파일)를 주면 그리퍼를 열고, 그 실행이 끝날 때까지 열린 채로 둔다.

Why / 왜 (2026-09-26 r20):
  쥐기·들기(+100mm)·상자 위까지 운반은 정책이 했다. 사용자 확인: "놓으면 무조건 들어가는 위치".
  그런데 정책이 40사이클 내내 폭 42.5mm(쥐어라)를 냈다. 놓기는 정책이 하지 못한다.
  → 놓기만 사람이 신호로 준다. 기록에는 manual_release 로 남긴다 (정책 성공으로 세지 않음).

Rule / 규칙:
  청크 실행 직전, 플래그 파일(기본 ~/release_now, JT_RELEASE_FLAG 로 변경)이 있으면
  이후 모든 청크의 그리퍼 목표 = 80mm 열림. 조임 래치는 해제로 표시(args._sq_released).
  플래그가 없으면 아무것도 바꾸지 않는다.

Idempotent. runtime_jt/umi_checkpoint_executor/robot_controller.py only.
"""
from __future__ import annotations

import sys
from pathlib import Path

MARK = "[JT-PATCH manual-release]"
ANCHOR = "        for candidate in candidates:\n            common.refuse_if_estopped()\n"
BLOCK = f'''        # {MARK} 플래그 파일 또는 상자 영역(좌우 <= JT_RELEASE_ZONE_X, 2청크 연속)이면 80mm 로 연다. 둘 다 없으면 무개입
        import os as _mr_os
        _mr_flag = _mr_os.path.expanduser(_mr_os.environ.get("JT_RELEASE_FLAG", "~/release_now"))
        _mr_zone = float(_mr_os.environ.get("JT_RELEASE_ZONE_X", "0") or 0)
        if _mr_zone > 0 and getattr(args, "_hf_gripped", False) and candidates:
            _mr_x = float(candidates[-1]["target_table_mm"][0])
            args._mr_zone_votes = (getattr(args, "_mr_zone_votes", 0) + 1) if _mr_x <= _mr_zone else 0
        _mr_reason = ("flag" if _mr_os.path.exists(_mr_flag) else
                      "zone" if getattr(args, "_mr_zone_votes", 0) >= 2 else None)
        if args.enable_gripper and (getattr(args, "_manual_open", False) or _mr_reason):
            from .robot_calibration import width_to_tick as _mr_w2t
            if not getattr(args, "_manual_open", False):
                print(f"MANUAL RELEASE ({{_mr_reason}}) — 그리퍼 연다", flush=True)
                args._mr_first_reason = _mr_reason
            args._manual_open = True
            args._sq_held = None
            args._sq_released = True
            _mr_tick = int(_mr_w2t(0.08))
            for _c in candidates:
                _c["gripper_tick"] = _mr_tick
            report["manual_release"] = {{"reason": getattr(args, "_mr_first_reason", None), "open_tick": _mr_tick, "zone_x": _mr_zone}}
'''


class PatchError(RuntimeError):
    """Anchor missing or ambiguous."""


def patch(text: str) -> tuple[str, str]:
    if MARK in text:
        return text, "= 이미 적용됨"
    n = text.count(ANCHOR)
    if n != 1:
        raise PatchError(f"앵커 발견 {n} / 기대 1")
    return text.replace(ANCHOR, BLOCK + ANCHOR), "+ manual-release 삽입"


def _run_block(flag_exists: bool, args, flag: Path, x: float = 400.0, zone: float = 0.0) -> list[int]:
    """Execute the inserted block alone with fakes. / 삽입 블록만 가짜 입력으로 실행."""
    import os
    import textwrap
    import types

    if flag_exists:
        flag.write_text("")
    elif flag.exists():
        flag.unlink()
    os.environ["JT_RELEASE_FLAG"] = str(flag)
    os.environ["JT_RELEASE_ZONE_X"] = str(zone)
    pkg = types.ModuleType("fakepkg")
    pkg.__path__ = []
    cal = types.ModuleType("fakepkg.robot_calibration")
    cal.width_to_tick = lambda w: int(round(1763 - min(max(w / 0.09, 0), 1) * (1763 - 130)))
    sys.modules["fakepkg"], sys.modules["fakepkg.robot_calibration"] = pkg, cal
    cands = [{"gripper_tick": 1212, "target_table_mm": [x, 400.0, 220.0]} for _ in range(4)]
    ns = {"args": args, "candidates": cands, "report": {}, "__package__": "fakepkg", "__name__": "fakepkg.x"}
    exec(compile(textwrap.dedent(BLOCK), "<block>", "exec"), ns)
    return [c["gripper_tick"] for c in cands]


def _selftest(src: Path) -> int:
    import tempfile
    from types import SimpleNamespace

    checks: list[tuple[str, bool]] = []
    text = src.read_text(encoding="utf-8")
    new, _ = patch(text)
    again, note = patch(new)
    checks.append(("멱등", again == new and note.startswith("=")))
    checks.append(("컴파일", bool(compile(new, "<patched>", "exec"))))
    flag = Path(tempfile.mkdtemp()) / "release_now"
    a = SimpleNamespace(enable_gripper=True, _sq_held=1212)
    checks.append(("플래그 없음 → 그대로", _run_block(False, a, flag) == [1212] * 4 and a._sq_held == 1212))
    got = _run_block(True, a, flag)
    checks.append(("플래그 생김 → 80mm(311틱)로 연다", got == [311] * 4 and a._sq_held is None and a._sq_released))
    checks.append(("플래그 지워도 계속 연 채", _run_block(False, a, flag) == [311] * 4))
    z = SimpleNamespace(enable_gripper=True, _sq_held=1119)
    checks.append(("영역: 안 쥐었으면 x 170 이어도 무개입", _run_block(False, z, flag, 170, 185) == [1212] * 4))
    z._hf_gripped = True
    checks.append(("영역: 쥐고 x 200 → 무개입", _run_block(False, z, flag, 200, 185) == [1212] * 4))
    checks.append(("영역: x 180 1청크 → 아직 무개입", _run_block(False, z, flag, 180, 185) == [1212] * 4))
    checks.append(("영역: x 190 로 벗어나면 투표 리셋", _run_block(False, z, flag, 190, 185) == [1212] * 4 and z._mr_zone_votes == 0))
    _run_block(False, z, flag, 180, 185)
    checks.append(("영역: x 180 2청크 연속 → 연다", _run_block(False, z, flag, 182, 185) == [311] * 4 and z._mr_first_reason == "zone"))
    checks.append(("영역: 연 뒤 x 200 으로 가도 연 채", _run_block(False, z, flag, 200, 185) == [311] * 4))
    o = SimpleNamespace(enable_gripper=True, _sq_held=1119, _hf_gripped=True)
    checks.append(("영역 0(끔): x 150 이어도 무개입", _run_block(False, o, flag, 150, 0) == [1212] * 4 and _run_block(False, o, flag, 150, 0) == [1212] * 4))
    b = SimpleNamespace(enable_gripper=False)
    checks.append(("그리퍼 비활성 → 무개입", _run_block(True, b, flag) == [1212] * 4))
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
        print("사용: patch_runtime_jt_manual_release.py runtime_jt/umi_checkpoint_executor")
        return 2
    path = Path(sys.argv[1]) / "robot_controller.py"
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
    print(f"  {note}  (robot_controller.py)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
