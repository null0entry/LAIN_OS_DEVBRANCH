import tempfile
import unittest
from pathlib import Path

from lain.config import RuntimeConfig
from lain.errors import ErrorCode, LainError
from lain.runtime.engine import RuntimeEngine
from lain.workflows.engine import WorkflowRunner


class WorkflowRunnerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.runtime = RuntimeEngine(RuntimeConfig.for_workspace(self.root))
        self.runner = WorkflowRunner(self.runtime, self.root / ".workflow-state")

    def plan(self):
        return [
            {"id": "move", "capability": "file.move",
             "arguments": {"source": "b.txt", "destination": "c.txt"},
             "depends_on": ["copy"]},
            {"id": "copy", "capability": "file.copy",
             "arguments": {"source": "a.txt", "destination": "b.txt"},
             "depends_on": ["write"]},
            {"id": "write", "capability": "file.write_text",
             "arguments": {"path": "a.txt", "content": "hello"},
             "depends_on": []},
        ]

    def test_dependency_order_executes_real_verified_actions(self):
        key = self.runner.create(self.plan())
        first = self.runner.advance(key)
        self.assertEqual(first["completed"], ["write"])
        self.assertEqual(first["status"], "pending")
        self.assertEqual(self.runner.advance(key)["completed"], ["write", "copy"])
        last = self.runner.advance(key)
        self.assertEqual(last["status"], "complete")
        self.assertEqual(last["completed"], ["write", "copy", "move"])
        self.assertEqual((self.root / "c.txt").read_text(), "hello")
        self.assertFalse((self.root / "b.txt").exists())
        self.assertEqual([r["verification"] for r in last["results"]], ["passed"] * 3)
        self.assertEqual(len([r for r in self.runtime.audit.records() if r.get("execution_attempted")]), 3)

    def test_resume_skips_completed_actions_without_mutating_again(self):
        key = self.runner.create(self.plan())
        self.runner.advance(key)
        # An intervening edit must not cause the first step to run again.
        (self.root / "a.txt").write_text("later")
        resumed = WorkflowRunner(RuntimeEngine(self.runtime.config), self.root / ".workflow-state")
        self.assertEqual(resumed.advance(key)["completed"], ["write", "copy"])
        self.assertEqual((self.root / "b.txt").read_text(), "later")
        self.assertEqual(len([r for r in self.runtime.audit.records() if r.get("execution_attempted") and r.get("capability") == "file.write_text"]), 1)

    def test_invalid_graph_fails_before_effects(self):
        base = self.plan()
        invalid = [
            base + [dict(base[0])],
            [{**base[2], "depends_on": ["ghost"]}],
            [{**base[2], "depends_on": ["write"]}],
            [{**base[2], "capability": "shell.exec"}],
            [{**base[2], "arguments": {"path": "x", "content": "x", "surprise": 2}}],
            [{**base[2], "id": "../escape"}],
            [{**base[2], "arguments": {"path": "../outside.txt", "content": "x"}}],
            [{**base[2], "extra": "ignored?"}],
            [{"id": "x", "capability": "file.write_text", "arguments": "bad", "depends_on": []}],
        ]
        for case in invalid:
            with self.subTest(case=case), self.assertRaises(LainError):
                self.runner.create(case)
        self.assertFalse((self.root / "a.txt").exists())
        self.assertFalse((self.root / "outside.txt").exists())

    def test_confirmation_is_explicit_and_never_inferred_from_text(self):
        (self.root / "a.txt").write_text("original")
        steps = [{"id": "overwrite", "capability": "file.write_text",
                  "arguments": {"path": "a.txt", "content": "replacement", "overwrite": True},
                  "depends_on": []}]
        key = self.runner.create(steps)
        paused = self.runner.advance(key)
        self.assertEqual(paused["status"], "awaiting_confirmation")
        self.assertEqual((self.root / "a.txt").read_text(), "original")
        confirmed = self.runner.advance(key, confirmed_action_ids=frozenset({"s1"}))
        self.assertEqual(confirmed["status"], "complete")
        self.assertEqual((self.root / "a.txt").read_text(), "replacement")
        self.assertEqual(len([r for r in self.runtime.audit.records() if r.get("execution_attempted")]), 1)

    def test_in_flight_crash_state_fails_closed(self):
        key = self.runner.create(self.plan())
        # Simulates a crash after intent was checkpointed but before a receipt.
        self.runner._save(key, self.runner._load(key) | {"in_flight": "s1"})
        with self.assertRaises(LainError) as caught:
            self.runner.advance(key)
        self.assertEqual(caught.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertFalse((self.root / "a.txt").exists())

    def test_budget_limit_and_nonserializable_arguments_fail(self):
        with self.assertRaises(LainError):
            self.runner.create(self.plan() * 20)
        with self.assertRaises(LainError):
            self.runner.create([{**self.plan()[2], "arguments": {"path": "a", "content": object()}}])
        with self.assertRaises(LainError):
            self.runner.advance("invalid-id")


if __name__ == "__main__":
    unittest.main()
