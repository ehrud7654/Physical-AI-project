"""Validate camera-to-TCP calibration and checkpoint provenance."""
from __future__ import annotations

import hashlib
import json

import numpy as np


def validate_camera_tcp_config(config):
    if config.get("status") != "physically_validated" or config.get("physical_deployment_allowed") is not True:
        raise ValueError("camera-TCP config is not approved for physical deployment")
    if config.get("image_orientation_contract") != "upright_v1":
        raise ValueError("live preprocessing requires an upright_v1 camera-TCP config")
    matrices = {
        name: np.asarray(config.get(name), dtype=np.float64)
        for name in ("T_camera_lens", "T_lens_tcp", "T_camera_tcp")
    }
    for name, matrix in matrices.items():
        if (matrix.shape != (4, 4) or not np.isfinite(matrix).all()
                or not np.allclose(matrix[3], [0, 0, 0, 1])):
            raise ValueError(f"invalid {name} in camera-TCP config")
        rotation = matrix[:3, :3]
        if not np.allclose(rotation.T @ rotation, np.eye(3), atol=1e-5) or np.linalg.det(rotation) < .999:
            raise ValueError(f"non-rigid {name} in camera-TCP config")
    if not np.allclose(matrices["T_camera_lens"] @ matrices["T_lens_tcp"], matrices["T_camera_tcp"], atol=1e-9):
        raise ValueError("T_camera_tcp does not equal T_camera_lens @ T_lens_tcp")


def load_camera_tcp_config(path):
    payload = path.read_bytes()
    config = json.loads(payload)
    validate_camera_tcp_config(config)
    return {
        "path": str(path.resolve()),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": config["status"],
        "image_orientation_contract": config["image_orientation_contract"],
    }


def validate_model_calibration(model, calibration):
    report_path = model / "dataset.report.json"
    if not report_path.is_file():
        raise FileNotFoundError(f"model calibration provenance is missing: {report_path}")
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if (report.get("training_input_status") != "ready"
            or report.get("physical_deployment_ready") is not True):
        raise ValueError("model dataset report is not deployment-ready")
    expected = report.get("config_sha256", {}).get("camera_tcp")
    if expected != calibration["sha256"]:
        raise ValueError("model camera-TCP SHA-256 does not match the live config")
    return {"dataset_sha256": report.get("dataset_sha256"), "camera_tcp_sha256": expected}
