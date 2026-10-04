"""App control contracts; execution deferred at the owner's request."""
import json
import tempfile
import threading
import unittest
from pathlib import Path

from lain.app.control import AppController


class FakePlannerProfiles:
    def __init__(self, binding):
        self.binding = binding

    def activeBindingJson(self):
        return json.dumps(self.binding, separators=(",", ":"))


class AppControlTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.app = AppController(Path(self.directory.name))

    def send(self, command, **arguments):
        return json.loads(self.app.dispatch(json.dumps({
            "version": 1, "command": command, "arguments": arguments,
        })))

    def settle(self):
        for _ in range(20):
            if not self.app.advance():
                return
        self.fail("demo did not settle within bounded steps")


    def test_native_profile_binding_is_pinned_and_reported_when_session_starts(self):
        binding_a = {
            "profile_id": "cloud-a",
            "mode": "cloud",
            "protocol": "openai_compatible_v1",
            "base_url": "https://a.example.invalid/v1",
            "model": "model-a",
            "credential_ref": "cred_0123456789abcdef0123456789abcdef",
            "timeout_seconds": 30.0,
            "max_response_bytes": 1048576,
            "response_mode": "json_schema",
            "allow_insecure_lan_http": False,
        }
        binding_b = {
            **binding_a,
            "profile_id": "cloud-b",
            "base_url": "https://b.example.invalid/v1",
            "model": "model-b",
            "credential_ref": "cred_fedcba9876543210fedcba9876543210",
        }
        profiles = FakePlannerProfiles(binding_a)
        self.app = AppController(Path(self.directory.name), planner_profiles=profiles)

        reply = self.send("start", goal="Create demo file")
        self.assertTrue(reply["ok"])
        profiles.binding = binding_b

        stored = self.app.store.load(reply["session"]["session_id"])
        self.assertEqual(stored.planner_binding.profile_id, "cloud-a")
        self.assertEqual(stored.planner_binding.model, "model-a")

        snapshot = self.send("inspect", session_id=reply["session"]["session_id"])
        self.assertEqual(
            snapshot["session"]["planner"],
            {"profile_id": "cloud-a", "mode": "cloud", "model": "model-a"},
        )

    def test_native_profile_source_rejects_non_opaque_credential_reference(self):
        profiles = FakePlannerProfiles({
            "profile_id": "cloud-a",
            "mode": "cloud",
            "protocol": "openai_compatible_v1",
            "base_url": "https://a.example.invalid/v1",
            "model": "model-a",
            "credential_ref": "sk-raw-secret",
            "timeout_seconds": 30.0,
            "max_response_bytes": 1048576,
            "response_mode": "json_schema",
            "allow_insecure_lan_http": False,
        })
        self.app = AppController(Path(self.directory.name), planner_profiles=profiles)

        reply = self.send("start", goal="Create demo file")
        self.assertFalse(reply["ok"])
        self.assertEqual(self.app.store.list_sessions(), ())

    def test_unknown_control_command_rejected(self):
        self.assertFalse(self.send("shell", command_line="id")["ok"])

    def test_oversized_or_duplicate_json_rejected(self):
        self.assertFalse(json.loads(self.app.dispatch(" " * 65537))["ok"])
        self.assertFalse(json.loads(self.app.dispatch(
            '{"version":1,"version":1,"command":"sessions","arguments":{}}'
        ))["ok"])

    def test_demo_creates_real_scoped_file(self):
        reply = self.send("start", goal="Create demo file")
        self.assertTrue(reply["ok"])
        self.settle()
        self.assertEqual((Path(self.directory.name) / "workspace" / "demo.txt").read_text(),
                         "Hello from LAIN_OS.\n")
        state = self.send("inspect", session_id=reply["session"]["session_id"])
        self.assertEqual(state["session"]["status"], "complete")
        self.assertEqual(state["session"]["actions"][0]["verification"], "passed")

    def test_demo_file_preset_is_repeatable_without_overwrite(self):
        first = self.send("start", goal="Create demo file")
        self.assertTrue(first["ok"])
        self.settle()
        first_state = self.send("inspect", session_id=first["session"]["session_id"])
        self.assertEqual(first_state["session"]["status"], "complete")

        second = self.send("start", goal="Create demo file")
        self.assertTrue(second["ok"])
        self.settle()
        second_state = self.send("inspect", session_id=second["session"]["session_id"])
        self.assertEqual(second_state["session"]["status"], "complete")

        workspace = Path(self.directory.name) / "workspace"
        self.assertEqual((workspace / "demo.txt").read_text(), "Hello from LAIN_OS.\n")
        self.assertEqual((workspace / "demo-2.txt").read_text(), "Hello from LAIN_OS.\n")
        self.assertEqual(second_state["session"]["actions"][0]["verification"], "passed")

    def test_stop_prevents_next_action(self):
        reply = self.send("start", goal="Create demo file")
        sid = reply["session"]["session_id"]
        self.app.advance()  # Planning only; no effect yet.
        self.assertTrue(self.send("stop", session_id=sid)["ok"])
        self.settle()
        self.assertFalse((Path(self.directory.name) / "workspace" / "demo.txt").exists())
        self.assertEqual(self.send("inspect", session_id=sid)["session"]["status"], "cancelled")

    def paused_share(self):
        reply = self.send("start", goal="Share demo text")
        self.settle()
        return self.send("inspect", session_id=reply["session"]["session_id"])["session"]

    def test_approval_bound_to_exact_stored_action(self):
        session = self.paused_share()
        self.assertEqual(session["status"], "paused_confirmation")
        self.assertFalse(self.send("approve", session_id=session["session_id"], token="invented")["ok"])
        self.assertTrue(self.send("approve", session_id=session["session_id"],
                                  token=session["approval"]["token"])["ok"])

    def test_approval_replay_rejected(self):
        session = self.paused_share()
        arguments = {"session_id": session["session_id"], "token": session["approval"]["token"]}
        self.assertTrue(self.send("approve", **arguments)["ok"])
        self.assertFalse(self.send("approve", **arguments)["ok"])

    def test_restart_requires_fresh_approval(self):
        session = self.paused_share()
        self.app = AppController(Path(self.directory.name))
        self.assertFalse(self.send("approve", session_id=session["session_id"],
                                   token=session["approval"]["token"])["ok"])
        self.assertFalse(self.app.advance())

    def test_sensitive_history_redacted(self):
        session = self.paused_share()
        serialized = json.dumps(session)
        self.assertNotIn("Hello from LAIN_OS.", serialized)
        self.assertIn("[REDACTED]", serialized)

    def test_restart_does_not_replay(self):
        self.send("start", goal="Create demo file")
        self.app.advance()
        self.app = AppController(Path(self.directory.name))
        self.assertFalse(self.app.advance())
        self.assertFalse((Path(self.directory.name) / "workspace" / "demo.txt").exists())

    def test_boolean_version_and_unknown_arguments_rejected(self):
        self.assertFalse(json.loads(self.app.dispatch(
            '{"version":true,"command":"sessions","arguments":{}}'
        ))["ok"])
        self.assertFalse(self.send("sessions", extra=True)["ok"])

    def test_stop_cancels_in_flight_planner_call(self):
        entered, cancelled = threading.Event(), threading.Event()

        class BlockingPlannerBridge:
            def execute(self, binding_json, request_body):
                entered.set()
                if not cancelled.wait(5):
                    raise RuntimeError("planner fixture was not cancelled")
                return json.dumps({"ok": False, "error": "PLANNER_CANCELLED"})

            def cancel(self):
                cancelled.set()

        profiles = FakePlannerProfiles({
            "profile_id": "cloud-a",
            "mode": "cloud",
            "protocol": "openai_compatible_v1",
            "base_url": "https://api.example.invalid/v1",
            "model": "model-a",
            "credential_ref": "cred_0123456789abcdef0123456789abcdef",
            "timeout_seconds": 30.0,
            "max_response_bytes": 1048576,
            "response_mode": "json_schema",
            "allow_insecure_lan_http": False,
        })
        self.app = AppController(
            Path(self.directory.name),
            planner_profiles=profiles,
            planner_bridge=BlockingPlannerBridge(),
        )
        sid = self.send("start", goal="Show battery")["session"]["session_id"]
        worker = threading.Thread(target=self.app.advance)
        worker.start()
        try:
            self.assertTrue(entered.wait(2))
            stopped = self.send("stop", session_id=sid)
            self.assertTrue(stopped["ok"])
            self.assertTrue(stopped["stop_requested"])
        finally:
            cancelled.set()
            worker.join(5)
        self.assertFalse(worker.is_alive())
        session = self.send("inspect", session_id=sid)["session"]
        self.assertEqual(session["status"], "cancelled")

    def test_stop_acknowledges_while_native_step_is_in_flight(self):
        entered, release = threading.Event(), threading.Event()

        class BlockingNative:
            def execute(self, name, arguments):
                entered.set()
                if not release.wait(5):
                    raise RuntimeError("fixture not released")
                return '{"status":"success","details":{"battery":{"percentage":50}}}'

        self.app = AppController(Path(self.directory.name), BlockingNative())
        sid = self.send("start", goal="Show battery")["session"]["session_id"]
        self.app.advance()
        worker = threading.Thread(target=self.app.advance)
        worker.start()
        try:
            self.assertTrue(entered.wait(2))
            self.assertTrue(self.send("stop", session_id=sid)["stop_requested"])
        finally:
            release.set()
            worker.join(5)
        self.assertFalse(worker.is_alive())
        session = self.send("inspect", session_id=sid)["session"]
        self.assertEqual(session["status"], "cancelled")
        self.assertEqual(session["actions"][0]["verification"], "passed")
