import unittest
from dataclasses import replace
from pathlib import Path
from tempfile import TemporaryDirectory
from uuid import uuid4

from lain.agent import (
    AgentBudget,
    AgentController,
    AgentPlanningService,
    AgentSession,
    AgentSessionStatus,
    AgentSessionStore,
    OFFLINE_DEMO_BINDING,
    PlannerBinding,
    build_agent_context,
)
from lain.errors import LainError
from lain.planning import AgentPlannerDecision, AgentPlannerStatus


class CompletePlanner:
    def decide(self, goal, context, capabilities):
        return AgentPlannerDecision(AgentPlannerStatus.COMPLETE, "done", ())


class NoopRuntime:
    def execute_action(self, *args, **kwargs):
        raise AssertionError("runtime must not execute")


def session(binding=OFFLINE_DEMO_BINDING):
    return AgentSession(
        version="1",
        session_id=str(uuid4()),
        goal="test",
        status=AgentSessionStatus.CREATED,
        created_at="2026-10-01T00:00:00+00:00",
        updated_at="2026-10-01T00:00:00+00:00",
        iterations=(),
        total_attempted_actions=0,
        budget=AgentBudget(12, 1, 32, 900.0),
        cumulative_runtime_seconds=0.0,
        terminal_reason=None,
        planner_binding=binding,
    )

ON_DEVICE_SHA = "a" * 64


def on_device_binding():
    return PlannerBinding(
        profile_id="offline-qwen", mode="on_device", protocol="gguf_native_v1",
        base_url="", model=ON_DEVICE_SHA, credential_ref=None,
        timeout_seconds=120.0, max_response_bytes=65536,
        response_mode="none", allow_insecure_lan_http=False,
    )



