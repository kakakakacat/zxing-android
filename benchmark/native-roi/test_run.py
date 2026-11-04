# SPDX-License-Identifier: Apache-2.0
import json
import tempfile
import unittest
from pathlib import Path
import run


class ProtocolTest(unittest.TestCase):
    def test_fixtures_have_identical_logical_roi_across_layouts(self):
        with tempfile.TemporaryDirectory() as folder:
            cases,manifest=run.fixtures(Path(folder))
            self.assertEqual(9,len(cases))
            for resolution in ('VGA','HD','FHD'):
                self.assertEqual(1,len({c['expected_sha256'] for c in cases if c['case'].startswith(resolution)}))
            self.assertEqual(9,len(manifest.read_text().splitlines()))

    def test_summary_uses_process_medians_not_pooled_repeats(self):
        # Unequal repeat counts must not give a process extra weight.
        report={'fixtures':[{'case':'VGA_packed'}], 'runs':[]}
        for backend in run.BACKENDS:
            for values in ([1,2,3],[100]*9,[10,11,12]):
                report['runs'].append({'backend':backend,'cases':[{'case':'VGA_packed','ns_per_copy':values}]})
        summary=run.summary(report)[0]
        self.assertEqual(11,summary['cpp']['median_ns'])
        self.assertEqual([2,100,11],summary['cpp']['process_medians_ns'])


if __name__=='__main__': unittest.main()
