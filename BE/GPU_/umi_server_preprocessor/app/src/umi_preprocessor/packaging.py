"""Create the small handoff archive consumed by the GPU training server."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import tarfile


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def package_training_input(dataset: Path) -> dict:
    dataset = Path(dataset).resolve()
    report = dataset.with_suffix(".report.json")
    if not dataset.is_file() or not report.is_file():
        raise FileNotFoundError("Zarr archive or its report is missing")
    name = dataset.name.removesuffix(".zarr.zip")
    manifest_path = dataset.parent / f"{name}_gpu_input.manifest.json"
    archive = dataset.parent / f"{name}_gpu_input.tgz"
    manifest = {
        "schema_version": 1,
        "dataset": dataset.name,
        "dataset_sha256": sha256(dataset),
        "report": report.name,
        "report_sha256": sha256(report),
    }
    if archive.exists():
        previous = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else None
        if previous != manifest:
            raise FileExistsError(f"existing archive does not match current inputs: {archive}")
        return {**manifest, "archive": str(archive), "archive_sha256": sha256(archive), "reused": True}

    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    with tarfile.open(archive, "w:gz") as bundle:
        bundle.add(dataset, arcname=dataset.name)
        bundle.add(report, arcname=report.name)
        bundle.add(manifest_path, arcname=manifest_path.name)
    return {**manifest, "archive": str(archive), "archive_sha256": sha256(archive), "reused": False}
