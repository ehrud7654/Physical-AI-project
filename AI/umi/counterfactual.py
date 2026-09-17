"""Policy-free counterfactual TCP targets for displaced simulation objects.

Reference anchors are reconstructed from the recorded first future step.
Every future world target receives the object displacement once, then is
re-expressed relative to the actual robot TCP. This prevents a receding-horizon
replay from adding the same displacement at every control cycle.
"""
from __future__ import annotations

import numpy as np

from umi.relative_dataset import relative_vector, vector_to_transform


def reference_anchors(actions: np.ndarray, *, start_row: int,
                      start_pose: np.ndarray) -> np.ndarray:
    """Reconstruct recorded TCP anchors from a predeclared fixed start row."""
    chunks = np.asarray(actions, dtype=np.float64)
    origin = np.asarray(start_pose, dtype=np.float64)
    if (chunks.ndim != 3 or chunks.shape[1:] != (8, 10)
            or origin.shape != (4, 4) or not np.isfinite(chunks).all()
            or not np.isfinite(origin).all() or not 0 <= start_row < len(chunks)):
        raise ValueError("expected finite action[N,8,10] and fixed start pose")
    anchors = [origin.copy()]
    for row in range(start_row + 1, len(chunks)):
        anchors.append(anchors[-1] @ vector_to_transform(chunks[row - 1, 0]))
    return np.stack(anchors)


def shifted_chunk_from_reference(
        action: np.ndarray, *, reference_pose: np.ndarray,
        actual_pose: np.ndarray, delta_world_xyz: np.ndarray) -> np.ndarray:
    """Shift each absolute world target once; preserve recorded rotation/gap."""
    chunk = np.asarray(action, dtype=np.float64)
    reference = np.asarray(reference_pose, dtype=np.float64)
    actual = np.asarray(actual_pose, dtype=np.float64)
    delta = np.asarray(delta_world_xyz, dtype=np.float64)
    if (chunk.shape != (8, 10) or reference.shape != (4, 4)
            or actual.shape != (4, 4) or delta.shape != (3,)
            or not np.isfinite(chunk).all() or not np.isfinite(reference).all()
            or not np.isfinite(actual).all() or not np.isfinite(delta).all()):
        raise ValueError("expected finite chunk[8,10], two poses[4,4], delta[3]")
    translated = []
    for future in chunk:
        world_target = reference @ vector_to_transform(future)
        world_target[:3, 3] += delta
        translated.append(relative_vector(actual, world_target, float(future[9])))
    return np.stack(translated).astype(np.float32)
