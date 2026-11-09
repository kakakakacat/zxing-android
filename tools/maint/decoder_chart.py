#!/usr/bin/env python3
"""Render the README's decoder A/B chart from two committed L2 result files.

The chart that shipped before this script was hand-maintained, which is the wrong
shape for a picture whose whole job is to carry measured numbers: every re-measure
meant re-typing nine pairs of milliseconds and nine percentages into two SVGs, in
two languages, with nothing checking them against the result files. This reads the
same envelopes `benchmark/tools/compare.py` reads and emits both SVGs.

    python3 -I tools/maint/decoder_chart.py \\
        --java docs/maint/reports/l2-<run>-java.json \\
        --native docs/maint/reports/l2-<run>-native.json \\
        --commit 099d1153a --gate '27-case gate: 21 pass / 3 fail / 3 inconclusive' \\
        --out-dir docs/maint/assets

It draws the nine `default` (all-format) cases only — the three hint sets are not
poolable (BENCHMARKS.md §10.5) and the README says which one it is showing.
Nothing here computes a verdict: `compare.py` owns that, and the gate line is
passed in so the picture cannot disagree with the file it came from.
"""

import argparse
import json
import math

W, H = 1100, 1320
PANEL_X, PANEL_W, PANEL_H = 24, 1052, 326
PANEL_YS = (150, 494, 838)
BAR_X, TICK_SPAN, TICKS = 300, 170, 3
JAVA_FILL, NATIVE_FILL = "#64748b", "#2563eb"
BETTER, WORSE = "#087f5b", "#b45309"

NICE = (1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0)

RESOLUTIONS = (("vga", "640 × 480"), ("hd", "1280 × 720"), ("fhd", "1920 × 1080"))
VARIANTS = ("miss", "hit_qr", "hit_1d")

STRINGS = {
    "en": {
        "file": "decoder-performance.svg",
        "title": "ZXing Java versus native decoder: Android frame-pipeline benchmark",
        "desc": ("Nine default all-format cases from commit {commit}. {summary} Each panel "
                 "has its own zero-based linear millisecond scale. One Android 14 device, "
                 "locked clocks; not a whole-app upstream comparison."),
        "heading": "ZXing Java vs native decoding",
        "subheading": "Android frame pipeline · Median time per frame · Lower is better",
        "java": "Java ZXing backend",
        "native": "This fork: zxing-cpp backend",
        "change": "Native time change",
        "panels": {
            "miss": "No barcode found",
            "hit_qr": "QR code detected",
            "hit_1d": "Centered 1D barcode detected",
        },
        "faster": "Native reduces time by {lo}–{hi}% across these frames.",
        "slower": "Trade-off: Java wins here. Native adds {lo}–{hi}%.",
        "mixed": "Native is {lo}% to {hi}% on these frames.",
        "scope": ("Scope: backend A/B in this fork, not original Barcode Scanner vs "
                  "current app."),
        "conditions": ("DN-ENG41 · Android 14 · Locked clocks · Warm standing thermal "
                       "conditions · All formats"),
        "protocol": ("Matched effort options · Clean generated frames · One run/backend "
                     "· Different scales per panel"),
        "gate": "L2 @ {commit} · {gate} · No whole-app or first-scan speed claim",
        "ms": "ms",
    },
    "zh-CN": {
        "file": "decoder-performance.zh-CN.svg",
        "title": "ZXing Java 与原生解码器：Android 帧链路性能测试",
        "desc": ("来自提交 {commit} 的九个默认全码制场景。{summary}各面板采用不同量程、"
                 "从零开始的线性毫秒刻度。单台 Android 14 设备、锁频；不是整应用与上游的对比。"),
        "heading": "ZXing Java 与原生解码性能对比",
        "subheading": "Android 帧处理链路 · 每帧耗时中位数 · 越低越好",
        "java": "Java ZXing 后端",
        "native": "本分支：zxing-cpp 后端",
        "change": "原生后端耗时变化",
        "panels": {
            "miss": "未识别到条码",
            "hit_qr": "二维码命中",
            "hit_1d": "居中一维码命中",
        },
        "faster": "这些测试帧中，原生后端耗时降低 {lo}–{hi}%。",
        "slower": "权衡：此处 Java 更快；原生后端增加 {lo}–{hi}%。",
        "mixed": "这些测试帧中，原生后端耗时变化 {lo}% 至 {hi}%。",
        "scope": "范围：本分支内部后端 A/B，并非原版 Barcode Scanner 与当前应用的整体对比。",
        "conditions": "DN-ENG41 · Android 14 · 锁频 · 长期偏热的测试状态 · 全码制",
        "protocol": "匹配处理选项 · 清晰生成帧 · 每后端一轮 · 各面板量程不同",
        "gate": "L2 @ {commit} · {gate} · 不宣称整应用或首扫提速",
        "ms": "ms",
    },
}


