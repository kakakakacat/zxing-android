#!/usr/bin/env python3
"""Turn a Macrobenchmark result file into an L3 result set.

L3 asks how long it takes to get from a cold launch to a camera that is ready, and from
there to a frame and to a scan — docs/maint/BENCHMARKS.md section 11. Macrobenchmark
writes its own JSON; this converts it into the common envelope of section 8, which is what
benchmark/tools/compare.py reads and what gets committed under docs/maint/reports/.

Nothing here invents a number. Every statistic is computed from the per-iteration runs
Macrobenchmark recorded. Where a span never occurred, the case is emitted with null
timings and `occurrences: 0` rather than a zero: section 8's rule is that a measurement
that is absent is null, never zero, and "no barcode was in front of the lens" and "the
scan took no time" are not the same claim.

What this refuses to do is the point of it:

  * **A run whose trace disagrees with the backend it claims is an error, not a warning.**
    The camera backend is chosen by a preference written outside the benchmark, and a
    preference that did not take looks exactly like two backends performing identically.
    ScannerView emits `zxing.backend.<name>` for whichever backend it really used, so the
    result can be checked against itself. This happened on the first working run.
  * An emulator result is refused outright. L3 measures a camera.

Usage:
  python3 -I benchmark/tools/l3_report.py \
      --input benchmark/l3/build/outputs/l3/zxing-l3/*-benchmarkData.json \
      --runner-args benchmark/l3/build/outputs/l3/l3-runner-args.json \
      --commit 0e477f04f --run-id camerax-DN-ENG41-20261102 \
      --device-label DN-ENG41 \
      --out docs/maint/reports/l3-camerax-DN-ENG41-20261102.json
"""
import argparse
import json
import math
import random
import sys

# Section 7: a fixed seed, so two people converting the same run get the same interval.
BOOTSTRAP_RESAMPLES = 10000
BOOTSTRAP_SEED = 20261102

# The metric each L3 case is built from, and what question it answers. The order is the
# order of the timeline, which is how the table reads in a report.
CASES = [
    ("startup_to_initial_display", "timeToInitialDisplayMs", False,
     "Cold launch to the window's first frame. NOT camera pixels: the first frame holds "
     "the viewfinder and an empty SurfaceView."),
    ("camera_open_to_ready", "zxing.camera.openMs", True,
     "ScannerView asking its backend to open, to the backend reporting ready."),
    ("ready_to_first_frame", "zxing.first.frameMs", True,
     "Camera ready to the first frame reaching the decode thread."),
    ("ready_to_first_scan", "zxing.first.scanMs", True,
     "Camera ready to the first successful decode. Scene dependent; absent unless a "
     "barcode was in front of the lens."),
]

# Derived, per iteration, from two spans that are sequential by construction:
# camera.open ends exactly where first.frame begins, at onCameraReady.
#
# It exists because it is the only camera figure here that means the same thing on both
# backends. "Ready" does not: Camera1 reports ready once startPreview has returned,
# CameraX once its use cases are bound, and those are different moments in a camera's
# life. "The scanner asked for a camera" and "a frame reached the decoder" are the same
# two events either way, so the interval between them is comparable and the two halves
# are not.
#
# Summed per iteration and then aggregated -- never by adding two medians, which would be
# a statistic of nothing.
DERIVED_OPEN_TO_FIRST_FRAME = (
    "open_to_first_frame",
    ("zxing.camera.open", "zxing.first.frame"),
    "The scanner asking for a camera to the first frame reaching the decoder. Derived per "
    "iteration from the two spans above, and the only camera interval here whose "
    "definition is identical on both backends.")

BACKEND_SPANS = {
    "camera1": "zxing.backend.camera1",
    "camerax": "zxing.backend.camerax",
}


def percentile(values, fraction):
    """Nearest-rank percentile: the smallest observed sample at or above the rank.

    No interpolation, for the same reason L2 does not interpolate: these are launches that
    actually happened, and a p90 between two of them is a number no launch ever took.
    """
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def bootstrap_median_ci(values, resamples=BOOTSTRAP_RESAMPLES, seed=BOOTSTRAP_SEED):
    """95% confidence interval on the median, by resampling with replacement."""
    if len(values) < 2:
        return (None, None)
    rng = random.Random(seed)
    n = len(values)
    medians = []
    for _ in range(resamples):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        medians.append(percentile(sample, 0.50))
    medians.sort()
    return (medians[int(0.025 * (len(medians) - 1))],
            medians[int(0.975 * (len(medians) - 1))])


