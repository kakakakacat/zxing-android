#!/usr/bin/env python3
"""Normalize Java/C++ Android ROI runs without treating iterations as independent processes."""
import argparse
import hashlib
import json
import pathlib
import random
import statistics

SIZES = [(640, 480, "VGA"), (1280, 720, "HD"), (1920, 1080, "FHD")]
LAYOUTS = [("packed", 1, 0), ("padded", 1, 32), ("pixel2", 2, 32)]
SEED = 20261104
RESAMPLES = 10000


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * fraction))]


def fixture_hashes():
    rows = []
    for fw, fh, size_name in SIZES:
        width, height = fw * 5 // 8, fh * 5 // 8
        left, top = (fw - width) // 2, (fh - height) // 2
        expected = bytes(((left + x) * 13 + (top + y) * 7 + 17) & 255
                         for y in range(height) for x in range(width))
        for layout, pixel, padding in LAYOUTS:
            row = fw * pixel + padding
            source = bytearray(row * fh)
            for y in range(fh):
                for x in range(fw):
                    source[y * row + x * pixel] = (x * 13 + y * 7 + 17) & 255
            rows.append({"case": f"{size_name}_{layout}", "frameWidth": fw,
                         "frameHeight": fh, "width": width, "height": height,
                         "left": left, "top": top, "rowStride": row,
                         "pixelStride": pixel,
                         "sourceSha256": hashlib.sha256(source).hexdigest(),
                         "expectedSha256": hashlib.sha256(expected).hexdigest(),
                         "outputBytes": len(expected)})
    canonical = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode()
    return rows, hashlib.sha256(canonical).hexdigest()


def read_run(path):
    raw = json.loads(path.read_text())
    if raw["context"]["build"]["model"] != "DN-ENG41" or raw["context"]["build"]["version"]["sdk"] != 34:
        raise ValueError(f"Unexpected device/API in {path}")
    by_case = {}
    for item in raw["benchmarks"]:
        indices = item["name"].removeprefix("pack[ROI_").removesuffix("]").split("_")
        if len(indices) != 2:
            raise ValueError(f"Unexpected benchmark case name: {item['name']}")
        size_index, layout_index = map(int, indices)
        name = f"{SIZES[size_index][2]}_{LAYOUTS[layout_index][0]}"
        times = item["metrics"]["timeNs"]["runs"]
        if len(times) < 50:
            raise ValueError(f"{path}: {name} has only {len(times)} measured samples")
        allocations = item["metrics"].get("allocationCount", {}).get("runs", [])
        by_case[name] = {"medianNs": statistics.median(times),
                         "p90Ns": percentile(times, .90), "iterations": len(times),
                         "allocationCountMedian": statistics.median(allocations) if allocations else None,
                         "rawNs": times}
    return raw, by_case


def summarize(paths, backend):
    results = [read_run(path) for path in paths]
    if len(results) < 3:
        raise ValueError(f"{backend}: at least three independent process runs are required")
    cases = [f"{size_name}_{layout[0]}" for _, _, size_name in SIZES
             for layout in LAYOUTS]
    if any(set(values) != set(cases) for _, values in results):
        raise ValueError(f"{backend}: case matrices differ")
    return results, cases


