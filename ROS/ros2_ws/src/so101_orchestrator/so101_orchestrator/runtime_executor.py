"""UMI 체크포인트 런타임 subprocess 실행기 (S15P21A103-46).

팀원 런타임(ROS/runtime/umi_checkpoint_executor)을 **별도 venv 인터프리터**로 띄운다.
런타임 코드는 건드리지 않는다 — 이미 내보내는 stdout(JSON report)·exit code 를 읽기만 한다.

왜 subprocess 인가: 런타임은 torch/diffusers 가 깔린 자기 venv 로 돌아야 하고
오케스트레이터는 rclpy(시스템 python)라 한 프로세스에 못 섞는다.

주의:
  - torch import 에 cusparselt 경로가 필요하다 → LD_LIBRARY_PATH 를 env 에 넣어준다.
  - `--move` 면 런타임이 stdin 으로 "MOVE" 를 요구한다 → 자동으로 써준다.
  - 런타임 report 는 indent=2 pretty JSON 이라 여러 줄이다 → 중괄호 기준으로 모아 파싱.
"""
from __future__ import annotations

import json
import os
import signal
import subprocess
from pathlib import Path


class RuntimeExecutor:
    """런타임을 한 번 실행하고 끝날 때까지 지켜본다(블로킹). 워커 스레드에서 호출."""

    def __init__(self, node, *, runtime_root: str, python: str = "",
                 extra_args: list[str] | None = None):
        self._log = node.get_logger()
        self.root = Path(os.path.expanduser(runtime_root))
        # 인터프리터는 별도 지정 가능 — 젯슨에 python3-venv 가 없어 ROS/.venv 를 못 만들면
        # 이미 구성된 기존 venv(예: stanford_umi_demo/.venv)를 그대로 가리킨다.
        self.python = (Path(os.path.expanduser(python)) if python
                       else self.root / ".venv" / "bin" / "python")
        self.shanks = self.root / "shanks"
        self.camera_tcp = self.root / "configs" / "s22_camera_tcp.json"
        self.extra_args = list(extra_args or [])
        self._proc: subprocess.Popen | None = None

    # ── 환경 ────────────────────────────────────────────────────────────
    def _env(self) -> dict:
        """torch import 에 cusparselt 가 필요하다. 인터프리터가 어느 venv 든 그 venv 의
        site-packages 에서 찾는다(ROS/.venv 고정 아님)."""
        env = os.environ.copy()
        venv = self.python.parent.parent          # <venv>/bin/python → <venv>
        # 표준 venv: lib/pyX/site-packages · Debian pip --prefix: local/lib/pyX/dist-packages
        paths = [str(p) for pattern in ("lib/python*/site-packages/cusparselt/lib",
                                        "local/lib/python*/dist-packages/cusparselt/lib")
                 for p in venv.glob(pattern)]
        paths.append("/usr/local/cuda/lib64")
        if env.get("LD_LIBRARY_PATH"):
            paths.append(env["LD_LIBRARY_PATH"])
        env["LD_LIBRARY_PATH"] = ":".join(paths)
        return env

    def preflight(self) -> str | None:
        """실행 전 필수 경로 확인. 문제 있으면 사유 문자열, 없으면 None."""
        if not self.python.exists():
            return (f"인터프리터 없음: {self.python} "
                    f"(ROS/setup_runtime.sh 실행, 또는 -p runtime_python:=<기존 venv>/bin/python)")
        if not self.shanks.exists():
            return f"shanks 없음: {self.shanks}"
        return None

    # ── 실행 ────────────────────────────────────────────────────────────
    def run(self, model_dir: Path, out_dir: Path, *, move: bool, cycles: int,
            on_report, should_cancel) -> tuple[int, str | None]:
        """런타임 1회 실행. (exit_code, 마지막 오류문자열) 반환.
        on_report(dict): 런타임이 뱉은 report JSON 마다 호출.
        should_cancel(): True 가 되면 SIGINT 로 정지(런타임이 hold 후 종료)."""
        problem = self.preflight()
        if problem:
            return 1, problem

        cmd = [
            str(self.python), "-m", "runtime.umi_checkpoint_executor",
            "--model", str(model_dir),
            "--out", str(out_dir),
            "--shanks-dir", str(self.shanks),
            "--cycles", str(cycles),
        ]
        if self.camera_tcp.exists():
            cmd += ["--camera-tcp", str(self.camera_tcp)]
        if move:
            cmd += ["--move", "--enable-gripper"]
        cmd += self.extra_args

        self._log.info(f"런타임 실행: {' '.join(cmd[-8:])}")
        self._proc = subprocess.Popen(
            cmd, cwd=str(self.root), env=self._env(),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1,
        )
        # --move 는 런타임이 "MOVE" 입력을 기다린다(대화식). 자동 승인.
        if move and self._proc.stdin:
            try:
                self._proc.stdin.write("MOVE\n")
                self._proc.stdin.flush()
            except Exception as e:  # noqa: BLE001
                self._log.warn(f"MOVE 입력 실패: {e}")

        last_error = None
        buf: list[str] = []
        canceled = False
        try:
            for raw in self._proc.stdout:            # type: ignore[union-attr]
                line = raw.rstrip("\n")
                if should_cancel() and not canceled:
                    canceled = True
                    self.stop()
                report = self._collect(line, buf)
                if report is None:
                    if line.strip():
                        self._log.info(f"[런타임] {line}")
                    continue
                status = str(report.get("status", ""))
                if status in ("REFUSED", "EXECUTION_FAILED", "INTERRUPTED"):
                    last_error = report.get("execution_error") or status
                try:
                    on_report(report)
                except Exception as e:  # noqa: BLE001
                    self._log.warn(f"report 콜백 오류: {e}")
        finally:
            code = self._proc.wait()
            self._proc = None
        return code, last_error

    @staticmethod
    def _collect(line: str, buf: list[str]) -> dict | None:
        """indent=2 pretty JSON 은 '{' 로 시작해 '}' 로 끝난다. 그 블록만 모아 파싱."""
        if line.startswith("{"):
            buf.clear()
            buf.append(line)
            return None
        if not buf:
            return None
        buf.append(line)
        if line == "}":
            text = "\n".join(buf)
            buf.clear()
            try:
                return json.loads(text)
            except json.JSONDecodeError:
                return None
        return None

    def stop(self):
        """SIGINT — 런타임이 현재 tick hold 시도 후 종료한다."""
        proc = self._proc
        if proc and proc.poll() is None:
            try:
                proc.send_signal(signal.SIGINT)
                self._log.info("런타임에 SIGINT (정지 요청)")
            except Exception as e:  # noqa: BLE001
                self._log.warn(f"런타임 정지 실패: {e}")
