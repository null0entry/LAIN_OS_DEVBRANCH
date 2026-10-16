import json
import tempfile
import unittest
from pathlib import Path

from lain.app.control import AppController


class AppWorkflowProtocolTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.app = AppController(self.root)

    def send(self, command, **arguments):
        return json.loads(self.app.dispatch(json.dumps({
            "version": 1, "command": command, "arguments": arguments,
        })))

    def test_android_dispatch_bridge_runs_verified_multistep_workflow(self):
        steps = [
            {"id": "copy", "capability": "file.copy",
             "arguments": {"source": "a.txt", "destination": "b.txt"},
             "depends_on": ["write"]},
            {"id": "write", "capability": "file.write_text",
             "arguments": {"path": "a.txt", "content": "private value"},
             "depends_on": []},
        ]
        created = self.send("workflow_create", steps=steps)
        self.assertTrue(created["ok"])
        key = created["workflow"]["workflow_id"]
        self.assertNotIn("private value", json.dumps(created))
        self.assertEqual(self.send("workflow_advance", workflow_id=key)["workflow"]["completed"], ["write"])
        ended = self.send("workflow_advance", workflow_id=key)
        self.assertEqual(ended["workflow"]["status"], "complete")
        self.assertNotIn("private value", json.dumps(ended))
        self.assertEqual((self.root / "workspace" / "b.txt").read_text(), "private value")
        self.assertEqual(self.send("workflow_inspect", workflow_id=key)["workflow"]["status"], "complete")

    def test_dispatch_rejects_unavailable_tool_before_side_effect(self):
        steps = [
            {"id": "first", "capability": "file.write_text",
             "arguments": {"path": "x.txt", "content": "x"}, "depends_on": []},
            {"id": "second", "capability": "shell.exec",
             "arguments": {}, "depends_on": ["first"]},
        ]
        self.assertFalse(self.send("workflow_create", steps=steps)["ok"])
        self.assertFalse((self.root / "workspace" / "x.txt").exists())

    def test_dispatch_cannot_self_confirm_sensitive_effect(self):
        target = self.root / "workspace" / "x.txt"
        target.write_text("original")
        steps = [{"id": "overwrite", "capability": "file.write_text",
                  "arguments": {"path": "x.txt", "content": "replacement", "overwrite": True},
                  "depends_on": []}]
        created = self.send("workflow_create", steps=steps)
        self.assertTrue(created["ok"])
        key = created["workflow"]["workflow_id"]
        paused = self.send("workflow_advance", workflow_id=key)
        self.assertEqual(paused["workflow"]["status"], "awaiting_confirmation")
        self.assertEqual(target.read_text(), "original")
        self.assertFalse(self.send("workflow_advance", workflow_id=key,
                                   confirmed_action_ids=["s1"])["ok"])
        self.assertEqual(target.read_text(), "original")

    def test_sensitive_workflow_approval_requires_fresh_token_and_single_use(self):
        target = self.root / "workspace" / "x.txt"
        target.write_text("original")
        steps = [{"id": "overwrite", "capability": "file.write_text",
                  "arguments": {"path": "x.txt", "content": "authorized", "overwrite": True},
                  "depends_on": []}]
        key = self.send("workflow_create", steps=steps)["workflow"]["workflow_id"]
        paused = self.send("workflow_advance", workflow_id=key)
        self.assertEqual(paused["workflow"]["status"], "awaiting_confirmation")
        token = paused["approval"]["token"]
        self.assertTrue(token)
        self.assertFalse(self.send("workflow_approve", workflow_id=key, token="fabricated")["ok"])
        self.assertEqual(target.read_text(), "original")
        accepted = self.send("workflow_approve", workflow_id=key, token=token)
        self.assertTrue(accepted["ok"])
        self.assertEqual(accepted["workflow"]["status"], "complete")
        self.assertEqual(target.read_text(), "authorized")
        self.assertFalse(self.send("workflow_approve", workflow_id=key, token=token)["ok"])

    def test_restart_invalidates_sensitive_workflow_approval(self):
        target = self.root / "workspace" / "x.txt"
        target.write_text("original")
        key = self.send("workflow_create", steps=[
            {"id": "overwrite", "capability": "file.write_text", "depends_on": [],
             "arguments": {"path": "x.txt", "content": "new", "overwrite": True}}
        ])["workflow"]["workflow_id"]
        token = self.send("workflow_advance", workflow_id=key)["approval"]["token"]
        self.app = AppController(self.root)
        self.assertFalse(self.send("workflow_approve", workflow_id=key, token=token)["ok"])
        self.assertEqual(target.read_text(), "original")
        refreshed = self.send("workflow_inspect", workflow_id=key)
        self.assertTrue(refreshed["approval"]["token"] != token)
        self.assertTrue(self.send("workflow_approve", workflow_id=key,
                                  token=refreshed["approval"]["token"])["ok"])

    def test_run_command_completes_composite_task_without_manual_steps(self):
        created = self.send("workflow_create", steps=[
            {"id": "copy", "capability": "file.copy",
             "arguments": {"source": {"$artifact": "write.path"}, "destination": "copied.txt"},
             "depends_on": ["write"]},
            {"id": "write", "capability": "file.write_text",
             "arguments": {"path": "original.txt", "content": "one instruction"},
             "depends_on": []},
        ])
        self.assertTrue(created["ok"])
        result = self.send("workflow_run", workflow_id=created["workflow"]["workflow_id"])
        self.assertTrue(result["ok"])
        self.assertEqual(result["workflow"]["status"], "complete")
        self.assertEqual((self.root / "workspace" / "copied.txt").read_text(), "one instruction")


if __name__ == "__main__":
    unittest.main()
