import tempfile
import unittest
from pathlib import Path

from lain.agent import AgentPlanningService
from lain.config import RuntimeConfig
from lain.errors import LainError
from lain.planning import AgentPlannerDecision, AgentPlannerStatus, ProposedAction
from lain.runtime.engine import RuntimeEngine
from lain.workflows.intent import WorkflowIntentBridge
from lain.workflows.engine import WorkflowRunner


class FakePlanner:
    def __init__(self, decision):
        self.decision = decision
        self.calls = []

    def decide(self, goal, context, capabilities):
        self.calls.append((goal, context, capabilities))
        return self.decision


class WorkflowIntentBridgeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.runtime = RuntimeEngine(RuntimeConfig.for_workspace(self.root))
        self.runner = WorkflowRunner(self.runtime, self.root / "workflows")

    def make_bridge(self, decision):
        planner = FakePlanner(decision)
        return WorkflowIntentBridge(AgentPlanningService(planner, max_actions=8), self.runner), planner

    def test_structured_model_actions_become_one_verified_workflow(self):
        decision = AgentPlannerDecision(
            AgentPlannerStatus.CONTINUE, "write and copy",
            (ProposedAction("file.write_text", {"path": "x.txt", "content": "result"}),
             ProposedAction("file.copy", {"source": "x.txt", "destination": "y.txt"})),
        )
        bridge, planner = self.make_bridge(decision)
        proposed = bridge.propose("Write and copy a note", context={"history": []})
        self.assertEqual(proposed["status"], "planned")
        self.assertTrue(proposed["workflow_id"])
        self.assertEqual(planner.calls[0][0], "Write and copy a note")
        self.assertEqual(len(planner.calls[0][2]), len(self.runtime.registry.definitions()))
        self.assertFalse((self.root / "x.txt").exists())  # proposing cannot execute
        final = self.runner.run_until_stop(proposed["workflow_id"])
        self.assertEqual(final["status"], "complete")
        self.assertEqual((self.root / "y.txt").read_text(), "result")

    def test_complete_or_blocked_model_message_cannot_claim_execution(self):
        for status in (AgentPlannerStatus.COMPLETE, AgentPlannerStatus.BLOCKED):
            with self.subTest(status=status):
                bridge, _ = self.make_bridge(AgentPlannerDecision(status, "I finished", ()))
                proposal = bridge.propose("Make a file")
                self.assertEqual(proposal, {"status": "no_action", "workflow_id": None})
                self.assertFalse((self.root / "x.txt").exists())

    def test_invalid_tool_proposal_rejected_before_any_side_effect(self):
        decision = AgentPlannerDecision(
            AgentPlannerStatus.CONTINUE, "unsafe",
            (ProposedAction("file.write_text", {"path": "x.txt", "content": "x"}),
             ProposedAction("shell.exec", {"command": "touch out"})),
        )
        bridge, _ = self.make_bridge(decision)
        with self.assertRaises(LainError):
            bridge.propose("Make file and arbitrary shell")
        self.assertFalse((self.root / "x.txt").exists())

    def test_model_cannot_preapprove_overwrite(self):
        (self.root / "x.txt").write_text("before")
        decision = AgentPlannerDecision(
            AgentPlannerStatus.CONTINUE, "overwrite",
            (ProposedAction("file.write_text", {"path": "x.txt", "content": "new", "overwrite": True}),),
        )
        bridge, _ = self.make_bridge(decision)
        proposal = bridge.propose("overwrite without confirmation")
        result = self.runner.run_until_stop(proposal["workflow_id"])
        self.assertEqual(result["status"], "awaiting_confirmation")
        self.assertEqual((self.root / "x.txt").read_text(), "before")

    def test_invalid_context_and_goal_never_reach_model(self):
        bridge, planner = self.make_bridge(
            AgentPlannerDecision(AgentPlannerStatus.COMPLETE, "no action", ()),
        )
        for goal, context in [("", {}), ("do work", []), ("x" * 5000, {})]:
            with self.subTest(goal=goal[:20]), self.assertRaises(LainError):
                bridge.propose(goal, context=context)
        self.assertEqual(planner.calls, [])


if __name__ == "__main__":
    unittest.main()
