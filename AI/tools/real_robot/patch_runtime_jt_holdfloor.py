"""Keep the TCP above a height floor once the squeeze latch has grasped (runtime_jt).
조임 래치로 파지한 뒤에는 목표 TCP 높이를 바닥 아래로 내리지 않는다.

Why / 왜 (2026-09-25 r4 실측):
  파지·리프트(+103mm) 뒤 운반 c16–c19 에서 z 243 → 211mm. 두 원인이 겹친다.
    ① 정책 자체 목표가 내려간다 (c19 목표 table z 220mm)
    ② 먼 도달에서 정착 잔차 −8 ~ −14mm
  상대 액션이라 처진 관측이 다음 청크 원점이 된다. 바닥은 '절대' table z 로 걸어
  누적을 끊는다. 관측은 여전히 바닥 − 잔차(≈10mm) 근처에 선다.

Rule / 규칙 (motion_planner.plan_action, IK 이전):
  활성 = args._sq_held 가 있음 또는 args._sq_released (한 번 잡은 뒤로는 계속)
  eff  = min(floor, 관측 table z + ramp)       # 파지 직후 한 청크에 급상승 방지
  목표 table z < eff 이면 base z 를 (eff − z) 만큼 올린다. x,y·자세는 그대로.
  기본 --hold-min-z-mm 0 = 현행 동작 그대로.

Requires patch_runtime_jt_squeeze (state 공유). Idempotent. runtime_jt only.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MARK_PLAN = "[JT-PATCH hold-floor]"
MARK_CLI = "[JT-PATCH hold-floor-args]"
ANCHOR_TARGET = 'target = state["pose"] @ pose10d_to_mat(row[:9])'
ANCHOR_DICT = '"target_table_mm": np.round(target_table, 2).tolist(),'

FLOOR_TEMPLATE = '''{i}# {mark} 파지 뒤 목표 table z 를 바닥 아래로 안 내린다. 기본 0 = off
{i}_hf = float(getattr(args, "hold_min_z_mm", 0) or 0)
{i}_hf_raised = 0.0
{i}if _hf > 0 and (getattr(args, "_sq_held", None) is not None or getattr(args, "_sq_released", False)):
{i}    _hf_obs = float(kinematics.table_from_base(state["pose"][:3, 3])[2])
{i}    _hf_eff = min(_hf, _hf_obs + float(getattr(args, "hold_min_z_ramp_mm", 50) or 50))
{i}    _hf_z = float(kinematics.table_from_base(target[:3, 3])[2])
{i}    if _hf_z < _hf_eff:
{i}        _hf_raised = _hf_eff - _hf_z
{i}        target = target.copy()
{i}        target[2, 3] += _hf_raised / 1000.0
'''

DICT_TEMPLATE = '{i}"hold_floor_raised_mm": round(_hf_raised, 2),  # {mark}\n'

CLI_TEMPLATE = (
    '{indent}# {mark}\n'
    '{indent}{recv}.add_argument("--hold-min-z-mm", type=float, default=0,\n'
    '{indent}    help="after the squeeze latch grasps, never plan TCP table z below this (0 = off)")\n'
    '{indent}{recv}.add_argument("--hold-min-z-ramp-mm", type=float, default=50,\n'
    '{indent}    help="max rise above the observed z per chunk while approaching the floor")\n'
)


class PatchError(RuntimeError):
    """Anchor missing or ambiguous. Refuse rather than write a guessed patch."""


def _indent(line: str) -> str:
    return line[: len(line) - len(line.lstrip())]


def patch_planner(text: str) -> tuple[str, str]:
    if MARK_PLAN in text:
        return text, "= 이미 적용됨"
    lines = text.splitlines(keepends=True)
    t = [k for k, line in enumerate(lines) if line.strip() == ANCHOR_TARGET]
    d = [k for k, line in enumerate(lines) if line.strip() == ANCHOR_DICT]
    if len(t) != 1 or len(d) != 1:
        raise PatchError(f"planner 앵커 target {len(t)} / dict {len(d)}, 기대 1 / 1")
    if not t[0] < d[0]:
        raise PatchError("앵커 순서가 다르다")
    lines.insert(d[0] + 1, DICT_TEMPLATE.format(i=_indent(lines[d[0]]), mark=MARK_PLAN))
    lines.insert(t[0] + 1, FLOOR_TEMPLATE.format(i=_indent(lines[t[0]]), mark=MARK_PLAN))
    return "".join(lines), "+ hold-floor 블록 삽입"


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
    return "".join(lines), "+ --hold-min-z-mm / --hold-min-z-ramp-mm 추가"


def apply_to(root: Path) -> int:
    changed = 0
    for name, fn in (("motion_planner.py", patch_planner), ("cli.py", patch_cli)):
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


# -------- self-test: import the REAL patched planner + kinematics from a temp package --------

def _selftest(src: Path | None) -> int:
    import importlib
    import shutil
    import tempfile
    from types import SimpleNamespace

    import numpy as np

    checks: list[tuple[str, bool]] = []
    src = src or Path(__file__).resolve().parent
    need = ["motion_planner.py", "kinematics.py", "robot_calibration.py"]
    if not all((src / n).is_file() for n in need):
        print(f"  자체검사 소스 없음: {src} 에 {need} 필요")
        return 1
    tmp = Path(tempfile.mkdtemp())
    for tag in ("orig", "hfpatched"):
        pkg = tmp / f"jt_{tag}"
        pkg.mkdir()
        (pkg / "__init__.py").write_text("")
        for n in need:
            shutil.copy(src / n, pkg / n)
    p = tmp / "jt_hfpatched" / "motion_planner.py"
    new, _ = patch_planner(p.read_text(encoding="utf-8"))
    p.write_text(new, encoding="utf-8")
    again, note = patch_planner(new)
    checks.append(("planner 멱등", again == new and note.startswith("=")))
    sys.path.insert(0, str(tmp))
    mo = importlib.import_module("jt_orig.motion_planner")
    mp = importlib.import_module("jt_hfpatched.motion_planner")
    kin = importlib.import_module("jt_hfpatched.kinematics")
    cal = importlib.import_module("jt_hfpatched.robot_calibration")
    common = SimpleNamespace(ARM_IDS=cal.ARM_IDS, GRIPPER_ID=cal.GRIPPER_ID, tick_limits=cal.tick_limits)

    q = np.array([0.0889708857, -0.5752427955, 1.5539225381, -0.8406214718, 0.0214757310])
    pose = kin.fk(q)
    ticks = {i + 1: t for i, t in enumerate(cal.rad_to_tick(q))}
    ticks[6] = 1100
    state = {"q": q, "pose": pose, "ticks": ticks}
    obs_z = float(kin.table_from_base(pose[:3, 3])[2])

    def action_dz(dz_mm: float) -> np.ndarray:
        """Rows that move the TCP by dz (table/base z) per waypoint, orientation fixed."""
        rows = np.zeros((6, 10))
        R = pose[:3, :3]
        for k in range(6):
            rows[k, :3] = R.T @ np.array([0, 0, dz_mm * k / 1000.0])
            rows[k, 3:9] = [1, 0, 0, 0, 1, 0]
            rows[k, 9] = 0.041
        return rows

    def run(mod, act, **kw):
        a = SimpleNamespace(workspace_x=(250, 570), workspace_y=(180, 550), workspace_z=(15, 300),
                            fail_closed=False, min_z_mm=15, min_joint_margin_ticks=0, **kw)
        return mod.plan_action(act, state, common, cal, kin, a)

    down = action_dz(-15)   # 4 waypoint 에 걸쳐 −15..−60mm
    base = run(mo, down)
    off = run(mp, down, hold_min_z_mm=0)
    same = all(b["arm_ticks"] == o["arm_ticks"] and b["target_table_mm"] == o["target_table_mm"] for b, o in zip(base, off))
    checks.append(("floor 0 = 원본과 동일", same))
    nolatch = run(mp, down, hold_min_z_mm=obs_z)
    checks.append(("래치 전 무개입", all(b["arm_ticks"] == o["arm_ticks"] for b, o in zip(base, nolatch))))
    held = run(mp, down, hold_min_z_mm=obs_z, _sq_held=1212)
    zs = [c["target_table_mm"][2] for c in held]
    checks.append(("래치 중 목표 z = 바닥", all(abs(z - obs_z) < 0.02 for z in zs)))
    checks.append(("올린 양 기록", all(c["hold_floor_raised_mm"] > 0 for c in held)))
    checks.append(("x·y 는 그대로", all(abs(h["target_table_mm"][j] - b["target_table_mm"][j]) < 0.02
                                        for h, b in zip(held, base) for j in (0, 1))))
    checks.append(("IK 가 올린 목표로 다시 풀림", held[-1]["arm_ticks"] != base[-1]["arm_ticks"]
                   and held[-1]["ik_residual_mm"] < 2.0))
    up = run(mp, action_dz(+10), hold_min_z_mm=obs_z, _sq_held=1212)
    checks.append(("바닥 위 목표 무개입", all(c["hold_floor_raised_mm"] == 0 for c in up)))
    ramp = run(mp, down, hold_min_z_mm=obs_z + 200, _sq_held=1212)
    checks.append(("급상승 제한 (관측+50)", all(abs(c["target_table_mm"][2] - (obs_z + 50)) < 0.02 for c in ramp)))
    rel = run(mp, down, hold_min_z_mm=obs_z, _sq_released=True)
    checks.append(("해제 뒤에도 유지", all(abs(c["target_table_mm"][2] - obs_z) < 0.02 for c in rel)))

    cli = ('def build_parser():\n'
           '    parser = argparse.ArgumentParser()\n'
           '    parser.add_argument("--gripper-squeeze-ticks", type=int, default=0,\n'
           '        help="x")\n')
    c, _ = patch_cli(cli)
    checks.append(("cli 삽입", MARK_CLI in c and _compiles(c)))
    d, note = patch_cli(c)
    checks.append(("cli 멱등", d == c and note.startswith("=")))
    for name, fn, bad in (("판별: planner 앵커 없음", patch_planner, "def f():\n    pass\n"),
                          ("판별: cli 앵커 없음", patch_cli, "parser = 1\n")):
        try:
            fn(bad)
            checks.append((name, False))
        except PatchError:
            checks.append((name, True))

    ok = sum(1 for _, v in checks if v)
    for name, v in checks:
        print(f"  [{'PASS' if v else 'FAIL'}] {name}")
    print(f"자체검사 통과 {ok} / 전체 {len(checks)}   (관측 z {obs_z:.1f}mm 기준)")
    return 0 if ok == len(checks) else 1


def _compiles(source: str) -> bool:
    try:
        compile(source, "<patched>", "exec")
        return True
    except SyntaxError:
        return False


def main() -> int:
    ap = argparse.ArgumentParser(description="runtime_jt 파지 후 높이 바닥 패치")
    ap.add_argument("root", nargs="?", help="runtime_jt/umi_checkpoint_executor")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    root = Path(a.root) if a.root else None
    if _selftest(root) != 0:
        print("FAIL: 자체검사 실패 — 적용하지 않는다", file=sys.stderr)
        return 1
    if a.selftest or not root:
        return 0
    try:
        return apply_to(root)
    except PatchError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
