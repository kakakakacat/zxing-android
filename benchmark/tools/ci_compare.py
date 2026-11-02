#!/usr/bin/env python3
"""Judge candidate L2 result files against the committed baselines, in CI.

The rules are BENCHMARKS.md section 13.3. The thing this tool exists to respect is that
**CI cannot produce an L2 number**: a shared runner has no locked clocks, no known
thermal state and no device. So nothing is measured here. A candidate is measured by an
operator on a reference device, committed to `docs/maint/reports/candidates/`, and this
step does the one part a machine can do honestly -- pick the right baseline, run the
section 10.6 gates, and publish the verdict where a reviewer reads it.

Picking the baseline is the part worth having in a tool. A candidate is judged against
the committed result that shares its **device label, config and decoder backend**. The
backend is in that key although compare.py deliberately does not guard it: across
backends compare.py is answering "which decoder is faster", which is section 10.6b's
question and not a regression check. Selecting on it is what keeps the two apart.

Exit code: 0 pass, 1 fail, 2 inconclusive, 3 a candidate could not be judged at all --
no baseline, an ambiguous one, or result sets compare.py refuses to compare. 4 means the
manifest itself is wrong.

Usage:
  python3 -I benchmark/tools/ci_compare.py            # judge every committed candidate
  python3 -I benchmark/tools/ci_compare.py --check    # the manifest only, nothing judged
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import compare  # noqa: E402

EXIT_MANIFEST_BROKEN = 4

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_MANIFEST = os.path.join(REPO, "docs", "maint", "reports", "l2-baselines.json")
DEFAULT_CANDIDATES = os.path.join(REPO, "docs", "maint", "reports", "candidates")

# Worst wins: a run nobody could judge outranks a failure, because "we do not know" is
# the more expensive answer to leave unread.
SEVERITY = {compare.PASS: 0, compare.INCONCLUSIVE: 1, compare.FAIL: 2, "unjudged": 3}
EXIT_FOR = {compare.PASS: 0, compare.FAIL: 1, compare.INCONCLUSIVE: 2, "unjudged": 3}


def selection_key(device_label, config, backend):
    return "%s/%s/%s" % (device_label, config, backend)


def load_manifest(path):
    with open(path, encoding="utf-8") as handle:
        manifest = json.load(handle)
    return manifest.get("baselines") or []


def check_manifest(entries, reports_dir):
    """What makes the manifest unusable, as a list of reasons.

    A manifest that points at a file that is not there, or that claims a config the file
    does not carry, selects the wrong baseline silently -- which is the one failure a
    comparison tool must not have.
    """
    reasons = []
    seen = set()
    for entry in entries:
        key = entry.get("key")
        for field in ("key", "file", "device", "config", "backend", "note"):
            if not entry.get(field):
                reasons.append("%r: missing %s" % (key, field))
        if key in seen:
            reasons.append("%r: duplicate key" % key)
        seen.add(key)

        expected = selection_key(entry.get("device"), entry.get("config"),
                                 entry.get("backend"))
        if key and key != expected:
            reasons.append("%r: key does not match its own fields, expected %r"
                           % (key, expected))

        path = os.path.join(reports_dir, entry.get("file") or "")
        if not entry.get("file") or not os.path.isfile(path):
            reasons.append("%r: no such result file: %s" % (key, entry.get("file")))
            continue
        try:
            with open(path, encoding="utf-8") as handle:
                report = json.load(handle)
        except ValueError as error:
            reasons.append("%r: %s is not readable JSON: %s" % (key, entry["file"], error))
            continue

        if report.get("layer") != "L2":
            reasons.append("%r: %s is layer %r, not L2"
                           % (key, entry["file"], report.get("layer")))
        label = (report.get("device") or {}).get("label")
        if label != entry.get("device"):
            reasons.append("%r: %s was taken on %r, not %r"
                           % (key, entry["file"], label, entry.get("device")))
        if report.get("config") != entry.get("config"):
            reasons.append("%r: %s has config %r, not %r"
                           % (key, entry["file"], report.get("config"),
                              entry.get("config")))

        recorded = report.get("backend")
        if recorded is None:
            # A result file old enough not to carry the field. The manifest may still say
            # which decoder produced it -- somebody knows -- but it has to say so out
            # loud, with the reason, rather than let a null match anything.
            if not entry.get("backend_asserted") or not entry.get("backend_note"):
                reasons.append(
                    "%r: %s records no backend, so the entry needs backend_asserted "
                    "with a backend_note saying how the backend is known"
                    % (key, entry["file"]))
        elif recorded != entry.get("backend"):
            reasons.append("%r: %s records backend %r, not %r"
                           % (key, entry["file"], recorded, entry.get("backend")))
        elif entry.get("backend_asserted"):
            reasons.append("%r: %s records backend %r itself, so the entry must not "
                           "assert one" % (key, entry["file"], recorded))
    return reasons


def select(entries, candidate):
    """The baseline this candidate is judged against, or why there is none."""
    label = (candidate.get("device") or {}).get("label")
    key = selection_key(label, candidate.get("config"), candidate.get("backend"))
    matches = [entry for entry in entries if entry.get("key") == key]
    if not matches:
        return None, ("no committed baseline for %s. Baselines available: %s. A run of a "
                      "device, configuration or decoder backend nothing has been "
                      "baselined on cannot be judged -- take the baseline first, or say "
                      "in the pull request why this run is not a regression check."
                      % (key, ", ".join(sorted(e.get("key") or "?" for e in entries))
                         or "none"))
    if len(matches) > 1:
        return None, "the manifest has %d baselines for %s" % (len(matches), key)
    return matches[0], None


def judge(entries, reports_dir, candidate_path, accept_noise_warnings=False):
    with open(candidate_path, encoding="utf-8") as handle:
        candidate = json.load(handle)
    name = os.path.basename(candidate_path)

    entry, why = select(entries, candidate)
    if entry is None:
        return {"candidate": name, "verdict": "unjudged", "reason": why,
                "baseline": None, "result": None}

    with open(os.path.join(reports_dir, entry["file"]), encoding="utf-8") as handle:
        baseline = json.load(handle)
    result = compare.compare(baseline, candidate, accept_noise_warnings)
    verdict = result["verdict"] if result["comparable"] else "unjudged"
    reason = None
    if not result["comparable"]:
        reason = "; ".join(result["guard_failures"])
    return {"candidate": name, "verdict": verdict, "reason": reason,
            "baseline": entry, "result": result}


def summarize(judgements):
    """One Markdown block for the job summary -- the verdict, then every reason."""
    lines = ["## L2 comparison", ""]
    if not judgements:
        lines += [
            "No candidate result was submitted, so nothing was compared.",
            "",
            "This is not a pass. CI cannot measure an L2 number -- a shared runner has no "
            "locked clocks and no device -- so a change to the decode path carries "
            "evidence only if an operator ran the benchmark and committed the result to "
            "`docs/maint/reports/candidates/`. See BENCHMARKS.md section 13.3.",
            "",
        ]
        return "\n".join(lines)

    for judgement in judgements:
        entry = judgement["baseline"]
        lines.append("### `%s` — **%s**" % (judgement["candidate"], judgement["verdict"]))
        lines.append("")
        if entry is None:
            lines += ["Not judged: %s" % judgement["reason"], ""]
            continue
        lines.append("Baseline `%s` (`%s`), selected on device, config and backend."
                     % (entry["key"], entry["file"]))
        if entry.get("backend_asserted"):
            lines.append("")
            lines.append("The baseline's backend is **asserted by the manifest, not "
                         "recorded in the file**: %s" % entry["backend_note"])
        lines.append("")
        if judgement["reason"]:
            lines += ["Not judged: %s" % judgement["reason"], ""]
            continue
        result = judgement["result"]
        body = []
        compare_lines = []
        for row in result["cases"]:
            if row["verdict"] != compare.INCONCLUSIVE:
                compare_lines.append("| `%s` | %s | %s | %s |"
                                     % (row["name"], compare.percent(row["delta"]),
                                        row["verdict"], row["reason"]))
        if compare_lines:
            body.append("| Case | Δ | Verdict | Why |")
            body.append("| --- | --- | --- | --- |")
            body += compare_lines
        else:
            body.append("Every case came back inconclusive. The per-case reasons are in "
                        "the uploaded `report.md`.")
        lines += body + [""]
        if result.get("read_through_warnings"):
            lines += ["Read through under section 10.6: %s."
                      % ", ".join(result["read_through_warnings"]), ""]
        if result.get("waived_warnings"):
            lines += ["Waived with `--accept-noise-warnings`: %s."
                      % ", ".join(result["waived_warnings"]), ""]
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default=DEFAULT_MANIFEST)
    parser.add_argument("--candidates", default=DEFAULT_CANDIDATES)
    parser.add_argument("--out-dir", default="build/benchmark/ci-compare")
    parser.add_argument("--summary", help="append the Markdown summary to this file, "
                                          "typically $GITHUB_STEP_SUMMARY")
    parser.add_argument("--check", action="store_true",
                        help="validate the manifest and stop; judge nothing")
    parser.add_argument("--accept-noise-warnings", action="store_true",
                        help="passed through to compare.py; see its help")
    args = parser.parse_args(argv)

    reports_dir = os.path.dirname(os.path.abspath(args.manifest))
    entries = load_manifest(args.manifest)
    reasons = check_manifest(entries, reports_dir)
    if reasons:
        print("the baseline manifest is not usable:")
        for reason in reasons:
            print("  %s" % reason)
        return EXIT_MANIFEST_BROKEN
    print("baseline manifest: %d entries, all readable" % len(entries))
    if args.check:
        return 0

    paths = sorted(os.path.join(args.candidates, name)
                   for name in (os.listdir(args.candidates)
                                if os.path.isdir(args.candidates) else [])
                   if name.endswith(".json"))
    judgements = [judge(entries, reports_dir, path, args.accept_noise_warnings)
                  for path in paths]

    os.makedirs(args.out_dir, exist_ok=True)
    for judgement in judgements:
        if judgement["result"] is None:
            continue
        stem = os.path.splitext(judgement["candidate"])[0]
        out_dir = os.path.join(args.out_dir, stem)
        os.makedirs(out_dir, exist_ok=True)
        with open(os.path.join(out_dir, "verdict.json"), "w", encoding="utf-8") as handle:
            json.dump({"baseline": judgement["baseline"], "comparison": judgement["result"]},
                      handle, indent=1)
            handle.write("\n")
        compare.write_csv(judgement["result"], os.path.join(out_dir, "cases.csv"))
        compare.write_markdown(judgement["result"], os.path.join(out_dir, "report.md"))

    summary = summarize(judgements)
    print(summary)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as handle:
            handle.write(summary + "\n")

    if not judgements:
        return 0
    worst = max((j["verdict"] for j in judgements), key=lambda v: SEVERITY[v])
    return EXIT_FOR[worst]


if __name__ == "__main__":
    sys.exit(main())
