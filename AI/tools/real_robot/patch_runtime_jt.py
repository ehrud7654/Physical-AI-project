#!/usr/bin/env python3
"""Build a patched copy of the UMI checkpoint executor at runtime_jt/ (original untouched).
현석 원본 runtime/ 은 그대로 두고 runtime_jt/ 사본에만 패치를 적용한다."""
from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
import tempfile
from pathlib import Path

PKG = "umi_checkpoint_executor"
MARKER = "[JT-PATCH"

# (파일, 앵커, 치환문) — 앵커는 파일에 정확히 1회만 나와야 한다
PATCHES: list[tuple[str, str, str]] = [
    (
        "motion_planner.py",
        "        # Diagnostics do not block execution. The motor-control boundary clamps\n"
        "        # each target to the calibrated per-servo tick range.\n"
        "        safe = True\n",
        "        # [JT-PATCH fail-closed] --fail-closed 가 켜지면 진단이 실행을 막는다.\n"
        "        # 끄면 원본과 동일하게 항상 통과한다.\n"
        "        safe = True\n"
        "        if getattr(args, \"fail_closed\", False):\n"
        "            safe = bool(\n"
        "                ik_ok\n"
        "                and limits_ok\n"
        "                and workspace_ok\n"
        "                and path_min_z_mm >= float(args.min_z_mm)\n"
        "                and min(margin_ticks) >= int(args.min_joint_margin_ticks)\n"
        "            )\n",
    ),
    (
        "cli.py",
        '    parser.add_argument("--max-ik-error-mm", type=float, default=0.5)\n',
        '    parser.add_argument(\n'
        '        "--fail-closed",\n'
        '        action="store_true",\n'
        '        help="[JT-PATCH] IK/관절한계/작업공간/경로z 위반 시 청크 전체를 거부하고 재샘플한다",\n'
        '    )\n'
        '    parser.add_argument("--max-ik-error-mm", type=float, default=0.5)\n',
    ),
    (
        "cli.py",
        "    args.out.mkdir(parents=True)\n"
        "    log_path = args.out / \"cycles.jsonl\"\n",
        "    args.out.mkdir(parents=True)\n"
        "    # [JT-PATCH args-dump] 실행 조건을 로그 디렉터리에 남긴다 (재현용)\n"
        "    (args.out / \"args.json\").write_text(\n"
        "        json.dumps({k: v for k, v in sorted(vars(args).items())},\n"
        "                   indent=2, ensure_ascii=False, default=str),\n"
        "        encoding=\"utf-8\",\n"
        "    )\n"
        "    log_path = args.out / \"cycles.jsonl\"\n",
    ),
    (
        "robot_controller.py",
        '        "gripper_width_m": float(common.gripper_tick_to_width_m(ticks[common.GRIPPER_ID])),\n',
        "        # [JT-PATCH gripper-clamp] 학습 정의역 0..0.09 m 밖 관측을 클램프하고 원값을 남긴다\n"
        '        "gripper_width_m": min(max(float(common.gripper_tick_to_width_m(ticks[common.GRIPPER_ID])), 0.0), 0.09),\n'
        '        "gripper_width_raw_m": float(common.gripper_tick_to_width_m(ticks[common.GRIPPER_ID])),\n',
    ),
]


def build(src: Path, dst: Path, quiet: bool = False) -> dict:
    """Copy src -> dst and apply every patch. Idempotent: dst is rebuilt from src each run."""
    if not (src / PKG / "cli.py").is_file():
        raise FileNotFoundError(f"원본이 없다: {src / PKG / 'cli.py'}")
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst, ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.before_*"))

    applied = 0
    for name, anchor, replacement in PATCHES:
        path = dst / PKG / name
        text = path.read_text(encoding="utf-8")
        found = text.count(anchor)
        if found != 1:
            raise RuntimeError(f"{name}: 앵커가 {found}회 (1회여야 한다). 원본이 바뀌었는지 확인하라")
        path.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
        applied += 1
        if not quiet:
            print(f"  적용 {applied}/{len(PATCHES)}  {name}")

    markers = sum(f.read_text(encoding="utf-8").count(MARKER)
                  for f in (dst / PKG).glob("*.py"))
    files = sorted(p.name for p in (dst / PKG).glob("*.py"))
    digest = hashlib.sha256()
    for f in sorted((dst / PKG).glob("*.py")):
        digest.update(f.read_bytes())
    return {"적용": applied, "전체": len(PATCHES), "마커": markers,
            "파일": files, "sha256": digest.hexdigest()[:16]}


def selftest(src: Path) -> bool:
    rows = []
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        a = build(src, tmp / "t1", quiet=True)
        b = build(src, tmp / "t1", quiet=True)          # 같은 경로에 2회
        c = build(src, tmp / "t2", quiet=True)          # 다른 경로에 1회
        rows.append(("1 패치 전부 적용", a["적용"] == len(PATCHES), f"{a['적용']}/{a['전체']}"))
        rows.append(("2 2회 실행 멱등 (해시 동일)", a["sha256"] == b["sha256"], f"{a['sha256']} vs {b['sha256']}"))
        rows.append(("3 경로 무관 동일 결과", a["sha256"] == c["sha256"], f"{c['sha256']}"))
        rows.append(("4 마커가 패치 수만큼", b["마커"] == len(PATCHES), f"{b['마커']} / {len(PATCHES)}"))
        # 정답 아는 행: 앵커를 고의로 지운 원본은 반드시 실패해야 한다
        broken = tmp / "broken"
        shutil.copytree(src, broken, ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.before_*"))
        p = broken / PKG / "motion_planner.py"
        p.write_text(p.read_text(encoding="utf-8").replace("        safe = True\n", "", 1), encoding="utf-8")
        try:
            build(broken, tmp / "t3", quiet=True)
            ok5 = False
        except RuntimeError:
            ok5 = True
        rows.append(("5 앵커 없는 원본 -> 거부 (정답 아는 행)", ok5, "RuntimeError"))
        # 원본이 변경되지 않았는지
        rows.append(("6 원본 무변경", (src / PKG / "motion_planner.py").read_text(encoding="utf-8").count(MARKER) == 0, "마커 0"))
    ok = sum(1 for _, p, _ in rows if p)
    for name, passed, note in rows:
        print(f"  {'PASS' if passed else 'FAIL'}  {name}   {note}")
    print(f"자체검증 {ok} / {len(rows)}")
    return ok == len(rows)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--repo", type=Path, default=Path.home() / "Workspace/stanford_umi_demo")
    ap.add_argument("--src", type=Path, help="기본 <repo>/runtime")
    ap.add_argument("--dst", type=Path, help="기본 <repo>/runtime_jt")
    ap.add_argument("--selftest-only", action="store_true")
    args = ap.parse_args()
    src = args.src or args.repo / "runtime"
    dst = args.dst or args.repo / "runtime_jt"

    print("=== 자체검증")
    if not selftest(src):
        print("!! 자체검증 실패 — 패치를 적용하지 않는다")
        sys.exit(1)
    if args.selftest_only:
        return
    print(f"\n=== 빌드  {src}  ->  {dst}")
    info = build(src, dst)
    print(f"\n적용 {info['적용']} / {info['전체']} · 마커 {info['마커']} · 파일 {len(info['파일'])}개 · sha {info['sha256']}")
    print(f"원본 {src} 은 건드리지 않았다")
    print(f"\n실행: cd {args.repo} && .venv/bin/python -m {dst.name}.{PKG} --help")


if __name__ == "__main__":
    main()
