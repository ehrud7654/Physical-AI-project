#!/usr/bin/env python3
"""Run RAW -> Atlas -> UMI Zarr and package the GPU training input."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys


HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from umi_preprocessor.contracts import (  # noqa: E402
    extract_archive,
    validate_config_dir,
    validate_name,
    validate_raw_layout,
)
from umi_preprocessor.packaging import package_training_input  # noqa: E402


def write_json(path: Path, value: dict) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="job directory containing raw/ and raw_mapping/")
    parser.add_argument("--archive", type=Path, help="optional .tgz/.tar.gz/.zip to extract into an empty root")
    parser.add_argument("--dataset-name", default="dataset")
    parser.add_argument("--config-dir", type=Path)
    parser.add_argument("--require-mapping", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--allow-unrated", action="store_true")
    parser.add_argument("--allow-tracking-loss", action="store_true")
    parser.add_argument("--allow-missing-tag", action="store_true")
    args = parser.parse_args()

    validate_name(args.dataset_name, "datasetName")
    root = args.root.resolve()
    status_path = root / "job_status.json"
    status = {"status": "running", "startedUtc": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    try:
        if args.archive:
            extract_archive(args.archive, root)
        else:
            root.mkdir(parents=True, exist_ok=True)
        write_json(status_path, status)
        status["input"] = validate_raw_layout(root, args.require_mapping)
        config_dir = args.config_dir or HERE.parent / "dataset_builder/configs"
        os.environ["UMI_CONFIG_DIR"] = str(validate_config_dir(config_dir))
        from umi_preprocessor.pipeline import run

        report = run(root, args.dataset_name, args.allow_unrated, args.allow_tracking_loss,
                     args.require_mapping, args.allow_missing_tag)
        if report.get("status") != "ready":
            raise RuntimeError(report.get("stopped", "pipeline did not produce a ready dataset"))
        artifact = package_training_input(Path(report["dataset"]["dataset"]))
        status.update(status="ready", completedUtc=datetime.now(timezone.utc).isoformat(timespec="seconds"),
                      report=str(root / "pipeline_report.json"), artifact=artifact)
        write_json(status_path, status)
        print(json.dumps(status, indent=2))
    except Exception as error:
        root.mkdir(parents=True, exist_ok=True)
        status.update(status="failed", completedUtc=datetime.now(timezone.utc).isoformat(timespec="seconds"),
                      error={"type": type(error).__name__, "message": str(error)})
        write_json(status_path, status)
        raise


if __name__ == "__main__":
    main()
