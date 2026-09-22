import json
import tempfile
import unittest
from pathlib import Path

from app.trainer.checkpoint_manifest import build_metadata


def valid_metadata(n_params: int = 35_072_492) -> dict:
    return {
        "architecture": "DIFFUSION_UNET_TIMM_UMI",
        "framework": "PyTorch",
        "frameworkVersion": "test",
        "contractVersion": "umi-policy-v1",
        "robotProfileId": "52000000-0000-4000-8000-000000000001",
        "actionSpace": "EEF_RELATIVE_ROT6D",
        "actionSpec": {"dim": 10},
        "runtimeSpec": {"execSliceAppliesTo": "raw"},
        "nParams": n_params,
        "controlRateHz": 10,
        "cameraNames": ["camera0_rgb"],
        "inputSchema": {"stateShape": [10]},
        "outputSchema": {"actionShape": [16, 10]},
    }


class CheckpointManifestTest(unittest.TestCase):
    def test_uses_trainer_parameter_count(self):
        with tempfile.TemporaryDirectory() as directory:
            run_directory = Path(directory)
            (run_directory / "checkpoint_manifest.json").write_text(
                json.dumps(valid_metadata()), encoding="utf-8"
            )

            metadata = build_metadata({}, run_directory=run_directory)

            self.assertEqual(metadata["nParams"], 35_072_492)

    def test_rejects_invalid_or_placeholder_parameter_count(self):
        for n_params in [1, 0, -1, True, "35072492"]:
            with self.subTest(n_params=n_params), tempfile.TemporaryDirectory() as directory:
                run_directory = Path(directory)
                (run_directory / "checkpoint_manifest.json").write_text(
                    json.dumps(valid_metadata(n_params)), encoding="utf-8"
                )

                with self.assertRaisesRegex(ValueError, "nParams"):
                    build_metadata({}, run_directory=run_directory)

    def test_rejects_missing_trainer_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "trainer metadata manifest"):
                build_metadata({}, run_directory=Path(directory))
