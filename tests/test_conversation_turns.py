import json
import os
import stat
import tempfile
import unittest
from pathlib import Path
from uuid import uuid4

from lain.conversation import (
    ConversationTurnManager,
    TurnFinality,
    TurnKind,
    TurnReference,
    TurnRoute,
    TurnSource,
)


class ConversationTurnManagerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "conversation"
        self.manager = ConversationTurnManager(self.root)

    def test_partial_transcript_is_ephemeral_and_never_allocates_turn_id(self):
        snapshot = self.manager.update_partial("make it")

        self.assertEqual(snapshot.partial_text, "make it")
        self.assertEqual(snapshot.next_turn_id, 1)
        self.assertEqual(snapshot.turns, ())

        restarted = ConversationTurnManager(self.root).snapshot()
        self.assertIsNone(restarted.partial_text)
        self.assertEqual(restarted.next_turn_id, 1)
        self.assertEqual(restarted.turns, ())

    def test_typed_and_final_speech_share_one_monotonic_turn_sequence(self):
        typed = self.manager.accept(
            "Create demo file",
            source=TurnSource.TYPED,
            kind=TurnKind.TASK,
            reference=TurnReference.NONE,
        )
        spoken = self.manager.accept(
            "Show battery",
            source=TurnSource.SPEECH,
            kind=TurnKind.TASK,
            reference=TurnReference.NONE,
        )

        self.assertEqual((typed.record.turn_id, spoken.record.turn_id), (1, 2))
        self.assertEqual(typed.record.route, TurnRoute.START_TASK)
        self.assertEqual(spoken.record.route, TurnRoute.START_TASK)
        self.assertEqual(typed.record.finality, TurnFinality.FINAL)
        self.assertEqual(spoken.record.finality, TurnFinality.FINAL)
        self.assertEqual(
            [item.source for item in self.manager.snapshot().turns],
            [TurnSource.TYPED, TurnSource.SPEECH],
        )

    def test_active_reference_routes_revision_without_mutating_target_identity(self):
        target = str(uuid4())
        self.manager.set_active_task(target)

        decision = self.manager.accept(
            "Make it shorter",
            source=TurnSource.SPEECH,
            kind=TurnKind.REVISION,
            reference=TurnReference.ACTIVE,
        )

        self.assertEqual(decision.record.route, TurnRoute.REVISION)
        self.assertEqual(decision.record.target_session_id, target)
        self.assertEqual(self.manager.snapshot().active_task_id, target)

    def test_ambiguous_revision_requires_clarification_and_has_no_target(self):
        self.manager.set_active_task(str(uuid4()))

        decision = self.manager.accept(
            "Change that one",
            source=TurnSource.TYPED,
            kind=TurnKind.REVISION,
            reference=TurnReference.AMBIGUOUS,
        )

        self.assertEqual(decision.record.route, TurnRoute.CLARIFICATION_REQUIRED)
        self.assertIsNone(decision.record.target_session_id)

    def test_explicit_revision_target_must_be_canonical_uuid(self):
        with self.assertRaises(ValueError):
            self.manager.accept(
                "Revise it",
                source=TurnSource.TYPED,
                kind=TurnKind.REVISION,
                reference=TurnReference.EXPLICIT,
                target_session_id="../../escape",
            )

    def test_turn_text_and_history_are_bounded(self):
        with self.assertRaises(ValueError):
            self.manager.accept(
                "x" * 4097,
                source=TurnSource.TYPED,
                kind=TurnKind.TASK,
                reference=TurnReference.NONE,
            )

        for index in range(140):
            self.manager.accept(
                f"task {index}",
                source=TurnSource.TYPED,
                kind=TurnKind.TASK,
                reference=TurnReference.NONE,
            )

        snapshot = self.manager.snapshot()
        self.assertEqual(snapshot.next_turn_id, 141)
        self.assertLessEqual(len(snapshot.turns), 128)
        self.assertEqual(snapshot.turns[-1].turn_id, 140)

    def test_state_file_is_private_and_schema_has_no_authority_or_credentials(self):
        self.manager.accept(
            "Create demo file",
            source=TurnSource.TYPED,
            kind=TurnKind.TASK,
            reference=TurnReference.NONE,
        )
        path = self.root / "state.json"
        raw = json.loads(path.read_text(encoding="utf-8"))

        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.root.stat().st_mode) & 0o077, 0)
        serialized = json.dumps(raw)
        for forbidden in ("credential_ref", "approval", "capability", "policy", "action_id"):
            self.assertNotIn(forbidden, serialized)


if __name__ == "__main__":
    unittest.main()
