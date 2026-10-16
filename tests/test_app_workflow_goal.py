import json
import tempfile
import unittest
from pathlib import Path

from lain.app.control import AppController


class CloudProfiles:
    def activeBindingJson(self):
        return json.dumps({
            "profile_id": "cloud-test",
            "mode": "cloud",
            "protocol": "openai_compatible_v1",
            "base_url": "https://planner.example.invalid/v1",
            "model": "test-model",
            "credential_ref": "cred_0123456789abcdef0123456789abcdef",
            "timeout_seconds": 30.0,
            "max_response_bytes": 65536,
            "response_mode": "json_schema",
            "allow_insecure_lan_http": False,
        })


class FakeNativeBridge:
    def __init__(self, decision):
        self.decision = decision
        self.calls = []

    def execute(self, binding_json, body):
        self.calls.append(json.loads(body))
        return json.dumps({
            "ok": True,
            "body": json.dumps({
                "choices": [{
                    "message": {"content": json.dumps(self.decision)},
                    "finish_reason": "stop",
                }]
            }),
        })

    def cancel(self):
        pass


class WorkflowGoalTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def make_app(self, decision):
        bridge = FakeNativeBridge(decision)
        app = AppController(self.root, planner_profiles=CloudProfiles(), planner_bridge=bridge)
        return app, bridge

    @staticmethod
    def send(app, command, **kwargs):
        return json.loads(app.dispatch(json.dumps({
            "version": 1, "command": command, "arguments": kwargs,
        })))

    def test_hosted_goal_turns_into_real_verified_composite_task(self):
        decision = {
            "status": "continue", "reason": "write and copy",
            "actions": [
                {"type": "file.write_text", "arguments": {
                    "path": "hello.txt", "content": "from a model", "overwrite": None}},
                {"type": "file.copy", "arguments": {
                    "source": "hello.txt", "destination": "backup.txt", "overwrite": None}},
            ],
        }
        app, bridge = self.make_app(decision)
        response = self.send(app, "workflow_goal", goal="Write and back up a note")
        self.assertTrue(response["ok"], response)
        self.assertEqual(response["workflow"]["status"], "complete")
        self.assertEqual((self.root / "workspace" / "backup.txt").read_text(), "from a model")
        self.assertNotIn("from a model", json.dumps(response))
        self.assertEqual(len(bridge.calls), 1)
        self.assertEqual(bridge.calls[0]["model"], "test-model")

    def test_claimed_model_completion_without_actions_is_not_a_verified_task(self):
        app, bridge = self.make_app({
            "status": "complete", "reason": "I already did it", "actions": [],
        })
        response = self.send(app, "workflow_goal", goal="Create a file")
        self.assertTrue(response["ok"])
        self.assertEqual(response["workflow"]["status"], "no_action")
        self.assertFalse((self.root / "workspace" / "hello.txt").exists())

    def test_missing_provider_and_untrusted_command_fail_closed(self):
        app = AppController(self.root)
        self.assertFalse(self.send(app, "workflow_goal", goal="write a file")["ok"])
        hostile, _ = self.make_app({
            "status": "continue", "reason": "run shell",
            "actions": [{"type": "shell.exec", "arguments": {"command": "touch pwned"}}],
        })
        self.assertFalse(self.send(hostile, "workflow_goal", goal="run shell")["ok"])
        self.assertFalse((self.root / "workspace" / "pwned").exists())


if __name__ == "__main__":
    unittest.main()