def comparison(candidate, baseline, random_source):
    deltas = []
    for _ in range(RESAMPLES):
        c = [random_source.choice(candidate) for _ in candidate]
        b = [random_source.choice(baseline) for _ in baseline]
        deltas.append(statistics.median(c) - statistics.median(b))
    return {"deltaCi95LowNs": percentile(deltas, .025),
            "deltaCi95HighNs": percentile(deltas, .975),
            "medianDeltaNs": statistics.median(candidate) - statistics.median(baseline)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", required=True, type=pathlib.Path,
                        help="directory containing final-rNN-java|jni_cpp/*.json")
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--warning", action="append", default=[])
    args = parser.parse_args()

    backend_runs = {}
    backend_paths = {}
    for backend in ("java", "jni_cpp"):
        paths = sorted(args.runs.glob(f"final-r*-{backend}/androidx-benchmark.json"))
        backend_paths[backend] = paths
        backend_runs[backend] = summarize(paths, backend)
    fixtures, dataset_hash = fixture_hashes()
    names = [fixture["case"] for fixture in fixtures]
    if names != backend_runs["java"][1] or names != backend_runs["jni_cpp"][1]:
        raise ValueError("ROI matrix differs from the required VGA/HD/FHD × layout matrix")

    rng = random.Random(SEED)
    cases = []
    for fixture in fixtures:
        name = fixture["case"]
        java_values = [values[name]["medianNs"] for _, values in backend_runs["java"][0]]
        cpp_values = [values[name]["medianNs"] for _, values in backend_runs["jni_cpp"][0]]
        java_p90 = [values[name]["p90Ns"] for _, values in backend_runs["java"][0]]
        cpp_p90 = [values[name]["p90Ns"] for _, values in backend_runs["jni_cpp"][0]]
        noise_floor = max(abs(value - statistics.median(java_values))
                          for value in java_values) / statistics.median(java_values)
        cpp_median = statistics.median(cpp_values)
        java_median = statistics.median(java_values)
        comparison_data = comparison(cpp_values, java_values, rng)
        effect = (java_median - cpp_median) / java_median
        status = "inconclusive"
        if comparison_data["deltaCi95HighNs"] < 0 and effect > noise_floor:
            status = "cpp_faster"
        elif comparison_data["deltaCi95LowNs"] > 0 and -effect > noise_floor:
            status = "cpp_slower"
        else:
            status = "no_stable_difference"
        cases.append({**fixture, "javaProcessMedianNs": java_values,
                      "jniCppProcessMedianNs": cpp_values,
                      "javaProcessP90Ns": java_p90, "jniCppProcessP90Ns": cpp_p90,
                      "javaMedianOfProcessMediansNs": java_median,
                      "jniCppMedianOfProcessMediansNs": cpp_median,
                      "observedAaNoiseFloor": noise_floor,
                      "relativeEffect": effect, "comparison": comparison_data,
                      "status": status,
                      "javaIterations": [values[name]["iterations"] for _, values in backend_runs["java"][0]],
                      "jniCppIterations": [values[name]["iterations"] for _, values in backend_runs["jni_cpp"][0]],
                      "javaAllocationCountMedian": [values[name]["allocationCountMedian"] for _, values in backend_runs["java"][0]],
                      "jniCppAllocationCountMedian": [values[name]["allocationCountMedian"] for _, values in backend_runs["jni_cpp"][0]]})

    raw_context = backend_runs["java"][0][0][0]["context"]
    report = {"schema": "roi-pack-android-v1", "stage": "roiPack",
              "commit": args.commit, "datasetHash": dataset_hash,
              "device": {"alias": "DN-ENG41", "model": "DN-ENG41", "api": 34,
                         "abi": "arm64-v8a", "process64Bit": True,
                         "cpuLocked": raw_context.get("cpuLocked"),
                         "cpuMaxFreqHz": raw_context.get("cpuMaxFreqHz"),
                         "buildType": "release"},
              "toolchain": {"ndk": "29.0.14206865", "cmake": "3.22.1",
                            "cppStandard": "C++17", "cppFlags": "-O3 -DNDEBUG"},
              "protocol": {"roundsPerBackend": len(backend_runs["java"][0]),
                           "samplesPerCasePerProcess": 50,
                           "bootstrapResamples": RESAMPLES, "bootstrapSeed": SEED,
                           "clusterUnit": "independent instrumentation process",
                           "threshold": "C++ 95% bootstrap delta interval excludes zero and effect exceeds observed Java A/A maximum relative deviation",
                           "jniArrayCopyProbe": False, "warnings": args.warning},
              "fixtures": fixtures, "cases": cases,
              "runMetadata": {backend: [{"model": raw["context"]["build"]["model"],
                  "api": raw["context"]["build"]["version"]["sdk"],
                  "cpuLocked": raw["context"].get("cpuLocked"),
                  "cpuMaxFreqHz": raw["context"].get("cpuMaxFreqHz"),
                  "source": str(path.relative_to(args.runs))} for path, (raw, _) in zip(
                      backend_paths[backend], values)]
                  for backend, (values, _) in backend_runs.items()}}
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    csv_lines = ["case,java_ns,jni_cpp_ns,relative_effect,aa_noise_floor,ci95_low_ns,ci95_high_ns,status"]
    for case in cases:
        cmp = case["comparison"]
        csv_lines.append(f"{case['case']},{case['javaMedianOfProcessMediansNs']:.3f},"
                         f"{case['jniCppMedianOfProcessMediansNs']:.3f},{case['relativeEffect']:.6f},"
                         f"{case['observedAaNoiseFloor']:.6f},{cmp['deltaCi95LowNs']:.3f},"
                         f"{cmp['deltaCi95HighNs']:.3f},{case['status']}")
    (args.out / "cases.csv").write_text("\n".join(csv_lines) + "\n")
    (args.out / "report.md").write_text(markdown(report))
    (args.out / "roi.svg").write_text(svg(report))


