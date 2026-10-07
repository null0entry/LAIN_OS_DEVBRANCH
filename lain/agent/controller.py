from __future__ import annotations

from dataclasses import replace
from datetime import datetime, timezone
from time import monotonic
from typing import Callable
from uuid import uuid4

from lain.agent.context import build_agent_context
from lain.agent.models import (
    AgentActionRecord,
    AgentBudget,
    AgentIterationRecord,
    AgentSession,
    AgentSessionStatus,
    OFFLINE_DEMO_BINDING,
    PlannerBinding,
    TERMINAL_AGENT_STATUSES,
)
from lain.agent.planning import AgentPlanningService
from lain.agent.store import AgentSessionStore
from lain.errors import ErrorCode, LainError
from lain.planning.models import AgentPlanner, AgentPlannerStatus
from lain.protocol.models import (
    ActionEnvelope,
    ActionResult,
    ActionStatus,
    VerificationResult,
    VerificationStatus,
)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


class AgentController:
    def __init__(
        self,
        planning: AgentPlanningService,
        runtime,
        store: AgentSessionStore,
        default_budget: AgentBudget,
        *,
        monotonic_clock: Callable[[], float] = monotonic,
        now: Callable[[], str] = utc_now,
        planner_binding_provider: Callable[[], PlannerBinding] | None = None,
        planner_factory: Callable[[PlannerBinding], AgentPlanner] | None = None,
    ):
        self.planning = planning
        self.runtime = runtime
        self.store = store
        self.default_budget = default_budget
        self.monotonic_clock = monotonic_clock
        self.now = now
        self.planner_binding_provider = planner_binding_provider or (lambda: OFFLINE_DEMO_BINDING)
        self.planner_factory = planner_factory

    def create(self, goal: str) -> AgentSession:
        if not isinstance(goal, str) or not goal.strip():
            raise LainError(ErrorCode.ARGUMENT_INVALID, "goal must be a non-empty string")
        timestamp = self.now()
        planner_binding = self.planner_binding_provider()
        if not isinstance(planner_binding, PlannerBinding):
            raise LainError(ErrorCode.PLANNER_UNAVAILABLE, "planner binding is unavailable")
        budget = replace(
            self.default_budget,
            max_actions_per_batch=self.planning.max_actions,
        )
        session = AgentSession(
            version="1",
            session_id=str(uuid4()),
            goal=goal,
            status=AgentSessionStatus.CREATED,
            created_at=timestamp,
            updated_at=timestamp,
            iterations=(),
            total_attempted_actions=0,
            budget=budget,
            cumulative_runtime_seconds=0.0,
            terminal_reason=None,
            planner_binding=planner_binding,
        )
        self.store.save(session)
        return session

    def revise(
        self,
        session_id: str,
        revision_text: str,
        *,
        turn_id: int,
        expected_session: AgentSession,
    ) -> AgentSession:
        if (
            not isinstance(revision_text, str)
            or not revision_text.strip()
            or len(revision_text.encode("utf-8")) > 4096
        ):
            raise LainError(ErrorCode.ARGUMENT_INVALID, "revision text must be bounded and non-empty")
        if not isinstance(turn_id, int) or isinstance(turn_id, bool) or turn_id < 1:
            raise LainError(ErrorCode.ARGUMENT_INVALID, "revision turn_id must be positive")

        if (
            not isinstance(expected_session, AgentSession)
            or expected_session.session_id != session_id
        ):
            raise LainError(
                ErrorCode.ARGUMENT_INVALID,
                "expected_session must match the revision target",
            )

        with self.store.lease(session_id):
            session = self.store.load(session_id)
            if session != expected_session:
                raise LainError(
                    ErrorCode.AGENT_STATE_INVALID,
                    "revision target changed since the turn was accepted",
                    details={"session_id": session_id},
                )
            self._reject_terminal(session)
            if session.last_revision_turn_id is not None and turn_id <= session.last_revision_turn_id:
                raise LainError(
                    ErrorCode.AGENT_STATE_INVALID,
                    "revision turn is stale or already applied",
                    details={
                        "turn_id": turn_id,
                        "last_revision_turn_id": session.last_revision_turn_id,
                    },
                )

            revised_goal = f"{session.goal}\n\nUser revision: {revision_text.strip()}"
            if len(revised_goal.encode("utf-8")) > 4096:
                raise LainError(ErrorCode.ARGUMENT_INVALID, "revised goal exceeds byte limit")

            iterations = tuple(
                replace(
                    iteration,
                    actions=tuple(
                        replace(
                            record,
                            result=ActionResult(
                                action_id=record.action.id,
                                status=ActionStatus.SKIPPED,
                                details={"reason": "superseded_by_conversational_revision"},
                                verification=VerificationResult(VerificationStatus.NOT_APPLICABLE),
                                error_code="AGENT_REVISION_SUPERSEDED",
                            ),
                        )
                        if (
                            record.result is None
                            or record.result.status is ActionStatus.CONFIRMATION_REQUIRED
                        )
                        else record
                        for record in iteration.actions
                    ),
                )
                if iteration.planner_status is AgentPlannerStatus.CONTINUE
                else iteration
                for iteration in session.iterations
            )
            revised = replace(
                session,
                goal=revised_goal,
                status=AgentSessionStatus.PLANNING,
                updated_at=self.now(),
                iterations=iterations,
                terminal_reason=None,
                last_revision_turn_id=turn_id,
            )
            self.store.save(revised)
            return revised

    def step(
        self,
        session_id: str,
        *,
        confirmed_action_ids: frozenset[str] = frozenset(),
    ) -> AgentSession:
        with self.store.lease(session_id):
            session = self.store.load(session_id)
            self._reject_terminal(session)
            return self._step_unlocked(session, confirmed_action_ids=confirmed_action_ids)

    def run_until_stop(
        self,
        session_id: str,
        *,
        confirmed_action_ids: frozenset[str] = frozenset(),
    ) -> AgentSession:
        with self.store.lease(session_id):
            session = self.store.load(session_id)
            self._reject_terminal(session)
            while True:
                session = self._step_unlocked(
                    session,
                    confirmed_action_ids=confirmed_action_ids,
                )
                if session.status in TERMINAL_AGENT_STATUSES:
                    return session
                if session.status is AgentSessionStatus.PAUSED_CONFIRMATION:
                    return session

    def cancel(self, session_id: str) -> AgentSession:
        with self.store.lease(session_id):
            session = self.store.load(session_id)
            self._reject_terminal(session)
            cancelled = session.transition(
                AgentSessionStatus.CANCELLED,
                updated_at=self.now(),
                terminal_reason="cancelled by user",
            )
            self.store.save(cancelled)
            return cancelled

    def _step_unlocked(
        self,
        session: AgentSession,
        *,
        confirmed_action_ids: frozenset[str],
    ) -> AgentSession:
        self._reject_terminal(session)
        if session.status in {AgentSessionStatus.CREATED, AgentSessionStatus.PLANNING}:
            return self._planning_step(session)
        if session.status in {
            AgentSessionStatus.EXECUTING,
            AgentSessionStatus.PAUSED_CONFIRMATION,
        }:
            return self._execution_step(
                session,
                confirmed_action_ids=confirmed_action_ids,
            )
        raise LainError(
            ErrorCode.AGENT_STATE_INVALID,
            "agent session cannot advance from this state",
            details={"status": session.status.value},
        )

    def _planning_step(self, session: AgentSession) -> AgentSession:
        if session.status is AgentSessionStatus.CREATED:
            session = session.transition(
                AgentSessionStatus.PLANNING,
                updated_at=self.now(),
            )
            self.store.save(session)

        budget_stop = self._planning_budget_stop(session)
        if budget_stop is not None:
            return budget_stop

        started = self.monotonic_clock()
        try:
            context = build_agent_context(session)
            planning = self.planning
            if self.planner_factory is not None:
                planning = AgentPlanningService(
                    self.planner_factory(session.planner_binding),
                    registry=self.planning.registry,
                    max_actions=self.planning.max_actions,
                )
            iteration = planning.decide(
                context["goal"],
                context,
                session.iteration_count + 1,
            )
        except LainError as exc:
            elapsed = max(0.0, self.monotonic_clock() - started)
            current = replace(
                session,
                cumulative_runtime_seconds=session.cumulative_runtime_seconds + elapsed,
            )
            if exc.code is ErrorCode.PLANNER_CANCELLED:
                cancelled = current.transition(
                    AgentSessionStatus.CANCELLED,
                    updated_at=self.now(),
                    terminal_reason="planner call cancelled",
                )
                self.store.save(cancelled)
                return cancelled
            failed = current.transition(
                AgentSessionStatus.FAILED,
                updated_at=self.now(),
                terminal_reason=f"planner failed: {exc.code.value}",
            )
            self.store.save(failed)
            return failed
        elapsed = max(0.0, self.monotonic_clock() - started)
        session = replace(
            session,
            iterations=session.iterations + (iteration,),
            cumulative_runtime_seconds=session.cumulative_runtime_seconds + elapsed,
            updated_at=self.now(),
        )

        if iteration.planner_status is AgentPlannerStatus.COMPLETE:
            complete = session.transition(
                AgentSessionStatus.COMPLETE,
                updated_at=self.now(),
                terminal_reason=f"planner marked complete: {iteration.planner_reason}",
            )
            self.store.save(complete)
            return complete

        if iteration.planner_status is AgentPlannerStatus.BLOCKED:
            blocked = session.transition(
                AgentSessionStatus.BLOCKED,
                updated_at=self.now(),
                terminal_reason=f"planner blocked: {iteration.planner_reason}",
            )
            self.store.save(blocked)
            return blocked

        if session.cumulative_runtime_seconds >= session.budget.max_runtime_seconds:
            exhausted = session.transition(
                AgentSessionStatus.BUDGET_EXHAUSTED,
                updated_at=self.now(),
                terminal_reason="agent runtime budget exhausted after planning",
            )
            self.store.save(exhausted)
            return exhausted

        executing = session.transition(
            AgentSessionStatus.EXECUTING,
            updated_at=self.now(),
        )
        self.store.save(executing)
        return executing

    def _execution_step(
        self,
        session: AgentSession,
        *,
        confirmed_action_ids: frozenset[str],
    ) -> AgentSession:
        iteration = self._current_continue_iteration(session)
        selected_index: int | None = None

        if session.status is AgentSessionStatus.PAUSED_CONFIRMATION:
            for index, record in enumerate(iteration.actions):
                if (
                    record.result is not None
                    and record.result.status is ActionStatus.CONFIRMATION_REQUIRED
                ):
                    selected_index = index
                    break
            if selected_index is None:
                raise LainError(
                    ErrorCode.AGENT_STATE_INVALID,
                    "paused session has no confirmation-required action",
                )
            action_id = iteration.actions[selected_index].action.id
            if action_id not in confirmed_action_ids:
                return session
        else:
            confirmation_index: int | None = None
            for index, record in enumerate(iteration.actions):
                if (
                    record.result is not None
                    and record.result.status is ActionStatus.CONFIRMATION_REQUIRED
                ):
                    confirmation_index = index
                    break
            if confirmation_index is not None:
                action_id = iteration.actions[confirmation_index].action.id
                if action_id not in confirmed_action_ids:
                    paused = session.transition(
                        AgentSessionStatus.PAUSED_CONFIRMATION,
                        updated_at=self.now(),
                    )
                    self.store.save(paused)
                    return paused
                selected_index = confirmation_index
            else:
                for index, record in enumerate(iteration.actions):
                    if record.result is None:
                        selected_index = index
                        break
                if selected_index is None:
                    raise LainError(
                        ErrorCode.AGENT_STATE_INVALID,
                        "executing session has no pending action",
                    )

        budget_stop = self._execution_budget_stop(session)
        if budget_stop is not None:
            return budget_stop

        if session.status is AgentSessionStatus.PAUSED_CONFIRMATION:
            session = session.transition(
                AgentSessionStatus.EXECUTING,
                updated_at=self.now(),
            )
            self.store.save(session)

        iteration = self._current_continue_iteration(session)
        record = iteration.actions[selected_index]
        envelope = ActionEnvelope(
            version="0",
            request_id=iteration.request_id or "",
            intent=session.goal,
            actions=tuple(item.action for item in iteration.actions),
        )

        started = self.monotonic_clock()
        try:
            result = self.runtime.execute_action(
                envelope,
                record.action.id,
                confirmed_action_ids=confirmed_action_ids,
            )
        except LainError as exc:
            elapsed = max(0.0, self.monotonic_clock() - started)
            session = replace(
                session,
                cumulative_runtime_seconds=session.cumulative_runtime_seconds + elapsed,
            )
            if exc.code is ErrorCode.DUPLICATE_REQUEST:
                blocked = session.transition(
                    AgentSessionStatus.BLOCKED,
                    updated_at=self.now(),
                    terminal_reason=(
                        "reconciliation required: audit records execution for the "
                        "current action but the session checkpoint does not"
                    ),
                )
                self.store.save(blocked)
                return blocked
            failed = session.transition(
                AgentSessionStatus.FAILED,
                updated_at=self.now(),
                terminal_reason=f"runtime failed: {exc.code.value}",
            )
            self.store.save(failed)
            return failed

        elapsed = max(0.0, self.monotonic_clock() - started)
        attempted_increment = (
            0
            if result.status
            in {
                ActionStatus.CONFIRMATION_REQUIRED,
                ActionStatus.DENIED,
                ActionStatus.SKIPPED,
            }
            else 1
        )
        updated_records = list(iteration.actions)
        updated_records[selected_index] = AgentActionRecord(
            action=record.action,
            result=result,
        )
        updated_iteration = replace(
            iteration,
            actions=tuple(updated_records),
        )
        updated_iterations = list(session.iterations)
        updated_iterations[-1] = updated_iteration
        session = replace(
            session,
            iterations=tuple(updated_iterations),
            total_attempted_actions=session.total_attempted_actions + attempted_increment,
            cumulative_runtime_seconds=session.cumulative_runtime_seconds + elapsed,
            updated_at=self.now(),
        )

        if result.status is ActionStatus.CONFIRMATION_REQUIRED:
            paused = session.transition(
                AgentSessionStatus.PAUSED_CONFIRMATION,
                updated_at=self.now(),
            )
            self.store.save(paused)
            return paused

        if result.status in {ActionStatus.DENIED, ActionStatus.UNSUPPORTED, ActionStatus.SKIPPED}:
            blocked = session.transition(
                AgentSessionStatus.BLOCKED,
                updated_at=self.now(),
                terminal_reason=f"action {record.action.id} stopped: {result.status.value}",
            )
            self.store.save(blocked)
            return blocked

        if result.status is ActionStatus.FAILURE:
            failed = session.transition(
                AgentSessionStatus.FAILED,
                updated_at=self.now(),
                terminal_reason=f"action {record.action.id} failed",
            )
            self.store.save(failed)
            return failed

        if session.cumulative_runtime_seconds >= session.budget.max_runtime_seconds:
            exhausted = session.transition(
                AgentSessionStatus.BUDGET_EXHAUSTED,
                updated_at=self.now(),
                terminal_reason="agent runtime budget exhausted after action execution",
            )
            self.store.save(exhausted)
            return exhausted

        if all(item.result is not None and item.result.status is ActionStatus.SUCCESS for item in updated_iteration.actions):
            planning = session.transition(
                AgentSessionStatus.PLANNING,
                updated_at=self.now(),
            )
            self.store.save(planning)
            return planning

        self.store.save(session)
        return session

    def _planning_budget_stop(self, session: AgentSession) -> AgentSession | None:
        reason: str | None = None
        if session.iteration_count >= session.budget.max_iterations:
            reason = "agent iteration budget exhausted"
        elif session.cumulative_runtime_seconds >= session.budget.max_runtime_seconds:
            reason = "agent runtime budget exhausted"
        if reason is None:
            return None
        exhausted = session.transition(
            AgentSessionStatus.BUDGET_EXHAUSTED,
            updated_at=self.now(),
            terminal_reason=reason,
        )
        self.store.save(exhausted)
        return exhausted

    def _execution_budget_stop(self, session: AgentSession) -> AgentSession | None:
        reason: str | None = None
        if session.total_attempted_actions >= session.budget.max_total_actions:
            reason = "agent total action budget exhausted"
        elif session.cumulative_runtime_seconds >= session.budget.max_runtime_seconds:
            reason = "agent runtime budget exhausted"
        if reason is None:
            return None
        exhausted = session.transition(
            AgentSessionStatus.BUDGET_EXHAUSTED,
            updated_at=self.now(),
            terminal_reason=reason,
        )
        self.store.save(exhausted)
        return exhausted

    @staticmethod
    def _current_continue_iteration(session: AgentSession) -> AgentIterationRecord:
        if not session.iterations:
            raise LainError(
                ErrorCode.AGENT_STATE_INVALID,
                "agent session has no current iteration",
            )
        iteration = session.iterations[-1]
        if iteration.planner_status is not AgentPlannerStatus.CONTINUE:
            raise LainError(
                ErrorCode.AGENT_STATE_INVALID,
                "current planner iteration is not executable",
            )
        return iteration

    @staticmethod
    def _reject_terminal(session: AgentSession) -> None:
        if session.status in TERMINAL_AGENT_STATUSES:
            raise LainError(
                ErrorCode.AGENT_STATE_INVALID,
                "terminal agent session cannot resume",
                details={"status": session.status.value},
            )
