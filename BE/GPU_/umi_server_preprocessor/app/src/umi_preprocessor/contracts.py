"""Input validation and safe archive extraction for preprocessing jobs."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import tarfile
import zipfile


NAME = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
SESSION_FILES = {
    "video.mp4",
    "frames.csv",
    "encoded.csv",
    "accelerometer.csv",
    "gyroscope.csv",
    "manifest.json",
}
CONFIG_FILES = {
    "dataset.json",
    "s22_camera_imu.json",
    "s22_camera_tcp.json",
    "s22_gripper.json",
    "s22_slam_tag.json",
    "s22_slam_mask.png",
}


def validate_name(value: str, field: str) -> str:
    if not NAME.fullmatch(value):
        raise ValueError(f"{field} must match {NAME.pattern}")
    return value


def job_root(jobs_root: Path, job_id: str) -> Path:
    base = Path(jobs_root).resolve()
    target = (base / validate_name(job_id, "jobId")).resolve()
    if target.parent != base:
        raise ValueError("job path escapes jobs root")
    return target


def _safe_target(root: Path, member: str) -> Path:
    target = (root / member).resolve()
    if target != root and root not in target.parents:
        raise ValueError(f"archive member escapes destination: {member}")
    return target


def extract_archive(archive: Path, destination: Path) -> None:
    """Extract one .tgz/.tar.gz/.zip without links or path traversal."""
    archive, destination = Path(archive).resolve(), Path(destination).resolve()
    if destination.exists() and any(destination.iterdir()):
        raise FileExistsError(f"destination is not empty: {destination}")
    destination.mkdir(parents=True, exist_ok=True)
    max_files = int(os.environ.get("UMI_MAX_ARCHIVE_FILES", "200000"))
    max_bytes = int(os.environ.get("UMI_MAX_ARCHIVE_BYTES", str(500 * 1024**3)))

    if tarfile.is_tarfile(archive):
        with tarfile.open(archive, "r:*") as bundle:
            members = bundle.getmembers()
            if len(members) > max_files or sum(m.size for m in members) > max_bytes:
                raise ValueError("archive exceeds configured file-count or byte limit")
            for member in members:
                _safe_target(destination, member.name)
                if member.issym() or member.islnk() or member.isdev():
                    raise ValueError(f"links/devices are not allowed: {member.name}")
            bundle.extractall(destination, members=members, filter="data")
        return

    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as bundle:
            members = bundle.infolist()
            if len(members) > max_files or sum(m.file_size for m in members) > max_bytes:
                raise ValueError("archive exceeds configured file-count or byte limit")
            for member in members:
                _safe_target(destination, member.filename)
                if (member.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError(f"links are not allowed: {member.filename}")
            bundle.extractall(destination)
        return

    raise ValueError(f"unsupported archive: {archive.name}")


def validate_raw_layout(root: Path, require_mapping: bool = True) -> dict:
    root = Path(root).resolve()
    sessions = _validate_sessions(root / "raw")
    mappings = _validate_sessions(root / "raw_mapping")
    if len(sessions) < 2:
        raise ValueError(f"at least 2 demonstration sessions are required, found {len(sessions)}")
    if require_mapping and not mappings:
        raise ValueError("raw_mapping must contain one mapping session")
    return {
        "root": str(root),
        "demonstration_sessions": len(sessions),
        "mapping_sessions": len(mappings),
        "success_sessions": sum(item["outcome"] == "success" for item in sessions),
    }


def validate_config_dir(folder: Path) -> Path:
    folder = Path(folder).resolve()
    missing = sorted(name for name in CONFIG_FILES if not (folder / name).is_file())
    if missing:
        raise ValueError(f"calibration profile missing files: {', '.join(missing)}")
    tag = json.loads((folder / "s22_slam_tag.json").read_text(encoding="utf-8"))
    if not isinstance(tag.get("tag_id"), int) or float(tag.get("tag_size_m", 0)) <= 0:
        raise ValueError("invalid table-marker id or size")
    return folder


def _validate_sessions(folder: Path) -> list[dict]:
    if not folder.is_dir():
        return []
    results = []
    for session in sorted(path for path in folder.iterdir() if path.is_dir()):
        missing = sorted(SESSION_FILES - {path.name for path in session.iterdir() if path.is_file()})
        if missing:
            raise ValueError(f"{session.name} missing files: {', '.join(missing)}")
        manifest = json.loads((session / "manifest.json").read_text(encoding="utf-8"))
        results.append({"session": session.name, "outcome": manifest.get("outcome")})
    return results
