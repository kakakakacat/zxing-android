#!/usr/bin/env python3
"""Compare two result sets and return pass / fail / inconclusive.

The gates are docs/maint/BENCHMARKS.md section 10.6. This tool exists to make
`inconclusive` a first-class answer: a tool that cannot tell "no regression" apart from
"could not tell" is worse than no tool, because it lets a noisy run be read as a result.

Exit code: 0 pass, 1 fail, 2 inconclusive, 3 the inputs could not be compared at all.

Usage:
  python3 -I benchmark/tools/compare.py \
      --baseline docs/maint/reports/l2-<commit>-DN-ENG41-default.json \
      --candidate build/l2-candidate.json \
      --out-dir build/benchmark/compare
"""
import argparse
import csv
import json
import os
import sys

PASS = "pass"
FAIL = "fail"
INCONCLUSIVE = "inconclusive"

EXIT_CODES = {PASS: 0, FAIL: 1, INCONCLUSIVE: 2}
EXIT_INCOMPARABLE = 3

# section 10.6: below this, a difference is not resolvable on the reference device.
#
# No longer a placeholder. Measured on DN-ENG41 by running the full matrix twice under
# one protocol with nothing changed between the runs (benchmark/tools/noise_floor.py,
# commit edef9d0a0, 135 cases): everything the two runs disagreed about is noise.
#
# The spread splits cleanly by magnitude, because the cheap stages are a few hundred
# nanoseconds and a handful of nanoseconds there is several percent:
#
#   cases >= 100 us (69):  p50 0.15%  p90 1.27%  p95 1.66%  max 2.51%
#   cases <  100 us (66):  p50 1.25%  p90 3.53%  p95 5.25%  max 8.44%
#
# So one global floor is wrong in both directions: it would hide a real 3% regression in
# decode, and still call noise a result in luminanceSource. Each floor sits above the
# worst its own band was observed to do, with a little margin — from a single pair of
# runs the maximum is a weak estimate of the tail, so the conservative reading is the
# right one. More pairs would sharpen these; they are specific to this device and this
# protocol, and re-measuring is a few minutes of work.
FLOOR_SPLIT_NS = 100_000
EFFECT_FLOOR_LARGE = 0.03
EFFECT_FLOOR_SMALL = 0.10
MIN_ITERATIONS = 50


def effect_floor(median_ns):
    """The smallest difference this device can resolve for a case of this size."""
    if median_ns is None:
        return EFFECT_FLOOR_SMALL
    return EFFECT_FLOOR_LARGE if median_ns >= FLOOR_SPLIT_NS else EFFECT_FLOOR_SMALL

# A warning that makes a timing meaningless rather than merely noisy.
INVALIDATING = ("DEBUGGABLE", "EMULATOR")
# A warning that widens the noise. Strict by default; see --accept-noise-warnings.
NOISE_WARNINGS = ("UNLOCKED_CLOCKS", "NO_SUSTAINED_PERFORMANCE_MODE", "THERMAL_THROTTLE",
                  "THROTTLE_WAIT_DISABLED")

# Fields that must agree before any case is compared at all.
GUARDED = ("layer", "config", "dataset_hash", "frames", "frames_golden_version")


def warning_kinds(report):
    return {w.split(":", 1)[0].strip() for w in report.get("warnings") or []}


def guard(baseline, candidate):
    """What makes these two result sets incomparable, as a list of reasons."""
    reasons = []
    for field in GUARDED:
        if baseline.get(field) != candidate.get(field):
            reasons.append("%s differs: baseline %r, candidate %r"
                           % (field, baseline.get(field), candidate.get(field)))

    base_device = baseline.get("device") or {}
    cand_device = candidate.get("device") or {}
    for field in ("label", "api", "emulator"):
        if base_device.get(field) != cand_device.get(field):
            reasons.append("device.%s differs: baseline %r, candidate %r"
                           % (field, base_device.get(field), cand_device.get(field)))
    return reasons


def case_index(report):
    return {case["name"]: case for case in report.get("cases") or []}


def ratio(baseline_value, candidate_value):
    if not baseline_value:
        return None
    return (candidate_value - baseline_value) / float(baseline_value)


