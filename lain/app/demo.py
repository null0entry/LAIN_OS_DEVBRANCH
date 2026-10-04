"""Deterministic offline demonstration, explicitly not a language model."""
from pathlib import Path

from lain.planning.models import AgentPlannerDecision, AgentPlannerStatus, ProposedAction


COMMANDS = ("Create demo file", "Show battery", "Show demo toast", "Vibrate briefly",
            "Copy demo text", "Share demo text")


class DemoPlanner:
    MAX_DEMO_FILES = 1000

    def __init__(self, workspace: Path):
        self.workspace = workspace

    def _next_demo_file(self) -> Path | None:
        for number in range(1, self.MAX_DEMO_FILES + 1):
            name = "demo.txt" if number == 1 else f"demo-{number}.txt"
            candidate = self.workspace / name
            if not candidate.exists() and not candidate.is_symlink():
                return candidate
        return None

    def decide(self, goal, context, capabilities):
        if context["history"]:
            return AgentPlannerDecision(AgentPlannerStatus.COMPLETE,
                                        "Offline demo finished; inspect execution and verification results.", ())
        normalized_goal = goal.strip().lower()
        if normalized_goal == "create demo file":
            target = self._next_demo_file()
            if target is None:
                return AgentPlannerDecision(
                    AgentPlannerStatus.BLOCKED,
                    "Offline demo workspace has no free bounded file slot.",
                    (),
                )
            action = ProposedAction("file.write_text", {
                "path": str(target), "content": "Hello from LAIN_OS.\n",
            })
            return AgentPlannerDecision(
                AgentPlannerStatus.CONTINUE,
                "Run the owner-selected offline demo.",
                (action,),
            )

        demos = {
            "show battery": ProposedAction("android.battery_status", {}),
            "show demo toast": ProposedAction("android.toast", {"content": "Hello from LAIN_OS."}),
            "vibrate briefly": ProposedAction("android.vibrate", {"duration_ms": 200}),
            "copy demo text": ProposedAction("android.clipboard_set", {"content": "Hello from LAIN_OS."}),
            "share demo text": ProposedAction("android.share_text", {"content": "Hello from LAIN_OS."}),
        }
        action = demos.get(normalized_goal)
        if action is None:
            return AgentPlannerDecision(AgentPlannerStatus.BLOCKED,
                                        "No language-model planner configured. Choose a listed offline demo.", ())
        return AgentPlannerDecision(AgentPlannerStatus.CONTINUE, "Run the owner-selected offline demo.", (action,))
