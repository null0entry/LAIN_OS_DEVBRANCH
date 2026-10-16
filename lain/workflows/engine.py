"""Trusted, checkpointed execution for bounded structured workflow proposals.

This is a backend foundation, not a model approval mechanism or Android UI.
Every node still executes through RuntimeEngine's existing policy, audit and verifier.
"""
from __future__ import annotations

import fcntl
import json
import os
import re
import tempfile
from contextlib import contextmanager
from pathlib import Path
from uuid import UUID, uuid4

from lain.errors import ErrorCode, LainError
from lain.execution.filesystem import sha256_file
from lain.security.paths import resolve_allowed_path
from lain.protocol.models import Action, ActionEnvelope, ActionStatus, VerificationStatus


_NODE = re.compile(r"[A-Za-z][A-Za-z0-9_-]{0,39}\Z")
_STATES = frozenset({"pending", "awaiting_confirmation", "complete", "blocked"})
_FILE_OUTPUTS = {"file.write_text": "path", "file.copy": "destination", "file.move": "destination"}
_FILE_CONSUMERS = frozenset({"file.copy", "file.move"})


def _invalid(message: str) -> LainError:
    return LainError(ErrorCode.PLANNER_OUTPUT_INVALID, message)


def _ordered_steps(proposal, max_steps: int) -> list[dict]:
    if type(proposal) is not list or not 1 <= len(proposal) <= min(max_steps, 32):
        raise _invalid("workflow must contain a bounded non-empty list of steps")

    nodes: dict[str, dict] = {}
    for raw in proposal:
        if type(raw) is not dict or set(raw) != {"id", "capability", "arguments", "depends_on"}:
            raise _invalid("workflow step fields are invalid")
        key = raw["id"]
        if type(key) is not str or _NODE.fullmatch(key) is None or key in nodes:
            raise _invalid("workflow step identifier is invalid or duplicated")
        if type(raw["capability"]) is not str or type(raw["arguments"]) is not dict:
            raise _invalid("workflow capability/arguments are invalid")
        deps = raw["depends_on"]
        if type(deps) is not list or any(type(d) is not str for d in deps) or len(set(deps)) != len(deps):
            raise _invalid("workflow dependencies must be unique identifiers")
        nodes[key] = dict(raw)

    for node in nodes.values():
        if any(d not in nodes for d in node["depends_on"]):
            raise _invalid("workflow references an unknown dependency")

    # Stable topological sort, independent of model-proposed action IDs.
    remaining = dict(nodes)
    ordered: list[dict] = []
    done: set[str] = set()
    while remaining:
        ready = next((k for k, v in remaining.items() if set(v["depends_on"]) <= done), None)
        if ready is None:
            raise _invalid("workflow dependencies form a cycle")
        ordered.append(remaining.pop(ready))
        done.add(ready)
    return ordered


def _workflow_id(value: str) -> str:
    if type(value) is not str:
        raise LainError(ErrorCode.ARGUMENT_INVALID, "workflow ID is invalid")
    try:
        parsed = UUID(value)
    except (ValueError, TypeError, AttributeError) as exc:
        raise LainError(ErrorCode.ARGUMENT_INVALID, "workflow ID is invalid") from exc
    if str(parsed) != value:
        raise LainError(ErrorCode.ARGUMENT_INVALID, "workflow ID is invalid")
    return value


