"""Registered deterministic device controls and the closed native evidence boundary."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from lain.agent.models import OFFLINE_DEMO_BINDING
from lain.app.control import AppController
from lain.app.demo import COMMANDS, DemoPlanner
from lain.app.native import NativeAndroidAdapter
from lain.capabilities.registry import DEFAULT_REGISTRY
from lain.config import RuntimeConfig
from lain.conversation_routing import classify_final_turn, TurnDisposition
from lain.errors import LainError
from lain.protocol.models import Action, ActionEnvelope, ActionStatus, VerificationStatus
from lain.runtime.engine import RuntimeEngine
from tests.test_android_planner_bridge import cloud_binding


ARGUMENTS = {
    'android.flashlight_set': {'enabled': True},
    'android.volume_get': {},
    'android.volume_set': {'percentage': 40},
    'android.volume_adjust': {'direction': 'down'},
    'android.brightness_set': {'percentage': 30},
    'android.rotation_set': {'enabled': False},
    'android.screen_timeout_set': {'milliseconds': 30000},
    'android.settings_open': {'page': 'wifi'},
    'android.app_open': {'package': 'com.example.app'},
    'android.app_list': {},
    'android.ui.inspect': {},
    'android.ui.click': {'snapshot_id': 's1', 'node_id': 'n1'},
    'android.ui.scroll': {'snapshot_id': 's1', 'node_id': 'n1', 'direction': 'forward'},
    'android.ui.set_text': {'snapshot_id': 's1', 'node_id': 'n1', 'text': 'hello'},
    'android.ui.global_action': {'action': 'home'},
}


class DeviceCommandTests(unittest.TestCase):
    def parser(self):
        from lain.app import demo
        # A failing assertion gives a meaningful red before the new module exists.
        parser = getattr(demo, 'parse_device_command', None)
        self.assertIsNotNone(parser, 'deterministic device parser is not integrated')
        return parser

    def test_closed_command_grammar_and_default_media(self):
        cases = (
            ('turn on the flashlight', 'android.flashlight_set', {'enabled': True}),
            ('Turn the torch off!', 'android.flashlight_set', {'enabled': False}),
            ('flashlight on', 'android.flashlight_set', {'enabled': True}),
            ('set media volume to 40 percent', 'android.volume_set', {'percentage': 40, 'stream': 'media'}),
            ('volume down', 'android.volume_adjust', {'direction': 'down', 'stream': 'media'}),
            ('mute volume', 'android.volume_adjust', {'direction': 'mute', 'stream': 'media'}),
            ('show alarm volume', 'android.volume_get', {'stream': 'alarm'}),
            ('set brightness to 30%', 'android.brightness_set', {'percentage': 30}),
            ('turn auto rotation off', 'android.rotation_set', {'enabled': False}),
            ('set screen timeout to 30 seconds', 'android.screen_timeout_set', {'milliseconds': 30000}),
            ('open Wi-Fi settings', 'android.settings_open', {'page': 'wifi'}),
            ('open app com.example.app', 'android.app_open', {'package': 'com.example.app'}),
            ('OPEN app com.example.App', 'android.app_open', {'package': 'com.example.App'}),
            ('list apps', 'android.app_list', {'query': ''}),
            ('find app Camera', 'android.app_list', {'query': 'Camera'}),
        )
        parse = self.parser()
        for phrase, capability, arguments in cases:
            with self.subTest(phrase=phrase):
                proposal = parse(phrase)
                self.assertIsNotNone(proposal)
                self.assertEqual((proposal.type, proposal.arguments), (capability, arguments))

    def test_prose_compound_out_of_range_and_unbounded_commands_never_match(self):
        parse = self.parser()
        for text in ('What is flashlight on?', 'please explain volume down', 'flashlight on and delete files',
                     'set volume to 101 percent', 'set volume to -1 percent',
                     'set screen timeout to 1 seconds', 'open Camera', 'flashlight on\0',
                     'x' * 4097, '🌀' * 1025, '\ud800', None):
            with self.subTest(text=repr(text)[:80]):
                self.assertIsNone(parse(text))

    def test_recognized_commands_bypass_all_model_modes(self):
        class BrokenBridge:
            def __getattr__(self, name):
                raise AssertionError('model bridge accessed: ' + name)
        bindings = (OFFLINE_DEMO_BINDING, cloud_binding(), cloud_binding('local'))
        from dataclasses import replace
        bindings += (replace(OFFLINE_DEMO_BINDING, profile_id='qwen', mode='on_device',
                             protocol='gguf_native_v1', model='a' * 64, timeout_seconds=90.0,
                             max_response_bytes=4096),)
        for binding in bindings:
            for text in ('flashlight on', 'volume down', 'open Wi-Fi settings'):
                with self.subTest(mode=binding.mode, text=text):
                    decision = classify_final_turn(text, binding, BrokenBridge())
                    self.assertEqual(decision.disposition, TurnDisposition.TASK)

    def test_controller_pins_builtin_planner_for_auto_and_explicit_turns(self):
        class Profiles:
            def activeBindingJson(self):
                return json.dumps(cloud_binding().to_dict())
        class BrokenBridge:
            def execute(self, *_):
                raise AssertionError('model must not run')
        for kind in ('auto', 'task'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as root:
                app = AppController(Path(root), planner_profiles=Profiles(), planner_bridge=BrokenBridge())
                reply = json.loads(app.dispatch(json.dumps({'version': 1, 'command': 'turn_submit', 'arguments': {
                    'text': 'flashlight on', 'source': 'speech', 'kind': kind,
                    'reference': 'none', 'target_session_id': None,
                }})))
                self.assertTrue(reply['ok'], reply)
                self.assertIn('session', reply)
                self.assertEqual(reply['session']['planner']['mode'], 'demo')
                app.advance()
                session = app.store.load(reply['session']['session_id'])
                self.assertEqual(session.iterations[0].actions[0].action.type, 'android.flashlight_set')

    def test_six_fixed_demos_are_preserved(self):
        self.assertEqual(COMMANDS, ('Create demo file', 'Show battery', 'Show demo toast',
                                    'Vibrate briefly', 'Copy demo text', 'Share demo text'))
        with tempfile.TemporaryDirectory() as root:
            planner = DemoPlanner(Path(root))
            for command in COMMANDS:
                self.assertEqual(len(planner.decide(command, {'history': []}, ()).actions), 1)


class DeviceRegistryTests(unittest.TestCase):
    def test_registration_android_only_and_strict_arguments(self):
        for name, arguments in ARGUMENTS.items():
            with self.subTest(name=name):
                self.assertIn(name, DEFAULT_REGISTRY.names(), 'missing device capability')
                cap = DEFAULT_REGISTRY.get(name)
                self.assertEqual(cap.environments, ('android',))
                cap.validate_arguments(arguments)
                with self.assertRaises(LainError):
                    cap.validate_arguments({**arguments, 'shell': 'id'})
        for name, args in (
            ('android.volume_get', {'stream': 'unknown'}),
            ('android.volume_set', {'percentage': True}),
            ('android.volume_set', {'percentage': 101}),
            ('android.flashlight_set', {'enabled': 1}),
            ('android.volume_adjust', {'direction': 'toggle'}),
            ('android.screen_timeout_set', {'milliseconds': 14999}),
            ('android.settings_open', {'page': 'security'}),
            ('android.app_open', {'package': 'com.example;id'}),
            ('android.ui.click', {'snapshot_id': '', 'node_id': 'n1'}),
            ('android.ui.set_text', {'snapshot_id': 's1', 'node_id': 'n1', 'text': '🌀' * 1025}),
            ('android.ui.global_action', {'action': 'grant_permission'}),
        ):
            with self.subTest(name=name, args=args):
                self.assertIn(name, DEFAULT_REGISTRY.names())
                with self.assertRaises(LainError):
                    DEFAULT_REGISTRY.get(name).validate_arguments(args)
        self.assertIn('android.app_list', DEFAULT_REGISTRY.names())
        self.assertEqual(DEFAULT_REGISTRY.get('android.app_list').validate_arguments({}), {'query': ''})
        self.assertEqual(DEFAULT_REGISTRY.get('android.volume_get').validate_arguments({}), {'stream': 'media'})

    def test_nonnative_host_never_calls_shell_for_new_controls(self):
        with tempfile.TemporaryDirectory() as root, patch('subprocess.run', side_effect=AssertionError('shell')):
            runtime = RuntimeEngine(RuntimeConfig.for_workspace(Path(root)))
            for i, (name, args) in enumerate(ARGUMENTS.items()):
                self.assertIn(name, DEFAULT_REGISTRY.names())
                result = runtime.execute(ActionEnvelope('0', f'r{i}', 'test', (Action('a', name, args),)),
                                         confirmed_action_ids=frozenset({'a'})).results[0]
                self.assertEqual(result.status, ActionStatus.UNSUPPORTED)

    def test_ui_mutations_need_exact_action_confirmation_before_native_call(self):
        class Native:
            calls = []
            def execute(self, *args):
                self.calls.append(args)
                return '{"status":"success","details":{"snapshot_id":"s1","node_id":"n1","accepted":true}}'
        with tempfile.TemporaryDirectory() as root:
            callback = Native()
            runtime = RuntimeEngine(RuntimeConfig.for_workspace(Path(root)), native_android=NativeAndroidAdapter(callback))
            for i, name in enumerate(('android.ui.click', 'android.ui.scroll', 'android.ui.set_text', 'android.ui.global_action')):
                self.assertIn(name, DEFAULT_REGISTRY.names())
                result = runtime.execute(ActionEnvelope('0', f'r{i}', 'test', (Action('a', name, ARGUMENTS[name]),)),
                                         confirmed_action_ids=frozenset({'some-other-action'})).results[0]
                self.assertEqual(result.status, ActionStatus.CONFIRMATION_REQUIRED)
            self.assertEqual(callback.calls, [])


class NativeDeviceEvidenceTests(unittest.TestCase):
    def execute(self, name, args, details, raw=None):
        class Callback:
            def execute(self, capability, payload):
                self.capability, self.arguments = capability, json.loads(payload)
                return raw if raw is not None else json.dumps({'status': 'success', 'details': details})
        callback = Callback()
        adapter = NativeAndroidAdapter(callback)
        action = Action('a', name, args)
        result = adapter.execute(action)
        return result, adapter.verify(action, result)

    def test_readback_preserved_and_compared_to_requested_state(self):
        cases = (
            ('android.flashlight_set', {'enabled': True}, {'enabled': True}, {'enabled': False}),
            ('android.volume_set', {'percentage': 40}, {'index': 4, 'min_index': 0, 'max_index': 10, 'muted': False}, {'index': 8, 'min_index': 0, 'max_index': 10, 'muted': False}),
            ('android.brightness_set', {'percentage': 30}, {'value': 77, 'mode': 'manual'}, {'value': 77, 'mode': 'automatic'}),
            ('android.rotation_set', {'enabled': False}, {'enabled': False}, {'enabled': True}),
            ('android.screen_timeout_set', {'milliseconds': 30000}, {'milliseconds': 30000}, {'milliseconds': 15000}),
        )
        for name, args, observed, wrong in cases:
            with self.subTest(name=name):
                extra = {'stream': 'media'} if name == 'android.volume_set' else {}
                good, verification = self.execute(name, args, {**extra, 'observed': observed})
                self.assertEqual(good.status, ActionStatus.SUCCESS)
                self.assertEqual(good.details, {**extra, 'observed': observed})
                self.assertEqual(verification.status, VerificationStatus.PASSED)
                _, verification = self.execute(name, args, {**extra, 'observed': wrong})
                self.assertEqual(verification.status, VerificationStatus.FAILED)
                result, verification = self.execute(name, args, {**extra, 'observed': None})
                self.assertEqual(result.status, ActionStatus.SUCCESS)
                self.assertEqual(verification.status, VerificationStatus.LIMITED)

    def test_strict_native_envelope_and_evidence_reject_extra_types_and_nonfinite(self):
        for details in ({}, {'observed': {'enabled': 1}}, {'observed': {'enabled': True, 'extra': 1}},
                        {'observed': {'enabled': True}, 'approved': True}, {'observed': 'matched'}):
            result, _ = self.execute('android.flashlight_set', {'enabled': True}, details)
            self.assertEqual(result.status, ActionStatus.FAILURE)
        for raw in ('{"status":"success","details":{"observed":{"enabled":true,"enabled":false}}}',
                    '{"status":"success","details":{"observed":null},"approved":true}',
                    '{"status":"success","details":{"observed":NaN}}'):
            result, _ = self.execute('android.flashlight_set', {'enabled': True}, {}, raw)
            self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_volume_get_adjust_and_acceptance_only_navigation(self):
        observed = {'index': 4, 'min_index': 0, 'max_index': 10, 'muted': False}
        result, verification = self.execute('android.volume_get', {}, {'stream': 'media', 'observed': observed})
        self.assertEqual(result.details, {'stream': 'media', 'observed': observed})
        self.assertEqual(verification.status, VerificationStatus.PASSED)
        for after, status in ((3, VerificationStatus.PASSED), (6, VerificationStatus.FAILED)):
            _, verification = self.execute('android.volume_adjust', {'direction': 'down'},
                {'stream': 'media', 'before': observed, 'observed': {**observed, 'index': after}})
            self.assertEqual(verification.status, status)
        for name, args in (('android.settings_open', {'page': 'wifi'}),
                           ('android.app_open', {'package': 'com.example.app'})):
            result, verification = self.execute(name, args, {**args, 'accepted': True})
            self.assertEqual(result.status, ActionStatus.SUCCESS)
            self.assertEqual(verification.status, VerificationStatus.LIMITED)
            result, _ = self.execute(name, args, {**args, 'accepted': False})
            self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_inventory_is_bounded_unique_launchable_identities(self):
        details = {'query': '', 'apps': [{'package': 'com.example.app', 'label': 'Camera'}], 'truncated': False}
        result, verification = self.execute('android.app_list', {}, details)
        self.assertEqual(result.details, details)
        self.assertEqual(verification.status, VerificationStatus.PASSED)
        for bad in ({**details, 'apps': details['apps'] * 2},
                    {**details, 'apps': details['apps'] * 129},
                    {**details, 'apps': [{'package': 'invalid;id', 'label': 'Camera'}]},
                    {**details, 'query': 'other'}):
            result, _ = self.execute('android.app_list', {}, bad)
            self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_snapshot_closed_bounds_and_private_fields(self):
        node = {'node_id': 'n1', 'depth': 0, 'text': 'Hello', 'description': '',
                'class_name': 'android.widget.TextView', 'view_id': '', 'enabled': True,
                'clickable': True, 'scrollable': False, 'editable': False, 'password': False,
                'bounds': [0, 0, 100, 100]}
        snapshot = {'snapshot_id': 's1', 'package': 'com.example.app', 'window_id': 1,
                    'captured_at_ms': 1000, 'expires_at_ms': 121000, 'nodes': [node], 'truncated': False}
        result, verification = self.execute('android.ui.inspect', {}, {'snapshot': snapshot})
        self.assertEqual(result.details, {'snapshot': snapshot})
        self.assertEqual(verification.status, VerificationStatus.PASSED)
        bad_nodes = ({**node, 'depth': 21}, {**node, 'text': 'x' * 257},
                     {**node, 'password': True}, {**node, 'approved': True})
        for bad in [*({'snapshot': {**snapshot, 'nodes': [item]}} for item in bad_nodes),
                    {'snapshot': {**snapshot, 'nodes': [node] * 129}},
                    {'snapshot': {**snapshot, 'expires_at_ms': 121001}}]:
            result, _ = self.execute('android.ui.inspect', {}, bad)
            self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_runtime_mismatch_is_failure_and_unavailable_not_passed(self):
        with tempfile.TemporaryDirectory() as root:
            class Callback:
                def execute(self, *_):
                    return '{"status":"success","details":{"observed":{"enabled":false}}}'
            runtime = RuntimeEngine(RuntimeConfig.for_workspace(Path(root)), native_android=NativeAndroidAdapter(Callback()))
            # Assert registry presence before runtime preflight, for a meaningful missing-feature red.
            self.assertIn('android.flashlight_set', DEFAULT_REGISTRY.names())
            result = runtime.execute(ActionEnvelope('0', 'mismatch', 'test',
                                     (Action('a', 'android.flashlight_set', {'enabled': True}),))).results[0]
            self.assertEqual(result.status, ActionStatus.FAILURE)
            self.assertEqual(result.error_code, 'VERIFICATION_FAILED')

class NativeDeviceBoundaryTests(unittest.TestCase):
    def test_failure_reasons_are_closed_and_preserved_without_private_diagnostics(self):
        class Callback:
            payload = {'status': 'failure', 'details': {'reason': 'permission_denied'}}
            def execute(self, *_):
                return json.dumps(self.payload)
        callback = Callback()
        adapter = NativeAndroidAdapter(callback)
        action = Action('a', 'android.flashlight_set', {'enabled': True})
        result = adapter.execute(action)
        self.assertEqual(result.status, ActionStatus.FAILURE)
        self.assertEqual(result.details.get('reason'), 'permission_denied')
        callback.payload = {'status': 'unsupported', 'details': {'reason': 'hardware_unavailable'}}
        result = adapter.execute(action)
        self.assertEqual(result.status, ActionStatus.UNSUPPORTED)
        self.assertEqual(result.details.get('reason'), 'hardware_unavailable')
        for details in ({'reason': 'private diagnostic'}, {'reason': 'permission_denied', 'raw': 'private'}):
            callback.payload = {'status': 'failure', 'details': details}
            self.assertNotIn('private', json.dumps(adapter.execute(action).details))

    def test_text_entry_is_redacted_from_audit_and_narrative_history(self):
        from lain.audit.logger import redact, redact_android_narratives
        action = {'type': 'android.ui.set_text', 'arguments': {'snapshot_id': 's1', 'node_id': 'n1', 'text': 'private marker'}}
        self.assertEqual(redact(action)['arguments']['text'], '[REDACTED]')
        self.assertEqual(redact_android_narratives({'goal': 'type private marker'}, action)['goal'], 'type [REDACTED]')

    def test_runtime_policy_deny_disabled_adapter_and_ui_approval_do_not_bypass_controls(self):
        from dataclasses import replace
        class Callback:
            calls = []
            def execute(self, name, payload):
                self.calls.append((name, json.loads(payload)))
                return '{"status":"success","details":{"snapshot_id":"s1","node_id":"n1","accepted":true}}'
        with tempfile.TemporaryDirectory() as root:
            config = RuntimeConfig.for_workspace(Path(root))
            action = Action('a', 'android.ui.click', ARGUMENTS['android.ui.click'])
            callback = Callback()
            adapter = NativeAndroidAdapter(callback)
            runtime = RuntimeEngine(replace(config, deny_risk_threshold=2), native_android=adapter)
            result = runtime.execute(ActionEnvelope('0', 'denied', 'test', (action,)), confirmed_action_ids=frozenset({'a'}))
            self.assertEqual(result.results[0].status, ActionStatus.DENIED)
            runtime = RuntimeEngine(replace(config, android_adapter='disabled'), native_android=adapter)
            result = runtime.execute(ActionEnvelope('0', 'disabled', 'test', (action,)), confirmed_action_ids=frozenset({'a'}))
            self.assertEqual(result.results[0].status, ActionStatus.UNSUPPORTED)
            self.assertEqual(callback.calls, [])
            runtime = RuntimeEngine(config, native_android=adapter)
            result = runtime.execute(ActionEnvelope('0', 'approved', 'test', (action,)), confirmed_action_ids=frozenset({'a'}))
            self.assertEqual(result.results[0].status, ActionStatus.SUCCESS)
            self.assertEqual(result.results[0].verification.status, VerificationStatus.LIMITED)
            self.assertEqual(len(callback.calls), 1)

    def test_invalid_ui_native_evidence_never_upgrades_acceptance(self):
        helper = NativeDeviceEvidenceTests()
        for name, details in (
            ('android.ui.click', {'snapshot_id': 'stale', 'node_id': 'n1', 'accepted': True}),
            ('android.ui.scroll', {'snapshot_id': 's1', 'node_id': 'n1', 'direction': 'backward', 'accepted': True}),
            ('android.ui.set_text', {'snapshot_id': 's1', 'node_id': 'n1', 'accepted': True, 'text': 'private'}),
            ('android.ui.global_action', {'action': 'back', 'accepted': True}),
        ):
            result, _ = helper.execute(name, ARGUMENTS[name], details)
            self.assertEqual(result.status, ActionStatus.FAILURE)


class VolumeRangeEvidenceTests(unittest.TestCase):
    def test_nonzero_minimum_percentage_and_adjustment_boundaries(self):
        helper = NativeDeviceEvidenceTests()
        observed = {'index': 6, 'min_index': 2, 'max_index': 10, 'muted': False}
        _, verification = helper.execute('android.volume_set', {'percentage': 50},
                                          {'stream': 'media', 'observed': observed})
        self.assertEqual(verification.status, VerificationStatus.PASSED)
        _, verification = helper.execute('android.volume_set', {'percentage': 0},
                                          {'stream': 'media', 'observed': {**observed, 'index': 0}})
        self.assertEqual(verification.status, VerificationStatus.NOT_APPLICABLE)
        boundary = {**observed, 'index': 2}
        _, verification = helper.execute('android.volume_adjust', {'direction': 'down'},
            {'stream': 'media', 'before': boundary, 'observed': boundary})
        self.assertEqual(verification.status, VerificationStatus.PASSED)

    def test_fixed_range_is_a_valid_reading(self):
        helper = NativeDeviceEvidenceTests()
        for index in (0, 2):
            _, verification = helper.execute('android.volume_get', {},
                {'stream': 'media', 'observed': {'index': index, 'min_index': index, 'max_index': index, 'muted': False}})
            self.assertEqual(verification.status, VerificationStatus.PASSED)

class DevicePlannerCatalogTests(unittest.TestCase):
    def test_catalog_and_response_schema_publish_closed_argument_constraints(self):
        from lain.planning.protocol import planner_request
        from lain.planner_adapters.openai_protocol import build_response_schema
        request = planner_request('test', DEFAULT_REGISTRY.definitions(), 1)
        caps = {cap['name']: cap for cap in request['capabilities']}
        self.assertIn('stream', caps['android.volume_get']['argument_limits'])
        self.assertEqual(caps['android.volume_get']['argument_limits']['stream']['choices'],
                         ['media', 'ring', 'alarm', 'notification', 'system', 'voice_call'])
        self.assertIn('pattern', caps['android.app_open']['argument_limits']['package'])
        self.assertTrue(caps['android.app_list']['argument_limits']['query']['allow_empty'])
        schema = build_response_schema(request)
        schemas = {item['properties']['type']['enum'][0]: item['properties']['arguments']['properties']
                   for item in schema['properties']['actions']['items']['anyOf']}
        self.assertEqual(schemas['android.settings_open']['page']['enum'],
                         ['wifi', 'bluetooth', 'display', 'sound', 'apps', 'app_permissions',
                          'notifications', 'accessibility', 'write_settings', 'dnd'])
        self.assertIn('pattern', schemas['android.app_open']['package'])

class DeviceObservationPrivacyTests(unittest.TestCase):
    def test_inspected_node_text_remains_available_as_untrusted_planner_observation(self):
        from lain.audit.logger import redact
        details = {'snapshot': {'snapshot_id': 's1', 'nodes': [{'node_id': 'n1', 'text': 'Wi-Fi'}]}}
        self.assertEqual(redact(details)['snapshot']['nodes'][0]['text'], 'Wi-Fi')


class ObservationBoundTests(unittest.TestCase):
    def test_snapshot_expiry_must_fit_android_signed_long(self):
        helper = NativeDeviceEvidenceTests()
        snapshot = {'snapshot_id': 's1', 'package': 'com.example.app', 'window_id': 1,
                    'captured_at_ms': 2**63 - 1, 'expires_at_ms': 2**63, 'nodes': [], 'truncated': False}
        result, _ = helper.execute('android.ui.inspect', {}, {'snapshot': snapshot})
        self.assertEqual(result.status, ActionStatus.FAILURE)

    def test_total_observation_bounds_apply_below_outer_native_limit(self):
        helper = NativeDeviceEvidenceTests()
        node = {'node_id': 'n1', 'depth': 0, 'text': 'x' * 256, 'description': '',
                'class_name': '', 'view_id': '', 'enabled': True, 'clickable': False,
                'scrollable': False, 'editable': False, 'password': False, 'bounds': [0, 0, 1, 1]}
        snapshot = {'snapshot_id': 's1', 'package': 'com.example.app', 'window_id': 1,
                    'captured_at_ms': 0, 'expires_at_ms': 120000,
                    'nodes': [{**node, 'node_id': 'n' + str(i)} for i in range(80)], 'truncated': False}
        inventory = {'query': '', 'apps': [{'package': 'com.example.app' + str(i), 'label': 'x' * 256}
                                           for i in range(128)], 'truncated': False}
        for name, details, bounded in (('android.ui.inspect', {'snapshot': snapshot}, snapshot),
                                       ('android.app_list', inventory, inventory)):
            size = len(json.dumps(bounded, ensure_ascii=False, separators=(',', ':')).encode())
            self.assertGreater(size, 32768)
            self.assertLess(size, 60000)
            result, _ = helper.execute(name, {}, details)
            self.assertEqual(result.status, ActionStatus.FAILURE)
