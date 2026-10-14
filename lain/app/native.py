"""Closed, trusted native adapter. Never expose Java objects to a planner."""
from __future__ import annotations

import json
import re

from lain.capabilities.registry import DEFAULT_REGISTRY
from lain.errors import ErrorCode, LainError
from lain.execution.models import ExecutionOutcome
from lain.execution.android import validate_android_uri
from lain.protocol.models import Action
from lain.verification.android import verify_android_expansion
from lain.verification.battery import validate_battery

NATIVE_CAPABILITIES = frozenset({"android.notify", "android.open_uri", "android.battery_status", "android.vibrate", "android.toast",
                                 "android.clipboard_set", "android.share_text", "android.sms.compose"})


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate native evidence field")
        result[key] = value
    return result


class NativeAndroidAdapter:
    def __init__(self, callback):
        self.callback = callback

    def execute(self, action: Action) -> ExecutionOutcome:
        if action.type not in NATIVE_CAPABILITIES:
            return ExecutionOutcome.unsupported(ErrorCode.PLATFORM_UNSUPPORTED.value,
                                                "Native capability is unavailable")
        try:
            arguments = DEFAULT_REGISTRY.get(action.type).validate_arguments(action.arguments)
            if action.type == "android.open_uri":
                validate_android_uri(arguments["uri"])
            if action.type == "android.sms.compose":
                # Only explicit numeric destinations; no model-supplied URI extras,
                # contact names, short-code guessing or implicit recipient lookup.
                recipient = re.sub(r"[ ()-]", "", arguments["recipient"])
                if not re.fullmatch(r"\+?[0-9]{3,15}", recipient):
                    raise ValueError("invalid SMS recipient")
                arguments["recipient"] = recipient
            payload = str(self.callback.execute(action.type, json.dumps(arguments, ensure_ascii=False)))
            if len(payload.encode("utf-8")) > 65536:
                raise ValueError("oversized native response")
            raw = json.loads(payload, object_pairs_hook=_unique_object)
            if not isinstance(raw, dict) or set(raw) != {"status", "details"}:
                raise ValueError("invalid native response")
            if not isinstance(raw["details"], dict):
                raise ValueError("invalid native details")
            if raw["status"] == "unsupported":
                return ExecutionOutcome.unsupported(ErrorCode.PLATFORM_UNSUPPORTED.value,
                                                    "Native operation is unavailable")
            if raw["status"] != "success":
                return ExecutionOutcome.failure(ErrorCode.EXECUTION_FAILED.value, "Native operation failed")
            if action.type == "android.battery_status":
                return ExecutionOutcome.success(battery=validate_battery(raw["details"].get("battery")))
            if action.type == "android.clipboard_set":
                readback = raw["details"].get("readback")
                if readback not in {"matched", "mismatch", "unavailable"}:
                    raise ValueError("invalid clipboard evidence")
                return ExecutionOutcome.success(readback=readback)
            return ExecutionOutcome.success()
        except LainError as exc:
            return ExecutionOutcome.failure(exc.code.value, "Native action arguments rejected")
        except Exception:
            # JNI/Java error strings can contain private data; never propagate them.
            return ExecutionOutcome.failure(ErrorCode.EXECUTION_FAILED.value, "Native operation failed")

    def verify(self, action: Action, outcome: ExecutionOutcome):
        return verify_android_expansion(action.type, outcome)
