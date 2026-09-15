"""Focused unit checks for provisional relative-chunk registration search."""

from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

import numpy as np

from sim.mujoco.build_scene import load_config
from tools.umi_mujoco import apply_kinematic_grasp

from search_relative_chunk_registration import (
    PROGRESS_SCHEMA,
    _cached_candidate,
    _candidate_starts,
    _dynamic_rank,
    _load_progress,
    _refined_starts,
    _save_candidate,
    _table_placement_metrics,
)


class Tests(unittest.TestCase):
    def test_table_margin_uses_full_object_footprint(self) -> None:
        metrics = _table_placement_metrics(
            object_xyz=np.asarray([0.52639255, -0.00444458, 0.03835]),
            object_size_m=np.asarray([0.039, 0.039, 0.0767]),
            table_xy=np.asarray([0.15, 0.0]),
            table_half_xy=np.asarray([0.4, 0.4]),
            edge_margin_m=0.03,
        )
        self.assertFalse(metrics["object_on_table_with_margin"])
        self.assertAlmostEqual(
            metrics["minimum_table_edge_clearance_m"], 0.00410745,
            places=7,
        )

    def test_table_margin_accepts_central_object(self) -> None:
        metrics = _table_placement_metrics(
            object_xyz=np.asarray([0.30, 0.02, 0.03835]),
            object_size_m=np.asarray([0.039, 0.039, 0.0767]),
            table_xy=np.asarray([0.15, 0.0]),
            table_half_xy=np.asarray([0.4, 0.4]),
            edge_margin_m=0.03,
        )
        self.assertTrue(metrics["object_on_table_with_margin"])
        self.assertGreaterEqual(
            metrics["minimum_table_edge_clearance_m"], 0.03)

    def test_handoff_overlay_replaces_legacy_pinch_frame(self) -> None:
        ai_root = Path(__file__).resolve().parents[1]
        cfg = load_config(ai_root / "configs" / "so101.yaml")
        payload = json.loads((
            ai_root / "configs" / "real"
            / "so101_ver1_phone_holder_kinematic.json"
        ).read_text(encoding="utf-8"))
        apply_kinematic_grasp(cfg, payload)
        np.testing.assert_allclose(
            cfg["grasp"]["pinch_offset_local"],
            [2.1e-7, -5.64e-7, -0.158118819], atol=1e-12)
        np.testing.assert_allclose(
            cfg["grasp"]["eef_frame_local"]["jaw_axis"],
            [-0.048656744, 0.998815559, -0.000003629], atol=1e-12)
        self.assertEqual(
            cfg["grasp"]["gap_dependent_pinch_midpoint"]["kind"],
            "symmetric_parallel_jaw")
        self.assertFalse(
            cfg["grasp"]["kinematic_overlay"]["dynamic_mujoco_ready"])

    def test_progress_round_trip_and_resume(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "search.progress.json"
            payload = {"arguments": {"candidates": 2}}
            state = _load_progress(
                path=path, resume=False, signature="abc",
                signature_payload=payload)
            self.assertEqual(state["schema"], PROGRESS_SCHEMA)
            report = {"candidate_id": 3, "start_arm_rad": [0, 1, 2, 3, 4]}
            _save_candidate(
                state=state, progress_path=path, stage="screen",
                report=report)

            resumed = _load_progress(
                path=path, resume=True, signature="abc",
                signature_payload=payload)
            cached = _cached_candidate(
                state=resumed, stage="screen", candidate_id=3,
                arm=np.asarray(report["start_arm_rad"], dtype=float))
            self.assertEqual(cached, report)

    def test_resume_rejects_changed_signature(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "search.progress.json"
            _load_progress(
                path=path, resume=False, signature="old",
                signature_payload={})
            with self.assertRaisesRegex(ValueError, "signature differs"):
                _load_progress(
                    path=path, resume=True, signature="new",
                    signature_payload={})

    def test_cached_candidate_rejects_changed_seed(self) -> None:
        state = {
            "stages": {"screen": {"0": {
                "candidate_id": 0,
                "start_arm_rad": [0, 0, 0, 0, 0],
            }}},
        }
        with self.assertRaisesRegex(ValueError, "different arm seed"):
            _cached_candidate(
                state=state, stage="screen", candidate_id=0,
                arm=np.ones(5))

    def test_dynamic_rank_penalises_simultaneous_tip(self) -> None:
        def report(tipped: int) -> dict:
            return {"dynamic_oracle": {
                "passed": False,
                "success_fraction": 1 / 3,
                "runner_errors": 0,
                "failure_counts": {
                    "ik_reject_after_contact": 1,
                    "object_tipped": tipped,
                    "object_displaced": tipped,
                    "static_scene_reject": 0,
                },
                "contacts": 3,
                "mean_max_lift_height_m": 0.05,
            }}
        self.assertGreater(
            _dynamic_rank(report(1), (0.0,)),
            _dynamic_rank(report(2), (1.0,)),
        )

    def test_source_registration_is_not_clipped_to_search_margin(self) -> None:
        ranges = np.asarray([[-2.0, 2.0]] * 5, dtype=float)
        source = np.asarray([0.0, 0.0, 0.0, -1.9, 1.9], dtype=float)
        candidates, targeted = _candidate_starts(
            source, ranges, count=1, seed=7, ik=None,
            initial_gap_m=0.06, targeted_start_rotation=np.eye(3))
        self.assertEqual(targeted, 0)
        np.testing.assert_array_equal(candidates[0], source)

    def test_refinement_keeps_valid_near_limit_seed_nearby(self) -> None:
        ranges = np.asarray([[-2.0, 2.0]] * 5, dtype=float)
        source = np.asarray([0.0, 0.0, 0.0, -1.9, 1.9], dtype=float)
        refined = _refined_starts(
            seeds=[source], ranges=ranges, existing=[source], count=4,
            sigma_fraction=0.001, seed=8)
        self.assertEqual(len(refined), 4)
        self.assertTrue(all(np.linalg.norm(value - source) < 0.05
                            for value in refined))


if __name__ == "__main__":
    unittest.main(verbosity=2)
