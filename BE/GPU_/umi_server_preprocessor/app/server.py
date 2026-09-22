#!/usr/bin/env python3
"""Internal HTTP adapter for a Spring service; heavy work stays in a child process."""
from __future__ import annotations

import json
import os
from pathlib import Path
import signal
import subprocess
import sys
from threading import Lock

from fastapi import FastAPI, HTTPException, Response, status
from pydantic import BaseModel


HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))

from umi_preprocessor.contracts import job_root, validate_name  # noqa: E402


PACKAGE_ROOT = HERE.parent
JOBS_ROOT = Path(os.environ.get("UMI_JOBS_ROOT", "/jobs"))
PROFILES_ROOT = Path(os.environ.get("UMI_PROFILES_ROOT", "/profiles"))
DEFAULT_PROFILE = "s22_20260921"
processes: dict[str, subprocess.Popen] = {}
lock = Lock()
app = FastAPI(title="UMI Preprocessor", version="1.0.0")


class JobRequest(BaseModel):
    job_id: str
    dataset_name: str = "dataset"
    calibration_profile: str = DEFAULT_PROFILE
    require_mapping: bool = True


def profile_path(profile: str) -> Path:
    validate_name(profile, "calibrationProfile")
    if profile == DEFAULT_PROFILE:
        return PACKAGE_ROOT / "dataset_builder" / "configs"
    base = PROFILES_ROOT.resolve()
    target = (base / profile).resolve()
    if target.parent != base or not target.is_dir():
        raise ValueError(f"unknown calibration profile: {profile}")
    return target


def read_status(root: Path) -> dict:
    path = root / "job_status.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {"status": "not_started"}


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/internal/v1/jobs", status_code=status.HTTP_202_ACCEPTED)
def start_job(request: JobRequest) -> dict:
    try:
        validate_name(request.dataset_name, "datasetName")
        root = job_root(JOBS_ROOT, request.job_id)
        profile = profile_path(request.calibration_profile)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    if not (root / "raw").is_dir():
        raise HTTPException(status_code=400, detail=f"RAW input is missing: {root / 'raw'}")

    with lock:
        for job_id, process in list(processes.items()):
            if process.poll() is not None:
                processes.pop(job_id)
        if request.job_id in processes:
            return read_status(root)
        if processes:
            raise HTTPException(status_code=409, detail="one preprocessing job is already running")
        command = [
            sys.executable, str(HERE / "run_preprocess.py"), str(root),
            "--dataset-name", request.dataset_name,
            "--config-dir", str(profile),
            "--require-mapping" if request.require_mapping else "--no-require-mapping",
        ]
        root.mkdir(parents=True, exist_ok=True)
        with (root / "worker.log").open("ab") as output:
            processes[request.job_id] = subprocess.Popen(
                command, stdout=output, stderr=subprocess.STDOUT,
                env=os.environ.copy(), start_new_session=True,
            )
    return {"jobId": request.job_id, "status": "accepted"}


@app.get("/internal/v1/jobs/{job_id}")
def get_job(job_id: str) -> dict:
    try:
        root = job_root(JOBS_ROOT, job_id)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    if not root.exists():
        raise HTTPException(status_code=404, detail="job not found")
    result = read_status(root)
    process = processes.get(job_id)
    if process is not None and process.poll() is not None:
        result["workerExitCode"] = process.returncode
    return result


@app.post("/internal/v1/jobs/{job_id}/cancel", status_code=status.HTTP_204_NO_CONTENT)
def cancel_job(job_id: str) -> Response:
    try:
        root = job_root(JOBS_ROOT, job_id)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    with lock:
        process = processes.get(job_id)
        if process is None or process.poll() is not None:
            raise HTTPException(status_code=409, detail="job is not running")
        os.killpg(process.pid, signal.SIGTERM)
        processes.pop(job_id, None)
        state = read_status(root)
        state.update(status="cancelled")
        (root / "job_status.json").write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    return Response(status_code=status.HTTP_204_NO_CONTENT)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8080)
