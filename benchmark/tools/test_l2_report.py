#!/usr/bin/env python3
"""Tests for l2_report.py.

Run: python3 -I benchmark/tools/test_l2_report.py
"""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import l2_report  # noqa: E402

GOLDENS = {
    "golden_version": 1,
    "frames": [
        {"resolution": "hd", "variant": "hit_qr", "sha256": "a" * 64},
        {"resolution": "hd", "variant": "miss", "sha256": "b" * 64},
    ],
}


def microbenchmark(name, time_runs, alloc_runs=None, **context):
    base_context = {
        "build": {"brand": "acme", "device": "dn", "model": "DN-ENG41",
                  "fingerprint": "acme/dn/dn:14/UP1A/1:user/release-keys",
                  "version": {"sdk": 34}},
        "cpuLocked": False,
        "cpuCoreCount": 8,
        "sustainedPerformanceModeEnabled": False,
    }
    base_context.update(context)
    metrics = {"timeNs": {"runs": time_runs}}
    if alloc_runs is not None:
        metrics["allocationCount"] = {"runs": alloc_runs}
    return {
        "context": base_context,
        "benchmarks": [{
            "name": name,
            "metrics": metrics,
            "repeatIterations": 100,
            "warmupIterations": 1000,
            "thermalThrottleSleepSeconds": 0,
        }],
    }


class CaseNameTest(unittest.TestCase):

    def test_parses_every_parameter_combination(self):
        for resolution in ("VGA", "HD", "FHD"):
            for variant in ("HIT_QR", "HIT_1D", "MISS"):
                for config in ("DEFAULT", "QR_ONLY", "ONED_ONLY_UPCA"):
                    name = "endToEnd[%s_%s_%s]" % (resolution, variant, config)
                    case = l2_report.convert_case({"name": name,
                                                   "metrics": {"timeNs": {"runs": [1, 2]}}})
                    self.assertIsNotNone(case, name)
                    self.assertEqual("endToEnd", case["stage"])
                    self.assertEqual(resolution.lower(), case["resolution"])
                    self.assertEqual(variant.lower(), case["variant"])
                    self.assertEqual(config.lower(), case["config"])

    def test_underscores_in_variant_and_config_are_not_mistaken_for_separators(self):
        case = l2_report.convert_case(
            {"name": "decode[FHD_HIT_1D_ONED_ONLY_UPCA]",
             "metrics": {"timeNs": {"runs": [1]}}})
        self.assertEqual("hit_1d", case["variant"])
        self.assertEqual("oned_only_upca", case["config"])

    def test_a_benchmark_from_another_class_is_not_an_l2_case(self):
        self.assertIsNone(l2_report.convert_case(
            {"name": "somethingElse", "metrics": {"timeNs": {"runs": [1]}}}))


class DecodesTest(unittest.TestCase):

    def test_the_matrix_records_which_cases_are_actually_hits(self):
        case = l2_report.convert_case({"name": "endToEnd[HD_HIT_QR_ONED_ONLY_UPCA]",
                                       "metrics": {"timeNs": {"runs": [1]}}})
        self.assertFalse(case["decodes"],
                         "a QR frame under a UPC-A-only reader is a miss, not a hit")
        case = l2_report.convert_case({"name": "endToEnd[HD_HIT_QR_QR_ONLY]",
                                       "metrics": {"timeNs": {"runs": [1]}}})
        self.assertTrue(case["decodes"])


class StatisticsTest(unittest.TestCase):

    def test_percentiles_come_from_the_observed_samples(self):
        values = list(range(1, 101))
        self.assertEqual(50, l2_report.percentile(values, 0.50))
        self.assertEqual(90, l2_report.percentile(values, 0.90))

    def test_the_interval_brackets_the_median_and_is_deterministic(self):
        values = [100 + (i % 7) for i in range(60)]
        first = l2_report.bootstrap_median_ci(values)
        second = l2_report.bootstrap_median_ci(values)
        self.assertEqual(first, second, "a fixed seed must give a reproducible interval")
        median = l2_report.percentile(values, 0.50)
        self.assertLessEqual(first[0], median)
        self.assertGreaterEqual(first[1], median)

    def test_a_single_sample_has_no_interval(self):
        self.assertEqual((None, None), l2_report.bootstrap_median_ci([42]))

    def test_a_noisy_sample_gets_a_wider_interval_than_a_quiet_one(self):
        quiet = l2_report.bootstrap_median_ci([100] * 60)
        noisy = l2_report.bootstrap_median_ci([50 + (i * 13) % 100 for i in range(60)])
        self.assertEqual(0, quiet[1] - quiet[0])
        self.assertGreater(noisy[1] - noisy[0], 0)