def compare_case(base, cand, blocking_warnings):
    """One case, one verdict, and the reason for it. Never a bare number."""
    delta = ratio(base.get("ns_p50"), cand.get("ns_p50"))
    alloc_delta = ratio(base.get("alloc_count_p50"), cand.get("alloc_count_p50"))
    row = {
        "name": base["name"],
        "stage": base.get("stage"),
        "resolution": base.get("resolution"),
        "variant": base.get("variant"),
        "config": base.get("config"),
        "decodes": base.get("decodes"),
        "baseline_ns_p50": base.get("ns_p50"),
        "candidate_ns_p50": cand.get("ns_p50"),
        "baseline_ns_p90": base.get("ns_p90"),
        "candidate_ns_p90": cand.get("ns_p90"),
        "baseline_ci95": [base.get("ns_ci95_low"), base.get("ns_ci95_high")],
        "candidate_ci95": [cand.get("ns_ci95_low"), cand.get("ns_ci95_high")],
        "delta": delta,
        "alloc_delta": alloc_delta,
        "verdict": INCONCLUSIVE,
        "reason": None,
        "floor": None,
    }

    if blocking_warnings:
        row["reason"] = ("the run carries a warning that invalidates timing: "
                         + ", ".join(sorted(blocking_warnings)))
        return row

    for label, case in (("baseline", base), ("candidate", cand)):
        if (case.get("iterations") or 0) < MIN_ITERATIONS:
            row["reason"] = ("%s has %s measured repeats, fewer than the %d floor"
                             % (label, case.get("iterations"), MIN_ITERATIONS))
            return row

    values = [base.get("ns_p50"), cand.get("ns_p50"),
              base.get("ns_ci95_low"), base.get("ns_ci95_high"),
              cand.get("ns_ci95_low"), cand.get("ns_ci95_high")]
    if any(value is None for value in values):
        row["reason"] = "a median or interval is missing, so nothing can be concluded"
        return row

    floor = effect_floor(base.get("ns_p50"))
    row["floor"] = floor
    if abs(delta) < floor:
        row["reason"] = ("the medians differ by %.1f%%, below the %.0f%% floor measured "
                         "for cases of this size: not resolvable on this device"
                         % (delta * 100, floor * 100))
        return row

    improved = cand["ns_ci95_high"] < base["ns_ci95_low"]
    regressed = cand["ns_ci95_low"] > base["ns_ci95_high"]
    if not improved and not regressed:
        row["reason"] = "the 95%% intervals overlap"
        return row

    if regressed and delta >= floor:
        row["verdict"] = FAIL
        row["reason"] = "%.1f%% slower, intervals disjoint" % (delta * 100)
        return row

    if improved and delta <= -floor:
        # A time win bought with allocations is not a win until L4 says the GC cost is
        # absorbed. section 10.6.
        if alloc_delta is not None and alloc_delta > floor:
            row["reason"] = ("%.1f%% faster, but allocations grew %.1f%%: not a win until "
                             "L4 says the GC cost is absorbed"
                             % (delta * 100, alloc_delta * 100))
            return row
        row["verdict"] = PASS
        row["reason"] = "%.1f%% faster, intervals disjoint" % (delta * 100)
        return row

    row["reason"] = "the interval and the median disagree about the direction"
    return row


def roll_up(rows):
    """A run is fail if any case fails, pass if at least one passes and none fails."""
    if any(row["verdict"] == FAIL for row in rows):
        return FAIL
    if rows and any(row["verdict"] == PASS for row in rows):
        return PASS
    return INCONCLUSIVE


def compare(baseline, candidate, accept_noise_warnings=False):
    reasons = guard(baseline, candidate)
    if reasons:
        return {
            "verdict": INCONCLUSIVE,
            "comparable": False,
            "guard_failures": reasons,
            "cases": [],
            "unmatched": [],
        }

    blocking = set()
    waived = set()
    for report in (baseline, candidate):
        for kind in warning_kinds(report):
            if kind in INVALIDATING:
                blocking.add(kind)
            elif kind in NOISE_WARNINGS:
                if accept_noise_warnings:
                    waived.add(kind)
                else:
                    blocking.add(kind)

    base_cases = case_index(baseline)
    cand_cases = case_index(candidate)
    shared = [name for name in base_cases if name in cand_cases]
    unmatched = sorted(set(base_cases) ^ set(cand_cases))

    rows = [compare_case(base_cases[name], cand_cases[name], blocking)
            for name in sorted(shared)]

    result = {
        "verdict": roll_up(rows),
        "comparable": True,
        "guard_failures": [],
        "baseline": {"run_id": baseline.get("run_id"), "commit": baseline.get("commit")},
        "candidate": {"run_id": candidate.get("run_id"), "commit": candidate.get("commit")},
        "device": (baseline.get("device") or {}).get("label"),
        "config": baseline.get("config"),
        "effect_floor": {"split_ns": FLOOR_SPLIT_NS,
                         "at_or_above_split": EFFECT_FLOOR_LARGE,
                         "below_split": EFFECT_FLOOR_SMALL},
        "blocking_warnings": sorted(blocking),
        "waived_warnings": sorted(waived),
        "cases": rows,
        "unmatched": unmatched,
    }
    if unmatched:
        # Cases present on one side only are not a verdict, they are a changed matrix.
        result["verdict"] = INCONCLUSIVE
    return result


