from __future__ import annotations

import json
from pathlib import Path
from urllib.parse import urlsplit

from lain.agent.models import PlannerBinding
from lain.app.demo import DemoPlanner
from lain.errors import ErrorCode, LainError
from lain.planner_adapters.openai_protocol import (
    PlannerProtocolError,
    build_chat_body,
    normalize_openai_response,
)
from lain.planning.protocol import agent_planner_request, parse_agent_decision


_MAX_COMPLETION_TOKENS = 1024
_ERROR_MAP = {
    "PLANNER_CANCELLED": ErrorCode.PLANNER_CANCELLED,
    "PLANNER_TIMEOUT": ErrorCode.PLANNER_TIMEOUT,
    "PLANNER_AUTH_REJECTED": ErrorCode.AUTHENTICATION_FAILED,
    "PLANNER_CREDENTIAL_MISSING": ErrorCode.AUTHENTICATION_REQUIRED,
    "PLANNER_HTTP_REJECTED": ErrorCode.REMOTE_REJECTED,
    "PLANNER_MODEL_NOT_FOUND": ErrorCode.REMOTE_REJECTED,
    "PLANNER_RATE_LIMITED": ErrorCode.REMOTE_RATE_LIMITED,
    "PLANNER_SERVER_ERROR": ErrorCode.REMOTE_UNAVAILABLE,
    "PLANNER_DNS_UNREACHABLE": ErrorCode.REMOTE_UNAVAILABLE,
    "PLANNER_CONNECTION_REFUSED": ErrorCode.REMOTE_UNAVAILABLE,
    "PLANNER_ENDPOINT_UNREACHABLE": ErrorCode.REMOTE_UNAVAILABLE,
    "PLANNER_TLS_FAILURE": ErrorCode.REMOTE_UNAVAILABLE,
    "PLANNER_CLEARTEXT_BLOCKED": ErrorCode.REMOTE_REJECTED,
    "PLANNER_RESPONSE_TOO_LARGE": ErrorCode.PLANNER_OUTPUT_TOO_LARGE,
    "PLANNER_RESPONSE_MALFORMED": ErrorCode.PLANNER_OUTPUT_INVALID,
    "PLANNER_RESPONSE_UNSUPPORTED": ErrorCode.PLANNER_OUTPUT_INVALID,
}


_GROQ_GPT_OSS_MODELS = frozenset({"openai/gpt-oss-120b", "openai/gpt-oss-20b"})


def _use_low_reasoning(binding: PlannerBinding) -> bool:
    return (
        urlsplit(binding.base_url).hostname == "api.groq.com"
        and binding.model in _GROQ_GPT_OSS_MODELS
    )


class AndroidPlannerFactory:
    def __init__(self, workspace: Path, native_bridge):
        self.workspace = Path(workspace)
        self.native_bridge = native_bridge

    def __call__(self, binding: PlannerBinding):
        if binding.mode == "demo":
            return DemoPlanner(self.workspace)
        if self.native_bridge is None:
            raise LainError(ErrorCode.PLANNER_UNAVAILABLE, "native planner bridge is unavailable")
        return NativeBridgePlanner(binding, self.native_bridge)


class NativeBridgePlanner:
    def __init__(self, binding: PlannerBinding, native_bridge, *, max_actions: int = 1):
        if binding.mode not in {"cloud", "local"}:
            raise LainError(ErrorCode.PLANNER_UNAVAILABLE, "planner binding is unsupported")
        self.binding = binding
        self.native_bridge = native_bridge
        self.max_actions = max_actions

    def decide(self, goal, context, capabilities):
        request = agent_planner_request(goal, context, capabilities, self.max_actions)
        try:
            body_payload = json.loads(build_chat_body(
                request,
                self.binding.model,
                response_mode=self.binding.response_mode,
                max_completion_tokens=_MAX_COMPLETION_TOKENS,
            ))
            if _use_low_reasoning(self.binding):
                body_payload["reasoning_effort"] = "low"
            body = json.dumps(body_payload, ensure_ascii=False, separators=(",", ":"))
        except (PlannerProtocolError, UnicodeError) as exc:
            raise LainError(ErrorCode.PLANNER_FAILED, "planner request could not be constructed") from exc

        binding_json = json.dumps(self.binding.to_dict(), separators=(",", ":"))
        try:
            raw_result = str(self.native_bridge.execute(binding_json, body))
            result = json.loads(raw_result)
        except (ValueError, TypeError, UnicodeError) as exc:
            raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data") from exc

        if not isinstance(result, dict) or result.get("ok") not in {True, False}:
            raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data")
        if result["ok"] is False:
            if set(result) != {"ok", "error"} or not isinstance(result["error"], str):
                raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data")
            code = _ERROR_MAP.get(result["error"], ErrorCode.PLANNER_UNAVAILABLE)
            raise LainError(code, "planner transport failed")
        if set(result) != {"ok", "body"} or not isinstance(result["body"], str):
            raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data")

        upstream = result["body"].encode("utf-8")
        if len(upstream) > self.binding.max_response_bytes:
            raise LainError(ErrorCode.PLANNER_OUTPUT_TOO_LARGE, "planner output exceeds configured byte limit")
        try:
            normalized = normalize_openai_response(upstream, request)
        except PlannerProtocolError as exc:
            raise LainError(ErrorCode.PLANNER_OUTPUT_INVALID, "planner output is invalid") from exc
        encoded = json.dumps(normalized, separators=(",", ":")).encode("utf-8")
        return parse_agent_decision(
            encoded,
            max_output_bytes=self.binding.max_response_bytes,
            max_actions=self.max_actions,
        )
