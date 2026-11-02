#!/usr/bin/env python3
"""Tests for l4_report.py.

Run: python3 -I benchmark/tools/test_l4_report.py

A soak reports mostly aggregates, and an aggregate hides its inputs. So the cases here are
about what the report refuses to smooth over: a process that died, a column that was not
sampled, a slope that is really noise, and a battery that never moved because the phone was
on charge.
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import l4_report  # noqa: E402

COLUMNS = ("uptime_ms utime stime majflt threads rss_kb java_heap_kb native_heap_kb "
           "total_pss_kb thermal_status battery_level battery_temp").split()


def write_samples(rows, meta="clk_tck=100 interval=5 slow_every=2", trailer=None):
    handle = tempfile.NamedTemporaryFile("w", suffix=".tsv", delete=False, encoding="utf-8")
    handle.write("# " + meta + "\n")
    handle.write("\t".join(COLUMNS) + "\n")
    for row in rows:
        handle.write("\t".join("" if v is None else str(v) for v in row) + "\n")
    if trailer:
        handle.write(trailer + "\n")
    handle.close()
    return handle.name


def sample(uptime, utime, stime, rss, slow=False, thermal=0, battery=100):
    return [uptime, utime, stime, 10, 40, rss,
            1000 if slow else None, 2000 if slow else None, 3000 if slow else None,
            thermal if slow else None, battery if slow else None, 300 if slow else None]


def steady(n=10, rss=100000, growth=0):
    return [sample(1000 + i * 5000, 100 * i, 10 * i, rss + i * growth, slow=(i % 2 == 0))
            for i in range(n)]


def build(rows, gc=None, scene="miss", **kwargs):
    meta, parsed = l4_report.parse_samples(write_samples(rows))
    return l4_report.build_report(meta, parsed, gc, "run", "abc", "DN-ENG41",
                                  {"api": 34}, "camerax", scene, 5, **kwargs)


class CpuAndMemory(unittest.TestCase):

    def test_cpu_comes_from_the_jiffy_deltas(self):
        # 110 jiffies over 5000 ms at 100 Hz = 1100 ms of CPU = 22% of one core.
        rows = [sample(0, 0, 0, 100000), sample(5000, 100, 10, 100000)]
        report = build(rows + [sample(10000, 200, 20, 100000)])
        cpu = next(c for c in report["cases"] if c["name"] == "cpu_percent_of_one_core")
        self.assertAlmostEqual(cpu["first"], 22.0, places=6)

    def test_a_flat_rss_has_no_growth_warning(self):
        report = build(steady(growth=0))
        self.assertFalse(any(w.startswith("RSS_GROWTH") for w in report["warnings"]))

    def test_a_steady_rise_in_rss_is_reported_with_its_slope(self):
        report = build(steady(n=20, growth=5000))
        rss = next(c for c in report["cases"] if c["name"] == "rss_kb")
        self.assertGreater(rss["slope_kb_per_minute"], 0)
        self.assertTrue(any(w.startswith("RSS_GROWTH") for w in report["warnings"]))

    def test_the_slope_is_reported_next_to_its_residual_spread(self):
        report = build(steady(n=20, growth=5000))
        rss = next(c for c in report["cases"] if c["name"] == "rss_kb")
        self.assertIn("slope_residual_kb", rss)
        self.assertIsNotNone(rss["slope_residual_kb"])

    def test_noise_without_a_trend_does_not_raise_growth(self):
        rows = []
        for i in range(20):
            jitter = 20000 if i % 2 else -20000
            rows.append(sample(1000 + i * 5000, 100 * i, 10 * i, 100000 + jitter,
                               slow=(i % 2 == 0)))
        report = build(rows)
        self.assertFalse(any(w.startswith("RSS_GROWTH") for w in report["warnings"]))

    def test_the_run_is_split_in_half_so_drift_is_visible(self):
        report = build(steady(n=20, growth=5000))
        rss = next(c for c in report["cases"] if c["name"] == "rss_kb")
        self.assertGreater(rss["halves"]["second_half_mean"], rss["halves"]["first_half_mean"])


class AbsenceIsNotZero(unittest.TestCase):

    def test_columns_sampled_only_on_slow_rows_count_only_those_rows(self):
        report = build(steady(n=10))
        java = next(c for c in report["cases"] if c["name"] == "java_heap_kb")
        rss = next(c for c in report["cases"] if c["name"] == "rss_kb")
        self.assertEqual(java["n"], 5)
        self.assertEqual(rss["n"], 10)

    def test_no_logcat_means_gc_was_not_measured(self):
        report = build(steady(), gc=None)
        self.assertIsNone(report["gc"])
        self.assertTrue(any(w.startswith("NO_GC_DATA") for w in report["warnings"]))

    def test_zero_logged_collections_is_reported_as_an_absent_log_not_as_no_gc(self):
        report = build(steady(), gc={"collections": 0, "paused_ms_total": 0.0,
                                     "gc_ms_total": 0.0})
        warning = next(w for w in report["warnings"] if w.startswith("NO_GC_LOGGED"))
        self.assertIn("not the same as", warning)


class Refusals(unittest.TestCase):

    def test_a_soak_whose_process_died_is_refused(self):
        meta, rows = l4_report.parse_samples(
            write_samples(steady(), trailer="# process 123 is gone at sample 4"))
        with self.assertRaises(SystemExit) as raised:
            l4_report.build_report(meta, rows, None, "r", "c", "d", {}, "camerax", "miss", 5)
        self.assertIn("died", str(raised.exception))

    def test_three_samples_are_not_a_soak(self):
        with self.assertRaises(SystemExit):
            build(steady(n=2))


class ConditionsAreRecorded(unittest.TestCase):

    def test_a_throttled_device_is_recorded(self):
        report = build([sample(1000 + i * 5000, 100 * i, 10 * i, 100000,
                               slow=(i % 2 == 0), thermal=3) for i in range(10)])
        self.assertTrue(any(w.startswith("THERMAL_THROTTLE") for w in report["warnings"]))

    def test_a_pinned_battery_means_drain_was_not_measured(self):
        report = build(steady())
        self.assertTrue(any(w.startswith("BATTERY_PINNED") for w in report["warnings"]))

    def test_a_draining_battery_raises_no_such_warning(self):
        report = build([sample(1000 + i * 5000, 100 * i, 10 * i, 100000,
                               slow=(i % 2 == 0), battery=100 - i) for i in range(10)])
        self.assertFalse(any(w.startswith("BATTERY_PINNED") for w in report["warnings"]))

    def test_a_short_soak_says_so(self):
        report = build(steady(n=10))
        self.assertTrue(any(w.startswith("SHORT_SOAK") for w in report["warnings"]))

    def test_a_long_soak_clears_the_floor(self):
        rows = [sample(1000 + i * 5000, 100 * i, 10 * i, 100000, slow=(i % 2 == 0))
                for i in range(80)]
        report = build(rows)
        self.assertFalse(any(w.startswith("SHORT_SOAK") for w in report["warnings"]))

    def test_the_miss_path_is_stated_rather_than_assumed(self):
        self.assertTrue(any(w.startswith("MISS_PATH") for w in build(steady())["warnings"]))
        self.assertFalse(
            any(w.startswith("MISS_PATH") for w in build(steady(), scene="hit")["warnings"]))

    def test_the_envelope_carries_the_layer_and_the_protocol(self):
        report = build(steady())
        self.assertEqual(report["layer"], "L4")
        self.assertEqual(report["protocol"]["clk_tck"], 100)
        self.assertEqual(report["protocol"]["slow_sample_every"], 2)
        self.assertIsNone(report["dataset_hash"])


class GcParsing(unittest.TestCase):

    def test_art_gc_lines_are_counted_with_their_pauses(self):
        handle = tempfile.NamedTemporaryFile("w", suffix=".log", delete=False,
                                             encoding="utf-8")
        handle.write("I/art: GC freed 9310(488KB) AllocSpace objects, 5(3960KB) LOS "
                     "objects, 49% free, 5356KB/10MB, paused 2.073ms,47us total 78.092ms\n")
        handle.write("I/art: GC freed 7329(473KB) AllocSpace objects, 4(3168KB) LOS "
                     "objects, 40% free, 5296KB/8961KB, paused 589us,59us total 109.684ms\n")
        handle.write("I/art: something else entirely\n")
        handle.close()
        gc = l4_report.parse_gc(handle.name)
        self.assertEqual(gc["collections"], 2)
        # 2.073 + 0.047 + 0.589 + 0.059
        self.assertAlmostEqual(gc["paused_ms_total"], 2.768, places=3)
        self.assertAlmostEqual(gc["gc_ms_total"], 187.776, places=3)

    def test_microsecond_and_second_units_are_converted(self):
        self.assertAlmostEqual(l4_report.to_ms("500", "us"), 0.5)
        self.assertAlmostEqual(l4_report.to_ms("1.5", "s"), 1500.0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
