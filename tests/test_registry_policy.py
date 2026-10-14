import tempfile
import unittest
from pathlib import Path

from lain.capabilities.registry import DEFAULT_REGISTRY, CapabilityDefinition, RiskClass
from lain.config import RuntimeConfig
from lain.errors import ErrorCode, LainError
from lain.policy.engine import PolicyDecision, evaluate_policy
from lain.protocol.models import Action


class RegistryPolicyTests(unittest.TestCase):
    def test_registry_contains_exact_supported_capabilities(self):
        self.assertEqual(
            set(DEFAULT_REGISTRY.names()),
            {"file.write_text", "file.copy", "file.move", "android.notify", "android.open_uri", "reddit.create_post",
             "android.battery_status", "android.vibrate", "android.toast", "android.clipboard_set", "android.share_text",
             "android.flashlight_set", "android.volume_get", "android.volume_set", "android.volume_adjust",
             "android.brightness_set", "android.rotation_set", "android.screen_timeout_set", "android.settings_open",
             "android.app_open", "android.app_list", "android.ui.inspect", "android.ui.click", "android.ui.scroll",
             "android.ui.set_text", "android.ui.global_action"},
        )
        for name in DEFAULT_REGISTRY.names():
            cap = DEFAULT_REGISTRY.get(name)
            self.assertIsInstance(cap, CapabilityDefinition)
            self.assertTrue(cap.verification)
            self.assertTrue(cap.environments)

    def test_unknown_capability_fails_closed(self):
        with self.assertRaises(LainError) as ctx:
            DEFAULT_REGISTRY.get("shell.exec")
        self.assertEqual(ctx.exception.code, ErrorCode.CAPABILITY_UNKNOWN)

    def test_argument_schema_rejects_missing_and_extra_values(self):
        cap = DEFAULT_REGISTRY.get("file.copy")
        with self.assertRaises(LainError) as missing:
            cap.validate_arguments({"source": "a"})
        self.assertEqual(missing.exception.code, ErrorCode.ARGUMENT_INVALID)
        with self.assertRaises(LainError) as extra:
            cap.validate_arguments({"source": "a", "destination": "b", "wat": 1})
        self.assertEqual(extra.exception.code, ErrorCode.ARGUMENT_INVALID)

    def test_argument_schema_applies_defaults(self):
        cap = DEFAULT_REGISTRY.get("file.write_text")
        args = cap.validate_arguments({"path": "a.md", "content": "x"})
        self.assertFalse(args["overwrite"])

    def test_low_risk_action_is_allowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = RuntimeConfig.for_workspace(Path(tmp))
            cap = DEFAULT_REGISTRY.get("file.copy")
            action = Action("a1", cap.name, {"source": "a", "destination": "b"})
            self.assertEqual(evaluate_policy(cap, action, config), PolicyDecision.ALLOW)

    def test_overwrite_requires_confirmation(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = RuntimeConfig.for_workspace(Path(tmp))
            cap = DEFAULT_REGISTRY.get("file.write_text")
            action = Action("a1", cap.name, {"path": "a", "content": "x", "overwrite": True})
            self.assertEqual(evaluate_policy(cap, action, config), PolicyDecision.REQUIRE_CONFIRMATION)

    def test_risk_thresholds_enforce_confirmation_and_denial(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = RuntimeConfig.for_workspace(Path(tmp))
            base = DEFAULT_REGISTRY.get("file.copy")
            class2 = CapabilityDefinition(
                name="test.external",
                arguments=base.arguments,
                risk_class=RiskClass.EXTERNAL_WRITE,
                required_permissions=(),
                reversible=False,
                confirmation_required=False,
                network_required=False,
                environments=("linux",),
                verification="test",
            )
            class3 = CapabilityDefinition(
                name="test.destructive",
                arguments=base.arguments,
                risk_class=RiskClass.DESTRUCTIVE,
                required_permissions=(),
                reversible=False,
                confirmation_required=False,
                network_required=False,
                environments=("linux",),
                verification="test",
            )
            action = Action("a1", "x", {})
            self.assertEqual(evaluate_policy(class2, action, config), PolicyDecision.REQUIRE_CONFIRMATION)
            self.assertEqual(evaluate_policy(class3, action, config), PolicyDecision.DENY)


if __name__ == "__main__":
    unittest.main()
