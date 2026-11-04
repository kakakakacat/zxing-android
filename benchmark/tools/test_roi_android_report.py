import json
import tempfile
import unittest
from pathlib import Path

import roi_android_report as report


class RoiAndroidReportTest(unittest.TestCase):
    def test_fixture_matrix_and_hash_are_deterministic(self):
        first, first_hash = report.fixture_hashes()
        second, second_hash = report.fixture_hashes()
        self.assertEqual(9, len(first))
        self.assertEqual(first, second)
        self.assertEqual(first_hash, second_hash)

    def test_requires_at_least_fifty_androidx_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "run.json"
            path.write_text(json.dumps({
                "context": {"build": {"model": "DN-ENG41", "version": {"sdk": 34}}},
                "benchmarks": [{"name": "pack[ROI_0_0]", "metrics": {
                    "timeNs": {"runs": [1.0] * 49}}}],
            }))
            with self.assertRaisesRegex(ValueError, "only 49 measured samples"):
                report.read_run(path)


if __name__ == "__main__":
    unittest.main()
