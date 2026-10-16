"""Model proposal adapter: existing validated AgentPlanner -> trusted workflow.

A model supplies possible actions, never action IDs, policy decisions, approval,
verified outcomes, or executable arbitrary code. No effects occur during propose().
"""
from __future__ import annotations

from typing import Any

from lain.agent.planning import AgentPlanningService
from lain.errors import ErrorCode, LainError
from lain.planning.models import AgentPlannerStatus
from lain.workflows.engine import WorkflowRunner


class WorkflowIntentBridge:
    def __init__(self, planning: AgentPlanningService, runner: WorkflowRunner):
        self.planning = planning
        self.runner = runner

    def propose(self, goal: str, *, context: dict[str, Any] | None = None) -> dict:
        if (type(goal) is not str or not goal.strip()
                or len(goal.encode("utf-8")) > 4096):
            raise LainError(ErrorCode.ARGUMENT_INVALID, "workflow goal must be bounded and non-empty")
        if context is None:
            context = {}
        if type(context) is not dict:
            raise LainError(ErrorCode.ARGUMENT_INVALID, "workflow context must be an object")

        iteration = self.planning.decide(goal, context, 1)
        # A model claiming 'complete' is not evidence of any action: no fake receipt.
        if iteration.planner_status in {AgentPlannerStatus.COMPLETE, AgentPlannerStatus.BLOCKED}:
            return {"status": "no_action", "workflow_id": None}
        if iteration.planner_status is not AgentPlannerStatus.CONTINUE or not iteration.actions:
            raise LainError(ErrorCode.PLANNER_OUTPUT_INVALID, "unusable workflow model decision")

        nodes: list[dict] = []
        for i, record in enumerate(iteration.actions, 1):
            nodes.append({
                "id": f"step{i}",
                "capability": record.action.type,
                "arguments": record.action.arguments,
                "depends_on": [f"step{i-1}"] if i > 1 else [],
            })

        # Double preflight: existing planner service validates capability and
        # arguments, then the workflow compiler validates graph and runtime.
        workflow_id = self.runner.create(nodes)
        return {"status": "planned", "workflow_id": workflow_id}
