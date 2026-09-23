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

import ctypes
import json
import os
import signal
import subprocess
import threading
from pathlib import Path

_PR_SET_PDEATHSIG = 1      # linux/prctl.h


def _die_with_parent():
    """자식 쪽에서 실행 — 부모가 죽으면 커널이 나에게 SIGINT 를 보내게 등록.
    오케스트레이터가 크래시/SIGKILL 돼도 런타임이 고아로 남아 팔을 계속 움직이는 것 방지.
    (런타임은 SIGINT 를 받으면 hold_current 후 종료한다)"""
    try:
        ctypes.CDLL("libc.so.6").prctl(_PR_SET_PDEATHSIG, signal.SIGINT)
    except Exception:       # noqa: BLE001 — 리눅스 아니면 그냥 넘어감(개발 PC)
        pass


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
        # shanks 는 런타임 실행 경로가 아니다(런타임 자기완결). torque/move/pose 등 수동 운용도구용.
        self.shanks = self.root / "shanks"
        self.camera_tcp = self.root / "configs" / "s22_camera_tcp.json"
        self.extra_args = list(extra_args or [])
        # 실행 대상 모듈. 테스트에서 스텁으로 갈아끼운다(실물 없이 통합 검증).
        self.launch = ["-m", "runtime.umi_checkpoint_executor"]
        self.stop_grace_s = 10.0        # SIGINT 후 SIGKILL 까지 유예
        self._proc: subprocess.Popen | None = None
        self._stop_requested = False

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

    # ── 서보 진단 (런타임이 안 돌 때만 — 포트는 하나다) ──────────────────
    # 자기완결 런타임 모듈(servo_bus/robot_hardware/robot_calibration)만 쓴다. shanks 불필요.
    _DIAG_SNIPPET = (
        "import json,sys;"
        "from runtime.umi_checkpoint_executor.servo_bus import ST3215Bus;"
        "from runtime.umi_checkpoint_executor.robot_hardware import retry_eintr;"
        "from runtime.umi_checkpoint_executor.robot_calibration import ALL_IDS;"
        "bus=ST3215Bus(sys.argv[1]);"
        "d={i: retry_eintr(lambda i=i: bus.read_diagnostics(i)) for i in ALL_IDS};"
        "print(json.dumps({str(i): {'temperature_c': v['temperature_c'],"
        " 'present_load_raw': v['present_load_raw'], 'torque_enabled': v['torque_enabled']}"
        " for i,v in d.items()}))"
    )

    def read_diagnostics(self, motor_port: str = "/dev/ttyTHS1",
                         timeout_s: float = 15.0) -> dict | None:
        """서보 온도·부하를 읽는다. **런타임이 실행 중이면 호출 금지**(같은 시리얼 포트).
        실패하면 None — 진단 실패가 실행을 막지는 않게 한다(호출자가 판단)."""
        if self._proc and self._proc.poll() is None:
            self._log.warn("런타임 실행 중 — 진단 읽기 생략(포트 점유)")
            return None
        if self.preflight():
            return None
        try:
            out = subprocess.run(
                [str(self.python), "-c", self._DIAG_SNIPPET, motor_port],
                cwd=str(self.root), env=self._env(), capture_output=True,
                text=True, timeout=timeout_s)
        except Exception as e:  # noqa: BLE001
            self._log.warn(f"서보 진단 실패: {e}")
            return None
        if out.returncode != 0:
            self._log.warn(f"서보 진단 실패(rc={out.returncode}): {out.stderr.strip()[:200]}")
            return None
        try:
            return json.loads(out.stdout.strip().splitlines()[-1])
        except (json.JSONDecodeError, IndexError):
            self._log.warn(f"서보 진단 파싱 실패: {out.stdout.strip()[:200]}")
            return None

    def preflight(self) -> str | None:
        """실행 전 필수 경로 확인. 문제 있으면 사유 문자열, 없으면 None."""
        if not self.python.exists():
            return (f"인터프리터 없음: {self.python} "
                    f"(ROS/setup_runtime.sh 실행, 또는 -p runtime_python:=<기존 venv>/bin/python)")
        pkg = self.root / "runtime" / "umi_checkpoint_executor" / "__init__.py"
        if not pkg.exists():
            return f"런타임 패키지 없음: {pkg.parent}"
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
            str(self.python), *self.launch,
            "--model", str(model_dir),
            "--out", str(out_dir),
            "--cycles", str(cycles),
        ]
        if self.camera_tcp.exists():
            cmd += ["--camera-tcp", str(self.camera_tcp)]
        if move:
            cmd += ["--move", "--enable-gripper"]
        cmd += self.extra_args

        self._log.info(f"런타임 실행: {' '.join(cmd[-8:])}")
        self._stop_requested = False
        self._proc = subprocess.Popen(
            cmd, cwd=str(self.root), env=self._env(),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1,
            preexec_fn=_die_with_parent,     # 부모 사망 시 동반 종료(고아 방지)
        )
        # --move 는 런타임이 "MOVE" 입력을 기다린다(대화식). 자동 승인.
        if move and self._proc.stdin:
            try:
                self._proc.stdin.write("MOVE\n")
                self._proc.stdin.flush()
            except Exception as e:  # noqa: BLE001
                self._log.warn(f"MOVE 입력 실패: {e}")

        last_error = None
        last_line = None        # 마지막 비-JSON 출력 줄 — report 없이 죽을 때 사유로 쓴다
                                # (예: Traceback 끝 "ModuleNotFoundError: No module named 'serial'")
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
                        last_line = line.strip()
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
        # report 로 못 받은 실패 사유는 마지막 출력 줄로 채운다(BE error.message 로 전달됨)
        if last_error is None and code != 0 and last_line:
            last_error = last_line[:300]
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
        """SIGINT 만 보내고 즉시 반환(논블로킹) — SSE 스레드에서 불려도 막히지 않게.
        유예 내 안 죽으면 타이머가 SIGKILL 한다. run() 의 stdout 루프는 SIGINT 를
        씹는 프로세스에서 EOF 를 영영 못 보므로, 에스컬레이션은 루프 밖에 있어야 한다."""
        proc = self._proc
        if not proc or proc.poll() is not None:
            return
        self._stop_requested = True
        try:
            proc.send_signal(signal.SIGINT)
            self._log.info("런타임에 SIGINT (정지 요청)")
        except Exception as e:  # noqa: BLE001
            self._log.warn(f"런타임 SIGINT 실패: {e}")
            return
        threading.Timer(self.stop_grace_s, self._kill_if_alive, [proc]).start()

    def _kill_if_alive(self, proc):
        if proc.poll() is not None:
            return
        self._log.error(f"런타임이 {self.stop_grace_s:.0f}s 내 정지 안 됨 → SIGKILL")
        try:
            proc.kill()
        except Exception as e:  # noqa: BLE001
            self._log.warn(f"런타임 SIGKILL 실패: {e}")
