#!/usr/bin/env python3
"""Verify split AAR ABI/ELF identity and byte-identical common contents."""
import argparse
import pathlib
import zipfile

ABIS = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}


def read_archive(path):
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError(f"{path}: duplicate archive entries")
        result = {name: archive.read(name) for name in names}
    if "classes.jar" not in result or "AndroidManifest.xml" not in result:
        raise ValueError(f"{path}: missing AAR bytecode or manifest")
    return result


def check_libraries(contents, expected, label):
    seen = set()
    for name, payload in contents.items():
        if not name.endswith(".so"):
            continue
        parts = name.split("/")
        if len(parts) != 3 or parts[0] != "jni" or parts[1] not in expected:
            raise ValueError(f"{label}: unexpected native library {name}")
        abi = parts[1]
        elf_class, machine = ABIS[abi]
        if (len(payload) < 20 or payload[:4] != b"\x7fELF" or
                payload[4] != elf_class or payload[5] != 1 or
                int.from_bytes(payload[18:20], "little") != machine):
            raise ValueError(f"{label}: incorrect ELF architecture for {name}")
        if parts[2] == "libzxingnative.so":
            seen.add(abi)
    if seen != set(expected):
        raise ValueError(f"{label}: missing libzxingnative.so for {set(expected) - seen}")


def verify(universal, splits):
    complete = read_archive(universal)
    check_libraries(complete, ABIS, str(universal))
    for abi, path in splits.items():
        candidate = read_archive(path)
        check_libraries(candidate, {abi}, str(path))
        expected = {name: value for name, value in complete.items()
                    if not name.startswith("jni/") or name.startswith(f"jni/{abi}/")}
        if candidate != expected:
            raise ValueError(f"{path}: split changes contents other than removing another ABI")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--universal", type=pathlib.Path, required=True)
    parser.add_argument("--arm64", type=pathlib.Path, required=True)
    parser.add_argument("--arm32", type=pathlib.Path, required=True)
    args = parser.parse_args()
    verify(args.universal, {"arm64-v8a": args.arm64, "armeabi-v7a": args.arm32})
    for name, path in (("universal", args.universal), ("arm64-v8a", args.arm64), ("armeabi-v7a", args.arm32)):
        print(f"{name}: {path.stat().st_size} bytes; ABI and payload verified")


if __name__ == "__main__":
    main()
