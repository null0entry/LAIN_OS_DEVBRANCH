import json
import tempfile
import unittest
from pathlib import Path

from lain.app.control import AppController


class AppConversationTurnTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.app = AppController(Path(self.tmp.name))

    def send(self, command, **arguments):
        return json.loads(
            self.app.dispatch(
                json.dumps({"version": 1, "command": command, "arguments": arguments})
            )
        )

    def test_partial_speech_cannot_start_work_or_allocate_accepted_turn(self):
        reply = self.send("turn_partial", text="create demo")

        self.assertTrue(reply["ok"])
        self.assertEqual(reply["conversation"]["next_turn_id"], 1)
        self.assertEqual(reply["conversation"]["turns"], [])
        self.assertEqual(self.send("sessions")["sessions"], [])

    def test_final_speech_and_legacy_typed_start_share_turn_pipeline(self):
        typed = self.send("start", goal="Create demo file")
        self.assertTrue(typed["ok"])
        typed_sid = typed["session"]["session_id"]
        self.app.stop_active()
        self.app.advance()

        speech = self.send(
            "turn_submit",
            text="Show battery",
            source="speech",
            kind="task",
            reference="none",
            target_session_id=None,
        )
        self.assertTrue(speech["ok"])
        self.assertNotEqual(speech["session"]["session_id"], typed_sid)

        conversation = self.send("turns")["conversation"]
        self.assertEqual([turn["turn_id"] for turn in conversation["turns"]], [1, 2])
        self.assertEqual(
            [turn["source"] for turn in conversation["turns"]],
            ["typed", "speech"],
        )

    def test_ambiguous_revision_executes_nothing(self):
        started = self.send("start", goal="Create demo file")
        sid = started["session"]["session_id"]
        before = self.send("inspect", session_id=sid)["session"]["revision"]

        reply = self.send(
            "turn_submit",
            text="Change that one",
            source="speech",
            kind="revision",
            reference="ambiguous",
            target_session_id=None,
        )

        self.assertTrue(reply["ok"])
        self.assertTrue(reply["clarification_required"])
        self.assertNotIn("session", reply)
        after = self.send("inspect", session_id=sid)["session"]["revision"]
        self.assertEqual(after, before)

    def test_active_revision_creates_new_intent_without_mutating_agent_session(self):
        started = self.send("start", goal="Create demo file")
        sid = started["session"]["session_id"]
        before = self.send("inspect", session_id=sid)["session"]["revision"]

        reply = self.send(
            "turn_submit",
            text="Make it shorter",
            source="typed",
            kind="revision",
            reference="active",
            target_session_id=None,
        )

        self.assertTrue(reply["ok"])
        self.assertEqual(reply["revision_intent"]["target_session_id"], sid)
        self.assertEqual(reply["turn"]["route"], "revision")
        after = self.send("inspect", session_id=sid)["session"]["revision"]
        self.assertEqual(after, before)

    def test_turn_history_read_stays_within_control_message_bound(self):
        for index in range(20):
            reply = self.send(
                "turn_submit",
                text=("😀" * 995) + f"-{index:02d}",
                source="typed",
                kind="revision",
                reference="ambiguous",
                target_session_id=None,
            )
            self.assertTrue(reply["ok"])

        state = self.send("turns")
        self.assertTrue(state["ok"])
        self.assertLessEqual(
            len(json.dumps(state, separators=(",", ":")).encode("utf-8")),
            65536,
        )

    def test_explicit_revision_rejects_unknown_target_before_recording_turn(self):
        reply = self.send(
            "turn_submit",
            text="Revise it",
            source="typed",
            kind="revision",
            reference="explicit",
            target_session_id="00000000-0000-0000-0000-000000000000",
        )

        self.assertFalse(reply["ok"])
        self.assertEqual(self.send("turns")["conversation"]["turns"], [])


if __name__ == "__main__":
    unittest.main()
