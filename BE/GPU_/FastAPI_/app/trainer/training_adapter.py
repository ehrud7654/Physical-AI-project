import json
import time
from pathlib import Path
from typing import Any

from app.infrastructure.storage.checksum import sha256_file
from app.trainer.checkpoint_manifest import build_metadata
from app.trainer.progress_reporter import ProgressReporter


class TrainingAdapter:
    """AI 담당자가 실제 UMI 학습 구현으로 교체할 경계."""

    def train(self, request: dict[str, Any], output: Path, reporter: ProgressReporter, duration_seconds: int) -> dict[str, Any]:
        epochs = int(request["trainingProfile"]["hyperparameters"]["epochs"])
        reporter.report("RUNNING", "PREPROCESSING", 5, epoch=0, totalEpochs=epochs)
        steps = max(1, min(epochs, duration_seconds))
        for step in range(1, steps + 1):
            time.sleep(duration_seconds / steps)
            reporter.report(
                "RUNNING", "TRAINING", min(90, 5 + int(85 * step / steps)),
                epoch=max(1, int(epochs * step / steps)), totalEpochs=epochs,
            )
        reporter.report("RUNNING", "CHECKPOINT_CREATING", 95, epoch=epochs, totalEpochs=epochs)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(json.dumps({"run": request["trainingJobId"], "metadata": build_metadata(request)}).encode("utf-8"))
        return {
            "checkpointStorageKey": str(output).replace("\\", "/"),
            "sizeBytes": output.stat().st_size,
            "sha256": sha256_file(output),
            "metadata": build_metadata(request),
        }