def markdown(report):
    rows = ["# Android ROI packing / Android ROI 打包", "",
            f"Commit: `{report['commit']}` · Dataset SHA-256: `{report['datasetHash']}` · "
            f"Device: {report['device']['alias']}, API {report['device']['api']}, arm64-v8a.",
            "The measurements cover packing only. Each backend was run in fresh processes, "
            "with 50 AndroidX measured samples per case. Confidence intervals bootstrap "
            "whole-process medians. Raw JSON files remain in the local ignored run directory.",
            "測量範圍僅為 ROI 打包。每個 backend 使用獨立進程，每 case 每進程 50 個 AndroidX 樣本；置信區間以進程中位数为聚类单位 bootstrap。原始 JSON 保留在本机忽略跟踪目录。", "",
            f"Warnings: {', '.join(report['protocol']['warnings']) or 'none'}. The ROI decision "
            "rule is separate from L2: the bootstrap C++−Java interval must exclude zero, "
            "and the median effect must exceed the largest observed Java A/A relative deviation.",
            f"告警：{', '.join(report['protocol']['warnings']) or '无'}。ROI 判定独立于 L2：C++−Java bootstrap 区间须排除零，且中位改善须超过 Java A/A 最大相对偏差。", "",
            "| Case | Java ns/copy | JNI→C++ ns/copy | Effect | A/A floor | 95% delta CI ns | Result |",
            "|---|---:|---:|---:|---:|---:|---|"]
    for case in report["cases"]:
        cmp = case["comparison"]
        rows.append(f"| {case['case']} | {case['javaMedianOfProcessMediansNs']:.0f} | "
                    f"{case['jniCppMedianOfProcessMediansNs']:.0f} | {case['relativeEffect']:.1%} | "
                    f"{case['observedAaNoiseFloor']:.1%} | [{cmp['deltaCi95LowNs']:.0f}, "
                    f"{cmp['deltaCi95HighNs']:.0f}] | {case['status']} |")
    rows.extend(["", "The result does not establish full decode or live camera gains. "
                 "The production native-direct path can skip this pack operation. "
                 "Decoder and camera stages are reported separately.",
                 "本结果不代表完整解码或实拍链路收益。生产 native-direct 路径可跳过此打包操作；解码与相机阶段须单独报告。", ""])
    return "\n".join(rows)


def svg(report):
    width, height, left, top = 1040, 430, 70, 48
    max_value = max(max(c["javaMedianOfProcessMediansNs"], c["jniCppMedianOfProcessMediansNs"])
                    for c in report["cases"]) * 1.18
    plot_h = 300
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
             '<rect width="100%" height="100%" fill="white"/>',
             '<text x="520" y="24" text-anchor="middle" font-family="sans-serif" font-size="18">Android ROI packing time (ns/copy)</text>',
             f'<line x1="{left}" y1="{top+plot_h}" x2="1015" y2="{top+plot_h}" stroke="#333"/>']
    group = 930 / len(report["cases"])
    for i, case in enumerate(report["cases"]):
        x = left + i * group + 14
        for j, key, color in [(0, "javaMedianOfProcessMediansNs", "#4878a8"),
                              (1, "jniCppMedianOfProcessMediansNs", "#d65f5f")]:
            value = case[key]
            bar_h = value / max_value * plot_h
            bx = x + j * 22
            by = top + plot_h - bar_h
            parts.append(f'<rect x="{bx:.1f}" y="{by:.1f}" width="18" height="{bar_h:.1f}" fill="{color}"/>')
        parts.append(f'<text x="{x+20:.1f}" y="{top+plot_h+19}" text-anchor="middle" font-family="sans-serif" font-size="11">{case["case"]}</text>')
    parts.extend(['<rect x="740" y="392" width="12" height="12" fill="#4878a8"/><text x="758" y="402" font-family="sans-serif" font-size="12">Java</text>',
                  '<rect x="820" y="392" width="12" height="12" fill="#d65f5f"/><text x="838" y="402" font-family="sans-serif" font-size="12">JNI → C++</text>', '</svg>'])
    return "\n".join(parts) + "\n"


if __name__ == "__main__":
    main()