def write_csv(result, path):
    columns = ["name", "stage", "resolution", "variant", "config", "decodes",
               "baseline_ns_p50", "candidate_ns_p50", "baseline_ns_p90",
               "candidate_ns_p90", "delta", "alloc_delta", "verdict", "reason"]
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, extrasaction="ignore")
        writer.writeheader()
        for row in result["cases"]:
            writer.writerow(row)


def percent(value):
    return "—" if value is None else "%+.1f%%" % (value * 100)


def nanos(value):
    if value is None:
        return "—"
    if value >= 1_000_000:
        return "%.2f ms" % (value / 1_000_000.0)
    if value >= 1_000:
        return "%.1f µs" % (value / 1_000.0)
    return "%.0f ns" % value


def write_markdown(result, path):
    lines = ["# L2 comparison", ""]
    if not result["comparable"]:
        lines.append("**inconclusive — the two result sets cannot be compared.**")
        lines.append("")
        for reason in result["guard_failures"]:
            lines.append("- %s" % reason)
        lines.append("")
        lines.append("Section 10.6: both sides must share the dataset or frame hashes, the "
                     "device, the API level and the configuration.")
    else:
        lines.append("**Verdict: %s**" % result["verdict"])
        lines.append("")
        lines.append("| | |")
        lines.append("| --- | --- |")
        lines.append("| Baseline | `%s` (%s) |"
                     % (result["baseline"]["run_id"], result["baseline"]["commit"]))
        lines.append("| Candidate | `%s` (%s) |"
                     % (result["candidate"]["run_id"], result["candidate"]["commit"]))
        lines.append("| Device | %s |" % result["device"])
        lines.append("| Configuration | %s |" % result["config"])
        lines.append("| Effect floor | %.0f%% at or above %d µs, %.0f%% below |"
                     % (result["effect_floor"]["at_or_above_split"] * 100,
                        result["effect_floor"]["split_ns"] // 1000,
                        result["effect_floor"]["below_split"] * 100))
        lines.append("")
        if result["blocking_warnings"]:
            lines.append("Every case is inconclusive: %s."
                         % ", ".join(result["blocking_warnings"]))
            lines.append("")
        if result["waived_warnings"]:
            lines.append("Waived by the operator with `--accept-noise-warnings`: %s. "
                         "The numbers below are read under a rule that was switched off "
                         "on purpose." % ", ".join(result["waived_warnings"]))
            lines.append("")
        lines.append("| Case | Baseline p50 | Candidate p50 | Δ | Alloc Δ | Verdict | Why |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- |")
        for row in result["cases"]:
            lines.append("| `%s` | %s | %s | %s | %s | %s | %s |"
                         % (row["name"], nanos(row["baseline_ns_p50"]),
                            nanos(row["candidate_ns_p50"]), percent(row["delta"]),
                            percent(row["alloc_delta"]), row["verdict"],
                            row["reason"] or ""))
        if result["unmatched"]:
            lines.append("")
            lines.append("Cases on one side only, so the matrix changed: %s"
                         % ", ".join("`%s`" % name for name in result["unmatched"]))
    lines.append("")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--out-dir", default="build/benchmark/compare")
    parser.add_argument(
        "--accept-noise-warnings", action="store_true",
        help="read the numbers although the run carries a noise warning such as unlocked "
             "CPU clocks. Section 10.6 says such a run is inconclusive; this switches that "
             "rule off deliberately, and the waiver is recorded in every output file.")
    args = parser.parse_args(argv)

    with open(args.baseline, encoding="utf-8") as handle:
        baseline = json.load(handle)
    with open(args.candidate, encoding="utf-8") as handle:
        candidate = json.load(handle)

    result = compare(baseline, candidate, args.accept_noise_warnings)

    os.makedirs(args.out_dir, exist_ok=True)
    with open(os.path.join(args.out_dir, "verdict.json"), "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=1)
        handle.write("\n")
    write_csv(result, os.path.join(args.out_dir, "cases.csv"))
    write_markdown(result, os.path.join(args.out_dir, "report.md"))

    print("verdict: %s" % result["verdict"])
    for reason in result["guard_failures"]:
        print("  not comparable: %s" % reason)
    for row in result["cases"]:
        if row["verdict"] != INCONCLUSIVE:
            print("  %s: %s (%s)" % (row["verdict"], row["name"], row["reason"]))
    print("wrote %s" % args.out_dir)

    if not result["comparable"]:
        return EXIT_INCOMPARABLE
    return EXIT_CODES[result["verdict"]]


if __name__ == "__main__":
    sys.exit(main())
