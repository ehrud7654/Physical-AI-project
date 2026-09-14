"""Hardware-free regression tests for the provisional relative-chunk contract."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np

from umi.action_decode import decode_relative_actions
from umi.relative_dataset import (
    MAX_GRIPPER_WIDTH_M,
    relative_vector,
    vector_to_transform,
)


def pose(xyz=(0, 0, 0), yaw_deg=0.0):
    angle = np.deg2rad(yaw_deg)
    c, s = np.cos(angle), np.sin(angle)
    result = np.eye(4)
    result[:3, :3] = [[c, -s, 0], [s, c, 0], [0, 0, 1]]
    result[:3, 3] = xyz
    return result


class RelativeDatasetTests(unittest.TestCase):
    def test_encode_decode_round_trip(self):
        current = pose((0.2, -0.1, 0.3), 30)
        target = current @ pose((0.03, 0.01, -0.02), -20)
        encoded = relative_vector(current, target, 0.039)[None]
        decoded = decode_relative_actions(encoded, current)
        np.testing.assert_allclose(decoded.poses[0], target, atol=1e-9)
        np.testing.assert_allclose(decoded.gripper_width_m, [0.039])

    def test_global_alignment_cancels(self):
        current, target = pose((0.1, 0.2, 0.3), 15), pose((0.4, -0.2, 0.5), -40)
        world_change = pose((-2, 3, 1), 127)
        direct = relative_vector(current, target, 0.04)
        moved = relative_vector(world_change @ current, world_change @ target, 0.04)
        np.testing.assert_allclose(direct, moved, atol=1e-9)

    def test_current_proprio_is_identity(self):
        current = pose((1, 2, 3), 72)
        encoded = relative_vector(current, current, 0.05)
        np.testing.assert_allclose(vector_to_transform(encoded), np.eye(4), atol=1e-9)

    def test_gap_contract_rejects_out_of_range(self):
        with self.assertRaisesRegex(ValueError, "gripper width"):
            relative_vector(np.eye(4), np.eye(4), MAX_GRIPPER_WIDTH_M + 1e-3)


if __name__ == "__main__":
    unittest.main()