class WorkflowRunner:
    """Executes one audited step per advance(), with fail-closed crash recovery.

    The caller must explicitly obtain an owner's approval before supplying a
    confirmed_action_id. Saved state is private; UI only receives a redacted summary.
    """

    def __init__(self, runtime, state_dir: Path):
        self.runtime = runtime
        self.state_dir = Path(state_dir).resolve()
        self.state_dir.mkdir(mode=0o700, parents=True, exist_ok=True)

    def create(self, proposal: list[dict]) -> str:
        ordered = _ordered_steps(proposal, self.runtime.config.max_actions)
        workflow_id = str(uuid4())
        steps = [
            {"id": step["id"], "action_id": f"s{i}", "capability": step["capability"],
             "arguments": step["arguments"], "depends_on": step["depends_on"]}
            for i, step in enumerate(ordered, 1)
        ]
        # Resolve only shape-valid references to *already declared* dependency
        # outputs. No planner-authored path or arbitrary dictionary is trusted.
        preview = [self._arguments_for_step(s, steps, {}, preview=True) for s in steps]
        validation_steps = [{**s, "arguments": args} for s, args in zip(steps, preview)]
        self.runtime.preflight(self._envelope(workflow_id, validation_steps))
        state = {
            "version": 1, "workflow_id": workflow_id, "steps": steps,
            "status": "pending", "completed": [], "results": [], "artifacts": {}, "in_flight": None,
        }
        self._save(workflow_id, state)
        return workflow_id

    def inspect(self, workflow_id: str) -> dict:
        return self._summary(self._load(workflow_id))

    def advance(
        self, workflow_id: str, *, confirmed_action_ids: frozenset[str] = frozenset()
    ) -> dict:
        workflow_id = _workflow_id(workflow_id)
        if type(confirmed_action_ids) is not frozenset or any(
            type(x) is not str for x in confirmed_action_ids
        ):
            raise LainError(ErrorCode.ARGUMENT_INVALID, "confirmations must be trusted action identifiers")
        with self._locked(workflow_id):
            state = self._load(workflow_id)
            if state["in_flight"] is not None:
                # The external effect may already have happened. Never auto-replay.
                raise LainError(
                    ErrorCode.AGENT_STATE_INVALID,
                    "workflow has an uncertain in-flight action; manual reconciliation required",
                )
            if state["status"] in {"complete", "blocked"}:
                return self._summary(state)

            completed = set(state["completed"])
            step = next((s for s in state["steps"] if s["id"] not in completed), None)
            if step is None:
                state["status"] = "complete"
                self._save(workflow_id, state)
                return self._summary(state)

            if any(dep not in completed for dep in step["depends_on"]):
                raise LainError(ErrorCode.AGENT_STATE_INVALID, "workflow dependency state is inconsistent")
            resolved = self._arguments_for_step(step, state["steps"], state["artifacts"])
            action = Action(step["action_id"], step["capability"], resolved)
            envelope = ActionEnvelope("0", workflow_id, "trusted-workflow", (action,))
            self.runtime.preflight(envelope)
            if confirmed_action_ids - {action.id}:
                raise LainError(ErrorCode.ARGUMENT_INVALID, "confirmation does not match current action")

            # State is made durable before crossing any external action boundary.
            # A crash after this point will fail closed instead of repeating the action.
            state["in_flight"] = action.id
            self._save(workflow_id, state)
            result = self.runtime.execute_action(
                envelope, action.id, confirmed_action_ids=confirmed_action_ids
            )
            if result.status is ActionStatus.CONFIRMATION_REQUIRED:
                state["in_flight"] = None
                state["status"] = "awaiting_confirmation"
                self._save(workflow_id, state)
                return self._summary(state)

            state["results"].append({
                "action_id": action.id,
                "status": result.status.value,
                "verification": result.verification.status.value,
                "error_code": result.error_code,
            })
            state["in_flight"] = None
            if (
                result.status is ActionStatus.SUCCESS
                and result.verification.status is VerificationStatus.PASSED
            ):
                state["completed"].append(step["id"])
                field = _FILE_OUTPUTS.get(step["capability"])
                if field is not None:
                    details = result.details
                    digest = details.get("sha256") if field == "path" else details.get("sha256_before")
                    if isinstance(details.get(field), str) and isinstance(digest, str):
                        state["artifacts"][f"{step['id']}.{field}"] = {
                            "path": details[field], "sha256": digest,
                        }
                state["status"] = (
                    "complete" if len(state["completed"]) == len(state["steps"])
                    else "pending"
                )
            else:
                # LIMITED/UNAVAILABLE readback is NOT verified completion.
                state["status"] = "blocked"
            self._save(workflow_id, state)
            return self._summary(state)

    def _arguments_for_step(
        self, step: dict, steps: list[dict], artifacts: dict,
        *, preview: bool = False,
    ) -> dict:
        resolved = {}
        producers = {s["id"]: s for s in steps}
        for name, value in step["arguments"].items():
            if type(value) is not dict:
                resolved[name] = value
                continue
            if (set(value) != {"$artifact"} or step["capability"] not in _FILE_CONSUMERS
                    or name != "source" or type(value["$artifact"]) is not str):
                raise _invalid("unsupported artifact binding")
            reference = value["$artifact"]
            if reference.count(".") != 1:
                raise _invalid("invalid artifact reference")
            producer_id, field = reference.split(".")
            producer = producers.get(producer_id)
            if (producer is None or producer_id not in step["depends_on"]
                    or field != _FILE_OUTPUTS.get(producer["capability"])):
                raise _invalid("artifact reference must name a verified direct dependency output")
            if preview:
                # Preview is a validated in-root string of the correct argument type.
                # Only file.copy/file.move source accepts a bound file output.
                resolved[name] = str(self.runtime.config.allowed_roots[0] / ".lain-validation-placeholder")
                continue
            receipt = artifacts.get(reference)
            if (type(receipt) is not dict or set(receipt) != {"path", "sha256"}
                    or type(receipt["path"]) is not str or type(receipt["sha256"]) is not str):
                raise LainError(ErrorCode.AGENT_STATE_INVALID, "verified artifact receipt missing")
            target = resolve_allowed_path(receipt["path"], self.runtime.config.allowed_roots)
            try:
                if not target.is_file() or sha256_file(target) != receipt["sha256"]:
                    raise LainError(ErrorCode.VERIFICATION_FAILED, "artifact changed since verification")
            except OSError as exc:
                raise LainError(ErrorCode.VERIFICATION_FAILED, "artifact no longer available") from exc
            resolved[name] = str(target)
        return resolved

    @staticmethod
    def _envelope(workflow_id: str, steps: list[dict]) -> ActionEnvelope:
        return ActionEnvelope(
            "0", workflow_id, "trusted-workflow",
            tuple(Action(s["action_id"], s["capability"], s["arguments"]) for s in steps),
        )

    @staticmethod
    def _summary(state: dict) -> dict:
        return {
            "workflow_id": state["workflow_id"],
            "status": state["status"],
            "completed": list(state["completed"]),
            "results": [dict(r) for r in state["results"]],
            "current_action_id": (
                next((s["action_id"] for s in state["steps"] if s["id"] not in state["completed"]), None)
                if state["status"] == "awaiting_confirmation" else None
            ),
        }

    def _path(self, workflow_id: str) -> Path:
        return self.state_dir / (_workflow_id(workflow_id) + ".json")

    def _load(self, workflow_id: str) -> dict:
        path = self._path(workflow_id)
        try:
            fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
            with os.fdopen(fd, "r", encoding="utf-8") as stream:
                state = json.load(stream)
            if (
                type(state) is not dict or state.get("version") != 1
                or state.get("workflow_id") != workflow_id
                or state.get("status") not in _STATES
                or type(state.get("completed")) is not list
                or type(state.get("results")) is not list
                or type(state.get("steps")) is not list
                or type(state.get("artifacts")) is not dict
            ):
                raise ValueError("invalid state")
            return state
        except (OSError, ValueError, TypeError, UnicodeError) as exc:
            raise LainError(ErrorCode.AGENT_STATE_INVALID, "workflow checkpoint is missing or invalid") from exc

    def _save(self, workflow_id: str, state: dict) -> None:
        # json.dumps before opening prevents partial files for non-serializable proposals.
        try:
            payload = json.dumps(state, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        except (ValueError, TypeError, UnicodeError) as exc:
            raise _invalid("workflow contains non-serializable values") from exc
        fd, name = tempfile.mkstemp(dir=self.state_dir, prefix=".workflow-", suffix=".tmp")
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(name, self._path(workflow_id))
        finally:
            try:
                os.unlink(name)
            except FileNotFoundError:
                pass

    @contextmanager
    def _locked(self, workflow_id: str):
        path = self.state_dir / (workflow_id + ".lock")
        fd = os.open(path, os.O_CREAT | os.O_RDWR | getattr(os, "O_NOFOLLOW", 0), 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX)
            yield
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)
            os.close(fd)
