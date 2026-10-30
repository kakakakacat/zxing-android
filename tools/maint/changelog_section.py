#!/usr/bin/env python3
"""Print one version's section of CHANGELOG.md, for a GitHub Release body.

Keeping the release notes in the changelog and quoting them here means there is one
copy, so the Release and the repository cannot drift apart.

Exits non-zero if the version has no section, so a release cannot go out with notes
nobody wrote.
"""
import argparse
import re
import sys

PREAMBLE = (
    "Community maintenance build of the ZXing Android client. "
    "Not an official ZXing release, and not signed with the upstream key: "
    "this APK cannot update the published Barcode Scanner app.\n"
)


def extract(text, version):
    # Matches "## [1.2.3]" and "## 1.2.3", with or without a trailing date.
    pattern = re.compile(
        r"^##\s+\[?%s\]?.*$" % re.escape(version), re.MULTILINE)
    match = pattern.search(text)
    if not match:
        return None
    rest = text[match.end():]
    next_heading = re.search(r"^##\s", rest, re.MULTILINE)
    return rest[:next_heading.start()].strip() if next_heading else rest.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--changelog", default="CHANGELOG.md")
    ap.add_argument("--coordinates", default="io.github.kakakakacat.zxing:zxing-android-scanner")
    args = ap.parse_args()

    with open(args.changelog, encoding="utf-8") as f:
        text = f.read()

    body = extract(text, args.version)
    if body is None:
        sys.exit(
            "No '## [%s]' section in %s. Add the release notes before tagging."
            % (args.version, args.changelog))

    print(PREAMBLE)
    print(body)
    print()
    print("AAR coordinates (GitHub Packages):")
    print("`%s:%s`" % (args.coordinates, args.version))


if __name__ == "__main__":
    main()