def escape(text):
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def medians(path):
    """{(variant, resolution): milliseconds} for the default/all-format cases."""
    with open(path, encoding="utf-8") as handle:
        result = json.load(handle)
    out = {}
    for case in result["cases"]:
        if case["stage"] != "endToEnd" or case["config"] != "default":
            continue
        out[(case["variant"], case["resolution"])] = case["ns_p50"] / 1e6
    return out


def nice_step(largest):
    """The tick step the old chart used: three ticks, the largest bar near 2.6 of them."""
    target = largest / TICKS
    magnitude = 10.0 ** math.floor(math.log10(target))
    for factor in NICE:
        if factor * magnitude >= target - 1e-12:
            return factor * magnitude
    return 10.0 * magnitude


def tick_label(step, index):
    decimals = max(0, -int(math.floor(math.log10(step))))
    return f"{step * index:.{decimals}f}"


def panel(strings, variant, java, native, top):
    rows = [(label, java[(variant, key)], native[(variant, key)])
            for key, label in RESOLUTIONS]
    deltas = [(n / j - 1.0) * 100.0 for _, j, n in rows]
    step = nice_step(max(max(j, n) for _, j, n in rows))
    scale = TICK_SPAN / step

    if all(d < 0 for d in deltas):
        caption = strings["faster"].format(lo=f"{-max(deltas):.1f}", hi=f"{-min(deltas):.1f}")
    elif all(d > 0 for d in deltas):
        caption = strings["slower"].format(lo=f"{min(deltas):.1f}", hi=f"{max(deltas):.1f}")
    else:
        caption = strings["mixed"].format(lo=f"{min(deltas):+.1f}", hi=f"{max(deltas):+.1f}")

    parts = [
        f'<rect x="{PANEL_X}" y="{top}" width="{PANEL_W}" height="{PANEL_H}" rx="14"'
        ' fill="#ffffff" stroke="#e1e7f0"/>',
        f'<text x="44" y="{top + 34}" font-size="22" font-weight="700">'
        f'{escape(strings["panels"][variant])}</text>',
        f'<text x="44" y="{top + 60}" font-size="16" fill="#526077">'
        f'{escape(caption)}</text>',
    ]
    for index in range(TICKS + 1):
        x = BAR_X + TICK_SPAN * index
        parts.append(
            f'<line x1="{x}" y1="{top + 79}" x2="{x}" y2="{top + 276}" stroke="#e7ecf3"/>'
            f'<text x="{x}" y="{top + 304}" text-anchor="middle" font-size="14"'
            f' fill="#64748b">{tick_label(step, index)}</text>')
    parts.append(f'<text x="{BAR_X + TICK_SPAN * TICKS + 33}" y="{top + 304}"'
                 f' font-size="14" fill="#64748b">{strings["ms"]}</text>')
    parts.append(f'<text x="1036" y="{top + 88}" text-anchor="end" font-size="13"'
                 f' fill="#64748b">{escape(strings["change"])}</text>')

    for index, ((label, java_ms, native_ms), delta) in enumerate(zip(rows, deltas)):
        base = top + 62 * index
        parts.append(f'<text x="44" y="{base + 125}" font-size="17" font-weight="600">'
                     f'{escape(label)}</text>')
        for value, fill, offset in ((java_ms, JAVA_FILL, 105), (native_ms, NATIVE_FILL, 128)):
            width = value * scale
            parts.append(
                f'<rect x="{BAR_X}" y="{base + offset}" width="{width:.3f}" height="17"'
                f' rx="3" fill="{fill}"/>'
                f'<text x="{BAR_X + width + 9:.2f}" y="{base + offset + 14}"'
                f' font-size="14" fill="{fill}">{value:.3f}</text>')
        parts.append(
            f'<text x="1036" y="{base + 129}" text-anchor="end" font-size="20"'
            f' font-weight="700" fill="{BETTER if delta < 0 else WORSE}">'
            f'{delta:+.1f}%</text>')
    return "".join(parts), deltas


