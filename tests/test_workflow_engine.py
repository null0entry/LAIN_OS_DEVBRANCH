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

    def test_verified_artifact_bindings_chain_file_steps(self):
        plan = [
            {"id": "copy", "capability": "file.copy", "arguments": {
                "source": {"$artifact": "write.path"}, "destination": "copy.txt"
            }, "depends_on": ["write"]},
            {"id": "write", "capability": "file.write_text", "arguments": {
                "path": "source.txt", "content": "provenance"
            }, "depends_on": []},
        ]
        key = self.runner.create(plan)
        self.assertEqual(self.runner.advance(key)["completed"], ["write"])
        finished = self.runner.advance(key)
        self.assertEqual(finished["status"], "complete")
        self.assertEqual((self.root / "copy.txt").read_text(), "provenance")
        self.assertNotIn("source.txt", str(finished))

    def test_tampered_artifact_source_blocks_follow_on_effect(self):
        plan = [
            {"id": "write", "capability": "file.write_text", "arguments": {
                "path": "source.txt", "content": "trusted"
            }, "depends_on": []},
            {"id": "copy", "capability": "file.copy", "arguments": {
                "source": {"$artifact": "write.path"}, "destination": "copy.txt"
            }, "depends_on": ["write"]},
        ]
        key = self.runner.create(plan)
        self.runner.advance(key)
        (self.root / "source.txt").write_text("changed externally")
        with self.assertRaises(LainError):
            self.runner.advance(key)
        self.assertFalse((self.root / "copy.txt").exists())

    def test_artifact_refs_are_not_model_controlled_paths_or_data(self):
        good = self.plan()
        bad = [
            {"id": "copy", "capability": "file.copy", "arguments": {
                "source": {"$artifact": "missing.path"}, "destination": "copy.txt"
            }, "depends_on": ["write"]},
            {"id": "write", "capability": "file.write_text", "arguments": {
                "path": "source.txt", "content": "trusted"
            }, "depends_on": []},
        ]
        invalid = [
            bad,  # unresolved artifact
            [{**bad[0], "arguments": {"source": {"$artifact": "write.path"}, "destination": "copy.txt"}, "depends_on": []}, bad[1]],
            [{**bad[0], "arguments": {"source": {"$artifact": "write.content"}, "destination": "copy.txt"}}, bad[1]],
            [{**bad[0], "arguments": {"source": {"$artifact": "write.path", "path": "/tmp/other"}, "destination": "copy.txt"}}, bad[1]],
            [{**bad[0], "arguments": {"source": {"$artifact": "write.path"}, "destination": {"$artifact": "write.path"}}}, bad[1]],
            [{"id": "msg", "capability": "android.toast", "arguments": {"content": {"$artifact": "write.path"}}, "depends_on": ["write"]}, bad[1]],
        ]
        for plan in invalid:
            with self.subTest(plan=plan), self.assertRaises(LainError):
                self.runner.create(plan)
        self.assertFalse((self.root / "source.txt").exists())
        self.assertFalse((self.root / "copy.txt").exists())

    def test_run_until_stop_completes_three_steps_and_does_not_replay(self):
        key = self.runner.create(self.plan())
        state = self.runner.run_until_stop(key)
        self.assertEqual(state["status"], "complete")
        self.assertEqual(state["completed"], ["write", "copy", "move"])
        self.assertEqual((self.root / "c.txt").read_text(), "hello")
        self.assertEqual(self.runner.run_until_stop(key)["status"], "complete")
        self.assertEqual(len([r for r in self.runtime.audit.records() if r.get("execution_attempted")]), 3)

    def test_run_until_stop_pauses_for_owner_confirmation(self):
        target = self.root / "protected.txt"
        target.write_text("original")
        key = self.runner.create([
            {"id": "protected", "capability": "file.write_text",
             "arguments": {"path": "protected.txt", "content": "new", "overwrite": True},
             "depends_on": []},
            {"id": "after", "capability": "file.write_text",
             "arguments": {"path": "after.txt", "content": "done"},
             "depends_on": ["protected"]},
        ])
        result = self.runner.run_until_stop(key)
        self.assertEqual(result["status"], "awaiting_confirmation")
        self.assertEqual(target.read_text(), "original")
        self.assertFalse((self.root / "after.txt").exists())

    def test_inspection_reports_uncertain_in_flight_action_truthfully(self):
        key = self.runner.create(self.plan())
        self.runner._save(key, self.runner._load(key) | {"in_flight": "s1"})
        observed = self.runner.inspect(key)
        self.assertEqual(observed["status"], "uncertain")
        self.assertEqual(observed["current_action_id"], "s1")
        self.assertEqual(observed["completed"], [])
        with self.assertRaises(LainError):
            self.runner.run_until_stop(key)


    def test_corrupt_checkpoint_cannot_fabricate_completed_work(self):
        key = self.runner.create(self.plan())
        checkpoint = self.runner._load(key)
        checkpoint["completed"] = ["write", "copy", "move"]
        checkpoint["status"] = "complete"
        self.runner._save(key, checkpoint)
        with self.assertRaises(LainError) as caught:
            self.runner.inspect(key)
        self.assertEqual(caught.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertFalse((self.root / "a.txt").exists())

    def test_corrupt_checkpoint_cannot_replace_trusted_action_identity(self):
        key = self.runner.create(self.plan())
        checkpoint = self.runner._load(key)
        checkpoint["steps"][0]["action_id"] = "model-approved"
        self.runner._save(key, checkpoint)
        with self.assertRaises(LainError) as caught:
            self.runner.advance(key)
        self.assertEqual(caught.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertFalse((self.root / "a.txt").exists())

    def test_stop_callback_pauses_before_scheduling_next_action(self):
        key = self.runner.create(self.plan())
        checks = 0

        def stop_after_first():
            nonlocal checks
            checks += 1
            return checks > 1

        paused = self.runner.run_until_stop(key, stop_requested=stop_after_first)
        self.assertEqual(paused["status"], "paused")
        self.assertEqual(paused["completed"], ["write"])
        self.assertFalse((self.root / "b.txt").exists())
        finished = self.runner.run_until_stop(key)
        self.assertEqual(finished["status"], "complete")
        self.assertEqual((self.root / "c.txt").read_text(), "hello")


if __name__ == "__main__":
    unittest.main()
