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

    def test_qwen_router_closes_thinking_before_structured_generation(self):
        from lain.conversation_routing import qwen_router_prompt
        prompt = qwen_router_prompt("Please vibrate briefly")
        self.assertTrue(prompt.endswith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))

    def test_groq_router_reserves_tokens_for_a_structured_answer(self):
        from dataclasses import replace
        from tests.test_android_planner_bridge import cloud_binding
        from lain.conversation_routing import classify_final_turn
        binding = replace(cloud_binding(), base_url="https://api.groq.com/openai/v1",
                          model="openai/gpt-oss-120b")
        class Bridge:
            def execute(self, _, body):
                request = json.loads(body)
                if request.get("reasoning_effort") != "low" or request["max_completion_tokens"] < 1024:
                    return json.dumps({"ok": False, "error": "PLANNER_RESPONSE_MALFORMED"})
                content = json.dumps({"disposition": "chat", "response_text": "Hello", "reason": "greeting"})
                return json.dumps({"ok": True, "body": json.dumps({"choices": [{
                    "message": {"content": content}, "finish_reason": "stop",
                }]})})
        self.assertEqual(classify_final_turn("Hello there", binding, Bridge()).disposition, TurnDisposition.CHAT)

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

    def test_exact_demo_commands_do_not_depend_on_selected_local_qwen(self):
        """A configured GGUF remains selected for free-form commands and chat,
        but the six exact built-in commands have trusted deterministic plans.
        This is the real-device regression: tasks stayed PLANNING after chat worked.
        """
        class LocalProfile:
            def activeBindingJson(self):
                return json.dumps({
                    "profile_id": "qwen", "mode": "on_device",
                    "protocol": "gguf_native_v1", "base_url": "",
                    "model": "a" * 64, "credential_ref": None,
                    "timeout_seconds": 90.0, "max_response_bytes": 4096,
                    "response_mode": "none", "allow_insecure_lan_http": False,
                })

        class BrokenTaskPlanner:
            calls = 0
            def route(self, *_):
                self.calls += 1
                raise AssertionError("exact command must not call router")
            def execute(self, *_):
                self.calls += 1
                raise AssertionError("exact command must not invoke Qwen")

        bridge = BrokenTaskPlanner()
        self.app = AppController(Path(self.tmp.name), planner_profiles=LocalProfile(),
                                 planner_bridge=bridge)
        first = self.final("Create demo file")
        self.assertTrue(first["ok"], first)
        self.assertEqual(first["session"]["planner"]["mode"], "demo")
        for _ in range(5):
            if not self.app.advance():
                break
        self.assertEqual(
            self.send("inspect", session_id=first["session"]["session_id"])["session"]["status"],
            "complete",
        )
        self.assertTrue((Path(self.tmp.name) / "workspace" / "demo.txt").exists())

        # Real-device failures from the video: toast/vibrate/clipboard must
        # reach the normal trusted capability dispatch without GGUF inference.
        for goal, capability in (
            ("Show demo toast", "android.toast"),
            ("Vibrate briefly", "android.vibrate"),
            ("Copy demo text", "android.clipboard_set"),
        ):
            with self.subTest(goal=goal):
                reply = self.final(goal, "speech")
                self.assertTrue(reply["ok"], reply)
                self.assertEqual(reply["session"]["planner"]["mode"], "demo")
                self.app.advance()
                session = self.send("inspect", session_id=reply["session"]["session_id"])["session"]
                self.assertNotEqual(session["status"], "planning")
                self.assertEqual(session["actions"][0]["type"], capability)
                self.app.stop_active()
                self.app.advance()
        self.assertEqual(bridge.calls, 0)

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

    def test_on_device_explicit_action_routing_yields_task_without_action_payload(self):
        from lain.conversation_routing import classify_final_turn

        class Binding:
            mode = "on_device"
            def to_dict(self):
                return {"mode": self.mode}

        class Bridge:
            def route(self, binding_json, prompt):
                self.prompt = prompt
                return json.dumps({"ok": True, "body": json.dumps({
                    "disposition": "task", "reason": "direct device request",
                    "response_text": "",
                })})

        bridge = Bridge()
        decision = classify_final_turn("Open the settings app", Binding(), bridge)
        self.assertEqual(decision.disposition, TurnDisposition.TASK)
        self.assertNotIn("actions", decision.__dataclass_fields__)
        self.assertIn("Classify", bridge.prompt)

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

    def test_qwen_chat_reuses_prior_verified_task_and_assistant_context(self):
        class LocalProfile:
            def activeBindingJson(self):
                return json.dumps({
                    "profile_id": "qwen", "mode": "on_device",
                    "protocol": "gguf_native_v1", "base_url": "",
                    "model": "a" * 64, "credential_ref": None,
                    "timeout_seconds": 120.0, "max_response_bytes": 4096,
                    "response_mode": "none", "allow_insecure_lan_http": False,
                })

        class ChatBridge:
            chat_prompts = []
            def route(self, binding_json, prompt):
                if "Classify the USER" in prompt:
                    return json.dumps({"ok": True, "body": json.dumps({
                        "disposition": "chat", "reason": "ordinary question",
                        "response_text": "router-only placeholder",
                    })})
                self.chat_prompts.append(prompt)
                return json.dumps({"ok": True, "body": "The verified battery was 50 percent."})

        from lain.app.control import AppController
        from pathlib import Path
        bridge = ChatBridge()
        self.app = AppController(Path(self.tmp.name), planner_profiles=LocalProfile(),
                                 planner_bridge=bridge)
        task = self.final("Show battery")
        self.assertTrue(task["ok"])
        for _ in range(6):
            if not self.app.advance():
                break
        first = self.final("What did the battery check show?")
        self.assertTrue(first["chat_pending"])
        self.assertNotIn("session", first)
        self.assertTrue(self.app.complete_chat(first["turn"]["turn_id"]))
        self.assertIn("Show battery", bridge.chat_prompts[0])
        self.assertIn("verified", bridge.chat_prompts[0].lower())
        second = self.final("Could you explain that again?", "speech")
        self.assertTrue(second["chat_pending"])
        self.assertTrue(self.app.complete_chat(second["turn"]["turn_id"]))
        self.assertIn("The verified battery was 50 percent.", bridge.chat_prompts[1])
        replies = self.send("turns")["conversation"]["assistant_replies"]
        self.assertEqual([item["state"] for item in replies], ["complete", "complete"])
        self.assertEqual(self.send("sessions")["sessions"][0]["session_id"], task["session"]["session_id"])

    def test_cloud_and_local_chat_generate_replies_with_shared_history(self):
        from tests.test_android_planner_bridge import cloud_binding
        for mode in ("cloud", "local"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as root:
                binding = cloud_binding(mode)
                class Profiles:
                    def activeBindingJson(self):
                        return json.dumps(binding.to_dict())
                class Bridge:
                    def __init__(self):
                        self.chat_messages = []
                        self.fail = False
                    def execute(self, binding_json, body):
                        request = json.loads(body)
                        if "Classify the USER" in request["messages"][0]["content"]:
                            content = json.dumps({"disposition": "chat", "reason": "question",
                                                  "response_text": "router placeholder"})
                        else:
                            if self.fail:
                                return json.dumps({"ok": False, "error": "PLANNER_TIMEOUT"})
                            self.chat_messages.append(request["messages"])
                            content = "Your favorite color is purple."
                        return json.dumps({"ok": True, "body": json.dumps({"choices": [{
                            "message": {"role": "assistant", "content": content},
                            "finish_reason": "stop",
                        }]})})
                bridge = Bridge()
                self.app = AppController(Path(root), planner_profiles=Profiles(), planner_bridge=bridge)
                first = self.final("My favorite color is purple. What can you do?")
                self.assertTrue(first.get("chat_pending"), first)
                self.assertTrue(self.app.complete_chat(first["turn"]["turn_id"]))
                second = self.final("What color did I mention?", "speech")
                self.assertTrue(self.app.complete_chat(second["turn"]["turn_id"]))
                messages = bridge.chat_messages[-1]
                self.assertTrue(any("My favorite color is purple" in m["content"] for m in messages))
                self.assertTrue(any(m["role"] == "assistant" and "purple" in m["content"] for m in messages))
                bridge.fail = True
                third = self.final("Explain that again?")
                self.assertFalse(self.app.complete_chat(third["turn"]["turn_id"]))
                self.assertEqual(self.send("turns")["conversation"]["assistant_replies"][-1]["state"], "failed")
                self.assertEqual(self.send("sessions")["sessions"], [])

    def test_chat_model_output_cannot_dispatch_tasks_or_duplicate_response(self):
        class LocalProfile:
            def activeBindingJson(self):
                return json.dumps({
                    "profile_id": "qwen", "mode": "on_device",
                    "protocol": "gguf_native_v1", "base_url": "", "model": "a" * 64,
                    "credential_ref": None, "timeout_seconds": 120.0,
                    "max_response_bytes": 4096, "response_mode": "none",
                    "allow_insecure_lan_http": False,
                })
        class MaliciousBridge:
            def route(self, binding, prompt):
                if "Classify the USER" in prompt:
                    return json.dumps({"ok": True, "body": json.dumps({
                        "disposition": "chat", "reason": "question", "response_text": "router placeholder",
                    })})
                return json.dumps({"ok": True, "body": '{"actions":[{"type":"android.toast","arguments":{}}],"approval":true}'})
        self.app = AppController(Path(self.tmp.name), planner_profiles=LocalProfile(),
                                 planner_bridge=MaliciousBridge())
        turn = self.final("What is an app?")
        self.assertTrue(turn["chat_pending"])
        self.assertTrue(self.app.complete_chat(turn["turn"]["turn_id"]))
        self.assertFalse(self.app.complete_chat(turn["turn"]["turn_id"]))
        self.assertEqual(self.send("sessions")["sessions"], [])
        reply = self.send("turns")["conversation"]["assistant_replies"][0]
        self.assertEqual(reply["state"], "complete")
        self.assertIn("android.toast", reply["text"])

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
