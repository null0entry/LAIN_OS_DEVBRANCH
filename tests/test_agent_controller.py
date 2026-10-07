import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from uuid import uuid4

from lain.agent import (
    AgentBudget,
    AgentController,
    AgentPlanningService,
    AgentSessionStatus,
    AgentSessionStore,
)
from lain.config import RuntimeConfig
from lain.errors import ErrorCode, LainError
from lain.planning import AgentPlannerDecision, AgentPlannerStatus, ProposedAction
from lain.protocol.models import ActionResult, ActionStatus, VerificationResult, VerificationStatus
from lain.runtime.engine import RuntimeEngine


class SequencePlanner:
    def __init__(self, decisions):
        self.decisions = list(decisions)
        self.calls = []

    def decide(self, goal, context, capabilities):
        self.calls.append((goal, context, capabilities))
        if not self.decisions:
            raise AssertionError("planner called more times than expected")
        return self.decisions.pop(0)


class FixedRuntime:
    def __init__(self, results):
        self.results = list(results)
        self.calls = []

    def execute_action(self, envelope, action_id, *, confirmed_action_ids=frozenset()):
        self.calls.append((envelope, action_id, confirmed_action_ids))
        if not self.results:
            raise AssertionError("runtime called more times than expected")
        result = self.results.pop(0)
        return replace(result, action_id=action_id)


class SequenceClock:
    def __init__(self, values):
        self.values = list(values)

    def __call__(self):
        if not self.values:
            raise AssertionError("clock called more times than expected")
        return self.values.pop(0)


def continue_files(*names):
    return AgentPlannerDecision(
        AgentPlannerStatus.CONTINUE,
        "write files",
        tuple(
            ProposedAction("file.write_text", {"path": name, "content": name})
            for name in names
        ),
    )


def complete(reason="goal satisfied"):
    return AgentPlannerDecision(AgentPlannerStatus.COMPLETE, reason, ())


def action_result(status, *, verification=VerificationStatus.NOT_APPLICABLE, error_code=None):
    return ActionResult(
        action_id="placeholder",
        status=status,
        details={},
        verification=VerificationResult(verification),
        error_code=error_code,
    )


class AgentControllerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        self.config = RuntimeConfig.for_workspace(self.root)
        self.store = AgentSessionStore(self.root / ".lain" / "sessions")
        self.runtime = RuntimeEngine(self.config)
        self.now_value = "2026-09-29T20:00:00+00:00"

    def tearDown(self):
        self.tmp.cleanup()

    def controller(self, planner, *, runtime=None, budget=None, clock=None, max_actions=8, store=None):
        planning = AgentPlanningService(planner, max_actions=max_actions)
        return AgentController(
            planning,
            runtime or self.runtime,
            store or self.store,
            budget or AgentBudget(12, max_actions, 32, 900.0),
            monotonic_clock=clock or __import__("time").monotonic,
            now=lambda: self.now_value,
        )

    def test_runs_bounded_batch_then_replans_to_complete(self):
        planner = SequencePlanner([continue_files("hello.txt", "done.txt"), complete()])
        controller = self.controller(planner)
        created = controller.create("create two files")

        final = controller.run_until_stop(created.session_id)

        self.assertEqual(final.status, AgentSessionStatus.COMPLETE)
        self.assertEqual((self.root / "hello.txt").read_text(), "hello.txt")
        self.assertEqual((self.root / "done.txt").read_text(), "done.txt")
        self.assertEqual(len(final.iterations), 2)
        self.assertEqual(
            [record.result.status for record in final.iterations[0].actions],
            [ActionStatus.SUCCESS, ActionStatus.SUCCESS],
        )
        self.assertEqual(len(planner.calls), 2)
        self.assertEqual(self.store.load(final.session_id), final)

    def test_batch_stops_on_non_success_and_maps_session_state(self):
        cases = (
            (ActionStatus.CONFIRMATION_REQUIRED, AgentSessionStatus.PAUSED_CONFIRMATION),
            (ActionStatus.DENIED, AgentSessionStatus.BLOCKED),
            (ActionStatus.UNSUPPORTED, AgentSessionStatus.BLOCKED),
            (ActionStatus.FAILURE, AgentSessionStatus.FAILED),
        )
        for action_status, expected_state in cases:
            with self.subTest(action_status=action_status):
                with tempfile.TemporaryDirectory() as tmp:
                    store = AgentSessionStore(Path(tmp) / "sessions")
                    runtime = FixedRuntime([action_result(action_status)])
                    planner = SequencePlanner([continue_files("a.txt", "b.txt")])
                    controller = self.controller(planner, runtime=runtime, store=store)
                    created = controller.create("x")
                    controller.step(created.session_id)
                    after = controller.step(created.session_id)
                    self.assertEqual(after.status, expected_state)
                    self.assertEqual(len(runtime.calls), 1)
                    if action_status in {ActionStatus.CONFIRMATION_REQUIRED, ActionStatus.DENIED}:
                        self.assertEqual(after.total_attempted_actions, 0)
                    else:
                        self.assertEqual(after.total_attempted_actions, 1)

    def test_success_advances_one_action_per_step_then_returns_to_planning(self):
        runtime = FixedRuntime([
            action_result(ActionStatus.SUCCESS, verification=VerificationStatus.PASSED),
            action_result(ActionStatus.SUCCESS, verification=VerificationStatus.PASSED),
        ])
        planner = SequencePlanner([continue_files("a.txt", "b.txt")])
        controller = self.controller(planner, runtime=runtime)
        created = controller.create("x")
        planned = controller.step(created.session_id)
        self.assertEqual(planned.status, AgentSessionStatus.EXECUTING)

        first = controller.step(created.session_id)
        self.assertEqual(first.status, AgentSessionStatus.EXECUTING)
        self.assertIsNotNone(first.iterations[0].actions[0].result)
        self.assertIsNone(first.iterations[0].actions[1].result)

        second = controller.step(created.session_id)
        self.assertEqual(second.status, AgentSessionStatus.PLANNING)
        self.assertEqual(len(runtime.calls), 2)

    def test_process_interruption_resume_does_not_replay_completed_action(self):
        first_planner = SequencePlanner([continue_files("a.txt", "b.txt")])
        first_controller = self.controller(first_planner)
        created = first_controller.create("write two")
        first_controller.step(created.session_id)
        checkpoint = first_controller.step(created.session_id)
        self.assertEqual(checkpoint.iterations[0].actions[0].result.status, ActionStatus.SUCCESS)
        self.assertIsNone(checkpoint.iterations[0].actions[1].result)

        second_planner = SequencePlanner([complete()])
        second_controller = self.controller(second_planner)
        final = second_controller.run_until_stop(created.session_id)

        self.assertEqual(final.status, AgentSessionStatus.COMPLETE)
        self.assertEqual((self.root / "a.txt").read_text(), "a.txt")
        self.assertEqual((self.root / "b.txt").read_text(), "b.txt")
        audit = list(self.runtime.audit.records())
        attempts_a = [r for r in audit if r.get("action_id") == "i1a1" and r.get("execution_attempted") is True]
        attempts_b = [r for r in audit if r.get("action_id") == "i1a2" and r.get("execution_attempted") is True]
        self.assertEqual(len(attempts_a), 1)
        self.assertEqual(len(attempts_b), 1)

    def test_audit_ahead_of_checkpoint_blocks_instead_of_replaying(self):
        planner = SequencePlanner([continue_files("a.txt")])
        controller = self.controller(planner)
        created = controller.create("write one")
        planned = controller.step(created.session_id)
        iteration = planned.iterations[0]
        action = iteration.actions[0].action
        self.runtime.audit.append(
            {
                "request_id": iteration.request_id,
                "action_id": action.id,
                "execution_attempted": True,
                "execution_status": "started",
            }
        )

        blocked = controller.step(created.session_id)

        self.assertEqual(blocked.status, AgentSessionStatus.BLOCKED)
        self.assertIn("reconciliation", blocked.terminal_reason.lower())
        self.assertFalse((self.root / "a.txt").exists())

    def test_confirmation_requires_exact_action_id_and_reuses_request(self):
        target = self.root / "x.txt"
        target.write_text("old")
        decision = AgentPlannerDecision(
            AgentPlannerStatus.CONTINUE,
            "overwrite file",
            (ProposedAction("file.write_text", {"path": "x.txt", "content": "new", "overwrite": True}),),
        )
        planner = SequencePlanner([decision, complete()])
        controller = self.controller(planner)
        created = controller.create("overwrite x")
        paused = controller.run_until_stop(created.session_id)
        request_id = paused.iterations[0].request_id
        self.assertEqual(paused.status, AgentSessionStatus.PAUSED_CONFIRMATION)
        self.assertEqual(paused.total_attempted_actions, 0)
        self.assertEqual(target.read_text(), "old")

        unchanged = controller.run_until_stop(created.session_id)
        self.assertEqual(unchanged.status, AgentSessionStatus.PAUSED_CONFIRMATION)
        self.assertEqual(target.read_text(), "old")

        wrong = controller.run_until_stop(created.session_id, confirmed_action_ids=frozenset({"wrong"}))
        self.assertEqual(wrong.status, AgentSessionStatus.PAUSED_CONFIRMATION)
        self.assertEqual(target.read_text(), "old")

        final = controller.run_until_stop(created.session_id, confirmed_action_ids=frozenset({"i1a1"}))
        self.assertEqual(final.status, AgentSessionStatus.COMPLETE)
        self.assertEqual(final.iterations[0].request_id, request_id)
        self.assertEqual(final.iterations[0].actions[0].result.status, ActionStatus.SUCCESS)
        self.assertEqual(final.total_attempted_actions, 1)
        self.assertEqual(target.read_text(), "new")

    def test_cancel_is_durable_and_terminal_sessions_cannot_resume(self):
        planner = SequencePlanner([continue_files("a.txt")])
        controller = self.controller(planner)
        created = controller.create("x")
        cancelled = controller.cancel(created.session_id)
        self.assertEqual(cancelled.status, AgentSessionStatus.CANCELLED)
        self.assertEqual(self.store.load(created.session_id).status, AgentSessionStatus.CANCELLED)

        with self.assertRaises(LainError) as ctx:
            controller.step(created.session_id)
        self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
        with self.assertRaises(LainError) as ctx:
            controller.run_until_stop(created.session_id)
        self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertEqual(planner.calls, [])

    def test_iteration_budget_prevents_next_planner_call(self):
        planner = SequencePlanner([continue_files("a.txt"), complete()])
        controller = self.controller(planner, budget=AgentBudget(1, 8, 32, 900.0))
        created = controller.create("x")
        controller.step(created.session_id)
        controller.step(created.session_id)
        exhausted = controller.step(created.session_id)
        self.assertEqual(exhausted.status, AgentSessionStatus.BUDGET_EXHAUSTED)
        self.assertEqual(len(planner.calls), 1)

    def test_total_action_budget_prevents_next_execution(self):
        planner = SequencePlanner([continue_files("a.txt", "b.txt")])
        controller = self.controller(planner, budget=AgentBudget(12, 8, 1, 900.0))
        created = controller.create("x")
        controller.step(created.session_id)
        controller.step(created.session_id)
        exhausted = controller.step(created.session_id)
        self.assertEqual(exhausted.status, AgentSessionStatus.BUDGET_EXHAUSTED)
        self.assertTrue((self.root / "a.txt").exists())
        self.assertFalse((self.root / "b.txt").exists())

    def test_planner_batch_limit_is_persisted_in_session_budget(self):
        planner = SequencePlanner([complete()])
        controller = self.controller(
            planner,
            budget=AgentBudget(12, 99, 32, 900.0),
            max_actions=3,
        )
        created = controller.create("x")
        self.assertEqual(created.budget.max_actions_per_batch, 3)

    def test_runtime_budget_after_planning_prevents_execution_and_persists(self):
        planner = SequencePlanner([continue_files("a.txt")])
        runtime = FixedRuntime([action_result(ActionStatus.SUCCESS)])
        controller = self.controller(
            planner,
            runtime=runtime,
            budget=AgentBudget(12, 8, 32, 900.0),
            clock=SequenceClock([0.0, 900.0]),
        )
        created = controller.create("x")
        exhausted = controller.step(created.session_id)
        self.assertEqual(exhausted.status, AgentSessionStatus.BUDGET_EXHAUSTED)
        self.assertEqual(exhausted.cumulative_runtime_seconds, 900.0)
        self.assertEqual(runtime.calls, [])
        self.assertEqual(self.store.load(created.session_id).cumulative_runtime_seconds, 900.0)

    def test_revision_preserves_completed_history_and_rejects_stale_turn(self):
        planner = SequencePlanner([continue_files("a.txt", "b.txt"), complete()])
        controller = self.controller(planner)
        created = controller.create("write two files")
        controller.step(created.session_id)
        before = controller.step(created.session_id)
        self.assertEqual(before.iterations[0].actions[0].result.status, ActionStatus.SUCCESS)
        self.assertIsNone(before.iterations[0].actions[1].result)
        self.assertTrue((self.root / "a.txt").exists())
        self.assertFalse((self.root / "b.txt").exists())

        revised = controller.revise(
            created.session_id,
            "Only keep the first file",
            turn_id=7,
            expected_session=before,
        )

        self.assertEqual(revised.status, AgentSessionStatus.PLANNING)
        self.assertEqual(revised.session_id, before.session_id)
        self.assertEqual(revised.planner_binding, before.planner_binding)
        self.assertEqual(revised.budget, before.budget)
        self.assertEqual(revised.total_attempted_actions, before.total_attempted_actions)
        self.assertEqual(revised.cumulative_runtime_seconds, before.cumulative_runtime_seconds)
        self.assertEqual(revised.iterations[0].actions[0].result.status, ActionStatus.SUCCESS)
        self.assertEqual(revised.iterations[0].actions[1].result.status, ActionStatus.SKIPPED)
        self.assertEqual(
            revised.iterations[0].actions[1].result.error_code,
            "AGENT_REVISION_SUPERSEDED",
        )
        self.assertEqual(revised.last_revision_turn_id, 7)

        checkpoint = self.store.load(created.session_id)
        fresh = self.controller(SequencePlanner([complete()]))
        with self.assertRaises(LainError) as ctx:
            fresh.revise(
                created.session_id,
                "Duplicate delivery",
                turn_id=7,
                expected_session=checkpoint,
            )
        self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertEqual(self.store.load(created.session_id), checkpoint)

        final = controller.run_until_stop(created.session_id)
        self.assertEqual(final.status, AgentSessionStatus.COMPLETE)
        self.assertFalse((self.root / "b.txt").exists())
        self.assertEqual(
            planner.calls[-1][0],
            "write two files\n\nUser revision: Only keep the first file",
        )

    def test_revision_rejects_stale_expected_session_before_mutation(self):
        planner = SequencePlanner([continue_files("a.txt")])
        controller = self.controller(planner)
        created = controller.create("write one")
        accepted_against = self.store.load(created.session_id)

        controller.step(created.session_id)
        current = self.store.load(created.session_id)
        self.assertNotEqual(current, accepted_against)

        with self.assertRaises(LainError) as ctx:
            controller.revise(
                created.session_id,
                "change after stale observation",
                turn_id=2,
                expected_session=accepted_against,
            )

        self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertEqual(self.store.load(created.session_id), current)

    def test_terminal_session_rejects_revision_without_mutation(self):
        planner = SequencePlanner([complete()])
        controller = self.controller(planner)
        created = controller.create("finish")
        final = controller.run_until_stop(created.session_id)
        checkpoint = self.store.load(created.session_id)

        with self.assertRaises(LainError) as ctx:
            controller.revise(
                created.session_id,
                "change it",
                turn_id=2,
                expected_session=checkpoint,
            )

        self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
        self.assertEqual(self.store.load(created.session_id), checkpoint)

    def test_session_lease_blocks_second_controller_before_planner_work(self):
        second_planner = SequencePlanner([complete()])
        second = self.controller(second_planner)
        captured = []

        class ReentrantPlanner:
            def __init__(self):
                self.calls = 0

            def decide(inner_self, goal, context, capabilities):
                inner_self.calls += 1
                try:
                    second.step(session_id)
                except LainError as exc:
                    captured.append(exc.code)
                return complete()

        first_planner = ReentrantPlanner()
        first = self.controller(first_planner)
        created = first.create("x")
        session_id = created.session_id

        final = first.run_until_stop(session_id)

        self.assertEqual(final.status, AgentSessionStatus.COMPLETE)
        self.assertEqual(captured, [ErrorCode.AGENT_SESSION_BUSY])
        self.assertEqual(second_planner.calls, [])


