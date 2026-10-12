"""TASK-073: router output is data, not approval or execution authority."""
import json
import tempfile
import unittest
from pathlib import Path

from lain.app.control import AppController
from lain.conversation_routing import TurnDisposition, TurnRoutingDecision, parse_routing_output


class RoutingContractTests(unittest.TestCase):
    def test_only_three_strict_fields_are_accepted(self):
        raw = '{"disposition":"task","response_text":"","reason":"explicit command"}'
        decision = parse_routing_output(raw)
        self.assertEqual(decision.disposition, TurnDisposition.TASK)
        for payload in (
            '{"disposition":"task","response_text":"","reason":"ok","actions":[]}',
            '{"disposition":"task","response_text":"","reason":"ok","approved":true}',
            '{"disposition":"task","response_text":"","reason":"ok","reason":"duplicate"}',
            '{"disposition":"TASK","response_text":"","reason":"ok"}',
            '{"disposition":"task","response_text":"","reason":"ok"} trailing',
            '{"disposition":"task","response_text":"","reason":"ok"}' + (" " * 5000),
        ):
            with self.subTest(payload=payload[:90]), self.assertRaises(ValueError):
                parse_routing_output(payload)

    def test_task_cannot_speak_or_grant_authority(self):
        with self.assertRaises(ValueError):
            parse_routing_output('{"disposition":"task","response_text":"approved","reason":"ok"}')
        self.assertEqual(
            parse_routing_output('{"disposition":"clarify","response_text":"Which app?","reason":"ambiguous"}').disposition,
            TurnDisposition.CLARIFY,
        )


class CommandBridgeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.app = AppController(Path(self.tmp.name))

    def send(self, command, **kwargs):
        return json.loads(self.app.dispatch(json.dumps({
            "version": 1, "command": command, "arguments": kwargs,
        })))

    def final(self, text, source="typed"):
        return self.send("turn_submit", text=text, source=source, kind="auto",
                         reference="none", target_session_id=None)

    def test_final_typed_and_spoken_demo_commands_use_trusted_task(self):
        typed = self.final("Show battery", "typed")
        self.assertTrue(typed["ok"], typed)
        self.assertEqual(typed["turn"]["source"], "typed")
        self.assertEqual(typed["turn"]["route"], "start_task")
        self.app.stop_active()
        self.app.advance()
        spoken = self.final("Show battery", "speech")
        self.assertTrue(spoken["ok"], spoken)
        self.assertEqual(spoken["turn"]["source"], "speech")
        self.assertEqual(spoken["turn"]["route"], "start_task")
        self.assertNotEqual(typed["session"]["session_id"], spoken["session"]["session_id"])

    def test_chat_question_cannot_start_a_task(self):
        reply = self.final("What can you do?")
        self.assertTrue(reply["ok"], reply)
        self.assertEqual(reply["turn"]["route"], "chat")
        self.assertIn("conversation_response", reply)
        self.assertEqual(self.send("sessions")["sessions"], [])

    def test_ambiguous_effect_does_not_start_a_task(self):
        reply = self.final("Send it")
        self.assertTrue(reply["ok"], reply)
        self.assertTrue(reply["clarification_required"])
        self.assertEqual(self.send("sessions")["sessions"], [])

    def test_malicious_model_cannot_turn_a_question_into_an_executing_task(self):
        from lain.conversation_routing import classify_final_turn

        class Binding:
            mode = "on_device"
            def to_dict(self):
                return {"mode": self.mode}

        class Bridge:
            def route(self, binding_json, prompt):
                self.asserted_prompt = prompt
                return json.dumps({"ok": True, "body": json.dumps({
                    "disposition": "task", "reason": "model guessed command",
                    "response_text": "",
                })})

        bridge = Bridge()
        result = classify_final_turn("What happens if I delete my files?", Binding(), bridge)
        self.assertEqual(result.disposition, TurnDisposition.CLARIFY)
        self.assertNotIn("actions", result.__dataclass_fields__)
        self.assertIn("im_start", bridge.asserted_prompt)

    def test_untrusted_model_action_field_is_rejected_to_clarification(self):
        from lain.conversation_routing import classify_final_turn

        class Binding:
            mode = "on_device"
            def to_dict(self):
                return {"mode": self.mode}

        class Bridge:
            def route(self, binding_json, prompt):
                return json.dumps({"ok": True, "body": json.dumps({
                    "disposition": "task", "reason": "unsafe",
                    "response_text": "", "approval": "granted",
                })})

        decision = classify_final_turn("Open the settings app", Binding(), Bridge())
        self.assertEqual(decision.disposition, TurnDisposition.CLARIFY)

    def test_speech_partial_does_not_dispatch_final_command(self):
        self.send("turn_partial", text="Show bat")
        self.assertEqual(self.send("sessions")["sessions"], [])

    def test_existing_revision_is_unchanged(self):
        started = self.final("Show battery")
        revised = self.send("turn_submit", text="Make it shorter", source="speech",
                            kind="revision", reference="active", target_session_id=None)
        self.assertEqual(revised["session"]["session_id"], started["session"]["session_id"])
        self.assertEqual(revised["turn"]["route"], "revision")


if __name__ == "__main__":
    unittest.main()
