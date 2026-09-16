"""Unit checks for the local recorded-oracle gate classification."""

from __future__ import annotations

from pathlib import Path
import unittest

import numpy as np

from gate_relative_chunk_oracle import (
    _aggregate,
    _classification,
    _kinematic_grasp_args,
    _require_provisional_registration,
    _scene_valid_episodes,
)
from render_relative_chunk_rollout import (
    _episode_approach_start,
    _interpolated_joint_target,
    _perturb_policy_images,
    _stable_side_grasp_success,
)


class Tests(unittest.TestCase):
    def test_policy_image_perturbations_are_explicit_and_deterministic(self) -> None:
        image = np.full((2, 3, 4, 5), 128, dtype=np.uint8)
        rng = np.random.default_rng(7)
        black, frozen = _perturb_policy_images(
            image, mode="blackout", rng=rng, frozen=None, noise_gray=32.0)
        self.assertFalse(np.any(black))
        self.assertIsNone(frozen)

        first, frozen = _perturb_policy_images(
            image, mode="freeze", rng=rng, frozen=None, noise_gray=32.0)
        changed = np.full_like(image, 255)
        second, frozen = _perturb_policy_images(
            changed, mode="freeze", rng=rng, frozen=frozen, noise_gray=32.0)
        np.testing.assert_array_equal(first, image)
        np.testing.assert_array_equal(second, image)

        noise_a, _ = _perturb_policy_images(
            image, mode="noise", rng=np.random.default_rng(11),
            frozen=None, noise_gray=32.0)
        noise_b, _ = _perturb_policy_images(
            image, mode="noise", rng=np.random.default_rng(11),
            frozen=None, noise_gray=32.0)
        np.testing.assert_array_equal(noise_a, noise_b)
        self.assertFalse(np.array_equal(noise_a, image))

    def test_only_known_explicitly_nonphysical_registrations_are_accepted(self) -> None:
        for status in (
            "PROVISIONAL_NOT_PHYSICAL_CALIBRATION",
            "DIAGNOSTIC_COLLISION_PROXY_CANDIDATE_NOT_PHYSICAL_VALIDATION",
            "DIAGNOSTIC_CANONICAL_REPLAY_NOT_REGISTRATION",
        ):
            _require_provisional_registration({"status": status})
        with self.assertRaises(ValueError):
            _require_provisional_registration({"status": "VALIDATED"})

    def test_fixed_approach_start_is_read_without_runtime_search(self) -> None:
        payload = {"selected": {"per_episode": [{
            "episode": "episode-a",
            "scene_constraints_ok": True,
            "approach_start_row": 7,
            "approach_start_arm_rad": [0.1, 0.2, 0.3, 0.4, 0.5],
            "approach_start_executed_gap_m": 0.038,
        }]}}
        row, arm, gap = _episode_approach_start(payload, "episode-a")
        self.assertEqual(row, 7)
        np.testing.assert_allclose(arm, [0.1, 0.2, 0.3, 0.4, 0.5])
        self.assertAlmostEqual(gap, 0.038)

    def test_kinematic_grasp_override_is_forwarded_explicitly(self) -> None:
        self.assertEqual(_kinematic_grasp_args(None), [])
        path = Path("configs/real/current-kinematic.json")
        args = _kinematic_grasp_args(path)
        self.assertEqual(args[0], "--kinematic-grasp")
        self.assertTrue(Path(args[1]).is_absolute())

    def test_scheduled_target_interpolates_arm_and_metric_gap(self) -> None:
        curve = [[-1.0, 0.0], [1.0, 10.0]]
        result = _interpolated_joint_target(
            segment_start_q=np.asarray([0, 1, 2, 3, 4, -0.2], dtype=float),
            target_arm_rad=np.asarray([2, 3, 4, 5, 6], dtype=float),
            segment_start_gap_m=0.04,
            target_gap_m=0.06,
            alpha=0.5,
            curve=curve,
        )
        np.testing.assert_allclose(result[:5], [1, 2, 3, 4, 5])
        # Curve widths are centimetres; 5 cm maps to angle 0 in this fixture.
        self.assertAlmostEqual(float(result[5]), 0.0)

    def test_post_lift_transport_is_not_scored_as_tabletop_shove(self) -> None:
        self.assertTrue(_stable_side_grasp_success(
            mujoco_success=True,
            pre_lift_xy_displacement_m=0.010,
            xy_threshold_m=0.0195,
            max_tilt_deg=8.0,
            tilt_threshold_deg=30.0,
        ))
        self.assertFalse(_stable_side_grasp_success(
            mujoco_success=True,
            pre_lift_xy_displacement_m=0.020,
            xy_threshold_m=0.0195,
            max_tilt_deg=8.0,
            tilt_threshold_deg=30.0,
        ))

    def test_scene_valid_episode_selection(self) -> None:
        payload = {"selected": {"per_episode": [
            {"episode": "ok-a", "scene_constraints_ok": True},
            {"episode": "bad", "scene_constraints_ok": False},
            {"episode": "ok-b", "scene_constraints_ok": True},
        ]}}
        self.assertEqual(_scene_valid_episodes(payload), ["ok-a", "ok-b"])

    def test_success_has_priority(self) -> None:
        self.assertEqual(_classification({
            "success": True,
            "first_contact_cycle": 1,
            "geometry_error": "late diagnostic",
        }), "success")

    def test_tip_has_priority_over_transient_lift(self) -> None:
        self.assertEqual(_classification({
            "success": True,
            "stable_side_grasp_success": False,
            "first_contact_cycle": 1,
            "geometry_error": None,
            "object_tipped": True,
            "object_displaced": False,
        }), "object_tipped")

    def test_ik_failure_is_split_by_contact(self) -> None:
        self.assertEqual(_classification({
            "success": False,
            "first_contact_cycle": None,
            "geometry_error": "bad IK",
        }), "ik_reject_before_contact")
        self.assertEqual(_classification({
            "success": False,
            "first_contact_cycle": 3,
            "geometry_error": "bad IK",
        }), "ik_reject_after_contact")

    def test_contact_outcomes(self) -> None:
        common = {
            "success": False,
            "first_contact_cycle": 2,
            "geometry_error": None,
        }
        self.assertEqual(_classification({
            **common,
            "object_tipped": True,
            "object_displaced": True,
        }), "object_tipped")
        self.assertEqual(_classification({
            **common,
            "object_tipped": False,
            "object_displaced": True,
        }), "object_displaced")
        self.assertEqual(_classification({
            **common,
            "lift_height_m": -0.02,
            "min_lift_height_m": -0.02,
            "max_lift_height_m": 0.04,
        }), "object_displaced_or_tipped")
        self.assertEqual(_classification({
            **common,
            "lift_height_m": 0.01,
            "min_lift_height_m": 0.0,
            "max_lift_height_m": 0.02,
        }), "partial_lift")
        self.assertEqual(_classification({
            **common,
            "lift_height_m": 0.001,
            "min_lift_height_m": 0.0,
            "max_lift_height_m": 0.001,
        }), "contact_without_lift")

    def test_no_contact(self) -> None:
        self.assertEqual(_classification({
            "success": False,
            "first_contact_cycle": None,
            "geometry_error": None,
        }), "no_contact")

    def test_aggregate_preserves_failure_types(self) -> None:
        rows = [
            {
                "classification": "success", "success": True,
                "first_contact_cycle": 2,
            },
            {
                "classification": "ik_reject_after_contact", "success": False,
                "first_contact_cycle": 3, "object_tipped": True,
                "object_displaced": True,
            },
            {
                "classification": "object_displaced_or_tipped", "success": False,
                "first_contact_cycle": 1,
            },
        ]
        result = _aggregate(rows, threshold=0.8)
        self.assertEqual(result["episodes"], 3)
        self.assertEqual(result["contacts"], 3)
        self.assertEqual(result["successes"], 1)
        self.assertAlmostEqual(result["success_fraction"], 1 / 3)
        self.assertFalse(result["passed"])
        self.assertEqual(result["classification_counts"], {
            "ik_reject_after_contact": 1,
            "object_displaced_or_tipped": 1,
            "success": 1,
        })
        self.assertEqual(result["failure_counts"], {
            "runner_error": 0,
            "ik_reject_after_contact": 1,
            "object_tipped": 1,
            "object_displaced": 1,
            "static_scene_reject": 0,
        })


if __name__ == "__main__":
    unittest.main(verbosity=2)