def render(language, strings, java, native, commit, gate):
    panels, all_deltas = [], {}
    for variant, top in zip(VARIANTS, PANEL_YS):
        markup, deltas = panel(strings, variant, java, native, top)
        panels.append(markup)
        all_deltas[variant] = deltas

    if language == "en":
        summary = ("Native is faster on misses and QR hits; Java is faster on centered "
                   "one-dimensional hits.")
    else:
        summary = "原生后端在无码和二维码命中时更快，Java 在居中一维码命中时更快。"

    head = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"'
        f' viewBox="0 0 {W} {H}" role="img" aria-labelledby="title desc">\n'
        f'<title id="title">{escape(strings["title"])}</title>\n'
        f'<desc id="desc">'
        f'{escape(strings["desc"].format(commit=commit, summary=summary))}</desc>\n'
        f'<rect width="{W}" height="{H}" rx="22" fill="#f5f7fb"/>\n'
        '<g font-family="Arial,Helvetica,sans-serif" fill="#18243b">\n'
        f'<text x="40" y="51" font-size="28" font-weight="700">'
        f'{escape(strings["heading"])}</text>\n'
        f'<text x="40" y="82" font-size="17" fill="#526077">'
        f'{escape(strings["subheading"])}</text>\n'
        f'<rect x="40" y="107" width="17" height="17" rx="3" fill="{JAVA_FILL}"/>'
        f'<text x="67" y="122" font-size="16">{escape(strings["java"])}</text>\n'
        f'<rect x="310" y="107" width="17" height="17" rx="3" fill="{NATIVE_FILL}"/>'
        f'<text x="337" y="122" font-size="16">{escape(strings["native"])}</text>\n')
    foot = (
        f'<text x="40" y="1213" font-size="15" font-weight="700">'
        f'{escape(strings["scope"])}</text>\n'
        f'<text x="40" y="1238" font-size="14" fill="#526077">'
        f'{escape(strings["conditions"])}</text>\n'
        f'<text x="40" y="1261" font-size="14" fill="#526077">'
        f'{escape(strings["protocol"])}</text>\n'
        f'<text x="40" y="1284" font-size="14" fill="#526077">'
        f'{escape(strings["gate"].format(commit=commit, gate=gate))}</text>\n'
        '</g></svg>\n')
    return head + "\n".join(panels) + "\n" + foot, all_deltas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, help="L2 result file for the Java backend")
    parser.add_argument("--native", required=True, help="L2 result file for the native one")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--gate", required=True,
                        help="the one-line verdict from compare.py, quoted verbatim")
    parser.add_argument("--out-dir", default="docs/maint/assets")
    args = parser.parse_args()

    java, native = medians(args.java), medians(args.native)
    missing = [key for key in
               ((variant, resolution) for variant in VARIANTS
                for resolution, _ in RESOLUTIONS)
               if key not in java or key not in native]
    if missing:
        raise SystemExit(f"these default cases are in neither run or only one: {missing}")

    for language, strings in STRINGS.items():
        svg, deltas = render(language, strings, java, native, args.commit, args.gate)
        path = f"{args.out_dir}/{strings['file']}"
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(svg)
        print(f"wrote {path}")
    for variant in VARIANTS:
        lows = ", ".join(f"{d:+.1f}%" for d in deltas[variant])
        print(f"  {variant}: {lows}")


if __name__ == "__main__":
    main()
