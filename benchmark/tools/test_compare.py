#!/usr/bin/env python3
"""Tests for compare.py — one per gate in BENCHMARKS.md section 10.6.

Run: python3 -I benchmark/tools/test_compare.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import compare  # noqa: E402

FRAMES = {"hd/hit_qr": "a" * 64, "hd/miss": "b" * 64}


def case(name="endToEnd[HD_HIT_QR_DEFAULT]", p50=1000, low=None, high=None,
         iterations=60, alloc=10):
    return {
        "name": name,
        "stage": name.split("[")[0],
        "resolution": "hd", "variant": "hit_qr", "config": "default", "decodes": True,
        "ns_p50": p50, "ns_p90": p50 * 1.2,
        "ns_ci95_low": p50 * 0.99 if low is None else low,
        "ns_ci95_high": p50 * 1.01 if high is None else high,
        "iterations": iterations,
        "alloc_count_p50": alloc,
    }


def report(cases=None, warnings=(), device_label="DN-ENG41", api=34, emulator=False,
           config="default", frames=None, run_id="run", commit="abc"):
    return {
        "layer": "L2", "run_id": run_id, "commit": commit, "config": config,
        "dataset_hash": None,
        "frames": FRAMES if frames is None else frames,
        "frames_golden_version": 1,
        "device": {"label": device_label, "api": api, "emulator": emulator},
        "warnings": list(warnings),
        "cases": cases if cases is not None else [case()],
    }


class GuardTest(unittest.TestCase):
    """Both sides must describe the same measurement before any case is compared."""

    def assert_incomparable(self, candidate, needle):
        result = compare.compare(report(), candidate)
        self.assertFalse(result["comparable"])
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertTrue(any(needle in reason for reason in result["guard_failures"]),
                        result["guard_failures"])

    def test_different_frames_are_not_comparable(self):
        self.assert_incomparable(report(frames={"hd/hit_qr": "c" * 64}), "frames differs")

    def test_a_different_device_is_not_comparable(self):
        self.assert_incomparable(report(device_label="Pixel 7"), "device.label differs")

    def test_a_different_api_level_is_not_comparable(self):
        self.assert_incomparable(report(api=36), "device.api differs")

    def test_an_emulator_against_a_phone_is_not_comparable(self):
        self.assert_incomparable(report(emulator=True), "device.emulator differs")

    def test_a_different_configuration_is_not_comparable(self):
        self.assert_incomparable(report(config="qr_only"), "config differs")

    def test_matching_sides_are_comparable(self):
        self.assertTrue(compare.compare(report(), report())["comparable"])


class VerdictTest(unittest.TestCase):

    def verdict_for(self, baseline_case, candidate_case, **kwargs):
        result = compare.compare(report([baseline_case]), report([candidate_case]),
                                 **kwargs)
        return result["cases"][0]

    def test_a_clear_improvement_passes(self):
        row = self.verdict_for(case(p50=1000, low=990, high=1010),
                               case(p50=800, low=790, high=810))
        self.assertEqual(compare.PASS, row["verdict"])
        self.assertAlmostEqual(-0.2, row["delta"])

    def test_a_clear_regression_fails(self):
        row = self.verdict_for(case(p50=1000, low=990, high=1010),
                               case(p50=1300, low=1290, high=1310))
        self.assertEqual(compare.FAIL, row["verdict"])

    def test_overlapping_intervals_are_inconclusive(self):
        row = self.verdict_for(case(p50=1000, low=800, high=1200),
                               case(p50=800, low=700, high=1100))
        self.assertEqual(compare.INCONCLUSIVE, row["verdict"])
        self.assertIn("overlap", row["reason"])

    def test_a_difference_below_the_floor_is_inconclusive(self):
        row = self.verdict_for(case(p50=1000, low=999, high=1001),
                               case(p50=970, low=969, high=971))
        self.assertEqual(compare.INCONCLUSIVE, row["verdict"])
        self.assertIn("floor", row["reason"])

    def test_too_few_repeats_is_inconclusive_however_clean_the_numbers(self):
        row = self.verdict_for(case(p50=1000, low=999, high=1001, iterations=49),
                               case(p50=500, low=499, high=501))
        self.assertEqual(compare.INCONCLUSIVE, row["verdict"])
        self.assertIn("49", row["reason"])

    def test_a_missing_interval_is_inconclusive_not_a_pass(self):
        broken = case(p50=500)
        broken["ns_ci95_low"] = None
        row = self.verdict_for(case(p50=1000, low=999, high=1001), broken)
        self.assertEqual(compare.INCONCLUSIVE, row["verdict"])
        self.assertIn("missing", row["reason"])

    def test_a_speedup_bought_with_allocations_is_not_a_win(self):
        row = self.verdict_for(case(p50=1000, low=999, high=1001, alloc=100),
                               case(p50=800, low=799, high=801, alloc=140))
        self.assertEqual(compare.INCONCLUSIVE, row["verdict"])
        self.assertIn("allocations grew", row["reason"])

    def test_a_speedup_with_steady_allocations_still_passes(self):
        row = self.verdict_for(case(p50=1000, low=999, high=1001, alloc=100),
                               case(p50=800, low=799, high=801, alloc=101))
        self.assertEqual(compare.PASS, row["verdict"])


class EffectFloorTest(unittest.TestCase):
    """The floor is measured, and it depends on how big the case is."""

    def test_an_expensive_case_gets_the_tighter_floor(self):
        self.assertEqual(compare.EFFECT_FLOOR_LARGE, compare.effect_floor(5_000_000))

    def test_a_sub_microsecond_case_gets_the_looser_floor(self):
        self.assertEqual(compare.EFFECT_FLOOR_SMALL, compare.effect_floor(195))

    def test_an_unknown_median_gets_the_looser_floor(self):
        # Not knowing how big a case is cannot be a reason to be more confident.
        self.assertEqual(compare.EFFECT_FLOOR_SMALL, compare.effect_floor(None))

    def test_a_four_percent_move_in_an_expensive_case_is_a_result(self):
        row = compare.compare(
            report([case(p50=5_000_000, low=4_990_000, high=5_010_000)]),
            report([case(p50=4_800_000, low=4_790_000, high=4_810_000)]))["cases"][0]
        self.assertEqual(compare.PASS, row["verdict"])
        self.assertEqual(compare.EFFECT_FLOOR_LARGE, row["floor"])

    def test_the_same_four_percent_move_in_a_tiny_case_is_not(self):
        # 195 ns to 187 ns is eight nanoseconds. The two runs of identical code
        # disagreed by more than that.
        row = compare.compare(
            report([case(p50=195, low=194, high=196)]),
            report([case(p50=187, low=186, high=188)]))["cases"][0]
        self.assertEqual(compare.INCONCLUSIVE, row["verdict"])
        self.assertIn("below the 10% floor", row["reason"])

    def test_the_floor_that_applied_is_recorded_on_every_case(self):
        result = compare.compare(report([case(p50=5_000_000)]),
                                 report([case(p50=5_000_000)]))
        self.assertIsNotNone(result["cases"][0]["floor"])


class WarningTest(unittest.TestCase):

    def test_unlocked_clocks_make_every_case_inconclusive_by_default(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=["UNLOCKED_CLOCKS: …"]),
            report([case(p50=500, low=499, high=501)]))
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["UNLOCKED_CLOCKS"], result["blocking_warnings"])
        self.assertIn("invalidates timing", result["cases"][0]["reason"])

    def test_the_operator_can_waive_a_noise_warning_and_it_is_recorded(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=["UNLOCKED_CLOCKS: …"]),
            report([case(p50=500, low=499, high=501)]),
            accept_noise_warnings=True)
        self.assertEqual(compare.PASS, result["verdict"])
        self.assertEqual(["UNLOCKED_CLOCKS"], result["waived_warnings"])
        self.assertEqual([], result["blocking_warnings"])

    def test_a_debuggable_build_cannot_be_waived(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=["DEBUGGABLE: …"]),
            report([case(p50=500, low=499, high=501)]),
            accept_noise_warnings=True)
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["DEBUGGABLE"], result["blocking_warnings"])

    def test_a_disabled_cooling_wait_blocks_by_default(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)],
                   warnings=["THROTTLE_WAIT_DISABLED: …"]),
            report([case(p50=500, low=499, high=501)]))
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["THROTTLE_WAIT_DISABLED"], result["blocking_warnings"])

    def test_thermal_throttling_blocks_by_default(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=["THERMAL_THROTTLE: …"]),
            report([case(p50=500, low=499, high=501)]))
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])


class ReadThroughTest(unittest.TestCase):
    """Section 10.6: a standing warning both sides carry, on a device whose floor was
    measured under it, is a shared condition rather than a difference."""

    def test_the_same_thermal_warning_on_both_sides_is_read_through(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=["THERMAL_THROTTLE: …"]),
            report([case(p50=500, low=499, high=501)], warnings=["THERMAL_THROTTLE: …"]))
        self.assertEqual(compare.PASS, result["verdict"])
        self.assertEqual(["THERMAL_THROTTLE"], result["read_through_warnings"])
        self.assertEqual([], result["blocking_warnings"])
        self.assertEqual([], result["waived_warnings"])

    def test_a_disabled_cooling_wait_is_read_through_on_both_sides_too(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)],
                   warnings=["THERMAL_THROTTLE: …", "THROTTLE_WAIT_DISABLED: …"]),
            report([case(p50=500, low=499, high=501)],
                   warnings=["THROTTLE_WAIT_DISABLED: …", "THERMAL_THROTTLE: …"]))
        self.assertEqual(compare.PASS, result["verdict"])
        self.assertEqual(["THERMAL_THROTTLE", "THROTTLE_WAIT_DISABLED"],
                         result["read_through_warnings"])

    def test_one_side_hot_and_the_other_cold_still_blocks(self):
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=["THERMAL_THROTTLE: …"]),
            report([case(p50=500, low=499, high=501)],
                   warnings=["THROTTLE_WAIT_DISABLED: …"]))
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["THERMAL_THROTTLE", "THROTTLE_WAIT_DISABLED"],
                         result["blocking_warnings"])
        self.assertEqual([], result["read_through_warnings"])

    def test_an_unmeasured_device_does_not_get_the_read_through(self):
        hot = ["THERMAL_THROTTLE: …"]
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=hot,
                   device_label="some-other-phone"),
            report([case(p50=500, low=499, high=501)], warnings=hot,
                   device_label="some-other-phone"))
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["THERMAL_THROTTLE"], result["blocking_warnings"])

    def test_a_missing_sustained_performance_mode_is_read_through_as_well(self):
        both = ["NO_SUSTAINED_PERFORMANCE_MODE: …"]
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=both),
            report([case(p50=500, low=499, high=501)], warnings=both))
        self.assertEqual(compare.PASS, result["verdict"])
        self.assertEqual(["NO_SUSTAINED_PERFORMANCE_MODE"],
                         result["read_through_warnings"])

    def test_the_read_through_does_not_extend_to_unlocked_clocks(self):
        both = ["UNLOCKED_CLOCKS: …"]
        result = compare.compare(
            report([case(p50=1000, low=999, high=1001)], warnings=both),
            report([case(p50=500, low=499, high=501)], warnings=both))
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["UNLOCKED_CLOCKS"], result["blocking_warnings"])


class RollUpTest(unittest.TestCase):

    def test_one_regression_fails_the_run_however_many_cases_improved(self):
        baseline = report([case("a", p50=1000, low=999, high=1001),
                           case("b", p50=1000, low=999, high=1001)])
        candidate = report([case("a", p50=500, low=499, high=501),
                            case("b", p50=2000, low=1999, high=2001)])
        result = compare.compare(baseline, candidate)
        self.assertEqual(compare.FAIL, result["verdict"])

    def test_no_pass_and_no_fail_rolls_up_to_inconclusive(self):
        baseline = report([case("a", p50=1000, low=900, high=1100)])
        candidate = report([case("a", p50=1010, low=900, high=1100)])
        self.assertEqual(compare.INCONCLUSIVE,
                         compare.compare(baseline, candidate)["verdict"])

    def test_a_changed_matrix_is_inconclusive_even_if_the_shared_cases_improved(self):
        baseline = report([case("a", p50=1000, low=999, high=1001)])
        candidate = report([case("a", p50=500, low=499, high=501),
                            case("b", p50=100, low=99, high=101)])
        result = compare.compare(baseline, candidate)
        self.assertEqual(compare.INCONCLUSIVE, result["verdict"])
        self.assertEqual(["b"], result["unmatched"])


class ExitCodeTest(unittest.TestCase):

    def test_the_exit_codes_tell_the_three_answers_apart(self):
        self.assertEqual(0, compare.EXIT_CODES[compare.PASS])
        self.assertEqual(1, compare.EXIT_CODES[compare.FAIL])
        self.assertEqual(2, compare.EXIT_CODES[compare.INCONCLUSIVE])
        self.assertEqual(3, compare.EXIT_INCOMPARABLE)


if __name__ == "__main__":
    unittest.main(verbosity=1)
