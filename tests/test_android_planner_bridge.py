import json
import tempfile
import unittest
from pathlib import Path

from lain.agent import AgentBudget, AgentController, AgentPlanningService, AgentSessionStatus, AgentSessionStore
from lain.agent.models import OFFLINE_DEMO_BINDING, PlannerBinding
from lain.app.control import AppController
from lain.app.demo import DemoPlanner
from lain.app.planner_bridge import AndroidPlannerFactory
from lain.errors import ErrorCode, LainError
from lain.planning import AgentPlannerStatus
from lain.runtime.engine import RuntimeEngine
from lain.config import RuntimeConfig


class FakeNativePlannerBridge:
    def __init__(self, response=None):
        self.response = response or {
            "ok": True,
            "body": json.dumps({
                "choices": [{
                    "message": {
                        "content": json.dumps({
                            "status": "complete",
                            "reason": "finished",
                            "actions": [],
                        })
                    },
                    "finish_reason": "stop",
                }]
            }),
        }
        self.calls = []
        self.cancelled = False

    def execute(self, binding_json, request_body):
        self.calls.append((binding_json, request_body))
        return json.dumps(self.response, separators=(",", ":"))

    def cancel(self):
        self.cancelled = True


def cloud_binding(mode="cloud"):
    return PlannerBinding(
        profile_id=f"{mode}-primary",
        mode=mode,
        protocol="openai_compatible_v1",
        base_url="https://planner.example.invalid/v1",
        model="model-a",
        credential_ref="cred_0123456789abcdef0123456789abcdef" if mode == "cloud" else None,
        timeout_seconds=30.0,
        max_response_bytes=1_048_576,
        response_mode="json_schema",
        allow_insecure_lan_http=False,
    )


class AndroidPlannerFactoryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.workspace = Path(self.directory.name)

    def test_demo_binding_stays_offline_and_does_not_call_native_bridge(self):
        bridge = FakeNativePlannerBridge()
        planner = AndroidPlannerFactory(self.workspace, bridge)(OFFLINE_DEMO_BINDING)

        self.assertIsInstance(planner, DemoPlanner)
        self.assertEqual(bridge.calls, [])

    def test_cloud_and_local_use_pinned_binding_and_normalize_native_response(self):
        for mode in ("cloud", "local"):
            with self.subTest(mode=mode):
                bridge = FakeNativePlannerBridge()
                binding = cloud_binding(mode)
                planner = AndroidPlannerFactory(self.workspace, bridge)(binding)

                decision = planner.decide("finish", {"iteration_count": 0}, ())

                self.assertEqual(decision.status, AgentPlannerStatus.COMPLETE)
                self.assertEqual(decision.reason, "finished")
                self.assertEqual(decision.actions, ())
                self.assertEqual(len(bridge.calls), 1)
                sent_binding = json.loads(bridge.calls[0][0])
                sent_request = json.loads(bridge.calls[0][1])
                self.assertEqual(sent_binding["profile_id"], binding.profile_id)
                self.assertEqual(sent_binding["mode"], mode)
                self.assertEqual(sent_binding["credential_ref"], binding.credential_ref)
                self.assertEqual(sent_request["model"], "model-a")
                self.assertEqual(sent_request["max_completion_tokens"], 1024)
                self.assertNotIn("reasoning_effort", sent_request)
                self.assertNotIn("secret", bridge.calls[0][0].lower())

    def test_groq_gpt_oss_uses_low_reasoning_effort(self):
        bridge = FakeNativePlannerBridge()
        binding = PlannerBinding(
            profile_id="groq-primary",
            mode="cloud",
            protocol="openai_compatible_v1",
            base_url="https://api.groq.com/openai/v1",
            model="openai/gpt-oss-120b",
            credential_ref="cred_0123456789abcdef0123456789abcdef",
            timeout_seconds=30.0,
            max_response_bytes=1_048_576,
            response_mode="json_schema",
            allow_insecure_lan_http=False,
        )
        planner = AndroidPlannerFactory(self.workspace, bridge)(binding)

        planner.decide("finish", {"iteration_count": 0}, ())

        sent_request = json.loads(bridge.calls[0][1])
        self.assertEqual(sent_request["max_completion_tokens"], 1024)
        self.assertEqual(sent_request["reasoning_effort"], "low")

    def test_native_cancellation_maps_to_planner_cancelled(self):
        bridge = FakeNativePlannerBridge({"ok": False, "error": "PLANNER_CANCELLED"})
        planner = AndroidPlannerFactory(self.workspace, bridge)(cloud_binding())

        with self.assertRaises(LainError) as raised:
            planner.decide("finish", {"iteration_count": 0}, ())

        self.assertEqual(raised.exception.code, ErrorCode.PLANNER_CANCELLED)



class FakePlannerProfiles:
    def __init__(self, binding):
        self.binding = binding

    def activeBindingJson(self):
        return json.dumps(self.binding.to_dict(), separators=(",", ":"))


