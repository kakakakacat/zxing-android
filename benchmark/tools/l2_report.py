#!/usr/bin/env python3
"""Turn an AndroidX Microbenchmark result file into an L2 result set.

Microbenchmark writes its own JSON, which is a fine record of a run and a poor basis for
a comparison: it carries per-repeat samples but no interval, and it knows nothing about
which frames were measured. This converts it into the common envelope of
docs/maint/BENCHMARKS.md section 8, which is what benchmark/tools/compare.py reads and
what gets committed under docs/maint/reports/.

Nothing here invents a number. Every statistic is computed from the per-repeat samples
Microbenchmark recorded; where a field cannot be filled from the input, it is null and the
comparison tool treats it as a reason to be inconclusive.

Usage:
  python3 -I benchmark/tools/l2_report.py \
      --input <module>/build/outputs/connected_android_test_additional_output/.../*-benchmarkData.json \
      --goldens benchmark/android/goldens/frames.golden.json \
      --commit 50e6e3860 --run-id b0-DN-ENG41-20261030 \
      --out docs/maint/reports/l2-b0-DN-ENG41-default.json
"""
import argparse
import json
import math
import random
import re
import sys

# section 7: a fixed seed, so two people converting the same run get the same interval.
BOOTSTRAP_RESAMPLES = 10000
BOOTSTRAP_SEED = 20261030

CASE_NAME = re.compile(
    r"^(?P<stage>[A-Za-z0-9]+)"
    r"\[(?P<resolution>VGA|HD|FHD)_"
    r"(?P<variant>HIT_QR|HIT_1D|MISS)_"
    r"(?P<config>DEFAULT|QR_ONLY|ONED_ONLY_UPCA)\]$"
)

# Which (variant, config) pairs decode. Mirrors L2Config#canDecode; a case that is a miss
# costs more than a hit, so the report has to say which it was.
DECODES = {
    ("HIT_QR", "DEFAULT"): True,
    ("HIT_QR", "QR_ONLY"): True,
    ("HIT_QR", "ONED_ONLY_UPCA"): False,
    ("HIT_1D", "DEFAULT"): True,
    ("HIT_1D", "QR_ONLY"): False,
    ("HIT_1D", "ONED_ONLY_UPCA"): True,
    ("MISS", "DEFAULT"): False,
    ("MISS", "QR_ONLY"): False,
    ("MISS", "ONED_ONLY_UPCA"): False,
}


