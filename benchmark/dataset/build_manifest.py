#!/usr/bin/env python3
"""Build the versioned benchmark dataset manifest.

Sources are the Apache-2.0 blackbox images that ship with this repository
(core/src/test/resources/blackbox). Every sample is an independent source image
with its own `.txt` sidecar giving the expected decoded text; we never count
transformations of one image as several samples.

Usage: python3 benchmark/dataset/build_manifest.py [--out manifest.json]
"""
import argparse
import hashlib
import json
import os
import struct
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BLACKBOX = os.path.join(REPO, "core", "src", "test", "resources", "blackbox")
MANIFEST_VERSION = 1

# directory prefix -> (expected BarcodeFormat, scene tags)
FORMAT_BY_PREFIX = {
    "aztec": "AZTEC",
    "codabar": "CODABAR",
    "code128": "CODE_128",
    "code39": "CODE_39",
    "code93": "CODE_93",
    "datamatrix": "DATA_MATRIX",
    "ean13": "EAN_13",
    "ean8": "EAN_8",
    "itf": "ITF",
    "maxicode": "MAXICODE",
    "pdf417": "PDF_417",
    "qrcode": "QR_CODE",
    "rss14": "RSS_14",
    "rssexpanded": "RSS_EXPANDED",
    "rssexpandedstacked": "RSS_EXPANDED",
    "upca": "UPC_A",
    "upce": "UPC_E",
}

# Directories whose samples are expected to produce no result at all.
NEGATIVE_DIRS = {"falsepositives", "falsepositives-2"}
# Directories we deliberately leave out of the v1 set, with the reason.
SKIP_DIRS = {
    "multi-1": "multi-code images; the single-result API under test has no defined expectation",
    "multi-qrcode-1": "multi-code images; see multi-1",
    "unsupported": "formats the reader is not expected to support",
}
IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".gif")


def image_size(path):
    """Width/height from the file header, without an imaging dependency."""
    with open(path, "rb") as f:
        head = f.read(32)
        if head[:8] == b"\x89PNG\r\n\x1a\n":
            w, h = struct.unpack(">II", head[16:24])
            return int(w), int(h)
        if head[:6] in (b"GIF87a", b"GIF89a"):
            w, h = struct.unpack("<HH", head[6:10])
            return int(w), int(h)
        if head[:2] == b"\xff\xd8":
            f.seek(2)
            while True:
                b = f.read(1)
                while b and b != b"\xff":
                    b = f.read(1)
                while b == b"\xff":
                    b = f.read(1)
                if not b:
                    return None, None
                marker = b[0]
                if marker in range(0xC0, 0xCF) and marker not in (0xC4, 0xC8, 0xCC):
                    f.read(3)
                    h, w = struct.unpack(">HH", f.read(4))
                    return int(w), int(h)
                size = struct.unpack(">H", f.read(2))[0]
                f.seek(size - 2, os.SEEK_CUR)
    return None, None


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def scene_tags(dirname, stem):
    tags = []
    if dirname in NEGATIVE_DIRS:
        tags.append("negative")
    if dirname == "inverted":
        tags.append("inverted")
    if dirname == "partial":
        tags.append("partial")
    return tags


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "manifest.json"))
    args = ap.parse_args()

    if not os.path.isdir(BLACKBOX):
        sys.exit("blackbox resources not found at %s" % BLACKBOX)

    samples = []
    skipped = []
    for dirname in sorted(os.listdir(BLACKBOX)):
        d = os.path.join(BLACKBOX, dirname)
        if not os.path.isdir(d):
            continue
        if dirname in SKIP_DIRS:
            skipped.append({"dir": dirname, "reason": SKIP_DIRS[dirname]})
            continue
        prefix = dirname.rsplit("-", 1)[0] if dirname[-1].isdigit() else dirname
        expected_format = FORMAT_BY_PREFIX.get(prefix)
        negative = dirname in NEGATIVE_DIRS
        if expected_format is None and not negative and dirname not in ("inverted", "partial"):
            skipped.append({"dir": dirname, "reason": "no expected format mapping"})
            continue
        for name in sorted(os.listdir(d)):
            stem, ext = os.path.splitext(name)
            if ext.lower() not in IMAGE_EXTS:
                continue
            path = os.path.join(d, name)
            txt = os.path.join(d, stem + ".txt")
            expected_text = None
            if os.path.isfile(txt):
                with open(txt, "rb") as f:
                    expected_text = f.read().decode("utf-8", "replace")
            elif not negative:
                # No expectation on file: it cannot score correctness, so it is not a sample.
                continue
            w, h = image_size(path)
            rel = os.path.relpath(path, REPO)
            samples.append({
                "sample_id": "%s/%s" % (dirname, stem),
                "path": rel,
                "sha256": sha256(path),
                "width": w,
                "height": h,
                # Source frames are 8-bit greyscale derived from the image; stride == width.
                "frame_format": "GREY8",
                "stride": w,
                "rotation": 0,
                "scene_tags": scene_tags(dirname, stem) or ["clear"],
                "expected_format": None if negative else expected_format,
                "expected_text": None if negative else expected_text,
                # Expected text is compared verbatim, including trailing newline handling
                # done by the runner; byte comparison is not applicable for these sources.
                "compare": "text_exact" if not negative else "no_result",
                "license": "Apache-2.0 (zxing project test corpus)",
                "source": "core/src/test/resources/blackbox",
            })

    samples.sort(key=lambda s: s["sample_id"])
    body = {
        "manifest_version": MANIFEST_VERSION,
        "sample_count": len(samples),
        "skipped": skipped,
        "samples": samples,
    }
    # The dataset hash covers the sample identities and their content hashes, so a
    # results file can state exactly which dataset produced it.
    digest = hashlib.sha256()
    for s in samples:
        digest.update(("%s|%s\n" % (s["sample_id"], s["sha256"])).encode())
    body["dataset_hash"] = digest.hexdigest()

    with open(args.out, "w") as f:
        json.dump(body, f, indent=1, sort_keys=True)
        f.write("\n")
    print("%d samples, dataset_hash=%s -> %s" % (len(samples), body["dataset_hash"][:16], args.out))


if __name__ == "__main__":
    main()
