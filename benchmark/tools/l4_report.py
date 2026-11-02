#!/usr/bin/env python3
"""Turn an L4 soak into a result set.

L4 asks what sustained scanning costs — docs/maint/BENCHMARKS.md §12. tools/maint/l4-soak.sh
writes a TSV of samples and a logcat; this converts them into the common envelope of §8.

Its job is to answer the three questions a per-frame median cannot:

  * **Does it leak?** RSS is fitted against time by least squares and the slope is reported
    in kB/minute, next to the residual spread so a slope through noise is visible as one.
  * **Does it allocate on a treadmill?** From ART's own GC lines: how many collections, how
    much they freed and how long they paused.
  * **Does a win evaporate when the device heats up?** CPU and thermal status are reported
    as a series, and the run is split in half so the second half can be compared with the
    first without anyone having to plot it.

Nothing here invents a number. Where a column was not sampled it is null, never zero —
§8's rule — and the slow columns are sampled every Nth row on purpose, so most rows have
none.

Usage:
  python3 -I benchmark/tools/l4_report.py \
      --samples benchmark/l4/out/samples-camerax.tsv \
      --logcat benchmark/l4/out/logcat-camerax.log \
      --backend camerax --scene miss \
      --commit 7d032431b --run-id camerax-DN-ENG41-20261102 --device-label DN-ENG41 \
      --out docs/maint/reports/l4-camerax-DN-ENG41-20261102.json
"""
import argparse
import json
import re
import sys

# "GC freed 9310(488KB) ... paused 2.073ms,47us total 78.092ms"
GC_LINE = re.compile(r"GC freed .*?paused ([^ ]+) total ([0-9.]+)(ms|s|us)")
DURATION = re.compile(r"([0-9.]+)(ms|s|us)")


def to_ms(value, unit):
    value = float(value)
    if unit == "s":
        return value * 1000.0
    if unit == "us":
        return value / 1000.0
    return value


def parse_samples(path):
    """Rows, plus the header comment's protocol values."""
    meta = {}
    rows = []
    columns = None
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.rstrip("\n")
            if line.startswith("#"):
                for part in line[1:].split():
                    if "=" in part:
                        key, _, value = part.partition("=")
                        meta[key] = value
                if "is gone at sample" in line:
                    meta["process_died"] = "true"
                continue
            fields = line.split("\t")
            if columns is None:
                columns = fields
                continue
            if len(fields) != len(columns):
                continue
            row = {}
            for name, raw in zip(columns, fields):
                raw = raw.strip()
                if raw == "":
                    row[name] = None
                else:
                    try:
                        row[name] = int(raw)
                    except ValueError:
                        row[name] = raw
            rows.append(row)
    return meta, rows


def linear_slope(xs, ys):
    """Least-squares slope and the residual standard deviation, in y units per x unit.

    Returned together on purpose: a slope with a residual spread far larger than the total
    rise it predicts is noise, and reporting the slope alone would hide that.
    """
    n = len(xs)
    if n < 3:
        return None, None
    mean_x = sum(xs) / n
    mean_y = sum(ys) / n
    denominator = sum((x - mean_x) ** 2 for x in xs)
    if denominator == 0:
        return None, None
    slope = sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys)) / denominator
    intercept = mean_y - slope * mean_x
    residuals = [y - (slope * x + intercept) for x, y in zip(xs, ys)]
    variance = sum(r * r for r in residuals) / (n - 2) if n > 2 else 0.0
    return slope, variance ** 0.5


def cpu_series(rows, clk_tck):
    """Per-interval CPU as a percentage of one core, from the jiffy counters."""
    series = []
    for before, after in zip(rows, rows[1:]):
        elapsed_ms = after["uptime_ms"] - before["uptime_ms"]
        if elapsed_ms <= 0:
            continue
        jiffies = (after["utime"] - before["utime"]) + (after["stime"] - before["stime"])
        cpu_ms = jiffies * 1000.0 / clk_tck
        series.append({"uptime_ms": after["uptime_ms"],
                       "cpu_percent_of_one_core": 100.0 * cpu_ms / elapsed_ms})
    return series