class WarningsTest(unittest.TestCase):

    def setUp(self):
        self.goldens = tempfile.NamedTemporaryFile(
            "w", suffix=".json", delete=False, encoding="utf-8")
        json.dump(GOLDENS, self.goldens)
        self.goldens.close()

    def tearDown(self):
        os.unlink(self.goldens.name)

    def report_for(self, data, **kwargs):
        return l2_report.build_report(data, self.goldens.name, "run", "commit",
                                      kwargs.get("device_label"),
                                      kwargs.get("config_label"))

    def test_unlocked_clocks_and_missing_sustained_mode_are_recorded(self):
        report = self.report_for(microbenchmark("crop[HD_MISS_DEFAULT]", [1] * 60))
        self.assertTrue(any(w.startswith("UNLOCKED_CLOCKS") for w in report["warnings"]))
        self.assertTrue(any(w.startswith("NO_SUSTAINED_PERFORMANCE_MODE")
                            for w in report["warnings"]))

    def test_an_emulator_is_detected_from_the_fingerprint(self):
        data = microbenchmark("crop[HD_MISS_DEFAULT]", [1] * 60)
        data["context"]["build"]["fingerprint"] = "google/sdk_gphone64_arm64/emu:16/x:user"
        report = self.report_for(data)
        self.assertTrue(report["device"]["emulator"])
        self.assertTrue(any(w.startswith("EMULATOR") for w in report["warnings"]))

    def test_too_few_iterations_is_a_warning_not_a_silent_result(self):
        report = self.report_for(microbenchmark("crop[HD_MISS_DEFAULT]", [1] * 49))
        self.assertTrue(any(w.startswith("TOO_FEW_ITERATIONS") for w in report["warnings"]))

    def test_fifty_iterations_clears_the_floor(self):
        report = self.report_for(microbenchmark("crop[HD_MISS_DEFAULT]", [1] * 50))
        self.assertFalse(any(w.startswith("TOO_FEW_ITERATIONS") for w in report["warnings"]))

    def test_thermal_throttling_is_recorded(self):
        data = microbenchmark("crop[HD_MISS_DEFAULT]", [1] * 60)
        data["benchmarks"][0]["thermalThrottleSleepSeconds"] = 12
        report = self.report_for(data)
        self.assertTrue(any(w.startswith("THERMAL_THROTTLE") for w in report["warnings"]))


class EnvelopeTest(unittest.TestCase):

    def setUp(self):
        self.goldens = tempfile.NamedTemporaryFile(
            "w", suffix=".json", delete=False, encoding="utf-8")
        json.dump(GOLDENS, self.goldens)
        self.goldens.close()

    def tearDown(self):
        os.unlink(self.goldens.name)

    def test_the_frame_hashes_travel_with_the_result(self):
        report = l2_report.build_report(
            microbenchmark("endToEnd[HD_MISS_DEFAULT]", [10] * 60, alloc_runs=[3] * 60),
            self.goldens.name, "run-1", "abc123", "DN-ENG41", None)
        self.assertEqual("L2", report["layer"])
        self.assertIsNone(report["dataset_hash"])
        self.assertEqual("a" * 64, report["frames"]["hd/hit_qr"])
        self.assertEqual(1, report["frames_golden_version"])
        self.assertEqual("DN-ENG41", report["device"]["label"])
        self.assertEqual(34, report["device"]["api"])
        self.assertEqual("default", report["config"])
        case = report["cases"][0]
        self.assertEqual(60, case["iterations"])
        self.assertEqual(10, case["ns_p50"])
        self.assertEqual(3, case["alloc_count_p50"])

    def test_a_run_covering_several_configurations_is_labelled_mixed(self):
        data = microbenchmark("endToEnd[HD_MISS_DEFAULT]", [10] * 60)
        data["benchmarks"].append({"name": "endToEnd[HD_MISS_QR_ONLY]",
                                   "metrics": {"timeNs": {"runs": [11] * 60}}})
        report = l2_report.build_report(data, self.goldens.name, "r", "c", None, None)
        self.assertEqual("mixed", report["config"])

    def test_a_missing_allocation_metric_is_null_rather_than_zero(self):
        report = l2_report.build_report(
            microbenchmark("endToEnd[HD_MISS_DEFAULT]", [10] * 60),
            self.goldens.name, "r", "c", None, None)
        self.assertIsNone(report["cases"][0]["alloc_count_p50"],
                          "an absent measurement must not be reported as zero allocations")


if __name__ == "__main__":
    unittest.main(verbosity=2)
