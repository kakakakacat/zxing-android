#!/usr/bin/env python3
"""Tests for ci_compare.py — baseline selection and manifest integrity.

The gates themselves are test_compare.py's subject. What is tested here is the step
before them: that a candidate is judged against the right committed result, and that a
manifest which would select the wrong one is refused rather than used.

Run: python3 -I benchmark/tools/test_ci_compare.py
"""
import json
import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import ci_compare  # noqa: E402
import compare  # noqa: E402

FRAMES = {"hd/hit_qr": "a" * 64}


def case(name="endToEnd[HD_HIT_QR_DEFAULT]", p50=1000, iterations=60):
    return {
        "name": name, "stage": "endToEnd", "resolution": "hd", "variant": "hit_qr",
        "config": "default", "decodes": True,
        "ns_p50": p50, "ns_p90": p50 * 1.2,
        "ns_ci95_low": p50 * 0.99, "ns_ci95_high": p50 * 1.01,
        "iterations": iterations, "alloc_count_p50": 10,
    }


def report(device="DN-ENG41", config="default", backend="java", cases=None, layer="L2"):
    return {
        "layer": layer, "run_id": "run", "commit": "abc", "config": config,
        "dataset_hash": None, "frames": FRAMES, "frames_golden_version": 1,
        "backend": backend,
        "device": {"label": device, "api": 34, "emulator": False},
        "warnings": [],
        "cases": cases if cases is not None else [case()],
    }


def entry(key="DN-ENG41/default/java", file="base.json", device="DN-ENG41",
          config="default", backend="java", **extra):
    out = {"key": key, "file": file, "device": device, "config": config,
           "backend": backend, "note": "a baseline"}
    out.update(extra)
    return out


class ManifestTest(unittest.TestCase):
    """A manifest that points at the wrong file selects the wrong baseline silently."""

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir)

    def write(self, name, payload):
        path = os.path.join(self.dir, name)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)
        return path

    def reasons(self, entries):
        return ci_compare.check_manifest(entries, self.dir)

    def test_a_consistent_manifest_has_no_reasons(self):
        self.write("base.json", report())
        self.assertEqual([], self.reasons([entry()]))

    def test_a_missing_file_is_named(self):
        reasons = self.reasons([entry()])
        self.assertEqual(1, len(reasons))
        self.assertIn("no such result file", reasons[0])

    def test_a_file_from_another_device_is_refused(self):
        self.write("base.json", report(device="mix5"))
        self.assertIn("was taken on", " ".join(self.reasons([entry()])))

    def test_a_file_with_another_config_is_refused(self):
        self.write("base.json", report(config="full"))
        self.assertIn("has config", " ".join(self.reasons([entry()])))

    def test_a_file_from_another_backend_is_refused(self):
        self.write("base.json", report(backend="native"))
        self.assertIn("records backend", " ".join(self.reasons([entry()])))

    def test_another_layer_is_refused(self):
        self.write("base.json", report(layer="L3"))
        self.assertIn("is layer", " ".join(self.reasons([entry()])))

    def test_a_key_that_disagrees_with_its_own_fields_is_refused(self):
        self.write("base.json", report())
        reasons = self.reasons([entry(key="DN-ENG41/default/native")])
        self.assertIn("does not match its own fields", " ".join(reasons))

    def test_duplicate_keys_are_refused(self):
        self.write("base.json", report())
        self.assertIn("duplicate key", " ".join(self.reasons([entry(), entry()])))

    def test_a_missing_note_is_refused(self):
        """Every baseline says why it is one. An unexplained entry is a guess later."""
        self.write("base.json", report())
        broken = entry()
        del broken["note"]
        self.assertIn("missing note", " ".join(self.reasons([broken])))

    def test_a_file_without_a_recorded_backend_needs_an_explicit_assertion(self):
        """Null must not match everything. Section 13.3."""
        payload = report()
        payload["backend"] = None
        self.write("base.json", payload)
        self.assertIn("records no backend", " ".join(self.reasons([entry()])))
        self.assertEqual([], self.reasons([entry(backend_asserted=True,
                                                 backend_note="one decoder existed")]))

    def test_asserting_a_backend_the_file_records_is_refused(self):
        """An assertion over a recorded value is a place for the two to drift apart."""
        self.write("base.json", report())
        reasons = self.reasons([entry(backend_asserted=True, backend_note="why")])
        self.assertIn("must not assert one", " ".join(reasons))

    def test_the_committed_manifest_is_usable(self):
        """The real one, not a synthetic: it is what CI reads."""
        entries = ci_compare.load_manifest(ci_compare.DEFAULT_MANIFEST)
        self.assertTrue(entries)
        reasons = ci_compare.check_manifest(
            entries, os.path.dirname(ci_compare.DEFAULT_MANIFEST))
        self.assertEqual([], reasons)


class SelectTest(unittest.TestCase):
    """Device, config and backend. A difference in any of them is a different question."""

    def test_the_matching_entry_is_selected(self):
        chosen, why = ci_compare.select([entry()], report())
        self.assertIsNone(why)
        self.assertEqual("DN-ENG41/default/java", chosen["key"])

    def test_a_different_backend_does_not_match(self):
        """Judging a native run against a Java baseline is section 10.6b's A/B wearing a
        regression check's clothes."""
        chosen, why = ci_compare.select([entry()], report(backend="native"))
        self.assertIsNone(chosen)
        self.assertIn("DN-ENG41/default/native", why)
        self.assertIn("DN-ENG41/default/java", why)

    def test_a_different_device_does_not_match(self):
        chosen, why = ci_compare.select([entry()], report(device="mix5"))
        self.assertIsNone(chosen)
        self.assertIn("mix5/default/java", why)

    def test_an_ambiguous_manifest_selects_nothing(self):
        chosen, why = ci_compare.select([entry(), entry()], report())
        self.assertIsNone(chosen)
        self.assertIn("2 baselines", why)


class JudgeTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir)
        self.candidates = os.path.join(self.dir, "candidates")
        os.makedirs(self.candidates)

    def write(self, directory, name, payload):
        path = os.path.join(directory, name)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle)
        return path

    def test_a_candidate_with_no_baseline_is_unjudged_not_passed(self):
        path = self.write(self.candidates, "cand.json", report(backend="native"))
        judgement = ci_compare.judge([entry()], self.dir, path)
        self.assertEqual("unjudged", judgement["verdict"])
        self.assertIsNone(judgement["result"])

    def test_the_same_run_twice_is_inconclusive(self):
        self.write(self.dir, "base.json", report())
        path = self.write(self.candidates, "cand.json", report())
        judgement = ci_compare.judge([entry()], self.dir, path)
        self.assertEqual(compare.INCONCLUSIVE, judgement["verdict"])

    def test_a_slower_candidate_fails(self):
        self.write(self.dir, "base.json", report())
        path = self.write(self.candidates, "cand.json",
                          report(cases=[case(p50=2000)]))
        judgement = ci_compare.judge([entry()], self.dir, path)
        self.assertEqual(compare.FAIL, judgement["verdict"])

    def test_result_sets_compare_refuses_come_back_unjudged(self):
        """compare.py's guard failures are not a verdict either."""
        self.write(self.dir, "base.json", report())
        candidate = report()
        candidate["frames"] = {"hd/hit_qr": "f" * 64}
        path = self.write(self.candidates, "cand.json", candidate)
        judgement = ci_compare.judge([entry()], self.dir, path)
        self.assertEqual("unjudged", judgement["verdict"])
        self.assertIn("frames differs", judgement["reason"])


class ExitCodeTest(unittest.TestCase):
    """The exit code is the whole interface to CI, so it is tested as one."""

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir)
        self.candidates = os.path.join(self.dir, "candidates")
        os.makedirs(self.candidates)
        self.out = os.path.join(self.dir, "out")
        with open(os.path.join(self.dir, "base.json"), "w", encoding="utf-8") as handle:
            json.dump(report(), handle)
        self.manifest = os.path.join(self.dir, "l2-baselines.json")
        with open(self.manifest, "w", encoding="utf-8") as handle:
            json.dump({"baselines": [entry()]}, handle)

    def run_main(self):
        return ci_compare.main(["--manifest", self.manifest,
                                "--candidates", self.candidates,
                                "--out-dir", self.out])

    def candidate(self, payload, name="cand.json"):
        with open(os.path.join(self.candidates, name), "w", encoding="utf-8") as handle:
            json.dump(payload, handle)

    def test_no_candidate_is_not_a_failure(self):
        """An unrelated pull request must not be blocked by a benchmark it never touched.
        The summary says plainly that nothing was compared."""
        self.assertEqual(0, self.run_main())

    def test_fail_exits_one(self):
        self.candidate(report(cases=[case(p50=2000)]))
        self.assertEqual(1, self.run_main())

    def test_inconclusive_exits_two(self):
        self.candidate(report())
        self.assertEqual(2, self.run_main())

    def test_unjudged_exits_three(self):
        self.candidate(report(backend="native"))
        self.assertEqual(3, self.run_main())

    def test_the_worst_candidate_decides(self):
        self.candidate(report(), "a.json")
        self.candidate(report(cases=[case(p50=2000)]), "b.json")
        self.assertEqual(1, self.run_main())

    def test_unjudged_outranks_fail(self):
        """A run nobody could judge is the more expensive thing to leave unread."""
        self.candidate(report(cases=[case(p50=2000)]), "a.json")
        self.candidate(report(backend="native"), "b.json")
        self.assertEqual(3, self.run_main())

    def test_a_broken_manifest_stops_everything(self):
        with open(self.manifest, "w", encoding="utf-8") as handle:
            json.dump({"baselines": [entry(file="gone.json")]}, handle)
        self.candidate(report())
        self.assertEqual(ci_compare.EXIT_MANIFEST_BROKEN, self.run_main())

    def test_check_validates_without_judging(self):
        self.candidate(report(cases=[case(p50=2000)]))
        self.assertEqual(0, ci_compare.main(["--manifest", self.manifest, "--check"]))


class SummaryTest(unittest.TestCase):

    def test_an_empty_run_says_it_is_not_a_pass(self):
        summary = ci_compare.summarize([])
        self.assertIn("not a pass", summary)
        self.assertIn("candidates", summary)

    def test_an_asserted_backend_is_stated_in_the_summary(self):
        judgement = {
            "candidate": "cand.json", "verdict": compare.INCONCLUSIVE, "reason": None,
            "baseline": entry(backend_asserted=True, backend_note="one decoder existed"),
            "result": {"comparable": True, "verdict": compare.INCONCLUSIVE, "cases": [],
                       "read_through_warnings": [], "waived_warnings": []},
        }
        summary = ci_compare.summarize([judgement])
        self.assertIn("asserted by the manifest", summary)
        self.assertIn("one decoder existed", summary)


if __name__ == "__main__":
    unittest.main(verbosity=2)
