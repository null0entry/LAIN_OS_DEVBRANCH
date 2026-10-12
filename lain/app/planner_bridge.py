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
from lain.planning.models import AgentPlannerStatus


_MAX_COMPLETION_TOKENS = 1024
# Reserve space inside the native 16 KiB prompt for the trusted chat envelope.
_MAX_ON_DEVICE_REQUEST_BYTES = 12_288
_ERROR_MAP = {
    "PLANNER_REQUEST_INVALID": ErrorCode.PLANNER_FAILED,
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
    "PLANNER_MODEL_LOAD_FAILED": ErrorCode.PLANNER_UNAVAILABLE,
    "PLANNER_OUTPUT_INVALID": ErrorCode.PLANNER_OUTPUT_INVALID,
    "PLANNER_MODEL_BUSY": ErrorCode.PLANNER_UNAVAILABLE,
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
        if binding.mode == "on_device":
            return OnDeviceBridgePlanner(binding, self.native_bridge)
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


class OnDeviceBridgePlanner:
    """Native GGUF output is *raw decision JSON*, never an OpenAI HTTP envelope."""

    def __init__(self, binding: PlannerBinding, native_bridge, *, max_actions: int = 1):
        if binding.mode != "on_device" or binding.protocol != "gguf_native_v1":
            raise LainError(ErrorCode.PLANNER_UNAVAILABLE, "not an on-device binding")
        self.binding = binding
        self.native_bridge = native_bridge
        self.max_actions = max_actions

    def decide(self, goal, context, capabilities):
        request = agent_planner_request(goal, context, capabilities, self.max_actions)
        # The untrusted goal and context are serialized as data; the native prompt
        # may repeat trusted rules, but only parse_agent_decision grants shape authority.
        body = json.dumps(request, ensure_ascii=False, separators=(",", ":"))
        if len(body.encode("utf-8")) > _MAX_ON_DEVICE_REQUEST_BYTES:
            raise LainError(ErrorCode.PLANNER_FAILED, "offline planner request is too large")
        binding_json = json.dumps(self.binding.to_dict(), separators=(",", ":"))
        try:
            response = json.loads(str(self.native_bridge.execute(binding_json, body)))
        except (ValueError, TypeError, UnicodeError) as exc:
            raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data") from exc
        if not isinstance(response, dict) or response.get("ok") not in (True, False):
            raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data")
        if response["ok"] is False:
            if set(response) != {"ok", "error"} or not isinstance(response["error"], str):
                raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data")
            if response["error"] == "PLANNER_REQUEST_INVALID":
                raise LainError(ErrorCode.PLANNER_FAILED, "offline planner request is invalid")
            code = _ERROR_MAP.get(response["error"], ErrorCode.PLANNER_UNAVAILABLE)
            raise LainError(code, "offline model generation failed")
        if set(response) != {"ok", "body"} or not isinstance(response["body"], str):
            raise LainError(ErrorCode.PLANNER_FAILED, "native planner bridge returned invalid data")
        payload = response["body"].encode("utf-8")
        # No markdown stripping, repair, permissive coercion, or cloud retry.
        decision = parse_agent_decision(
            payload, max_output_bytes=self.binding.max_response_bytes,
            max_actions=self.max_actions,
        )
        # Unlike the demo path, a local model cannot claim a completed *task*
        # without at least one trusted executed action in the session history.
        history = context.get("history") if isinstance(context, dict) else None
        if decision.status is AgentPlannerStatus.COMPLETE and isinstance(history, list):
            witnessed = any(
                isinstance(iteration, dict)
                and isinstance(iteration.get("actions"), list)
                and any(
                    isinstance(action, dict)
                    and isinstance(action.get("result"), dict)
                    and action["result"].get("status") == "success"
                    and isinstance(action["result"].get("verification"), dict)
                    and action["result"]["verification"].get("status") in {"passed", "not_applicable"}
                    for action in iteration["actions"]
                )
                for iteration in history
            )
            if not witnessed:
                raise LainError(ErrorCode.PLANNER_OUTPUT_INVALID,
                                "model cannot claim completion without a verified action")
        return decision
