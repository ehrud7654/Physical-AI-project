import os
import signal
import time

import psutil


class ProcessController:
    def terminate(self, pid: int | None, grace_seconds: float = 5.0) -> None:
        if pid is None:
            return
        try:
            if os.name == "nt":
                parent = psutil.Process(pid)
                for child in parent.children(recursive=True):
                    child.terminate()
                parent.terminate()
                _, alive = psutil.wait_procs([parent, *parent.children(recursive=True)], timeout=grace_seconds)
                for process in alive:
                    process.kill()
            else:
                os.killpg(os.getpgid(pid), signal.SIGTERM)
                deadline = time.monotonic() + grace_seconds
                while time.monotonic() < deadline and psutil.pid_exists(pid):
                    time.sleep(0.1)
                if psutil.pid_exists(pid):
                    os.killpg(os.getpgid(pid), signal.SIGKILL)
        except (psutil.Error, ProcessLookupError, PermissionError):
            return