def metric_runs(benchmark, metric):
    """Per-iteration values for one metric, or [] when the metric is not present.

    Macrobenchmark names a TraceSectionMetric's duration `<section>SumMs` and its count
    `<section>Count`. The count is what tells an absent span from a zero-length one.
    """
    entry = (benchmark.get("metrics") or {}).get(metric) or {}
    runs = entry.get("runs")
    if isinstance(runs, list) and runs:
        return [float(value) for value in runs]
    return []


def trace_metric(benchmark, section):
    return metric_runs(benchmark, section + "SumMs"), metric_runs(benchmark, section + "Count")


def convert_case(benchmark, name, metric, is_trace_section, question):
    if is_trace_section:
        section = metric[:-len("Ms")]
        values, counts = trace_metric(benchmark, section)
        occurrences = sum(1 for c in counts if c > 0)
        # A span that never fired sums to 0.0 in every iteration. Reporting that as a
        # measured zero would be a fabricated number.
        values = [v for v, c in zip(values, counts) if c > 0]
    else:
        values = metric_runs(benchmark, metric)
        occurrences = len(values)

    if not values:
        return {
            "name": name, "stage": name, "question": question,
            "ms_p50": None, "ms_p90": None, "ms_ci95_low": None, "ms_ci95_high": None,
            "ms_min": None, "ms_max": None,
            "iterations": 0, "occurrences": occurrences,
        }

    low, high = bootstrap_median_ci(values)
    return {
        "name": name, "stage": name, "question": question,
        "ms_p50": percentile(values, 0.50),
        "ms_p90": percentile(values, 0.90),
        "ms_ci95_low": low, "ms_ci95_high": high,
        "ms_min": min(values), "ms_max": max(values),
        "iterations": len(values), "occurrences": occurrences,
    }


def convert_derived_sum(benchmark, name, sections, question):
    """One case from the per-iteration sum of several sequential spans.

    An iteration counts only if every span occurred in it. A partial sum would be a
    shorter interval reported as if it were the whole one.
    """
    per_section = []
    for section in sections:
        values, counts = trace_metric(benchmark, section)
        per_section.append(list(zip(values, counts)))
    if not per_section or not all(per_section):
        return {
            "name": name, "stage": name, "question": question, "derived_from": list(sections),
            "ms_p50": None, "ms_p90": None, "ms_ci95_low": None, "ms_ci95_high": None,
            "ms_min": None, "ms_max": None, "iterations": 0, "occurrences": 0,
        }
    totals = []
    for iteration in zip(*per_section):
        if all(count > 0 for _, count in iteration):
            totals.append(sum(value for value, _ in iteration))

    if not totals:
        return {
            "name": name, "stage": name, "question": question, "derived_from": list(sections),
            "ms_p50": None, "ms_p90": None, "ms_ci95_low": None, "ms_ci95_high": None,
            "ms_min": None, "ms_max": None, "iterations": 0, "occurrences": 0,
        }
    low, high = bootstrap_median_ci(totals)
    return {
        "name": name, "stage": name, "question": question, "derived_from": list(sections),
        "ms_p50": percentile(totals, 0.50), "ms_p90": percentile(totals, 0.90),
        "ms_ci95_low": low, "ms_ci95_high": high,
        "ms_min": min(totals), "ms_max": max(totals),
        "iterations": len(totals), "occurrences": len(totals),
    }


def device_from_context(context, label):
    build = context.get("build") or {}
    version = build.get("version") or {}
    fingerprint = build.get("fingerprint") or ""
    emulator = any(marker in fingerprint.lower()
                   for marker in ("generic", "emulator", "sdk_gphone", "ranchu"))
    return {
        "label": label or build.get("model") or "unknown",
        "model": build.get("model"),
        "brand": build.get("brand"),
        "device": build.get("device"),
        "api": version.get("sdk"),
        "fingerprint": fingerprint,
        "emulator": emulator,
        "cpu_locked": context.get("cpuLocked"),
        "cpu_core_count": context.get("cpuCoreCount"),
        "sustained_performance_mode": context.get("sustainedPerformanceModeEnabled"),
    }


def observed_backend(benchmark):
    """Which backend the trace says ran, or None when the trace does not say.

    None is not the same as a mismatch: a device below API 29 emits no spans at all, and
    ScannerTrace says so.
    """
    seen = [name for name, section in BACKEND_SPANS.items()
            if any(c > 0 for c in metric_runs(benchmark, section + "Count"))]
    if len(seen) == 1:
        return seen[0]
    if len(seen) > 1:
        return "+".join(sorted(seen))
    return None


