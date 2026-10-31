#!/usr/bin/env python3
"""Compare two L1 corpus runs sample by sample.

L1 is correctness, not speed: both runs scored the same 1168 images with the same rules
(zxingbench.corpus.L1Classifier), so a difference between them is a decoder behaving
differently, never noise. There is no effect floor here and no `inconclusive` -- that
answer belongs to L2, where a measurement is involved.

What it answers: which samples one decoder reads and the other does not, and what the
one that is wrong said instead.

Exit code: 0 no regression, 1 at least one regression, 3 the inputs are not comparable.

Usage:
  python3 -I benchmark/tools/l1_compare.py \
      --baseline docs/maint/reports/l1-<commit>-host-java.json \
      --candidate docs/maint/reports/l1-<commit>-DN-ENG41-native.json \
      --out-dir build/benchmark/l1-compare
"""
import argparse
import csv
import json
import os
import sys

SAME = "same"
REGRESSED = "regressed"
EXIT_INCOMPARABLE = 3

STATUSES = (
    "CORRECT",
    "INCORRECT",
    "NOT_FOUND",
    "CORRECT_NO_RESULT",
    "FALSE_POSITIVE",
    "ERROR",
)

# The two statuses that mean the decoder gave the right answer. Leaving either of them is
# a regression; arriving at either is an improvement. Everything else is a change of how
# it is wrong, which is worth printing and is neither.
GOOD = ("CORRECT", "CORRECT_NO_RESULT")


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def guard(baseline, candidate):
    """Reasons these two files are not about the same thing."""
    reasons = []
    for field in ("layer", "dataset_hash", "config", "sample_count"):
        base, cand = baseline.get(field), candidate.get(field)
        if base != cand:
            reasons.append("%s differs: %s vs %s" % (field, base, cand))
    if baseline.get("layer") not in (None, "L1"):
        reasons.append("baseline is not an L1 file: %s" % baseline.get("layer"))
    # Same backend on both sides compares a decoder with itself. That is a legitimate
    # thing to do -- it is how the device harness was checked against the host one -- so
    # it is reported, not refused.
    return reasons


def index(report):
    return {row["sample_id"]: row for row in report.get("samples", [])}


def compare(baseline, candidate):
    reasons = guard(baseline, candidate)
    if reasons:
        return {"verdict": "not comparable", "reasons": reasons, "rows": []}

    base_rows, cand_rows = index(baseline), index(candidate)
    missing = sorted(set(base_rows) - set(cand_rows))
    extra = sorted(set(cand_rows) - set(base_rows))
    if missing or extra:
        return {
            "verdict": "not comparable",
            "reasons": ["the two runs scored different samples: %d only in the baseline, "
                        "%d only in the candidate" % (len(missing), len(extra))],
            "rows": [],
        }

    rows = []
    for sample_id in sorted(base_rows):
        base, cand = base_rows[sample_id], cand_rows[sample_id]
        if base["status"] == cand["status"] and base.get("actual_format") == cand.get("actual_format"):
            continue
        if base["status"] in GOOD and cand["status"] not in GOOD:
            kind = "regression"
        elif base["status"] not in GOOD and cand["status"] in GOOD:
            kind = "improvement"
        else:
            kind = "changed"
        rows.append({
            "sample_id": sample_id,
            "kind": kind,
            "expected_format": base.get("expected_format"),
            "baseline_status": base["status"],
            "candidate_status": cand["status"],
            "baseline_format": base.get("actual_format"),
            "candidate_format": cand.get("actual_format"),
            "candidate_text": cand.get("actual_text"),
        })

    counts = {kind: sum(1 for row in rows if row["kind"] == kind)
              for kind in ("regression", "improvement", "changed")}
    return {
        "verdict": REGRESSED if counts["regression"] else SAME,
        "reasons": [],
        "counts": counts,
        "status_counts": {
            "baseline": {s: baseline.get("status_counts", {}).get(s, 0) for s in STATUSES},
            "candidate": {s: candidate.get("status_counts", {}).get(s, 0) for s in STATUSES},
        },
        "baseline": describe(baseline),
        "candidate": describe(candidate),
        "rows": rows,
    }


def describe(report):
    """The run, in the terms section 2 requires a result to carry."""
    return {
        "run_id": report.get("run_id"),
        "commit": report.get("commit"),
        "runner": report.get("runner"),
        "backend": report.get("backend"),
        "backend_build": report.get("backend_build"),
        "device": report.get("device") or report.get("os"),
        "config": report.get("config"),
        "dataset_hash": report.get("dataset_hash"),
    }


def write_csv(result, path):
    fields = ("sample_id", "kind", "expected_format", "baseline_status",
              "candidate_status", "baseline_format", "candidate_format", "candidate_text")
    with open(path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        for row in result["rows"]:
            writer.writerow({field: row.get(field) for field in fields})


def write_markdown(result, path):
    lines = ["# L1 comparison", ""]
    lines.append("verdict: **%s**" % result["verdict"])
    lines.append("")
    if result["verdict"] == "not comparable":
        for reason in result["reasons"]:
            lines.append("- %s" % reason)
        _write(path, lines)
        return

    lines.append("| | baseline | candidate |")
    lines.append("| --- | --- | --- |")
    for field in ("run_id", "commit", "runner", "backend", "backend_build", "device"):
        lines.append("| %s | %s | %s |" % (field, result["baseline"].get(field),
                                           result["candidate"].get(field)))
    lines.append("")
    lines.append("| Status | baseline | candidate | delta |")
    lines.append("| --- | --- | --- | --- |")
    for status in STATUSES:
        base = result["status_counts"]["baseline"][status]
        cand = result["status_counts"]["candidate"][status]
        lines.append("| %s | %d | %d | %+d |" % (status, base, cand, cand - base))
    lines.append("")
    lines.append("%d regressions, %d improvements, %d changed another way."
                 % (result["counts"]["regression"], result["counts"]["improvement"],
                    result["counts"]["changed"]))
    lines.append("")
    if result["rows"]:
        lines.append("| Sample | kind | expected | baseline | candidate |")
        lines.append("| --- | --- | --- | --- | --- |")
        for row in result["rows"]:
            lines.append("| `%s` | %s | %s | %s (%s) | %s (%s) |" % (
                row["sample_id"], row["kind"], row["expected_format"],
                row["baseline_status"], row["baseline_format"],
                row["candidate_status"], row["candidate_format"]))
    _write(path, lines)


def _write(path, lines):
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--out-dir", default="build/benchmark/l1-compare")
    args = parser.parse_args(argv)

    result = compare(load(args.baseline), load(args.candidate))
    os.makedirs(args.out_dir, exist_ok=True)
    with open(os.path.join(args.out_dir, "l1-comparison.json"), "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2, sort_keys=True)
        handle.write("\n")
    write_csv(result, os.path.join(args.out_dir, "l1-comparison.csv"))
    write_markdown(result, os.path.join(args.out_dir, "l1-comparison.md"))

    print("verdict: %s" % result["verdict"])
    if result["verdict"] == "not comparable":
        for reason in result["reasons"]:
            print("  %s" % reason)
        return EXIT_INCOMPARABLE
    print("  %d regressions, %d improvements, %d changed another way"
          % (result["counts"]["regression"], result["counts"]["improvement"],
             result["counts"]["changed"]))
    print("wrote %s" % args.out_dir)
    return 1 if result["verdict"] == REGRESSED else 0


if __name__ == "__main__":
    sys.exit(main())
