"""Gate the hold-floor on an actually closed gripper, not on the squeeze latch alone.
높이 바닥을 '조임 래치' 가 아니라 '실제로 닫힌 그리퍼' 에 건다.

Why / 왜 (2026-09-25 r5):
  r5 가 물체를 잡기 전에 들어 올렸다. holdfloor v1 은 args._sq_held 로 켜지는데,
  squeeze 래치는 '정책 그리퍼 틱이 2청크 연속 안 변함' 이면 걸린다 — 열린 채 안 변해도 걸린다.
  즉 v1 의 '파지 뒤' 는 실제로는 '정책 폭이 안정된 뒤' 였다. 어시스턴트 설계 오류.

Rule / 규칙:
  gripped := 래치 중 이고 관측 그리퍼 폭 <= --hold-grip-max-mm (기본 45; 감자 48mm, r4 파지 후 관측 34~38mm)
  한 번 gripped 가 되면 이번 실행 동안 유지 (놓은 뒤에도 바닥 유지).
  바닥은 gripped 일 때만 켜진다.

Requires patch_runtime_jt_holdfloor. Idempotent.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MARK = "[JT-PATCH hold-floor-grip]"
MARK_CLI = "[JT-PATCH hold-floor-grip-args]"
OLD = 'if _hf > 0 and (getattr(args, "_sq_held", None) is not None or getattr(args, "_sq_released", False)):'

NEW_TEMPLATE = '''{i}# {mark} 래치만으로는 안 켠다. 관측 폭이 실제로 닫혔을 때만 gripped
{i}_hf_w = float(state.get("gripper_width_m", 1.0))
{i}if getattr(args, "_sq_held", None) is not None and _hf_w <= float(getattr(args, "hold_grip_max_mm", 45) or 45) / 1000.0:
{i}    args._hf_gripped = True
{i}if _hf > 0 and getattr(args, "_hf_gripped", False):
'''

CLI_TEMPLATE = (
    '{indent}# {mark}\n'
    '{indent}{recv}.add_argument("--hold-grip-max-mm", type=float, default=45,\n'
    '{indent}    help="hold-floor turns on only when the observed gripper width is at or below this while latched")\n'
)


class PatchError(RuntimeError):
    """Anchor missing or ambiguous."""


def patch_planner(text: str) -> tuple[str, str]:
    if MARK in text:
        return text, "= 이미 적용됨"
    lines = text.splitlines(keepends=True)
    hits = [k for k, line in enumerate(lines) if line.strip() == OLD]
    if len(hits) != 1:
        raise PatchError(f"holdfloor 조건 줄 발견 {len(hits)} / 기대 1 (holdfloor 먼저)")
    k = hits[0]
    ind = lines[k][: len(lines[k]) - len(lines[k].lstrip())]
    lines[k] = NEW_TEMPLATE.format(i=ind, mark=MARK)
    return "".join(lines), "+ gripped 조건으로 교체"


def patch_cli(text: str) -> tuple[str, str]:
    if MARK_CLI in text:
        return text, "= 이미 적용됨"
    pat = re.compile(r'^(\s*)([A-Za-z_][\w.]*)\.add_argument\("--hold-min-z-mm"')
    lines = text.splitlines(keepends=True)
    hits = [(k, m) for k, line in enumerate(lines) if (m := pat.match(line))]
    if len(hits) != 1:
        raise PatchError(f'cli 앵커 "--hold-min-z-mm" 발견 {len(hits)} / 기대 1')
    k, m = hits[0]
    lines.insert(k, CLI_TEMPLATE.format(indent=m.group(1), recv=m.group(2), mark=MARK_CLI))
    return "".join(lines), "+ --hold-grip-max-mm 추가"


def apply_to(root: Path) -> int:
    changed = 0
    for name, fn in (("motion_planner.py", patch_planner), ("cli.py", patch_cli)):
        path = root / name
        before = path.read_text(encoding="utf-8")
        after, note = fn(before)
        if after != before:
            compile(after, str(path), "exec")
            path.write_text(after, encoding="utf-8")
            changed += 1
        print(f"  {note}  ({name})")
    print(f"변경 {changed} / 대상 2")
    return 0


def _selftest(root: Path) -> int:
    """Run the REAL planner from `root` in a temp package, patched by this file.
    root 의 실제 planner 사본에 이 패치를 적용해 실행한다."""
    import importlib
    import shutil
    import tempfile
    from types import SimpleNamespace

    import numpy as np

    checks: list[tuple[str, bool]] = []
    tmp = Path(tempfile.mkdtemp())
    pkg = tmp / "jt_grip"
    pkg.mkdir()
    (pkg / "__init__.py").write_text("")
    for n in ("motion_planner.py", "kinematics.py", "robot_calibration.py"):
        shutil.copy(root / n, pkg / n)
    p = pkg / "motion_planner.py"
    src = p.read_text(encoding="utf-8")
    if MARK not in src:
        new, _ = patch_planner(src)
        p.write_text(new, encoding="utf-8")
    again, note = patch_planner(p.read_text(encoding="utf-8"))
    checks.append(("planner 멱등", note.startswith("=")))
    sys.path.insert(0, str(tmp))
    mp = importlib.import_module("jt_grip.motion_planner")
    kin = importlib.import_module("jt_grip.kinematics")
    cal = importlib.import_module("jt_grip.robot_calibration")
    common = SimpleNamespace(ARM_IDS=cal.ARM_IDS, GRIPPER_ID=cal.GRIPPER_ID, tick_limits=cal.tick_limits)
    q = np.array([0.0889708857, -0.5752427955, 1.5539225381, -0.8406214718, 0.0214757310])
    pose = kin.fk(q)
    obs_z = float(kin.table_from_base(pose[:3, 3])[2])
    rows = np.zeros((6, 10))
    for k in range(6):
        rows[k, :3] = pose[:3, :3].T @ np.array([0, 0, -0.015 * k])
        rows[k, 3:9] = [1, 0, 0, 0, 1, 0]
        rows[k, 9] = 0.041

    def raised(width_m: float, **kw) -> float:
        ticks = {i + 1: t for i, t in enumerate(cal.rad_to_tick(q))}
        ticks[6] = 1100
        st = {"q": q, "pose": pose, "ticks": ticks, "gripper_width_m": width_m}
        a = kw.pop("a", None) or SimpleNamespace(workspace_x=(250, 570), workspace_y=(180, 550),
                                                  workspace_z=(15, 300), fail_closed=False)
        for key, v in kw.items():
            setattr(a, key, v)
        return max(c["hold_floor_raised_mm"] for c in mp.plan_action(rows, st, common, cal, kin, a)), a

    r, _ = raised(0.080, hold_min_z_mm=obs_z, _sq_held=455)
    checks.append(("r5 재현: 래치+열림(80mm) → 안 올림", r == 0))
    r, _ = raised(0.052, hold_min_z_mm=obs_z, _sq_held=900)
    checks.append(("닫히는 중(52mm) → 안 올림", r == 0))
    r, a = raised(0.035, hold_min_z_mm=obs_z, _sq_held=1212)
    checks.append(("래치+닫힘(35mm) → 올림", r > 0))
    r, _ = raised(0.080, a=a, _sq_held=None, _sq_released=True)
    checks.append(("한 번 잡은 뒤 놓아도 유지", r > 0))
    r, _ = raised(0.035, hold_min_z_mm=obs_z, _sq_held=None)
    checks.append(("래치 없이 닫힘 → 안 올림", r == 0))
    r, _ = raised(0.035, hold_min_z_mm=0, _sq_held=1212)
    checks.append(("floor 0 → 안 올림", r == 0))
    cli = 'p = 1\n    parser.add_argument("--hold-min-z-mm", type=float, default=0,\n'
    c, _ = patch_cli(cli)
    d, note = patch_cli(c)
    checks.append(("cli 삽입·멱등", MARK_CLI in c and d == c and note.startswith("=")))
    try:
        patch_planner("def f():\n    pass\n")
        checks.append(("판별: holdfloor 미적용 거부", False))
    except PatchError:
        checks.append(("판별: holdfloor 미적용 거부", True))
    ok = sum(v for _, v in checks)
    for n, v in checks:
        print(f"  [{'PASS' if v else 'FAIL'}] {n}")
    print(f"자체검사 통과 {ok} / 전체 {len(checks)}")
    return 0 if ok == len(checks) else 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("root")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    root = Path(a.root)
    if _selftest(root) != 0:
        print("FAIL: 자체검사 실패 — 적용하지 않는다", file=sys.stderr)
        return 1
    if a.selftest:
        return 0
    try:
        return apply_to(root)
    except PatchError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
