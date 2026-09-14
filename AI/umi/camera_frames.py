"""Explicit camera-frame conversions for the real S22 ARCore recordings."""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np


ARCORE_GL_TO_OPENCV = np.diag([1.0, -1.0, -1.0, 1.0])
ARCORE_CAMERA_AXES = "+X right, +Y up, -Z forward (ARCore/OpenGL)"
GRIPPER_BODY_TO_PINCH_ROTATION = np.diag([1.0, -1.0, -1.0])
"""Pose of the OpenCV camera frame in the ARCore/OpenGL camera frame.

ARCore/OpenGL: +x right, +y up, -z forward.
OpenCV:        +x right, +y down, +z forward.
"""


def rigid(value, *, name="transform") -> np.ndarray:
    matrix = np.asarray(value, dtype=np.float64)
    if (matrix.shape != (4, 4) or not np.isfinite(matrix).all()
            or not np.allclose(matrix[3], [0, 0, 0, 1], atol=1e-9, rtol=0)
            or not np.allclose(matrix[:3, :3].T @ matrix[:3, :3], np.eye(3),
                               atol=1e-8, rtol=0)
            or not np.isclose(np.linalg.det(matrix[:3, :3]), 1.0, atol=1e-8)):
        raise ValueError(f"{name} must be a rigid 4x4 transform")
    return matrix.copy()


def camera_opencv_to_arcore_child(t_opencv_child) -> np.ndarray:
    """Express an OpenCV-camera child pose in the ARCore camera frame."""
    return ARCORE_GL_TO_OPENCV @ rigid(t_opencv_child, name="t_opencv_child")


def world_arcore_to_world_opencv(t_world_arcore) -> np.ndarray:
    """Convert an ARCore camera pose to the corresponding OpenCV camera pose."""
    return rigid(t_world_arcore, name="t_world_arcore") @ ARCORE_GL_TO_OPENCV


def load_arcore_pinch_calibration(path: Path | str) -> tuple[dict, np.ndarray]:
    """Load only an explicitly ARCore/OpenGL-expressed camera calibration."""
    config = json.loads(Path(path).read_text(encoding="utf-8"))
    if config.get("t_cam_to_pinch_axes") != ARCORE_CAMERA_AXES:
        raise ValueError(
            "calibration must explicitly use ARCore/OpenGL camera axes; "
            "convert OpenCV with diag(1,-1,-1,1) @ T_cv"
        )
    if "t_cam_to_pinch" not in config:
        raise ValueError("calibration is missing t_cam_to_pinch")
    return config, rigid(config["t_cam_to_pinch"], name="t_cam_to_pinch")


def gripper_camera_from_pinch(
    t_camera_pinch, pinch_offset_local=(0.0, 0.0, -0.080)
) -> np.ndarray:
    """Camera pose in the MuJoCo gripper body from the contract pinch frame.

    Contract EEF axes are [body +x, body -y, body -z], not body axes.  Omitting
    this proper 180-degree rotation makes the wrist camera look away from the
    demonstrated object while leaving translations deceptively plausible.
    """
    t_gripper_pinch = np.eye(4)
    t_gripper_pinch[:3, :3] = GRIPPER_BODY_TO_PINCH_ROTATION
    t_gripper_pinch[:3, 3] = np.asarray(pinch_offset_local, dtype=float)
    return rigid(t_gripper_pinch @ np.linalg.inv(
        rigid(t_camera_pinch, name="t_camera_pinch")), name="t_gripper_camera")


def s22_opencv_to_pinch() -> np.ndarray:
    """Provisional CAD-derived S22 camera -> fixed UMI pinch transform.

    The supplied rotation is exactly Rx(105 deg); using the rounded 0.259/0.966
    coefficients would make it slightly non-rigid. Translation is metres.
    """
    angle = np.deg2rad(105.0)
    c, s = np.cos(angle), np.sin(angle)
    transform = np.eye(4)
    transform[:3, :3] = [[1, 0, 0], [0, c, -s], [0, s, c]]
    transform[:3, 3] = [0.0, 0.0246, 0.1763]
    return transform


def s22_arcore_to_pinch() -> np.ndarray:
    """Same physical extrinsic expressed in the recorded ARCore camera axes."""
    return camera_opencv_to_arcore_child(s22_opencv_to_pinch())
