from __future__ import annotations

from lain.execution.models import ExecutionOutcome
from lain.protocol.models import ActionStatus, VerificationResult, VerificationStatus
from lain.verification.battery import validate_battery
from lain.capabilities.registry import DEVICE_CAPABILITIES
from lain.verification.device import verify_device_evidence


def verify_android_command(outcome: ExecutionOutcome) -> VerificationResult:
    if outcome.status is ActionStatus.SUCCESS:
        return VerificationResult(
            VerificationStatus.LIMITED,
            {"reason": "Android accepted the adapter command; downstream UI state is not observable"},
        )
    return VerificationResult(
        VerificationStatus.NOT_APPLICABLE,
        {"reason": "Android command did not execute successfully"},
    )


def verify_android_expansion(name: str, outcome: ExecutionOutcome, arguments: dict | None = None) -> VerificationResult:
    if outcome.status is not ActionStatus.SUCCESS:
        return verify_android_command(outcome)
    if name in DEVICE_CAPABILITIES:
        return verify_device_evidence(name, arguments or {}, outcome.details)
    if name == "android.battery_status":
        try:
            validate_battery(outcome.details.get("battery"))
        except (ValueError, TypeError, OverflowError):
            return VerificationResult(VerificationStatus.FAILED, {"reason": "invalid battery evidence"})
        return VerificationResult(VerificationStatus.PASSED, {"reason": "valid structured battery reading obtained"})
    if name == "android.clipboard_set":
        readback = outcome.details.get("readback")
        if readback == "matched":
            return VerificationResult(VerificationStatus.PASSED, {"reason": "private immediate readback matched the written value"})
        if readback == "mismatch":
            return VerificationResult(VerificationStatus.FAILED, {"reason": "private immediate readback did not match"})
        return VerificationResult(VerificationStatus.LIMITED, {"reason": "write command accepted; private readback unavailable"})
    return verify_android_command(outcome)