def collect_warnings(context, device, cases, claimed_backend, seen_backend):
    """Conditions section 11 turns into `inconclusive`, recorded rather than suppressed."""
    warnings = []
    if context.get("cpuLocked") is False:
        warnings.append("UNLOCKED_CLOCKS: CPU clocks were not locked")
    if context.get("sustainedPerformanceModeEnabled") is False:
        warnings.append("NO_SUSTAINED_PERFORMANCE_MODE: sustained performance mode is off")
    if seen_backend is None:
        warnings.append(
            "NO_BACKEND_SPAN: the trace carries no zxing.backend.* span, so which camera "
            "backend ran cannot be confirmed from the result. ScannerTrace needs API 29.")
    scan = next((c for c in cases if c["name"] == "ready_to_first_scan"), None)
    if scan is not None and scan["iterations"] == 0:
        warnings.append(
            "NO_SCENE: nothing decoded in any iteration, so ready_to_first_scan is absent "
            "rather than slow. Section 11's scan target needs a barcode in a fixed, "
            "documented scene and this run had none.")
    thin = [c["name"] for c in cases if 0 < c["iterations"] < 10]
    if thin:
        warnings.append("TOO_FEW_ITERATIONS: fewer than 10 measured launches in "
                        + ", ".join(sorted(thin)))
    return warnings


def build_report(macro, run_id, commit, device_label, claimed_backend, runner_args):
    context = macro.get("context") or {}
    device = device_from_context(context, device_label)
    benchmarks = macro.get("benchmarks") or []
    if not benchmarks:
        raise SystemExit("no benchmarks in the input file")
    if len(benchmarks) > 1:
        raise SystemExit("expected one benchmark, found %d" % len(benchmarks))
    benchmark = benchmarks[0]

    if device["emulator"]:
        raise SystemExit(
            "refusing an emulator result: L3 measures a camera, and an emulator's is a "
            "virtual scene. BENCHMARKS.md §6.")

    seen = observed_backend(benchmark)
    if seen is not None and claimed_backend is not None and seen != claimed_backend:
        raise SystemExit(
            "backend mismatch: the run claims %r and the trace says %r. The preference "
            "that selects the backend is written outside the benchmark and did not take. "
            "Fix the run rather than the report." % (claimed_backend, seen))

    cases = [convert_case(benchmark, name, metric, is_trace, question)
             for name, metric, is_trace, question in CASES]
    cases.insert(3, convert_derived_sum(benchmark, *DERIVED_OPEN_TO_FIRST_FRAME))

    return {
        "layer": "L3",
        "run_id": run_id,
        "commit": commit,
        "config": claimed_backend,
        "dataset_hash": None,
        "frames": None,
        "device": device,
        "camera_backend": {"claimed": claimed_backend, "observed_in_trace": seen},
        "runner_args": runner_args,
        "warnings": collect_warnings(context, device, cases, claimed_backend, seen),
        "bootstrap": {"resamples": BOOTSTRAP_RESAMPLES, "seed": BOOTSTRAP_SEED},
        "cases": cases,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", required=True, help="Macrobenchmark benchmarkData.json")
    parser.add_argument("--runner-args", help="l3-runner-args.json written by l3-run.sh")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--device-label")
    parser.add_argument("--backend", choices=sorted(BACKEND_SPANS),
                        help="the backend this run claims; defaults to the runner args")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    with open(args.input, encoding="utf-8") as handle:
        macro = json.load(handle)

    runner_args = {}
    if args.runner_args:
        with open(args.runner_args, encoding="utf-8") as handle:
            runner_args = json.load(handle)

    backend = args.backend or runner_args.get("l3.backend")
    if backend is None:
        raise SystemExit(
            "no backend given and none in the runner args: a result that does not say "
            "which camera stack it measured is not a result.")

    report = build_report(macro, args.run_id, args.commit, args.device_label, backend,
                          runner_args)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, sort_keys=False)
        handle.write("\n")

    measured = sum(1 for c in report["cases"] if c["iterations"] > 0)
    print("wrote %s: %d/%d cases measured, %d warnings"
          % (args.out, measured, len(report["cases"]), len(report["warnings"])))
    for warning in report["warnings"]:
        print("  warning: " + warning)
    return 0


if __name__ == "__main__":
    sys.exit(main())
