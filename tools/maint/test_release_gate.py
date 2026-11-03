import unittest
from release_gate import evaluate, validate_version, REQUIRED

class ReleaseGateTest(unittest.TestCase):
    def runs(self):
        return [dict(id=i, path=f'.github/workflows/{p}', head_sha='new', status='completed',
                     conclusion='success') for i,p in enumerate(sorted(REQUIRED))]
    def test_success_requires_every_workflow_on_exact_commit(self):
        self.assertEqual(([], []), evaluate(self.runs(), 'new'))
        self.assertEqual(sorted(REQUIRED), evaluate(self.runs(), 'old')[1])
    def test_failure_cannot_be_hidden_by_a_green_build(self):
        runs=self.runs(); runs[0]['conclusion']='failure'
        self.assertTrue(evaluate(runs,'new')[0])
    def test_latest_retry_wins(self):
        runs=self.runs(); runs.append(dict(runs[0], id=100, conclusion='cancelled'))
        self.assertTrue(evaluate(runs,'new')[0])
        runs[-1]['status']='in_progress'
        self.assertTrue(evaluate(runs,'new')[1])
    def test_missing_or_skipped_is_not_success(self):
        runs=self.runs(); runs.pop()
        self.assertTrue(evaluate(runs,'new')[1])
        runs=self.runs(); runs[0]['conclusion']='skipped'
        self.assertTrue(evaluate(runs,'new')[0])
    def test_version_cannot_be_shell_code(self):
        for v in ['0.2.1','1.0.0-rc.1']:
            self.assertEqual(v,validate_version(v))
        for v in ['v0.2.1','0.2.1; echo bad','$(id)','../x','0.2.1\n']:
            with self.assertRaises(ValueError): validate_version(v)

if __name__=='__main__': unittest.main()
