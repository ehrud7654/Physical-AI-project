"""진짜 UMI 런타임의 I/O 계약만 흉내내는 스텁 (S15P21A103-46 테스트용).

실물(팔·카메라·CUDA) 없이 RuntimeExecutor 통합을 검증하기 위한 것.
진짜 런타임(cli.py)과 동일하게 행동한다:
  - --move 면 stdin 으로 "MOVE" 입력을 기다린다 (안 오면 거부하고 종료)
  - 사이클마다 indent=2 pretty JSON report 를 stdout 에 출력 (여러 줄!)
  - JSON 아닌 진행 로그도 섞어 출력
  - SIGINT 를 받으면 hold 로그를 남기고 종료
  - --cycles 0 이면 정지 신호까지 계속
"""
from __future__ import annotations

import argparse
import json
import signal
import sys
import time

_stop = False


def _on_sigint(_sig, _frame):
    global _stop
    _stop = True


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--model")
    p.add_argument("--out")
    p.add_argument("--camera-tcp")
    p.add_argument("--cycles", type=int, default=1)
    p.add_argument("--move", action="store_true")
    p.add_argument("--enable-gripper", action="store_true")
    p.add_argument("--fail-at", type=int, default=-1, help="이 사이클에서 실패 종료")
    p.add_argument("--ignore-sigint", action="store_true", help="SIGINT 무시(SIGKILL 경로 확인용)")
    p.add_argument("--cycle-sec", type=float, default=0.2)
    p.add_argument("--crash", action="store_true",
                   help="JSON report 없이 즉사(import 에러 등 재현) — 마지막 줄이 사유")
    args = p.parse_args()

    signal.signal(signal.SIGINT, (lambda *_: None) if args.ignore_sigint else _on_sigint)

    if args.crash:
        # 진짜 런타임이 import 단계에서 죽는 모양: Traceback 몇 줄 뒤 에러 한 줄, exit 1.
        print("Traceback (most recent call last):", flush=True)
        print("  File \"cli.py\", line 14, in <module>", flush=True)
        print("ModuleNotFoundError: No module named 'serial'", flush=True)
        return 1

    if args.move:
        # 진짜 런타임과 동일: 모터 구동 전 "MOVE" 를 요구한다.
        print("Type MOVE to start checkpoint control: ", end="", flush=True)
        line = sys.stdin.readline()
        if line.strip() != "MOVE":
            print("cancelled; no motor command sent", flush=True)
            return 1
        print("MOVE accepted", flush=True)

    cycle = 0
    while (args.cycles == 0 or cycle < args.cycles) and not _stop:
        if cycle == args.fail_at:
            report = {"cycle": cycle, "phase": "execution_failed", "status": "EXECUTION_FAILED",
                      "execution_error": "fake failure"}
            print(json.dumps(report, indent=2), flush=True)
            return 1
        print("unsafe sample; asking the checkpoint again (0/10)", flush=True)  # 비-JSON 라인
        report = {"cycle": cycle, "phase": "completed", "status": "PASS",
                  "motor_commands_sent": 4, "executed_waypoints": 4,
                  "candidates": [{"action_index": i, "safe": True} for i in range(1, 5)]}
        print(json.dumps(report, indent=2), flush=True)
        cycle += 1
        end = time.time() + args.cycle_sec
        while time.time() < end and not _stop:
            time.sleep(0.02)

    if _stop:
        print("holding current ticks {1: 1000}", flush=True)
        print("stopped by Ctrl+C", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
