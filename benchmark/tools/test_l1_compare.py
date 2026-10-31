#!/usr/bin/env python3
"""Tests for l1_compare.py — what counts as a regression on the corpus.

Run: python3 -I benchmark/tools/test_l1_compare.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import l1_compare  # noqa: E402


def sample(sample_id, status, expected="QR_CODE", actual="QR_CODE", text=None):
    row = {
        "sample_id": sample_id,
        "expected_format": expected,
        "actual_format": actual,
        "status": status,
    }
    if text is not None:
        row["actual_text"] = text
    return row


def report(samples, backend="zxing-java", dataset_hash="hash", config="DEFAULT",
           sample_count=None, layer="L1"):
    counts = {}
    for row in samples:
        counts[row["status"]] = counts.get(row["status"], 0) + 1
    return {
        "layer": layer,
        "run_id": "run",
        "commit": "abc",
        "backend": backend,
        "config": config,
        "dataset_hash": dataset_hash,
        "sample_count": len(samples) if sample_count is None else sample_count,
        "status_counts": counts,
        "samples": samples,
    }


class GuardTest(unittest.TestCase):

    def test_a_different_dataset_is_not_comparable(self):
        result = l1_compare.compare(
            report([sample("a", "CORRECT")]),
            report([sample("a", "CORRECT")], dataset_hash="other"))
        self.assertEqual("not comparable", result["verdict"])

    def test_a_different_config_is_not_comparable(self):
        result = l1_compare.compare(
            report([sample("a", "CORRECT")]),
            report([sample("a", "CORRECT")], config="QR_ONLY"))
        self.assertEqual("not comparable", result["verdict"])

    def test_different_samples_are_not_comparable(self):
        # A run that stopped early would otherwise look like a decoder that scored
        # everything it was given, which is the shape of a clean pass.
        result = l1_compare.compare(
            report([sample("a", "CORRECT"), sample("b", "CORRECT")]),
            report([sample("a", "CORRECT")], sample_count=2))
        self.assertEqual("not comparable", result["verdict"])

    def test_an_l2_file_is_refused(self):
        result = l1_compare.compare(
            report([sample("a", "CORRECT")], layer="L2"),
            report([sample("a", "CORRECT")], layer="L2"))
        self.assertEqual("not comparable", result["verdict"])


class VerdictTest(unittest.TestCase):

    def test_identical_runs_are_the_same(self):
        rows = [sample("a", "CORRECT"), sample("b", "NOT_FOUND", actual=None)]
        result = l1_compare.compare(report(rows), report(list(rows), backend="zxing-cpp"))
        self.assertEqual(l1_compare.SAME, result["verdict"])
        self.assertEqual([], result["rows"])

    def test_losing_a_read_is_a_regression(self):
        result = l1_compare.compare(
            report([sample("a", "CORRECT")]),
            report([sample("a", "NOT_FOUND", actual=None)], backend="zxing-cpp"))
        self.assertEqual(l1_compare.REGRESSED, result["verdict"])
        self.assertEqual(1, result["counts"]["regression"])

    def test_decoding_a_negative_sample_is_a_regression(self):
        result = l1_compare.compare(
            report([sample("a", "CORRECT_NO_RESULT", expected=None, actual=None)]),
            report([sample("a", "FALSE_POSITIVE", expected=None, actual="EAN_8")],
                   backend="zxing-cpp"))
        self.assertEqual(l1_compare.REGRESSED, result["verdict"])

    def test_gaining_a_read_is_an_improvement_and_not_a_regression(self):
        result = l1_compare.compare(
            report([sample("a", "NOT_FOUND", actual=None)]),
            report([sample("a", "CORRECT")], backend="zxing-cpp"))
        self.assertEqual(l1_compare.SAME, result["verdict"])
        self.assertEqual(1, result["counts"]["improvement"])

    def test_one_wrong_answer_for_another_is_neither(self):
        # Still worth printing: it says the two decoders fail differently, which is a
        # fact about them, but neither of them was right before or after.
        result = l1_compare.compare(
            report([sample("a", "NOT_FOUND", actual=None)]),
            report([sample("a", "INCORRECT", actual="UPC_E", text="17804419")],
                   backend="zxing-cpp"))
        self.assertEqual(l1_compare.SAME, result["verdict"])
        self.assertEqual(1, result["counts"]["changed"])
        self.assertEqual("17804419", result["rows"][0]["candidate_text"])

    def test_the_same_status_from_a_different_format_is_still_reported(self):
        # Two decoders can both be INCORRECT on a sample and be wrong about different
        # things. Collapsing that to "no change" would hide it.
        result = l1_compare.compare(
            report([sample("a", "INCORRECT", actual="UPC_E")]),
            report([sample("a", "INCORRECT", actual="EAN_8")], backend="zxing-cpp"))
        self.assertEqual(l1_compare.SAME, result["verdict"])
        self.assertEqual(1, len(result["rows"]))

    def test_an_error_is_a_regression_from_a_read(self):
        result = l1_compare.compare(
            report([sample("a", "CORRECT")]),
            report([sample("a", "ERROR", actual=None)], backend="zxing-cpp"))
        self.assertEqual(l1_compare.REGRESSED, result["verdict"])


if __name__ == "__main__":
    unittest.main()