class PlannerBindingTests(unittest.TestCase):
    def test_on_device_binding_round_trips_and_rejects_network_authority(self):
        model = on_device_binding()
        self.assertEqual(PlannerBinding.from_dict(model.to_dict()), model)
        raw = model.to_dict()
        for updates in ({'base_url':'https://evil.example'}, {'credential_ref':'cred-secret'}, {'protocol':'openai_compatible_v1'}, {'model':'../../evil'}, {'allow_insecure_lan_http':True}):
            with self.subTest(updates=updates), self.assertRaises(LainError):
                PlannerBinding.from_dict({**raw, **updates})


    def test_round_trip_preserves_binding_exactly(self):
        binding = PlannerBinding(
            profile_id="cloud-primary",
            mode="cloud",
            protocol="openai_compatible_v1",
            base_url="https://example.invalid/v1",
            model="model-a",
            credential_ref="cred-123",
            timeout_seconds=30.0,
            max_response_bytes=1048576,
            response_mode="json_schema",
            allow_insecure_lan_http=False,
        )
        original = session(binding)
        loaded = AgentSession.from_dict(original.to_dict())
        self.assertEqual(loaded.planner_binding, binding)
        self.assertEqual(loaded, original)

    def test_session_constructor_rejects_non_binding_value(self):
        with self.assertRaises(LainError):
            replace(session(), planner_binding="not-a-binding")

    def test_old_session_without_binding_migrates_to_offline_demo(self):
        raw = session().to_dict()
        del raw["planner_binding"]
        loaded = AgentSession.from_dict(raw)
        self.assertEqual(loaded.planner_binding, OFFLINE_DEMO_BINDING)

    def test_binding_rejects_unknown_or_malformed_fields(self):
        raw = OFFLINE_DEMO_BINDING.to_dict()
        cases = (
            {**raw, "mode": "other"},
            {**raw, "protocol": "other"},
            {**raw, "timeout_seconds": float("nan")},
            {**raw, "max_response_bytes": True},
            {**raw, "credential_ref": ""},
            {**raw, "extra": True},
        )
        for candidate in cases:
            with self.subTest(candidate=candidate), self.assertRaises(Exception):
                PlannerBinding.from_dict(candidate)

    def test_endpoint_policy_rejects_insecure_or_secret_bearing_urls(self):
        cloud = {
            "profile_id": "cloud-primary",
            "mode": "cloud",
            "protocol": "openai_compatible_v1",
            "base_url": "https://api.example/v1",
            "model": "model-a",
            "credential_ref": "cred-a",
            "timeout_seconds": 30.0,
            "max_response_bytes": 1048576,
            "response_mode": "json_schema",
            "allow_insecure_lan_http": False,
        }
        self.assertEqual(PlannerBinding.from_dict(cloud).base_url, "https://api.example/v1")
        rejected = (
            {**cloud, "base_url": "http://api.example/v1"},
            {**cloud, "base_url": "https://user:secret@api.example/v1"},
            {**cloud, "base_url": "https://api.example/v1?token=secret"},
            {**cloud, "mode": "local", "base_url": "http://192.168.1.50/v1"},
            {**cloud, "mode": "local", "base_url": "http://8.8.8.8/v1", "allow_insecure_lan_http": True},
            {**cloud, "mode": "local", "base_url": "http://example.com/v1", "allow_insecure_lan_http": True},
            {**cloud, "mode": "local", "base_url": "http://192.0.2.10/v1", "allow_insecure_lan_http": True},
            {**cloud, "mode": "local", "base_url": "http://169.254.1.10/v1", "allow_insecure_lan_http": True},
        )
        for raw in rejected:
            with self.subTest(raw=raw), self.assertRaises(LainError):
                PlannerBinding.from_dict(raw)

        for endpoint in (
            "http://127.0.0.1:8080/v1",
            "http://192.168.1.50:8080/v1",
            "http://[::1]:8080/v1",
            "http://[fd00::1]:8080/v1",
            "http://[fe80::1]:8080/v1",
        ):
            with self.subTest(endpoint=endpoint):
                raw = {**cloud, "mode": "local", "base_url": endpoint, "allow_insecure_lan_http": True}
                self.assertEqual(PlannerBinding.from_dict(raw).base_url, endpoint)

    def test_context_exposes_safe_identity_not_endpoint_or_credential_ref(self):
        binding = PlannerBinding(
            profile_id="cloud-primary",
            mode="cloud",
            protocol="openai_compatible_v1",
            base_url="https://private.example/v1",
            model="model-a",
            credential_ref="secret-ref",
            timeout_seconds=30.0,
            max_response_bytes=1048576,
            response_mode="json_schema",
            allow_insecure_lan_http=False,
        )
        context = build_agent_context(session(binding))
        self.assertEqual(
            context["planner"],
            {"profile_id": "cloud-primary", "mode": "cloud", "model": "model-a"},
        )
        rendered = repr(context)
        self.assertNotIn("secret-ref", rendered)
        self.assertNotIn("private.example", rendered)

    def test_controller_pins_binding_at_session_creation(self):
        current = {"binding": PlannerBinding(
            profile_id="a",
            mode="cloud",
            protocol="openai_compatible_v1",
            base_url="https://a.example/v1",
            model="model-a",
            credential_ref="cred-a",
            timeout_seconds=30.0,
            max_response_bytes=1048576,
            response_mode="json_schema",
            allow_insecure_lan_http=False,
        )}
        binding_b = replace(
            current["binding"],
            profile_id="b",
            base_url="https://b.example/v1",
            model="model-b",
            credential_ref="cred-b",
        )
        with TemporaryDirectory() as tmp:
            store = AgentSessionStore(Path(tmp) / "sessions")
            controller = AgentController(
                AgentPlanningService(CompletePlanner(), max_actions=1),
                NoopRuntime(),
                store,
                AgentBudget(12, 1, 32, 900.0),
                planner_binding_provider=lambda: current["binding"],
            )
            created = controller.create("x")
            current["binding"] = binding_b
            self.assertEqual(created.planner_binding.profile_id, "a")
            self.assertEqual(store.load(created.session_id).planner_binding.profile_id, "a")


if __name__ == "__main__":
    unittest.main()
