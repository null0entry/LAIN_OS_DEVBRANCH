from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
import math
import re
import ipaddress
from typing import Any
from urllib.parse import urlsplit
from uuid import UUID

from lain.errors import ErrorCode, LainError
from lain.planning.models import AgentPlannerStatus
from lain.protocol.models import (
    Action,
    ActionResult,
    ActionStatus,
    VerificationResult,
    VerificationStatus,
)


class AgentSessionStatus(str, Enum):
    CREATED = "created"
    PLANNING = "planning"
    EXECUTING = "executing"
    PAUSED_CONFIRMATION = "paused_confirmation"
    BLOCKED = "blocked"
    COMPLETE = "complete"
    CANCELLED = "cancelled"
    BUDGET_EXHAUSTED = "budget_exhausted"
    FAILED = "failed"


TERMINAL_AGENT_STATUSES = frozenset(
    {
        AgentSessionStatus.BLOCKED,
        AgentSessionStatus.COMPLETE,
        AgentSessionStatus.CANCELLED,
        AgentSessionStatus.BUDGET_EXHAUSTED,
        AgentSessionStatus.FAILED,
    }
)


_ALLOWED_TRANSITIONS = {
    AgentSessionStatus.CREATED: {
        AgentSessionStatus.PLANNING,
        AgentSessionStatus.CANCELLED,
        AgentSessionStatus.FAILED,
    },
    AgentSessionStatus.PLANNING: {
        AgentSessionStatus.EXECUTING,
        AgentSessionStatus.COMPLETE,
        AgentSessionStatus.BLOCKED,
        AgentSessionStatus.CANCELLED,
        AgentSessionStatus.BUDGET_EXHAUSTED,
        AgentSessionStatus.FAILED,
    },
    AgentSessionStatus.EXECUTING: {
        AgentSessionStatus.PLANNING,
        AgentSessionStatus.PAUSED_CONFIRMATION,
        AgentSessionStatus.BLOCKED,
        AgentSessionStatus.COMPLETE,
        AgentSessionStatus.CANCELLED,
        AgentSessionStatus.BUDGET_EXHAUSTED,
        AgentSessionStatus.FAILED,
    },
    AgentSessionStatus.PAUSED_CONFIRMATION: {
        AgentSessionStatus.EXECUTING,
        AgentSessionStatus.BLOCKED,
        AgentSessionStatus.CANCELLED,
        AgentSessionStatus.BUDGET_EXHAUSTED,
        AgentSessionStatus.FAILED,
    },
}


def _invalid(message: str, **details: Any) -> LainError:
    return LainError(ErrorCode.AGENT_SESSION_INVALID, message, details=details)


