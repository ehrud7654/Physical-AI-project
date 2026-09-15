"""Unit checks for the local recorded-oracle gate classification."""

from __future__ import annotations

import unittest

from gate_relative_chunk_oracle import _classification, _scene_valid_episodes


class Tests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main(verbosity=2)
