"""Native adapter contracts; execution deferred at the owner's request."""
import json
import tempfile
import unittest
from pathlib import Path

from lain.app.native import NativeAndroidAdapter
from lain.config import RuntimeConfig
from lain.protocol.models import Action, ActionStatus, VerificationStatus
from lain.runtime.engine import RuntimeEngine


class NativeStub:
    def __init__(self, details):
        self.details = details

    def execute(self, name, arguments):
        return json.dumps({"status": "success", "details": self.details})


class NativeAdapterTests(unittest.TestCase):
    def test_battery_exposes_approved_fields_only(self):
        adapter = NativeAndroidAdapter(NativeStub({"battery": {"percentage": 50, "secret": "private"}}))
        result = adapter.execute(Action("a1", "android.battery_status", {}))
        self.assertEqual(result.details, {"battery": {"percentage": 50}})

    def test_malformed_battery_fails_closed(self):
        adapter = NativeAndroidAdapter(NativeStub({"battery": {"percentage": True}}))
        result = adapter.execute(Action("a1", "android.battery_status", {}))
        self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_clipboard_mismatch_never_exposes_observed_value(self):
        adapter = NativeAndroidAdapter(NativeStub({"readback": "mismatch", "observed": "PRIVATE"}))
        action = Action("a1", "android.clipboard_set", {"content": "requested"})
        result = adapter.execute(action)
        self.assertNotIn("PRIVATE", json.dumps(result.details))
        self.assertEqual(adapter.verify(action, result).status, VerificationStatus.FAILED)

    def test_ui_effects_remain_limited(self):
        adapter = NativeAndroidAdapter(NativeStub({}))
        action = Action("a1", "android.toast", {"content": "test"})
        self.assertEqual(adapter.verify(action, adapter.execute(action)).status, VerificationStatus.LIMITED)

    def test_registered_native_uri_and_notification_reach_callback_with_limited_verification(self):
        for name, args in (("android.open_uri", {"uri": "https://example.com"}),
                           ("android.notify", {"title": "LAIN", "content": "Ready"})):
            with self.subTest(name=name):
                adapter = NativeAndroidAdapter(NativeStub({}))
                action = Action("a1", name, args)
                result = adapter.execute(action)
                self.assertEqual(result.status, ActionStatus.SUCCESS)
                self.assertEqual(adapter.verify(action, result).status, VerificationStatus.LIMITED)

    def test_native_uri_rejects_unapproved_schemes_before_callback(self):
        class NeverCalled:
            def execute(self, *_):
                raise AssertionError("invalid URI crossed native boundary")
        for uri in ("intent://settings", "file:///private", "javascript:alert(1)"):
            result = NativeAndroidAdapter(NeverCalled()).execute(
                Action("a1", "android.open_uri", {"uri": uri}))
            self.assertEqual(result.error_code, "ARGUMENT_INVALID")

    def test_unknown_native_operation_fails_closed(self):
        result = NativeAndroidAdapter(NativeStub({})).execute(Action("a1", "android.shell", {}))
        self.assertEqual(result.status, ActionStatus.UNSUPPORTED)

    def test_sms_compose_is_registered_and_requires_confirmation(self):
        from lain.capabilities.registry import DEFAULT_REGISTRY, RiskClass
        from lain.policy.engine import PolicyDecision, evaluate_policy

        capability = DEFAULT_REGISTRY.get("android.sms.compose")
        action = Action("a1", "android.sms.compose",
                        {"recipient": "+15551234567", "content": "Running late"})
        config = RuntimeConfig.for_workspace(Path(tempfile.gettempdir()))
        self.assertEqual(capability.risk_class, RiskClass.EXTERNAL_WRITE)
        self.assertTrue(capability.confirmation_required)
        self.assertEqual(evaluate_policy(capability, action, config),
                         PolicyDecision.REQUIRE_CONFIRMATION)

    def test_sms_compose_reaches_native_callback_without_claiming_delivery(self):
        class Recorder:
            def __init__(self):
                self.calls = []

            def execute(self, name, arguments):
                self.calls.append((name, json.loads(arguments)))
                return json.dumps({"status": "success", "details": {"handoff": "composer_opened"}})

        callback = Recorder()
        adapter = NativeAndroidAdapter(callback)
        action = Action("a1", "android.sms.compose",
                        {"recipient": "+15551234567", "content": "Running late"})
        outcome = adapter.execute(action)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertEqual(callback.calls, [("android.sms.compose", action.arguments)])
        self.assertEqual(adapter.verify(action, outcome).status, VerificationStatus.LIMITED)
        self.assertNotIn("delivered", json.dumps(outcome.details))

    def test_sms_compose_rejects_names_or_uri_injection_before_android(self):
        class NeverCalled:
            def execute(self, *_):
                raise AssertionError("unsafe SMS recipient reached Android")

        adapter = NativeAndroidAdapter(NeverCalled())
        for recipient in ("Alex", "smsto:911", "123?body=oops", "123\\n456",
                          "+", "", "12345678901234567890", "555;1234"):
            with self.subTest(recipient=recipient):
                action = Action("a1", "android.sms.compose",
                                {"recipient": recipient, "content": "hello"})
                result = adapter.execute(action)
                self.assertEqual(result.status, ActionStatus.FAILURE)
        self.assertEqual(adapter.execute(Action("a1", "android.sms.compose",
            {"recipient": "5551234567", "content": "hi", "send_now": True})).status,
            ActionStatus.FAILURE)

    def test_cli_keeps_default_dispatch(self):
        with tempfile.TemporaryDirectory() as root:
            runtime = RuntimeEngine(RuntimeConfig.for_workspace(Path(root)))
            self.assertIsNone(runtime.native_android)

    def test_hostile_arguments_rejected_before_callback(self):
        adapter = NativeAndroidAdapter(NativeStub({}))
        result = adapter.execute(Action("a1", "android.vibrate", {"duration_ms": True}))
        self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_duplicate_battery_fields_fail_closed(self):
        class DuplicateNative:
            def execute(self, name, arguments):
                return '{"status":"success","details":{"battery":{"percentage":50,"percentage":99}}}'

        result = NativeAndroidAdapter(DuplicateNative()).execute(Action("a1", "android.battery_status", {}))
        self.assertEqual(result.status, ActionStatus.FAILURE)
