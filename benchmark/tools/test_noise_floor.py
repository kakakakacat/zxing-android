#!/usr/bin/env python3
"""Tests for noise_floor.py.

Run: python3 -I benchmark/tools/test_noise_floor.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import noise_floor  # noqa: E402


def run(cases, commit="abc", device="DN-ENG41", args=None, run_id="r"):
    return {
        "layer": "L2", "commit": commit, "config": "default", "run_id": run_id,
        "frames": {"hd/miss": "a" * 64}, "frames_golden_version": 1,
        "device": {"label": device},
        "protocol": {"runner_args": args if args is not None else {"l2.matrix": "full"}},
        "cases": [{"name": name, "stage": name.split("[")[0], "ns_p50": p50}
                  for name, p50 in cases.items()],
    }


class GuardTest(unittest.TestCase):

    def test_two_runs_of_the_same_thing_are_usable(self):
        self.assertEqual([], noise_floor.guard([run({"a": 100}), run({"a": 101})]))

    def test_a_different_commit_is_not_noise(self):
        reasons = noise_floor.guard([run({"a": 100}), run({"a": 200}, commit="def")])
        self.assertTrue(any("commit differs" in r for r in reasons), reasons)

    def test_a_different_protocol_is_not_noise(self):
        reasons = noise_floor.guard(
            [run({"a": 100}), run({"a": 200}, args={"l2.matrix": "ci"})])
        self.assertTrue(any("instrumentation arguments" in r for r in reasons), reasons)

    def test_a_different_device_is_not_noise(self):
        reasons = noise_floor.guard([run({"a": 100}), run({"a": 200}, device="Pixel")])
        self.assertTrue(any("device differs" in r for r in reasons), reasons)


class SpreadTest(unittest.TestCase):

    def test_spread_is_relative_to_the_smaller_median(self):
        rows, _ = noise_floor.deltas([run({"a": 100}), run({"a": 110})])
        self.assertAlmostEqual(0.10, rows[0]["spread"])

    def test_identical_runs_have_no_spread(self):
        rows, _ = noise_floor.deltas([run({"a": 100}), run({"a": 100})])
        self.assertEqual(0.0, rows[0]["spread"])

    def test_more_than_two_runs_use_the_full_range(self):
        rows, _ = noise_floor.deltas(
            [run({"a": 100}), run({"a": 105}), run({"a": 120})])
        self.assertAlmostEqual(0.20, rows[0]["spread"])

    def test_a_case_missing_from_one_run_is_reported_not_silently_dropped(self):
        rows, missing = noise_floor.deltas([run({"a": 100, "b": 50}), run({"a": 101})])
        self.assertEqual(["a"], [row["name"] for row in rows])
        self.assertEqual(["b"], missing)

    def test_a_zero_or_missing_median_cannot_produce_a_ratio(self):
        rows, _ = noise_floor.deltas([run({"a": 0}), run({"a": 100})])
        self.assertEqual([], rows)


class FloorTest(unittest.TestCase):

    def test_the_floor_sits_at_or_above_the_p95_of_observed_spread(self):
        spreads = {"c%d" % i: 100 for i in range(20)}
        second = {"c%d" % i: 100 + i for i in range(20)}
        rows, _ = noise_floor.deltas([run(spreads), run(second)])
        summary = noise_floor.summarise(rows)
        self.assertGreaterEqual(summary["suggested_floor"], summary["spread_p95"])

    def test_the_floor_is_a_whole_percent(self):
        self.assertEqual(0.03, noise_floor.round_up(0.021))
        self.assertEqual(0.05, noise_floor.round_up(0.0401))
        self.assertEqual(0.02, noise_floor.round_up(0.02))

    def test_the_worst_cases_are_named_so_an_outlier_can_be_investigated(self):
        rows, _ = noise_floor.deltas(
            [run({"a": 100, "b": 100}), run({"a": 101, "b": 300})])
        summary = noise_floor.summarise(rows)
        self.assertEqual("b", summary["worst_cases"][0]["name"])


if __name__ == "__main__":
    unittest.main(verbosity=1)