def summarise(values):
    if not values:
        return {"n": 0, "min": None, "max": None, "mean": None, "first": None, "last": None}
    return {"n": len(values), "min": min(values), "max": max(values),
            "mean": sum(values) / len(values), "first": values[0], "last": values[-1]}


def parse_gc(path):
    """ART's own GC lines. Absence is reported as absence, not as zero collections."""
    if not path:
        return None
    count = 0
    paused_ms = 0.0
    total_ms = 0.0
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                match = GC_LINE.search(line)
                if not match:
                    continue
                count += 1
                for value, unit in DURATION.findall(match.group(1)):
                    paused_ms += to_ms(value, unit)
                total_ms += to_ms(match.group(2), match.group(3))
    except OSError:
        return None
    return {"collections": count, "paused_ms_total": paused_ms, "gc_ms_total": total_ms}


def halves(values):
    """First and second half, for the question "did it get worse as it went on?"."""
    if len(values) < 4:
        return None
    middle = len(values) // 2
    first, second = values[:middle], values[middle:]
    return {"first_half_mean": sum(first) / len(first),
            "second_half_mean": sum(second) / len(second)}


def build_report(meta, rows, gc, run_id, commit, device_label, device_info, backend, scene,
                 interval_seconds):
    if len(rows) < 3:
        raise SystemExit("only %d samples: that is not a soak" % len(rows))
    if meta.get("process_died") == "true":
        raise SystemExit("the app died during the soak; the samples describe a crash, "
                         "not a workload")

    clk_tck = int(meta.get("clk_tck", 100))
    duration_ms = rows[-1]["uptime_ms"] - rows[0]["uptime_ms"]

    cpu = cpu_series(rows, clk_tck)
    cpu_values = [point["cpu_percent_of_one_core"] for point in cpu]
    rss = [row["rss_kb"] for row in rows if row.get("rss_kb") is not None]
    rss_minutes = [(row["uptime_ms"] - rows[0]["uptime_ms"]) / 60000.0
                   for row in rows if row.get("rss_kb") is not None]
    rss_slope, rss_residual = linear_slope(rss_minutes, rss)

    def column(name):
        return [row[name] for row in rows if row.get(name) is not None]

    thermal = column("thermal_status")
    battery = column("battery_level")

    cases = [
        {"name": "cpu_percent_of_one_core", "unit": "percent",
         "question": "CPU the scanning process used, as a percentage of a single core",
         **summarise(cpu_values), "halves": halves(cpu_values)},
        {"name": "rss_kb", "unit": "kB",
         "question": "Resident set size over the soak",
         **summarise(rss),
         "slope_kb_per_minute": rss_slope,
         "slope_residual_kb": rss_residual,
         "halves": halves(rss)},
        {"name": "java_heap_kb", "unit": "kB",
         "question": "Java heap, sampled on the slow rows only",
         **summarise(column("java_heap_kb")), "halves": halves(column("java_heap_kb"))},
        {"name": "native_heap_kb", "unit": "kB",
         "question": "Native heap, sampled on the slow rows only",
         **summarise(column("native_heap_kb")), "halves": halves(column("native_heap_kb"))},
        {"name": "total_pss_kb", "unit": "kB",
         "question": "Total PSS, sampled on the slow rows only",
         **summarise(column("total_pss_kb")), "halves": halves(column("total_pss_kb"))},
        {"name": "threads", "unit": "count",
         "question": "Thread count; a soak that grows threads is leaking something else",
         **summarise(column("threads")), "halves": halves(column("threads"))},
        {"name": "major_faults", "unit": "count",
         "question": "Cumulative major page faults",
         **summarise(column("majflt")), "halves": None},
        {"name": "thermal_status", "unit": "PowerManager.THERMAL_STATUS",
         "question": "Thermal status over the soak; 0 is none, 6 is shutdown",
         **summarise(thermal), "halves": None},
        {"name": "battery_level", "unit": "percent",
         "question": "Battery level over the soak",
         **summarise(battery), "halves": None},
    ]

    warnings = []
    if thermal and max(thermal) >= 3:
        warnings.append(
            "THERMAL_THROTTLE: thermal status reached %d during the soak, so these are "
            "numbers for a throttled device. They are real numbers; they are not a "
            "thermally-unconstrained ceiling." % max(thermal))
    if rss_slope is not None and rss_residual is not None:
        predicted_rise = rss_slope * (duration_ms / 60000.0)
        if abs(predicted_rise) > 2 * rss_residual and rss_slope > 0:
            warnings.append(
                "RSS_GROWTH: RSS rose about %.0f kB over the soak (%.1f kB/min), which is "
                "larger than the sample spread. Not proof of a leak -- a soak this short "
                "cannot separate a leak from a cache filling -- but it is the thing to "
                "look at next." % (predicted_rise, rss_slope))
    if battery and len(set(battery)) == 1 and battery[0] == 100:
        warnings.append(
            "BATTERY_PINNED: the battery stayed at 100% for the whole soak, so it was on "
            "charge. Battery drain was not measured and must not be read off this run.")
    if gc is None:
        warnings.append("NO_GC_DATA: no logcat was supplied, so GC was not measured.")
    elif gc["collections"] == 0:
        warnings.append(
            "NO_GC_LOGGED: ART logged no GC lines during the soak. That is not the same as "
            "no collections happening; it is the absence of a log, and the only honest "
            "reading of it is that GC was not measured here.")
    if duration_ms < 5 * 60 * 1000:
        warnings.append("SHORT_SOAK: under five minutes of samples; §12 wants a soak, and "
                        "a leak or a thermal effect needs time to appear.")
    if scene != "hit":
        warnings.append(
            "MISS_PATH: nothing was decoded during this soak, so it measures the "
            "decode-nothing path. §10 measured that as the most expensive case and it is "
            "what live preview does almost every frame, so it is the right default -- but "
            "it is not a scan-every-frame workload.")

    return {
        "layer": "L4",
        "run_id": run_id,
        "commit": commit,
        "config": backend,
        "dataset_hash": None,
        "frames": None,
        "device": dict(device_info or {}, label=device_label),
        "camera_backend": backend,
        "scene": scene,
        "protocol": {
            "duration_ms": duration_ms,
            "samples": len(rows),
            "interval_seconds": interval_seconds,
            "clk_tck": clk_tck,
            "slow_sample_every": int(meta.get("slow_every", 0)) or None,
        },
        "gc": gc,
        "warnings": warnings,
        "cases": cases,
        "series": {"cpu_percent_of_one_core": cpu,
                   "rss_kb": [{"uptime_ms": row["uptime_ms"], "value": row["rss_kb"]}
                              for row in rows if row.get("rss_kb") is not None]},
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--samples", required=True)
    parser.add_argument("--logcat")
    parser.add_argument("--backend", required=True, choices=["camera1", "camerax"])
    parser.add_argument("--scene", default="miss", choices=["miss", "hit"],
                        help="whether anything was in front of the lens")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--device-label", required=True)
    parser.add_argument("--device-api", type=int)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    meta, rows = parse_samples(args.samples)
    gc = parse_gc(args.logcat)
    device_info = {"api": args.device_api, "emulator": False}
    report = build_report(meta, rows, gc, args.run_id, args.commit, args.device_label,
                          device_info, args.backend, args.scene,
                          int(meta.get("interval", 0)) or None)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2)
        handle.write("\n")

    print("wrote %s: %d samples over %.1f min, %d warnings"
          % (args.out, report["protocol"]["samples"],
             report["protocol"]["duration_ms"] / 60000.0, len(report["warnings"])))
    for warning in report["warnings"]:
        print("  warning: " + warning)
    return 0


if __name__ == "__main__":
    sys.exit(main())
