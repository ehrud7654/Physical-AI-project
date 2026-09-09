"""Run the complete hardware-free UMI regression suite on a local PC.

This is intentionally forbidden on the shared Jupyter GPU server: team policy
reserves that host for device-2 training only.
"""
from __future__ import annotations

import argparse
import os
import platform
import subprocess
import sys
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
CHECKS = (
    "check_umi_convert.py",
    "check_umi_provenance.py",
    "check_umi_action_decode.py",
    "check_umi_action_ik.py",
    "check_umi_policy_preflight.py",
    "check_umi_arm_preflight.py",
    "check_umi_chunk_runtime.py",
    "check_umi_real_fk.py",
    "check_umi_action_roundtrip.py",
)


def is_shared_gpu_server(hostname: str | None = None) -> bool:
    name = (hostname or platform.node()).lower()
    return name.startswith("jupyter") or name == "jupyter01"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--list", action="store_true", help="검사 목록만 출력")
    args = parser.parse_args()
    if args.list:
        print("\n".join(CHECKS))
        return 0
    if is_shared_gpu_server():
        print("거부: 이 회귀검사는 로컬 PC 전용이다. GPU 서버는 device 2 학습만 허용한다.")
        return 2

    env = os.environ.copy()
    env.setdefault("PYTHONUTF8", "1")
    failures: list[str] = []
    for index, script in enumerate(CHECKS, 1):
        print(f"\n[{index}/{len(CHECKS)}] {script}", flush=True)
        result = subprocess.run([sys.executable, str(AI_ROOT / "tools" / script)],
                                cwd=AI_ROOT, env=env, check=False)
        if result.returncode:
            failures.append(f"{script}({result.returncode})")

    print("\n=== UMI local regression ===")
    if failures:
        print("FAIL: " + ", ".join(failures))
        return 1
    print(f"PASS: {len(CHECKS)}/{len(CHECKS)} checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
