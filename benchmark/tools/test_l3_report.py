#!/usr/bin/env python3
"""Tests for l3_report.py.

Run: python3 -I benchmark/tools/test_l3_report.py

The cases that matter here are the refusals. An L3 result that quietly describes the wrong
camera backend, or reports an absent span as a measured zero, is worse than no result —
both of those happened during the harness's first runs and both are tested below.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import l3_report  # noqa: E402


def macrobenchmark(metrics, **context):
    base_context = {
        "build": {"brand": "acme", "device": "dn", "model": "DN-ENG41",
                  "fingerprint": "acme/dn/dn:14/UP1A/1:user/release-keys",
                  "version": {"sdk": 34}},
        "cpuLocked": True,
        "cpuCoreCount": 8,
        "sustainedPerformanceModeEnabled": True,
    }
    base_context.update(context)
    return {"context": base_context,
            "benchmarks": [{"name": "coldStartToCameraReady", "metrics": metrics}]}


def section(values, counts=None):
    counts = [1.0] * len(values) if counts is None else counts
    return values, counts


def metrics_for(startup, open_ms, frame_ms, scan_ms=None, backend="camerax"):
    out = {"timeToInitialDisplayMs": {"runs": startup}}
    for name, values in (("zxing.camera.open", open_ms),
                         ("zxing.first.frame", frame_ms),
                         ("zxing.first.scan", scan_ms or [])):
        runs, counts = section(values)
        out[name + "SumMs"] = {"runs": runs or [0.0] * len(startup)}
        out[name + "Count"] = {"runs": counts or [0.0] * len(startup)}
    for name in ("camera1", "camerax"):
        present = name == backend
        out["zxing.backend.%sSumMs" % name] = {
            "runs": [float(i + 1) for i in range(len(startup))] if present
                    else [0.0] * len(startup)}
        out["zxing.backend.%sCount" % name] = {
            "runs": [1.0] * len(startup) if present else [0.0] * len(startup)}
    return out


class Percentiles(unittest.TestCase):

    def test_percentiles_come_from_observed_launches(self):
        self.assertEqual(l3_report.percentile([5.0, 1.0, 3.0], 0.50), 3.0)

    def test_no_interpolation_between_two_launches(self):
        # 2.0 is a launch that happened; 1.5 is not.
        self.assertEqual(l3_report.percentile([1.0, 2.0], 0.50), 1.0)

    def test_the_interval_is_deterministic_and_brackets_the_median(self):
        values = [float(v) for v in range(1, 21)]
        first = l3_report.bootstrap_median_ci(values)
        self.assertEqual(first, l3_report.bootstrap_median_ci(values))
        self.assertLessEqual(first[0], l3_report.percentile(values, 0.50))
        self.assertGreaterEqual(first[1], l3_report.percentile(values, 0.50))

    def test_a_single_launch_has_no_interval(self):
        self.assertEqual(l3_report.bootstrap_median_ci([7.0]), (None, None))


class AbsentIsNotZero(unittest.TestCase):

    def test_a_span_that_never_fired_is_null_not_zero(self):
        macro = macrobenchmark(metrics_for([100.0, 110.0], [10.0, 12.0], [20.0, 22.0]))
        report = l3_report.build_report(macro, "r", "c", "DN-ENG41", "camerax", {})
        scan = next(c for c in report["cases"] if c["name"] == "ready_to_first_scan")
        self.assertIsNone(scan["ms_p50"])
        self.assertEqual(scan["iterations"], 0)
        self.assertEqual(scan["occurrences"], 0)

    def test_an_absent_scan_is_reported_as_a_missing_scene(self):
        macro = macrobenchmark(metrics_for([100.0], [10.0], [20.0]))
        report = l3_report.build_report(macro, "r", "c", "DN-ENG41", "camerax", {})
        self.assertTrue(any(w.startswith("NO_SCENE") for w in report["warnings"]))

    def test_an_iteration_where_the_span_did_not_fire_is_dropped_not_counted_as_zero(self):
        metrics = metrics_for([100.0, 110.0], [10.0, 12.0], [20.0, 22.0])
        metrics["zxing.camera.openSumMs"] = {"runs": [10.0, 0.0]}
        metrics["zxing.camera.openCount"] = {"runs": [1.0, 0.0]}
        report = l3_report.build_report(macrobenchmark(metrics), "r", "c", "d", "camerax", {})
        case = next(c for c in report["cases"] if c["name"] == "camera_open_to_ready")
        self.assertEqual(case["iterations"], 1)
        self.assertEqual(case["ms_p50"], 10.0)


class DerivedInterval(unittest.TestCase):

    def test_the_derived_interval_sums_per_iteration_not_per_median(self):
        # Medians: open 10, frame 40. Per-iteration sums: 50 and 50, so the median is 50 —
        # which here equals the sum of medians. The next test is the one that separates them.
        macro = macrobenchmark(metrics_for([1.0, 1.0], [10.0, 10.0], [40.0, 40.0]))
        report = l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        case = next(c for c in report["cases"] if c["name"] == "open_to_first_frame")
        self.assertEqual(case["ms_p50"], 50.0)

    def test_a_sum_of_medians_would_give_a_different_answer(self):
        # open  = [10, 30] -> median 10 (nearest-rank)
        # frame = [50, 10] -> median 10
        # sum of medians = 20, but no iteration took 20: they took 60 and 40.
        macro = macrobenchmark(metrics_for([1.0, 1.0], [10.0, 30.0], [50.0, 10.0]))
        report = l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        case = next(c for c in report["cases"] if c["name"] == "open_to_first_frame")
        self.assertIn(case["ms_p50"], (40.0, 60.0))
        self.assertNotEqual(case["ms_p50"], 20.0)

    def test_an_iteration_missing_either_half_is_excluded(self):
        metrics = metrics_for([1.0, 1.0], [10.0, 10.0], [40.0, 40.0])
        metrics["zxing.first.frameCount"] = {"runs": [1.0, 0.0]}
        metrics["zxing.first.frameSumMs"] = {"runs": [40.0, 0.0]}
        report = l3_report.build_report(macrobenchmark(metrics), "r", "c", "d", "camerax", {})
        case = next(c for c in report["cases"] if c["name"] == "open_to_first_frame")
        self.assertEqual(case["iterations"], 1)


class BackendIsCheckedAgainstTheTrace(unittest.TestCase):

    def test_a_mismatch_is_refused_rather_than_warned_about(self):
        macro = macrobenchmark(metrics_for([1.0], [10.0], [20.0], backend="camera1"))
        with self.assertRaises(SystemExit) as raised:
            l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        self.assertIn("backend mismatch", str(raised.exception))

    def test_a_match_is_recorded_in_the_result(self):
        macro = macrobenchmark(metrics_for([1.0], [10.0], [20.0], backend="camera1"))
        report = l3_report.build_report(macro, "r", "c", "d", "camera1", {})
        self.assertEqual(report["camera_backend"],
                         {"claimed": "camera1", "observed_in_trace": "camera1"})

    def test_no_backend_span_is_a_warning_not_a_mismatch(self):
        metrics = metrics_for([1.0], [10.0], [20.0])
        for name in ("camera1", "camerax"):
            metrics["zxing.backend.%sCount" % name] = {"runs": [0.0]}
            metrics["zxing.backend.%sSumMs" % name] = {"runs": [0.0]}
        report = l3_report.build_report(macrobenchmark(metrics), "r", "c", "d", "camerax", {})
        self.assertIsNone(report["camera_backend"]["observed_in_trace"])
        self.assertTrue(any(w.startswith("NO_BACKEND_SPAN") for w in report["warnings"]))


class RefusalsAndWarnings(unittest.TestCase):

    def test_an_emulator_result_is_refused_outright(self):
        macro = macrobenchmark(
            metrics_for([1.0], [10.0], [20.0]),
            build={"brand": "Google", "device": "emu64a", "model": "sdk_gphone64_arm64",
                   "fingerprint": "google/sdk_gphone64/emu64a:16/BP/1:user/release-keys",
                   "version": {"sdk": 36}})
        with self.assertRaises(SystemExit) as raised:
            l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        self.assertIn("emulator", str(raised.exception))

    def test_unlocked_clocks_are_recorded(self):
        macro = macrobenchmark(metrics_for([1.0], [10.0], [20.0]), cpuLocked=False)
        report = l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        self.assertTrue(any(w.startswith("UNLOCKED_CLOCKS") for w in report["warnings"]))

    def test_too_few_launches_is_a_warning_not_a_silent_result(self):
        macro = macrobenchmark(metrics_for([1.0] * 3, [10.0] * 3, [20.0] * 3))
        report = l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        self.assertTrue(any(w.startswith("TOO_FEW_ITERATIONS") for w in report["warnings"]))

    def test_twelve_launches_clear_the_floor(self):
        macro = macrobenchmark(metrics_for([1.0] * 12, [10.0] * 12, [20.0] * 12))
        report = l3_report.build_report(macro, "r", "c", "d", "camerax", {})
        self.assertFalse(any(w.startswith("TOO_FEW_ITERATIONS") for w in report["warnings"]))

    def test_the_envelope_carries_the_layer_and_the_protocol(self):
        macro = macrobenchmark(metrics_for([1.0] * 12, [10.0] * 12, [20.0] * 12))
        report = l3_report.build_report(macro, "run-1", "abc1234", "DN-ENG41", "camerax",
                                        {"l3.iterations": "12"})
        self.assertEqual(report["layer"], "L3")
        self.assertEqual(report["run_id"], "run-1")
        self.assertEqual(report["commit"], "abc1234")
        self.assertEqual(report["runner_args"], {"l3.iterations": "12"})
        self.assertIsNone(report["dataset_hash"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