def percentile(values, fraction):
    """Nearest-rank percentile: the smallest observed sample at or above the rank.

    No interpolation. These are measurements that actually happened, and a p90 that lies
    between two of them is a number no frame ever took.
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
    # The same median definition as ns_p50, so the interval brackets the figure it is
    # reported next to rather than a slightly different one.
    medians = []
    for _ in range(resamples):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        medians.append(percentile(sample, 0.50))
    medians.sort()
    low = medians[int(0.025 * (len(medians) - 1))]
    high = medians[int(0.975 * (len(medians) - 1))]
    return (low, high)


def metric_runs(benchmark, metric):
    metrics = benchmark.get("metrics") or {}
    entry = metrics.get(metric) or {}
    runs = entry.get("runs")
    if isinstance(runs, list) and runs:
        return [float(value) for value in runs]
    return []


def convert_case(benchmark):
    name = benchmark.get("name", "")
    match = CASE_NAME.match(name)
    if not match:
        return None

    time_runs = metric_runs(benchmark, "timeNs")
    alloc_runs = metric_runs(benchmark, "allocationCount")

    low, high = bootstrap_median_ci(time_runs)
    variant = match.group("variant")
    config = match.group("config")

    return {
        "name": name,
        "stage": match.group("stage"),
        "resolution": match.group("resolution").lower(),
        "variant": variant.lower(),
        "config": config.lower(),
        "decodes": DECODES.get((variant, config)),
        "ns_p50": percentile(time_runs, 0.50),
        "ns_p90": percentile(time_runs, 0.90),
        "ns_ci95_low": low,
        "ns_ci95_high": high,
        "iterations": len(time_runs),
        "repeat_iterations": benchmark.get("repeatIterations"),
        "warmup_iterations": benchmark.get("warmupIterations"),
        "alloc_count_p50": percentile(alloc_runs, 0.50),
        "thermal_throttle_sleep_seconds": benchmark.get("thermalThrottleSleepSeconds"),
    }


def device_from_context(context, label):
    build = (context.get("build") or {})
    version = (build.get("version") or {})
    fingerprint = build.get("fingerprint") or ""
    model = build.get("model") or "unknown"
    # Microbenchmark does not say "this is an emulator" in so many words.
    emulator = any(
        marker in fingerprint.lower()
        for marker in ("generic", "emulator", "sdk_gphone", "ranchu")
    )
    return {
        "label": label or model,
        "model": model,
        "brand": build.get("brand"),
        "device": build.get("device"),
        "api": version.get("sdk"),
        "fingerprint": fingerprint,
        "emulator": emulator,
        "cpu_locked": context.get("cpuLocked"),
        "cpu_core_count": context.get("cpuCoreCount"),
        "sustained_performance_mode": context.get("sustainedPerformanceModeEnabled"),
    }


def collect_warnings(context, cases, device):
    """Conditions section 10.6 turns into `inconclusive`, recorded rather than suppressed."""
    warnings = []
    if device.get("emulator"):
        warnings.append("EMULATOR: run on an emulator; shape only, never a magnitude")
    if context.get("cpuLocked") is False:
        warnings.append("UNLOCKED_CLOCKS: CPU clocks were not locked")
    if context.get("sustainedPerformanceModeEnabled") is False:
        warnings.append("NO_SUSTAINED_PERFORMANCE_MODE: sustained performance mode is off")
    throttled = [c["name"] for c in cases
                 if (c.get("thermal_throttle_sleep_seconds") or 0) > 0]
    if throttled:
        warnings.append("THERMAL_THROTTLE: the harness slept to cool down in "
                        + ", ".join(sorted(throttled)))
    thin = [c["name"] for c in cases if c["iterations"] < 50]
    if thin:
        warnings.append("TOO_FEW_ITERATIONS: fewer than 50 measured repeats in "
                        + ", ".join(sorted(thin)))
    return warnings


def load_goldens(path):
    with open(path, encoding="utf-8") as handle:
        document = json.load(handle)
    frames = {
        "%s/%s" % (frame["resolution"], frame["variant"]): frame["sha256"]
        for frame in document["frames"]
    }
    return document.get("golden_version"), frames


def build_report(microbenchmark, goldens_path, run_id, commit, device_label, config_label):
    context = microbenchmark.get("context") or {}
    cases = []
    skipped = []
    for benchmark in microbenchmark.get("benchmarks") or []:
        case = convert_case(benchmark)
        if case is None:
            skipped.append(benchmark.get("name", "<unnamed>"))
        else:
            cases.append(case)
    cases.sort(key=lambda c: (c["resolution"], c["variant"], c["config"], c["stage"]))

    golden_version, frames = load_goldens(goldens_path)
    device = device_from_context(context, device_label)

    return {
        "layer": "L2",
        "run_id": run_id,
        "commit": commit,
        # Section 10.5: one configuration per result set. "mixed" when a single run covered
        # several, which the comparison tool refuses to compare as a whole.
        "config": config_label or (cases[0]["config"] if len(
            {c["config"] for c in cases}) == 1 else "mixed"),
        # L2 replays generated frames, not the L1 corpus; the frame hashes are the
        # comparability key instead (section 10.4).
        "dataset_hash": None,
        "frames_golden_version": golden_version,
        "frames": frames,
        "device": device,
        "warnings": collect_warnings(context, cases, device),
        "skipped_benchmarks": skipped,
        "bootstrap": {"resamples": BOOTSTRAP_RESAMPLES, "seed": BOOTSTRAP_SEED},
        "cases": cases,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True,
                        help="Microbenchmark *-benchmarkData.json")
    parser.add_argument("--goldens", required=True,
                        help="benchmark/android/goldens/frames.golden.json")
    parser.add_argument("--out", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--device-label", default=None,
                        help="the name this device is known by, e.g. DN-ENG41")
    parser.add_argument("--config-label", default=None)
    args = parser.parse_args(argv)

    with open(args.input, encoding="utf-8") as handle:
        microbenchmark = json.load(handle)

    report = build_report(microbenchmark, args.goldens, args.run_id, args.commit,
                          args.device_label, args.config_label)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=1, sort_keys=False)
        handle.write("\n")

    print("wrote %s: %d cases, %d warnings"
          % (args.out, len(report["cases"]), len(report["warnings"])))
    for warning in report["warnings"]:
        print("  warning: %s" % warning)
    if report["skipped_benchmarks"]:
        print("  skipped (not an L2 case): %s" % ", ".join(report["skipped_benchmarks"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
