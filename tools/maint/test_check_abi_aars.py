import pathlib
import tempfile
import unittest
import zipfile
from check_abi_aars import verify


def elf(bits, machine):
    header = bytearray(64)
    header[:4] = b"\x7fELF"
    header[4:6] = bytes((bits, 1))
    header[18:20] = machine.to_bytes(2, "little")
    return bytes(header)


class AbiAarTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = pathlib.Path(self.temporary.name)
        common = {"classes.jar": b"identical classes", "AndroidManifest.xml": b"manifest",
                  "res/values/strings.xml": b"resources", "R.txt": b"symbols"}
        self.complete = dict(common, **{
            "jni/arm64-v8a/libzxingnative.so": elf(2, 183),
            "jni/armeabi-v7a/libzxingnative.so": elf(1, 40)})
        self.split = {**common, "jni/arm64-v8a/libzxingnative.so": elf(2, 183)}

    def archive(self, name, entries):
        path = self.directory / name
        with zipfile.ZipFile(path, "w") as out:
            for key, value in entries.items():
                out.writestr(key, value)
        return path

    def validate(self):
        verify(self.archive("universal.aar", self.complete),
               {"arm64-v8a": self.archive("arm64.aar", self.split)})

    def test_accepts_only_removed_native_payload(self):
        self.validate()

    def test_rejects_other_abi_in_split(self):
        self.split.update({"jni/armeabi-v7a/libzxingnative.so": elf(1, 40)})
        with self.assertRaisesRegex(ValueError, "unexpected native"):
            self.validate()

    def test_rejects_wrong_elf_bits(self):
        self.split["jni/arm64-v8a/libzxingnative.so"] = elf(1, 183)
        with self.assertRaisesRegex(ValueError, "incorrect ELF"):
            self.validate()

    def test_rejects_wrong_machine(self):
        self.split["jni/arm64-v8a/libzxingnative.so"] = elf(2, 62)
        with self.assertRaisesRegex(ValueError, "incorrect ELF"):
            self.validate()

    def test_rejects_changed_bytecode(self):
        self.split["classes.jar"] = b"changed"
        with self.assertRaisesRegex(ValueError, "changes contents"):
            self.validate()

    def test_rejects_missing_native_library(self):
        del self.split["jni/arm64-v8a/libzxingnative.so"]
        with self.assertRaisesRegex(ValueError, "missing libzxingnative"):
            self.validate()

    def test_rejects_hidden_native_payload(self):
        self.split["assets/extra.so"] = elf(1, 40)
        with self.assertRaisesRegex(ValueError, "unexpected native"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
