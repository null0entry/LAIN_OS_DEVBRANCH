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

    def test_active_revision_applies_to_same_session_and_preserves_authority_bounds(self):
        started = self.send("start", goal="Create demo file")
        sid = started["session"]["session_id"]
        before_view = self.send("inspect", session_id=sid)["session"]
        before_state = self.app.store.load(sid)

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
        self.assertEqual(reply["revision_applied"]["turn_id"], reply["turn"]["turn_id"])
        self.assertEqual(reply["session"]["session_id"], sid)
        self.assertEqual(reply["session"]["status"], "planning")
        self.assertNotEqual(reply["session"]["revision"], before_view["revision"])

        after_state = self.app.store.load(sid)
        self.assertEqual(after_state.session_id, before_state.session_id)
        self.assertEqual(after_state.planner_binding, before_state.planner_binding)
        self.assertEqual(after_state.budget, before_state.budget)
        self.assertEqual(after_state.total_attempted_actions, before_state.total_attempted_actions)
        self.assertEqual(after_state.cumulative_runtime_seconds, before_state.cumulative_runtime_seconds)
        self.assertEqual(after_state.goal, "Create demo file\n\nUser revision: Make it shorter")
        self.assertEqual(after_state.last_revision_turn_id, reply["turn"]["turn_id"])

    def test_revision_invalidates_old_approval_without_replaying_pending_action(self):
        started = self.send("start", goal="Share demo text")
        sid = started["session"]["session_id"]
        for _ in range(20):
            if not self.app.advance():
                break
        paused = self.send("inspect", session_id=sid)["session"]
        self.assertEqual(paused["status"], "paused_confirmation")
        old_token = paused["approval"]["token"]
        before_attempts = paused["attempted_actions"]

        revised = self.send(
            "turn_submit",
            text="Do not share it; summarize instead",
            source="speech",
            kind="revision",
            reference="active",
            target_session_id=None,
        )

        self.assertTrue(revised["ok"])
        self.assertEqual(revised["session"]["status"], "planning")
        self.assertNotIn("approval", revised["session"])
        self.assertEqual(revised["session"]["attempted_actions"], before_attempts)
        self.assertEqual(revised["session"]["actions"][-1]["status"], "skipped")
        self.assertFalse(self.send("approve", session_id=sid, token=old_token)["ok"])

    def test_revision_cross_session_and_busy_or_stopped_guards_fail_closed(self):
        started = self.send("start", goal="Create demo file")
        active_id = started["session"]["session_id"]
        other = self.app.controller.create("Show battery")

        active_before = self.app.store.load(active_id)
        other_before = self.app.store.load(other.session_id)
        cross = self.send(
            "turn_submit",
            text="Change the other task",
            source="typed",
            kind="revision",
            reference="explicit",
            target_session_id=other.session_id,
        )
        self.assertFalse(cross["ok"])
        self.assertEqual(self.app.store.load(active_id), active_before)
        self.assertEqual(self.app.store.load(other.session_id), other_before)

        self.app._advancing = True
        try:
            busy = self.send(
                "turn_submit",
                text="Change while step is in flight",
                source="speech",
                kind="revision",
                reference="active",
                target_session_id=None,
            )
        finally:
            self.app._advancing = False
        self.assertFalse(busy["ok"])
        self.assertEqual(self.app.store.load(active_id), active_before)

        self.app._stopped.set()
        try:
            stopped = self.send(
                "turn_submit",
                text="Change after stop",
                source="typed",
                kind="revision",
                reference="active",
                target_session_id=None,
            )
        finally:
            self.app._stopped.clear()
        self.assertFalse(stopped["ok"])
        self.assertEqual(self.app.store.load(active_id), active_before)

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

    def test_transcript_read_returns_latest_32_accepted_turns_in_order(self):
        for index in range(40):
            reply = self.send(
                "turn_submit",
                text=f"revision-{index:02d}",
                source="typed" if index % 2 == 0 else "speech",
                kind="revision",
                reference="ambiguous",
                target_session_id=None,
            )
            self.assertTrue(reply["ok"])

        conversation = self.send("turns")["conversation"]

        self.assertEqual(len(conversation["turns"]), 32)
        self.assertEqual(
            [turn["turn_id"] for turn in conversation["turns"]],
            list(range(9, 41)),
        )
        self.assertEqual(conversation["turns"][0]["text"], "revision-08")
        self.assertEqual(conversation["turns"][-1]["text"], "revision-39")


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
