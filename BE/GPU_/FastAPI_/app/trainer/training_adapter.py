import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

from app.infrastructure.storage.checksum import sha256_file
from app.trainer.checkpoint_manifest import build_metadata
from app.trainer.model_package import find_and_validate_package
from app.trainer.progress_reporter import ProgressReporter


class TrainingAdapter:
    """검증된 Zarr 입력을 공식 UMI trainer에 전달하는 어댑터."""

    def train(
        self,
        request: dict[str, Any],
        output: Path,
        reporter: ProgressReporter,
        duration_seconds: int,
    ) -> dict[str, Any]:
        del duration_seconds  # 실제 trainer가 실행 시간을 결정한다.

        shared_root = Path(os.getenv("SHARED_STORAGE_ROOT", "/data/ssafy-gpu")).resolve()
        bundle = Path(os.getenv("UMI_TRAINING_BUNDLE_ROOT", "/opt/umi-training")).resolve()
        python = os.getenv("UMI_TRAINING_PYTHON", sys.executable)
        trainer = bundle / "03_umi_policy_trainer" / "train_policy.py"
        profile = Path(
            os.getenv(
                "UMI_TRAINING_PROFILE",
                str(bundle / "03_umi_policy_trainer" / "configs" / "policy_resnet18_gpu.yaml"),
            )
        ).resolve()
        zarr = self._shared_file(shared_root, request["trainingInput"]["storageKey"])
        runs_dir = self._shared_directory(
            shared_root, os.getenv("UMI_TRAINING_RUNS_DIR", "trainer/runs")
        )
        epochs = self._positive_int("TRAINING_EPOCHS", 60)
        batch = self._positive_int("TRAINING_BATCH_SIZE", 8)
        eval_batch = self._positive_int("TRAINING_EVAL_BATCH_SIZE", batch)
        gpu = os.getenv("CUDA_VISIBLE_DEVICES", str(request.get("gpuIndex", 0)))
        run_name = os.getenv("TRAINING_RUN_NAME", request["trainingJobId"])
        run_id = f"{self._safe_run_name(run_name)}_{time.strftime('%H%M%S')}"

        self._require_file(trainer, "UMI trainer")
        self._require_file(profile, "UMI trainer profile")
        runs_dir.mkdir(parents=True, exist_ok=True)

        environment = os.environ.copy()
        umi_root = Path(os.getenv("UMI_ROOT", str(bundle / "third_party" / "umi"))).resolve()
        environment.update(
            {
                "CUDA_VISIBLE_DEVICES": gpu,
                "UMI_ROOT": str(umi_root),
                "WANDB_MODE": os.getenv("WANDB_MODE", "disabled"),
                "HYDRA_FULL_ERROR": os.getenv("HYDRA_FULL_ERROR", "1"),
            }
        )
        # An empty seed means "use the official trainer default", not integer zero.
        if not environment.get("TRAINING_SEED", "").strip():
            environment.pop("TRAINING_SEED", None)

        print(f"run-id {run_id} · GPU {gpu}", flush=True)
        reporter.report("RUNNING", "DATASET_CHECK", 5, epoch=0, totalEpochs=epochs)
        self._run(
            [python, str(trainer), "check", str(zarr)],
            cwd=bundle,
            env=environment,
            title="dataset check",
        )

        reporter.report("RUNNING", "TRAINING", 10, epoch=0, totalEpochs=epochs)
        self._run(
            [
                python,
                str(trainer),
                "train",
                str(zarr),
                "--run-id",
                run_id,
                "--runs-dir",
                str(runs_dir),
                "--epochs",
                str(epochs),
                "--batch",
                str(batch),
                "--eval-batch",
                str(eval_batch),
                "--profile",
                str(profile),
            ],
            cwd=bundle,
            env=environment,
            title=f"학습 {epochs} epoch",
        )

        reporter.report(
            "RUNNING", "CHECKPOINT_CREATING", 95, epoch=epochs, totalEpochs=epochs
        )
        trained_package = find_and_validate_package(runs_dir / run_id)
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(trained_package, output)

        return {
            "checkpointStorageKey": str(output).replace("\\", "/"),
            "sizeBytes": output.stat().st_size,
            "sha256": sha256_file(output),
            "metadata": build_metadata(request, output),
        }

    @staticmethod
    def _run(command: list[str], cwd: Path, env: dict[str, str], title: str) -> None:
        print(f"[{title}] {' '.join(command)}", flush=True)
        completed = subprocess.run(command, cwd=cwd, env=env, check=False)
        if completed.returncode != 0:
            raise RuntimeError(f"{title} 실패: exitCode={completed.returncode}")

    @staticmethod
    def _shared_file(root: Path, storage_key: str) -> Path:
        path = (root / storage_key).resolve()
        if not path.is_relative_to(root) or not path.is_file() or path.is_symlink():
            raise ValueError("Zarr 입력이 공유 저장소의 일반 파일이 아닙니다.")
        return path

    @staticmethod
    def _shared_directory(root: Path, value: str) -> Path:
        configured = Path(value)
        path = (configured if configured.is_absolute() else root / configured).resolve()
        if not path.is_relative_to(root):
            raise ValueError("학습 결과 경로가 공유 저장소를 벗어났습니다.")
        return path

    @staticmethod
    def _require_file(path: Path, label: str) -> None:
        if not path.is_file():
            raise FileNotFoundError(f"{label} 파일을 찾을 수 없습니다: {path}")

    @staticmethod
    def _positive_int(name: str, default: int) -> int:
        value = int(os.getenv(name, str(default)))
        if value < 1:
            raise ValueError(f"{name}은 1 이상이어야 합니다.")
        return value

    @staticmethod
    def _safe_run_name(value: str) -> str:
        safe = "".join(
            character if character.isalnum() or character in "-_" else "_"
            for character in value
        )
        if not safe:
            raise ValueError("학습 run 이름이 올바르지 않습니다.")
        return safe