def _validate_planner_endpoint(mode: str, base_url: str, allow_insecure_lan_http: bool) -> None:
    try:
        parsed = urlsplit(base_url)
        hostname = parsed.hostname
        port = parsed.port
    except ValueError as exc:
        raise _invalid("planner base_url is invalid") from exc
    if (
        not hostname
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise _invalid("planner base_url is invalid")
    if parsed.scheme == "https":
        return
    if parsed.scheme != "http" or mode != "local" or not allow_insecure_lan_http:
        raise _invalid("planner base_url must use HTTPS")
    try:
        address = ipaddress.ip_address(hostname)
    except ValueError as exc:
        raise _invalid("insecure local planner must use a private address literal") from exc
    private_networks = (
        ipaddress.ip_network("10.0.0.0/8"),
        ipaddress.ip_network("172.16.0.0/12"),
        ipaddress.ip_network("192.168.0.0/16"),
        ipaddress.ip_network("fc00::/7"),
        ipaddress.ip_network("fe80::/10"),
    )
    if not address.is_loopback and not any(address in network for network in private_networks):
        raise _invalid("insecure local planner must use a private address literal")


@dataclass(frozen=True, slots=True)
class PlannerBinding:
    profile_id: str
    mode: str
    protocol: str
    base_url: str
    model: str
    credential_ref: str | None
    timeout_seconds: float
    max_response_bytes: int
    response_mode: str
    allow_insecure_lan_http: bool

    def __post_init__(self) -> None:
        if not isinstance(self.profile_id, str) or not self.profile_id.strip():
            raise _invalid("planner profile_id must be non-empty")
        if self.mode not in {"demo", "cloud", "local", "on_device"}:
            raise _invalid("planner mode is invalid")
        if self.protocol not in {"demo_v1", "openai_compatible_v1", "gguf_native_v1"}:
            raise _invalid("planner protocol is invalid")
        if not isinstance(self.base_url, str) or not isinstance(self.model, str):
            raise _invalid("planner endpoint/model fields are invalid")
        if self.credential_ref is not None and (
            not isinstance(self.credential_ref, str) or not self.credential_ref
        ):
            raise _invalid("planner credential_ref is invalid")
        if (
            not isinstance(self.timeout_seconds, (int, float))
            or isinstance(self.timeout_seconds, bool)
            or not math.isfinite(self.timeout_seconds)
            or self.timeout_seconds < 0
        ):
            raise _invalid("planner timeout_seconds is invalid")
        if (
            not isinstance(self.max_response_bytes, int)
            or isinstance(self.max_response_bytes, bool)
            or self.max_response_bytes < 0
        ):
            raise _invalid("planner max_response_bytes is invalid")
        if self.response_mode not in {"none", "json_schema", "json_object"}:
            raise _invalid("planner response_mode is invalid")
        if not isinstance(self.allow_insecure_lan_http, bool):
            raise _invalid("planner allow_insecure_lan_http is invalid")
        if self.mode == "demo":
            if (
                self.protocol != "demo_v1"
                or self.base_url
                or self.credential_ref is not None
                or self.timeout_seconds != 0
                or self.max_response_bytes != 0
                or self.response_mode != "none"
                or self.allow_insecure_lan_http
            ):
                raise _invalid("demo planner binding is invalid")
        elif self.mode == "on_device":
            if (
                self.protocol != "gguf_native_v1"
                or self.base_url
                or self.credential_ref is not None
                or re.fullmatch(r"[0-9a-f]{64}", self.model) is None
                or self.timeout_seconds <= 0
                or self.timeout_seconds > 180
                or self.max_response_bytes < 128
                or self.max_response_bytes > 65536
                or self.response_mode != "none"
                or self.allow_insecure_lan_http
            ):
                raise _invalid("on-device planner binding is invalid")
        else:
            if (
                self.protocol != "openai_compatible_v1"
                or not self.base_url.strip()
                or not self.model.strip()
                or self.timeout_seconds <= 0
                or self.max_response_bytes <= 0
                or self.response_mode == "none"
            ):
                raise _invalid("OpenAI-compatible planner binding is invalid")
            if self.mode == "cloud" and self.allow_insecure_lan_http:
                raise _invalid("cloud planner cannot allow insecure LAN HTTP")
            _validate_planner_endpoint(self.mode, self.base_url, self.allow_insecure_lan_http)

    def to_dict(self) -> dict[str, Any]:
        return {
            "profile_id": self.profile_id,
            "mode": self.mode,
            "protocol": self.protocol,
            "base_url": self.base_url,
            "model": self.model,
            "credential_ref": self.credential_ref,
            "timeout_seconds": self.timeout_seconds,
            "max_response_bytes": self.max_response_bytes,
            "response_mode": self.response_mode,
            "allow_insecure_lan_http": self.allow_insecure_lan_http,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "PlannerBinding":
        required = {
            "profile_id", "mode", "protocol", "base_url", "model", "credential_ref",
            "timeout_seconds", "max_response_bytes", "response_mode", "allow_insecure_lan_http",
        }
        try:
            if not isinstance(raw, dict) or set(raw) != required:
                raise ValueError("fields")
            return cls(
                profile_id=raw["profile_id"],
                mode=raw["mode"],
                protocol=raw["protocol"],
                base_url=raw["base_url"],
                model=raw["model"],
                credential_ref=raw["credential_ref"],
                timeout_seconds=raw["timeout_seconds"],
                max_response_bytes=raw["max_response_bytes"],
                response_mode=raw["response_mode"],
                allow_insecure_lan_http=raw["allow_insecure_lan_http"],
            )
        except LainError:
            raise
        except (KeyError, TypeError, ValueError, OverflowError) as exc:
            raise _invalid("planner binding is invalid") from exc


OFFLINE_DEMO_BINDING = PlannerBinding(
    profile_id="offline-demo",
    mode="demo",
    protocol="demo_v1",
    base_url="",
    model="offline_demo",
    credential_ref=None,
    timeout_seconds=0.0,
    max_response_bytes=0,
    response_mode="none",
    allow_insecure_lan_http=False,
)


@dataclass(frozen=True, slots=True)
class AgentBudget:
    max_iterations: int
    max_actions_per_batch: int
    max_total_actions: int
    max_runtime_seconds: float

    def __post_init__(self) -> None:
        for name, value in (
            ("max_iterations", self.max_iterations),
            ("max_actions_per_batch", self.max_actions_per_batch),
            ("max_total_actions", self.max_total_actions),
        ):
            if not isinstance(value, int) or isinstance(value, bool) or value < 1:
                raise _invalid(f"{name} must be a positive integer")
        if (
            not isinstance(self.max_runtime_seconds, (int, float))
            or isinstance(self.max_runtime_seconds, bool)
            or not math.isfinite(self.max_runtime_seconds)
            or self.max_runtime_seconds <= 0
        ):
            raise _invalid("max_runtime_seconds must be a finite positive number")

    def to_dict(self) -> dict[str, Any]:
        return {
            "max_iterations": self.max_iterations,
            "max_actions_per_batch": self.max_actions_per_batch,
            "max_total_actions": self.max_total_actions,
            "max_runtime_seconds": self.max_runtime_seconds,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "AgentBudget":
        try:
            if set(raw) != {
                "max_iterations",
                "max_actions_per_batch",
                "max_total_actions",
                "max_runtime_seconds",
            }:
                raise ValueError("fields")
            return cls(
                int(raw["max_iterations"]),
                int(raw["max_actions_per_batch"]),
                int(raw["max_total_actions"]),
                float(raw["max_runtime_seconds"]),
            )
        except (TypeError, ValueError, OverflowError) as exc:
            raise _invalid("agent budget is invalid") from exc


@dataclass(frozen=True, slots=True)
class AgentActionRecord:
    action: Action
    result: ActionResult | None = None

    def __post_init__(self) -> None:
        if self.result is not None and self.result.action_id != self.action.id:
            raise _invalid("agent action result id does not match action id")

    def to_dict(self) -> dict[str, Any]:
        return {
            "action": {
                "id": self.action.id,
                "type": self.action.type,
                "arguments": dict(self.action.arguments),
            },
            "result": self.result.to_dict() if self.result is not None else None,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "AgentActionRecord":
        try:
            if set(raw) != {"action", "result"}:
                raise ValueError("fields")
            action_raw = raw["action"]
            if not isinstance(action_raw, dict) or set(action_raw) != {"id", "type", "arguments"}:
                raise ValueError("action")
            if not isinstance(action_raw["id"], str) or not action_raw["id"]:
                raise ValueError("action id")
            if not isinstance(action_raw["type"], str) or not action_raw["type"]:
                raise ValueError("action type")
            if not isinstance(action_raw["arguments"], dict):
                raise ValueError("arguments")
            action = Action(action_raw["id"], action_raw["type"], dict(action_raw["arguments"]))

            result_raw = raw["result"]
            result = None
            if result_raw is not None:
                if not isinstance(result_raw, dict):
                    raise ValueError("result")
                allowed = {"action_id", "status", "details", "verification", "error_code"}
                if not set(result_raw).issubset(allowed) or not {"action_id", "status", "details", "verification"}.issubset(result_raw):
                    raise ValueError("result fields")
                verification_raw = result_raw["verification"]
                if not isinstance(verification_raw, dict) or set(verification_raw) != {"status", "details"}:
                    raise ValueError("verification")
                if not isinstance(result_raw["details"], dict) or not isinstance(verification_raw["details"], dict):
                    raise ValueError("details")
                verification = VerificationResult(
                    VerificationStatus(verification_raw["status"]),
                    dict(verification_raw["details"]),
                )
                result = ActionResult(
                    action_id=result_raw["action_id"],
                    status=ActionStatus(result_raw["status"]),
                    details=dict(result_raw["details"]),
                    verification=verification,
                    error_code=result_raw.get("error_code"),
                )
            return cls(action=action, result=result)
        except LainError:
            raise
        except (KeyError, TypeError, ValueError, OverflowError) as exc:
            raise _invalid("agent action record is invalid") from exc


@dataclass(frozen=True, slots=True)
class AgentIterationRecord:
    number: int
    planner_status: AgentPlannerStatus
    planner_reason: str
    request_id: str | None
    actions: tuple[AgentActionRecord, ...]

    def __post_init__(self) -> None:
        if self.number < 1:
            raise _invalid("iteration number must be >= 1")
        if not isinstance(self.planner_reason, str) or not self.planner_reason.strip():
            raise _invalid("planner reason must be non-empty")
        if self.planner_status is AgentPlannerStatus.CONTINUE:
            if not self.request_id or not self.actions:
                raise _invalid("continue iteration requires request_id and actions")
            try:
                UUID(self.request_id)
            except (ValueError, TypeError) as exc:
                raise _invalid("continue iteration request_id must be a UUID") from exc
        else:
            if self.request_id is not None or self.actions:
                raise _invalid(f"{self.planner_status.value} iteration must not contain request_id or actions")

    def to_dict(self) -> dict[str, Any]:
        return {
            "number": self.number,
            "planner_status": self.planner_status.value,
            "planner_reason": self.planner_reason,
            "request_id": self.request_id,
            "actions": [record.to_dict() for record in self.actions],
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "AgentIterationRecord":
        try:
            if set(raw) != {"number", "planner_status", "planner_reason", "request_id", "actions"}:
                raise ValueError("fields")
            if not isinstance(raw["actions"], list):
                raise ValueError("actions")
            return cls(
                number=int(raw["number"]),
                planner_status=AgentPlannerStatus(raw["planner_status"]),
                planner_reason=raw["planner_reason"],
                request_id=raw["request_id"],
                actions=tuple(AgentActionRecord.from_dict(item) for item in raw["actions"]),
            )
        except LainError:
            raise
        except (KeyError, TypeError, ValueError, OverflowError) as exc:
            raise _invalid("agent iteration record is invalid") from exc


@dataclass(frozen=True, slots=True)
class AgentSession:
    version: str
    session_id: str
    goal: str
    status: AgentSessionStatus
    created_at: str
    updated_at: str
    iterations: tuple[AgentIterationRecord, ...]
    total_attempted_actions: int
    budget: AgentBudget
    cumulative_runtime_seconds: float
    terminal_reason: str | None
    planner_binding: PlannerBinding = OFFLINE_DEMO_BINDING
    last_revision_turn_id: int | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.planner_binding, PlannerBinding):
            raise _invalid("planner_binding must be a PlannerBinding")
        if self.last_revision_turn_id is not None and (
            not isinstance(self.last_revision_turn_id, int)
            or isinstance(self.last_revision_turn_id, bool)
            or self.last_revision_turn_id < 1
        ):
            raise _invalid("last_revision_turn_id must be a positive integer")
        if self.version != "1":
            raise _invalid("unsupported agent session schema version", version=self.version)
        try:
            UUID(self.session_id)
        except (ValueError, TypeError) as exc:
            raise _invalid("session_id must be a UUID") from exc
        if not isinstance(self.goal, str) or not self.goal.strip():
            raise _invalid("goal must be non-empty")
        if not isinstance(self.created_at, str) or not self.created_at:
            raise _invalid("created_at must be non-empty")
        if not isinstance(self.updated_at, str) or not self.updated_at:
            raise _invalid("updated_at must be non-empty")
        if (
            not isinstance(self.total_attempted_actions, int)
            or isinstance(self.total_attempted_actions, bool)
            or self.total_attempted_actions < 0
        ):
            raise _invalid("total_attempted_actions must be a non-negative integer")
        if (
            not isinstance(self.cumulative_runtime_seconds, (int, float))
            or isinstance(self.cumulative_runtime_seconds, bool)
            or not math.isfinite(self.cumulative_runtime_seconds)
            or self.cumulative_runtime_seconds < 0
        ):
            raise _invalid("cumulative_runtime_seconds must be a finite non-negative number")
        expected_numbers = tuple(range(1, len(self.iterations) + 1))
        actual_numbers = tuple(item.number for item in self.iterations)
        if actual_numbers != expected_numbers:
            raise _invalid("iteration numbers must be contiguous and one-based")

    @property
    def iteration_count(self) -> int:
        return len(self.iterations)

    def transition(
        self,
        status: AgentSessionStatus,
        *,
        updated_at: str,
        terminal_reason: str | None = None,
    ) -> "AgentSession":
        if self.status in TERMINAL_AGENT_STATUSES or status not in _ALLOWED_TRANSITIONS.get(self.status, set()):
            raise LainError(
                ErrorCode.AGENT_STATE_INVALID,
                "invalid agent session transition",
                details={"from": self.status.value, "to": status.value},
            )
        return replace(self, status=status, updated_at=updated_at, terminal_reason=terminal_reason)

    def to_dict(self) -> dict[str, Any]:
        return {
            "version": self.version,
            "session_id": self.session_id,
            "goal": self.goal,
            "status": self.status.value,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "iteration_count": self.iteration_count,
            "iterations": [item.to_dict() for item in self.iterations],
            "total_attempted_actions": self.total_attempted_actions,
            "budget": self.budget.to_dict(),
            "cumulative_runtime_seconds": self.cumulative_runtime_seconds,
            "terminal_reason": self.terminal_reason,
            "planner_binding": self.planner_binding.to_dict(),
            "last_revision_turn_id": self.last_revision_turn_id,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "AgentSession":
        try:
            legacy_required = {
                "version",
                "session_id",
                "goal",
                "status",
                "created_at",
                "updated_at",
                "iteration_count",
                "iterations",
                "total_attempted_actions",
                "budget",
                "cumulative_runtime_seconds",
                "terminal_reason",
            }
            optional = {"planner_binding", "last_revision_turn_id"}
            fields = set(raw)
            if not legacy_required.issubset(fields) or not fields.issubset(legacy_required | optional):
                raise ValueError("fields")
            if raw["version"] != "1":
                raise ValueError("version")
            if not isinstance(raw["iterations"], list) or not isinstance(raw["budget"], dict):
                raise ValueError("nested fields")
            iterations = tuple(AgentIterationRecord.from_dict(item) for item in raw["iterations"])
            if int(raw["iteration_count"]) != len(iterations):
                raise ValueError("iteration_count")
            return cls(
                version=raw["version"],
                session_id=raw["session_id"],
                goal=raw["goal"],
                status=AgentSessionStatus(raw["status"]),
                created_at=raw["created_at"],
                updated_at=raw["updated_at"],
                iterations=iterations,
                total_attempted_actions=int(raw["total_attempted_actions"]),
                budget=AgentBudget.from_dict(raw["budget"]),
                cumulative_runtime_seconds=float(raw["cumulative_runtime_seconds"]),
                terminal_reason=raw["terminal_reason"],
                planner_binding=(
                    PlannerBinding.from_dict(raw["planner_binding"])
                    if "planner_binding" in raw
                    else OFFLINE_DEMO_BINDING
                ),
                last_revision_turn_id=raw.get("last_revision_turn_id"),
            )
        except LainError:
            raise
        except (KeyError, TypeError, ValueError, OverflowError) as exc:
            raise _invalid("agent session is invalid") from exc