class AppControllerPlannerBridgeTests(unittest.TestCase):
    def test_cloud_session_uses_pinned_bridge_and_completes(self):
        with tempfile.TemporaryDirectory() as directory:
            binding = cloud_binding()
            bridge = FakeNativePlannerBridge()
            app = AppController(
                Path(directory),
                planner_profiles=FakePlannerProfiles(binding),
                planner_bridge=bridge,
            )
            reply = json.loads(app.dispatch(json.dumps({
                "version": 1,
                "command": "start",
                "arguments": {"goal": "finish"},
            })))
            self.assertTrue(reply["ok"])

            self.assertFalse(app.advance())

            stored = app.store.load(reply["session"]["session_id"])
            self.assertEqual(stored.status, AgentSessionStatus.COMPLETE)
            self.assertEqual(stored.planner_binding.profile_id, "cloud-primary")
            self.assertEqual(len(bridge.calls), 1)

    def test_local_transport_failure_never_falls_back(self):
        with tempfile.TemporaryDirectory() as directory:
            binding = cloud_binding("local")
            bridge = FakeNativePlannerBridge({"ok": False, "error": "PLANNER_CONNECTION_REFUSED"})
            app = AppController(
                Path(directory),
                planner_profiles=FakePlannerProfiles(binding),
                planner_bridge=bridge,
            )
            reply = json.loads(app.dispatch(json.dumps({
                "version": 1,
                "command": "start",
                "arguments": {"goal": "finish"},
            })))

            self.assertFalse(app.advance())

            stored = app.store.load(reply["session"]["session_id"])
            self.assertEqual(stored.status, AgentSessionStatus.FAILED)
            self.assertEqual(stored.planner_binding.mode, "local")
            self.assertEqual(len(bridge.calls), 1)

    def test_required_transport_failures_are_terminal_without_fallback(self):
        failures = (
            ("PLANNER_DNS_UNREACHABLE", AgentSessionStatus.FAILED),
            ("PLANNER_CONNECTION_REFUSED", AgentSessionStatus.FAILED),
            ("PLANNER_TLS_FAILED", AgentSessionStatus.FAILED),
            ("PLANNER_AUTH_REJECTED", AgentSessionStatus.FAILED),
            ("PLANNER_MODEL_NOT_FOUND", AgentSessionStatus.FAILED),
            ("PLANNER_TIMEOUT", AgentSessionStatus.FAILED),
            ("PLANNER_RATE_LIMITED", AgentSessionStatus.FAILED),
            ("PLANNER_SERVER_ERROR", AgentSessionStatus.FAILED),
            ("PLANNER_CANCELLED", AgentSessionStatus.CANCELLED),
            ("PLANNER_RESPONSE_TOO_LARGE", AgentSessionStatus.FAILED),
            ("PLANNER_RESPONSE_MALFORMED", AgentSessionStatus.FAILED),
            ("PLANNER_RESPONSE_UNSUPPORTED", AgentSessionStatus.FAILED),
            ("PLANNER_CREDENTIAL_MISSING", AgentSessionStatus.FAILED),
        )
        for error, expected_status in failures:
            with self.subTest(error=error), tempfile.TemporaryDirectory() as directory:
                binding = cloud_binding("local")
                bridge = FakeNativePlannerBridge({"ok": False, "error": error})
                app = AppController(
                    Path(directory),
                    planner_profiles=FakePlannerProfiles(binding),
                    planner_bridge=bridge,
                )
                reply = json.loads(app.dispatch(json.dumps({
                    "version": 1,
                    "command": "start",
                    "arguments": {"goal": "finish"},
                })))

                self.assertFalse(app.advance())

                stored = app.store.load(reply["session"]["session_id"])
                self.assertEqual(stored.status, expected_status)
                self.assertEqual(stored.planner_binding.mode, "local")
                self.assertEqual(stored.planner_binding.profile_id, "local-primary")
                self.assertEqual(len(bridge.calls), 1)

    def test_malformed_and_unknown_capability_outputs_never_execute(self):
        decisions = (
            "not-json",
            json.dumps({
                "status": "continue",
                "reason": "bypass",
                "actions": [{
                    "id": "untrusted-1",
                    "type": "unknown.capability",
                    "arguments": {},
                }],
            }),
        )
        for decision in decisions:
            with self.subTest(decision=decision), tempfile.TemporaryDirectory() as directory:
                response = {
                    "ok": True,
                    "body": json.dumps({
                        "choices": [{
                            "message": {"content": decision},
                            "finish_reason": "stop",
                        }],
                    }),
                }
                bridge = FakeNativePlannerBridge(response)
                app = AppController(
                    Path(directory),
                    planner_profiles=FakePlannerProfiles(cloud_binding()),
                    planner_bridge=bridge,
                )
                reply = json.loads(app.dispatch(json.dumps({
                    "version": 1,
                    "command": "start",
                    "arguments": {"goal": "finish"},
                })))

                self.assertFalse(app.advance())

                stored = app.store.load(reply["session"]["session_id"])
                self.assertEqual(stored.status, AgentSessionStatus.FAILED)
                self.assertEqual(stored.total_attempted_actions, 0)
                self.assertEqual(len(bridge.calls), 1)
                self.assertEqual(list((Path(directory) / "workspace").iterdir()), [])


class PlannerCancellationControllerTests(unittest.TestCase):
    def test_planner_cancelled_transitions_session_to_cancelled(self):
        class CancelledPlanner:
            def decide(self, goal, context, capabilities):
                raise LainError(ErrorCode.PLANNER_CANCELLED, "planner call cancelled")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = RuntimeConfig.for_workspace(root / "workspace")
            runtime = RuntimeEngine(config)
            store = AgentSessionStore(root / "sessions")
            controller = AgentController(
                AgentPlanningService(CancelledPlanner(), max_actions=1),
                runtime,
                store,
                AgentBudget(2, 1, 2, 30.0),
            )
            session = controller.create("finish")

            cancelled = controller.step(session.session_id)

            self.assertEqual(cancelled.status, AgentSessionStatus.CANCELLED)
            self.assertEqual(store.load(session.session_id).status, AgentSessionStatus.CANCELLED)


if __name__ == "__main__":
    unittest.main()
