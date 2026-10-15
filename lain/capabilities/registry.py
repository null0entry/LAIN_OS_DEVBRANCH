from __future__ import annotations

from dataclasses import dataclass
from enum import IntEnum
import re
from typing import Any

from lain.errors import ErrorCode, LainError


class RiskClass(IntEnum):
    READ_ONLY = 0
    REVERSIBLE_LOCAL_WRITE = 1
    EXTERNAL_WRITE = 2
    DESTRUCTIVE = 3


_MISSING = object()


@dataclass(frozen=True, slots=True)
class ArgumentSpec:
    types: tuple[type, ...]
    required: bool = True
    default: Any = _MISSING
    minimum: int | None = None
    maximum: int | None = None
    max_bytes: int | None = None
    choices: tuple[str, ...] | None = None
    pattern: str | None = None
    allow_empty: bool = False

    def limits(self) -> dict[str, Any]:
        result = {name: value for name, value in (
            ("minimum", self.minimum), ("maximum", self.maximum), ("max_bytes", self.max_bytes),
        ) if value is not None}
        if self.choices is not None:
            result["choices"] = list(self.choices)
        if self.pattern is not None:
            result["pattern"] = self.pattern
        if self.allow_empty:
            result["allow_empty"] = True
        return result


@dataclass(frozen=True, slots=True)
class CapabilityDefinition:
    name: str
    arguments: dict[str, ArgumentSpec]
    risk_class: RiskClass
    required_permissions: tuple[str, ...]
    reversible: bool
    confirmation_required: bool
    network_required: bool
    environments: tuple[str, ...]
    verification: str

    def validate_arguments(self, arguments: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(arguments, dict):
            raise LainError(ErrorCode.ARGUMENT_INVALID, "arguments must be an object")
        unknown = set(arguments) - set(self.arguments)
        if unknown:
            raise LainError(
                ErrorCode.ARGUMENT_INVALID,
                "unknown arguments",
                details={"arguments": sorted(unknown)},
            )
        validated: dict[str, Any] = {}
        missing: list[str] = []
        for name, spec in self.arguments.items():
            if name not in arguments:
                if spec.required:
                    missing.append(name)
                elif spec.default is not _MISSING:
                    validated[name] = spec.default
                continue
            value = arguments[name]
            if not isinstance(value, spec.types) or (isinstance(value, bool) and bool not in spec.types):
                raise LainError(
                    ErrorCode.ARGUMENT_INVALID,
                    f"argument '{name}' has invalid type",
                    details={"argument": name},
                )
            if spec.choices is not None and value not in spec.choices:
                raise LainError(ErrorCode.ARGUMENT_INVALID, f"argument '{name}' has invalid choice")
            if spec.pattern is not None and re.fullmatch(spec.pattern, value) is None:
                raise LainError(ErrorCode.ARGUMENT_INVALID, f"argument '{name}' has invalid identity")
            if spec.minimum is not None and value < spec.minimum:
                raise LainError(ErrorCode.ARGUMENT_INVALID, f"argument '{name}' is below its minimum")
            if spec.maximum is not None and value > spec.maximum:
                raise LainError(ErrorCode.ARGUMENT_INVALID, f"argument '{name}' exceeds its maximum")
            if spec.max_bytes is not None:
                try:
                    size = len(value.encode("utf-8"))
                except UnicodeError as exc:
                    raise LainError(ErrorCode.ARGUMENT_INVALID, f"argument '{name}' must be valid UTF-8") from exc
                if (not size and not spec.allow_empty) or size > spec.max_bytes or "\x00" in value:
                    raise LainError(ErrorCode.ARGUMENT_INVALID, f"argument '{name}' is empty, contains NUL, or exceeds its byte limit")
            validated[name] = value
        if missing:
            raise LainError(
                ErrorCode.ARGUMENT_INVALID,
                "missing required arguments",
                details={"arguments": sorted(missing)},
            )
        return validated


class CapabilityRegistry:
    def __init__(self, capabilities: tuple[CapabilityDefinition, ...]):
        self._capabilities = {cap.name: cap for cap in capabilities}
        if len(self._capabilities) != len(capabilities):
            raise ValueError("duplicate capability names")

    def get(self, name: str) -> CapabilityDefinition:
        try:
            return self._capabilities[name]
        except KeyError as exc:
            raise LainError(
                ErrorCode.CAPABILITY_UNKNOWN,
                "unknown capability",
                details={"capability": name},
            ) from exc

    def names(self) -> tuple[str, ...]:
        return tuple(sorted(self._capabilities))

    def definitions(self) -> tuple[CapabilityDefinition, ...]:
        return tuple(self._capabilities[name] for name in self.names())


_STR = (str,)
_BOOL = (bool,)

VOLUME_STREAMS = ("media", "ring", "alarm", "notification", "system", "voice_call")
SETTINGS_PAGES = ("wifi", "bluetooth", "display", "sound", "apps", "app_permissions",
                  "notifications", "accessibility", "write_settings", "dnd")
PACKAGE_PATTERN = r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+"
IDENTITY_PATTERN = r"[A-Za-z0-9_-]{1,128}"


def _device_capabilities() -> tuple[CapabilityDefinition, ...]:
    stream = ArgumentSpec(_STR, required=False, default="media", choices=VOLUME_STREAMS)
    percentage = ArgumentSpec((int,), minimum=0, maximum=100)
    identity = ArgumentSpec(_STR, max_bytes=128, pattern=IDENTITY_PATTERN)
    target = {"snapshot_id": identity, "node_id": identity}
    # Arguments, permission, risk. All generic UI mutations require exact-action
    # approval, since visible controls may have external or destructive effects.
    definitions = (
        ("flashlight_set", {"enabled": ArgumentSpec(_BOOL)}, "camera", 1),
        ("volume_get", {"stream": stream}, "audio", 0),
        ("volume_set", {"percentage": percentage, "stream": stream}, "audio", 1),
        ("volume_adjust", {"direction": ArgumentSpec(_STR, choices=("up", "down", "mute", "unmute")), "stream": stream}, "audio", 1),
        ("brightness_set", {"percentage": percentage}, "write_settings", 1),
        ("rotation_set", {"enabled": ArgumentSpec(_BOOL)}, "write_settings", 1),
        ("screen_timeout_set", {"milliseconds": ArgumentSpec((int,), minimum=15000, maximum=1800000)}, "write_settings", 1),
        ("settings_open", {"page": ArgumentSpec(_STR, choices=SETTINGS_PAGES)}, "intent", 1),
        ("app_open", {"package": ArgumentSpec(_STR, max_bytes=255, pattern=PACKAGE_PATTERN)}, "launcher", 1),
        ("app_list", {"query": ArgumentSpec(_STR, required=False, default="", max_bytes=256, allow_empty=True)}, "launcher", 0),
        ("ui.inspect", {}, "accessibility", 0),
        ("ui.click", target, "accessibility", 2),
        ("ui.scroll", {**target, "direction": ArgumentSpec(_STR, choices=("forward", "backward"))}, "accessibility", 2),
        ("ui.set_text", {**target, "text": ArgumentSpec(_STR, max_bytes=4096, allow_empty=True)}, "accessibility", 2),
        ("ui.global_action", {"action": ArgumentSpec(_STR, choices=("home", "back", "recents", "notifications", "quick_settings"))}, "accessibility", 2),
    )
    return tuple(CapabilityDefinition(
        name="android." + name, arguments=args, risk_class=RiskClass(risk),
        required_permissions=("android." + permission,), reversible=False,
        confirmation_required=risk == 2, network_required=False,
        environments=("android",), verification="native_device_evidence",
    ) for name, args, permission, risk in definitions)


DEVICE_CAPABILITIES = frozenset(cap.name for cap in _device_capabilities())


DEFAULT_REGISTRY = CapabilityRegistry(
    (
        CapabilityDefinition(
            name="file.write_text",
            arguments={
                "path": ArgumentSpec(_STR),
                "content": ArgumentSpec(_STR),
                "overwrite": ArgumentSpec(_BOOL, required=False, default=False),
            },
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("filesystem.write",),
            reversible=True,
            confirmation_required=False,
            network_required=False,
            environments=("linux", "termux"),
            verification="content_sha256",
        ),
        CapabilityDefinition(
            name="file.copy",
            arguments={
                "source": ArgumentSpec(_STR),
                "destination": ArgumentSpec(_STR),
                "overwrite": ArgumentSpec(_BOOL, required=False, default=False),
            },
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("filesystem.read", "filesystem.write"),
            reversible=True,
            confirmation_required=False,
            network_required=False,
            environments=("linux", "termux"),
            verification="destination_sha256",
        ),
        CapabilityDefinition(
            name="file.move",
            arguments={
                "source": ArgumentSpec(_STR),
                "destination": ArgumentSpec(_STR),
                "overwrite": ArgumentSpec(_BOOL, required=False, default=False),
            },
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("filesystem.read", "filesystem.write"),
            reversible=True,
            confirmation_required=False,
            network_required=False,
            environments=("linux", "termux"),
            verification="source_absent_destination_sha256",
        ),
        CapabilityDefinition(
            name="android.notify",
            arguments={
                "title": ArgumentSpec(_STR),
                "content": ArgumentSpec(_STR),
            },
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("android.notification",),
            reversible=False,
            confirmation_required=False,
            network_required=False,
            environments=("termux", "android"),
            verification="command_acceptance",
        ),
        CapabilityDefinition(
            name="android.open_uri",
            arguments={"uri": ArgumentSpec(_STR)},
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("android.intent",),
            reversible=False,
            confirmation_required=False,
            network_required=False,
            environments=("termux", "android"),
            verification="intent_acceptance",
        ),
        CapabilityDefinition(
            name="android.battery_status",
            arguments={},
            risk_class=RiskClass.READ_ONLY,
            required_permissions=("android.battery",),
            reversible=False,
            confirmation_required=False,
            network_required=False,
            environments=("termux", "android"),
            verification="structured_battery_result",
        ),
        CapabilityDefinition(
            name="android.vibrate",
            arguments={"duration_ms": ArgumentSpec((int,), minimum=1, maximum=5000)},
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("android.vibration",),
            reversible=False,
            confirmation_required=False,
            network_required=False,
            environments=("termux", "android"),
            verification="command_acceptance",
        ),
        CapabilityDefinition(
            name="android.toast",
            arguments={"content": ArgumentSpec(_STR, max_bytes=1024)},
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("android.toast",),
            reversible=False,
            confirmation_required=False,
            network_required=False,
            environments=("termux", "android"),
            verification="command_acceptance",
        ),
        CapabilityDefinition(
            name="android.clipboard_set",
            arguments={"content": ArgumentSpec(_STR, max_bytes=16384)},
            risk_class=RiskClass.REVERSIBLE_LOCAL_WRITE,
            required_permissions=("android.clipboard.write",),
            reversible=False,
            confirmation_required=False,
            network_required=False,
            environments=("termux", "android"),
            verification="private_immediate_readback",
        ),
        CapabilityDefinition(
            name="android.share_text",
            arguments={"content": ArgumentSpec(_STR, max_bytes=16384)},
            # Chooser exposes content to an external action surface; policy still
            # requires confirmation even though this adapter never selects a receiver.
            risk_class=RiskClass.EXTERNAL_WRITE,
            required_permissions=("android.share_chooser",),
            reversible=False,
            confirmation_required=True,
            network_required=False,
            environments=("termux", "android"),
            verification="chooser_command_acceptance",
        ),
        CapabilityDefinition(
            name="android.sms.compose",
            arguments={
                "recipient": ArgumentSpec(_STR, max_bytes=32),
                "content": ArgumentSpec(_STR, max_bytes=4096),
            },
            risk_class=RiskClass.EXTERNAL_WRITE,
            required_permissions=("android.sms_composer",),
            reversible=False,
            confirmation_required=True,
            network_required=False,
            environments=("android",),
            verification="sms_composer_handoff_only",
        ),
        CapabilityDefinition(
            name="reddit.create_post",
            arguments={
                "subreddit": ArgumentSpec(_STR),
                "title": ArgumentSpec(_STR),
                "body": ArgumentSpec(_STR),
            },
            risk_class=RiskClass.EXTERNAL_WRITE,
            required_permissions=("reddit.submit",),
            reversible=False,
            confirmation_required=True,
            network_required=True,
            environments=("linux", "termux"),
            verification="reddit_post_lookup",
        ),
    ) + _device_capabilities()
)
