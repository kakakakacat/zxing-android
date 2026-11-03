#!/usr/bin/env python3
"""Refuse publication until the exact commit's verification workflows pass."""
import json
import os
import re
import time
import urllib.request

REQUIRED = {"build.yml", "instrumentation.yml", "l2-compare.yml", "codeql-analysis.yml"}


def evaluate(runs, sha):
    latest = {}
    for run in runs:
        path = run.get("path", "").split("/")[-1].split("@")[0]
        if run.get("head_sha") == sha and path in REQUIRED:
            if path not in latest or (run["id"], run.get("run_attempt", 1)) > (
                latest[path]["id"], latest[path].get("run_attempt", 1)
            ):
                latest[path] = run
    failed = [p for p, r in latest.items() if r["status"] == "completed"
              and r.get("conclusion") != "success"]
    pending = sorted(REQUIRED - latest.keys()) + [
        p for p, r in latest.items() if r["status"] != "completed"]
    return sorted(failed), sorted(pending)


def validate_version(version):
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?", version):
        raise ValueError("expected a numeric release version, optionally with a prerelease suffix")
    return version


def main():
    version = validate_version(os.environ["RELEASE_VERSION"])
    sha = os.environ["GITHUB_SHA"]
    repo = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GITHUB_TOKEN"]
    deadline = time.monotonic() + 1200
    while True:
        runs = []
        for page in range(1, 11):
            request = urllib.request.Request(
                f"https://api.github.com/repos/{repo}/actions/runs?head_sha={sha}&per_page=100&page={page}",
                headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"})
            with urllib.request.urlopen(request, timeout=30) as response:
                batch = json.load(response)["workflow_runs"]
            runs.extend(batch)
            if len(batch) < 100:
                break
        failed, pending = evaluate(runs, sha)
        if failed:
            raise SystemExit("Release blocked by failed checks: " + ", ".join(failed))
        if not pending:
            print(f"Verification passed for {sha}; version {version}")
            return
        if time.monotonic() >= deadline:
            raise SystemExit("Release blocked: missing or unfinished checks: " + ", ".join(pending))
        print("Waiting for exact-commit checks: " + ", ".join(pending), flush=True)
        time.sleep(15)


if __name__ == "__main__":
    main()
