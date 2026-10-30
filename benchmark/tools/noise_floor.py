#!/usr/bin/env python3
"""Measure the run-to-run noise floor from two runs of identical code.

BENCHMARKS.md section 10.6 gates on a minimum effect size, and section 10.7 step 3 says
that floor must be the measured run-to-run spread rather than a guess. This computes it.

The method is the only honest one available: run the same commit, on the same device,
under the same protocol, twice, and look at how much each case moved. Nothing changed
between the runs, so every difference observed is noise. A floor below that is a floor
that will call noise a result.

This is deliberately not the bootstrap interval inside a single run. That measures how
much the 50 repeats of one run disagreed; it cannot see anything that varies between
runs — install, JIT state, page cache, how warm the device was when the run started.
In practice it is several times smaller, and using it as the floor would be the
flattering mistake.

Usage:
  python3 -I benchmark/tools/noise_floor.py --runs a.json b.json [--out floor.json]
"""
import argparse
import json
import math
import sys


def percentile(values, fraction):
    """Nearest-rank, as in l2_report.py. These are observed differences."""
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def guard(reports):
    """Two runs only measure noise if nothing else differs between them."""
    reasons = []
    first = reports[0]
    for other in reports[1:]:
        for field in ("layer", "commit", "config", "frames", "frames_golden_version"):
            if first.get(field) != other.get(field):
                reasons.append("%s differs between the runs: %r vs %r"
                               % (field, first.get(field), other.get(field)))
        first_device = (first.get("device") or {}).get("label")
        other_device = (other.get("device") or {}).get("label")
        if first_device != other_device:
            reasons.append("device differs: %r vs %r" % (first_device, other_device))
        first_args = (first.get("protocol") or {}).get("runner_args")
        other_args = (other.get("protocol") or {}).get("runner_args")
        if first_args != other_args:
            reasons.append("the runs used different instrumentation arguments: %r vs %r"
                           % (first_args, other_args))
    return reasons


def deltas(reports):
    """Relative movement of each case's median, across every pair of runs."""
    indexes = [{case["name"]: case for case in report.get("cases") or []}
               for report in reports]
    shared = set(indexes[0])
    for index in indexes[1:]:
        shared &= set(index)

    rows = []
    for name in sorted(shared):
        medians = [index[name].get("ns_p50") for index in indexes]
        if any(value is None or value == 0 for value in medians):
            continue
        low, high = min(medians), max(medians)
        rows.append({
            "name": name,
            "stage": indexes[0][name].get("stage"),
            "medians": medians,
            # Spread relative to the smaller value: the worst a comparison could be misled.
            "spread": (high - low) / float(low),
        })
    missing = sorted(set().union(*[set(index) for index in indexes]) - shared)
    return rows, missing


def summarise(rows):
    spreads = [row["spread"] for row in rows]
    return {
        "cases": len(rows),
        "spread_p50": percentile(spreads, 0.50),
        "spread_p90": percentile(spreads, 0.90),
        "spread_p95": percentile(spreads, 0.95),
        "spread_max": max(spreads) if spreads else None,
        # The floor has to sit above what identical code was observed to do. p95 is the
        # defensible line: a couple of outlying cases should not set the gate for all of
        # them, but nor should the median.
        "suggested_floor": None if not spreads else round_up(percentile(spreads, 0.95)),
        "worst_cases": sorted(rows, key=lambda r: -r["spread"])[:5],
    }


def round_up(value):
    """To the next whole percent. A floor with decimals pretends to a precision it has."""
    return math.ceil(value * 100) / 100.0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", nargs="+", required=True,
                        help="two or more result files of the same commit and protocol")
    parser.add_argument("--out", default=None)
    args = parser.parse_args(argv)

    if len(args.runs) < 2:
        parser.error("a noise floor needs at least two runs")

    reports = []
    for path in args.runs:
        with open(path, encoding="utf-8") as handle:
            reports.append(json.load(handle))

    reasons = guard(reports)
    if reasons:
        print("these runs do not measure noise; something else differs:")
        for reason in reasons:
            print("  %s" % reason)
        return 3

    rows, missing = deltas(reports)
    if not rows:
        print("no shared cases with usable medians")
        return 3

    summary = summarise(rows)
    summary["runs"] = [report.get("run_id") for report in reports]
    summary["commit"] = reports[0].get("commit")
    summary["device"] = (reports[0].get("device") or {}).get("label")
    summary["protocol"] = (reports[0].get("protocol") or {}).get("runner_args")
    summary["unmatched_cases"] = missing

    print("run-to-run spread over %d shared cases, %s, commit %s"
          % (summary["cases"], summary["device"], summary["commit"]))
    for key in ("spread_p50", "spread_p90", "spread_p95", "spread_max"):
        print("  %-12s %6.2f%%" % (key, summary[key] * 100))
    print("  suggested floor for §10.6: %.0f%%" % (summary["suggested_floor"] * 100))
    print("  worst cases:")
    for row in summary["worst_cases"]:
        print("    %-36s %6.2f%%  %s" % (row["name"], row["spread"] * 100,
                                         " vs ".join("%.0f" % m for m in row["medians"])))
    if missing:
        print("  cases not present in every run: %s" % ", ".join(missing))

    if args.out:
        with open(args.out, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=1)
            handle.write("\n")
        print("wrote %s" % args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