if __name__ == "__main__":
    unittest.main()


class ConfirmationCrashRecoveryRegressionTests(unittest.TestCase):
    def test_executing_checkpoint_with_confirmation_result_recovers_to_pause(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            config = RuntimeConfig.for_workspace(root)
            store = AgentSessionStore(root / ".lain" / "sessions")
            runtime = RuntimeEngine(config)
            decision = AgentPlannerDecision(
                AgentPlannerStatus.CONTINUE,
                "overwrite",
                (
                    ProposedAction(
                        "file.write_text",
                        {"path": "x.txt", "content": "new", "overwrite": True},
                    ),
                    ProposedAction(
                        "file.write_text",
                        {"path": "later.txt", "content": "later"},
                    ),
                ),
            )
            planning = AgentPlanningService(SequencePlanner([decision]), max_actions=8)
            ctl = AgentController(
                planning,
                runtime,
                store,
                AgentBudget(12, 8, 32, 900.0),
                now=lambda: "2026-09-29T20:00:00+00:00",
            )
            target = root / "x.txt"
            target.write_text("old")
            created = ctl.create("overwrite x")
            paused = ctl.run_until_stop(created.session_id)
            self.assertEqual(paused.status, AgentSessionStatus.PAUSED_CONFIRMATION)

            crash_checkpoint = replace(
                paused,
                status=AgentSessionStatus.EXECUTING,
                updated_at="2026-09-29T20:00:01+00:00",
            )
            store.save(crash_checkpoint)

            fresh = AgentController(
                AgentPlanningService(SequencePlanner([complete()]), max_actions=8),
                runtime,
                store,
                AgentBudget(12, 8, 32, 900.0),
                now=lambda: "2026-09-29T20:00:02+00:00",
            )
            recovered = fresh.run_until_stop(created.session_id)
            self.assertEqual(recovered.status, AgentSessionStatus.PAUSED_CONFIRMATION)
            self.assertEqual(target.read_text(), "old")
            self.assertFalse((root / "later.txt").exists())

            final = fresh.run_until_stop(
                created.session_id,
                confirmed_action_ids=frozenset({"i1a1"}),
            )
            self.assertEqual(final.status, AgentSessionStatus.COMPLETE)
            self.assertEqual(target.read_text(), "new")
            self.assertEqual((root / "later.txt").read_text(), "later")

    def test_every_terminal_state_rejects_step_and_resume_before_planner_work(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            config = RuntimeConfig.for_workspace(root)
            store = AgentSessionStore(root / ".lain" / "sessions")
            runtime = RuntimeEngine(config)
            planner = SequencePlanner([complete()])
            ctl = AgentController(
                AgentPlanningService(planner, max_actions=8),
                runtime,
                store,
                AgentBudget(12, 8, 32, 900.0),
            )
            for status in (
                AgentSessionStatus.BLOCKED,
                AgentSessionStatus.COMPLETE,
                AgentSessionStatus.CANCELLED,
                AgentSessionStatus.BUDGET_EXHAUSTED,
                AgentSessionStatus.FAILED,
            ):
                with self.subTest(status=status):
                    created = ctl.create("x")
                    terminal = replace(
                        created,
                        status=status,
                        terminal_reason="terminal",
                    )
                    store.save(terminal)
                    with self.assertRaises(LainError) as ctx:
                        ctl.step(created.session_id)
                    self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
                    with self.assertRaises(LainError) as ctx:
                        ctl.run_until_stop(created.session_id)
                    self.assertEqual(ctx.exception.code, ErrorCode.AGENT_STATE_INVALID)
            self.assertEqual(planner.calls, [])
