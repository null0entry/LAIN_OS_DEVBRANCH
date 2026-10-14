from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

_SECRET_MARKERS = (
    "password",
    "passwd",
    "token",
    "secret",
    "api_key",
    "apikey",
    "authorization",
    "cookie",
    "recovery",
    "credential",
)


def _is_secret_key(key: str) -> bool:
    lowered = key.lower().replace("-", "_")
    if lowered in {"content", "body"}:
        return True
    return any(marker in lowered for marker in _SECRET_MARKERS)


def redact(value: Any) -> Any:
    if isinstance(value, dict):
        # Text entry arguments are private; inspected node text is required as
        # untrusted planner observation and does not carry a snapshot_id here.
        text_entry = {"snapshot_id", "node_id", "text"}.issubset(value)
        return {
            key: "[REDACTED]" if (isinstance(key, str) and _is_secret_key(key)) or (text_entry and key == "text") else redact(item)
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [redact(item) for item in value]
    if isinstance(value, tuple):
        return [redact(item) for item in value]
    return value


def redact_android_narratives(value: Any, source: Any) -> Any:
    """Mask echoes of known private Android payloads in free-text views.

    Keep structural IDs/types/statuses untouched. The initial user-supplied goal
    is intentionally available to its planner; once a typed private payload is
    known, historical/context/display echoes no longer need that plaintext.
    """
    contents: set[str] = set()

    def collect(item):
        if isinstance(item, dict):
            if item.get("type") in {"android.clipboard_set", "android.share_text", "android.ui.set_text"}:
                args = item.get("arguments")
                key = "text" if item.get("type") == "android.ui.set_text" else "content"
                if isinstance(args, dict) and isinstance(args.get(key), str) and args[key]:
                    contents.add(args[key])
            for child in item.values():
                collect(child)
        elif isinstance(item, (list, tuple)):
            for child in item:
                collect(child)

    collect(source)
    narratives = {"goal", "intent", "reason", "planner_reason", "terminal_reason"}
    private = sorted(contents, key=len, reverse=True)
    pattern = re.compile("|".join(re.escape(content) for content in private)) if private else None

    def mask(item, field=None):
        if isinstance(item, dict):
            return {key: mask(child, key) for key, child in item.items()}
        if isinstance(item, (list, tuple)):
            return [mask(child, field) for child in item]
        if isinstance(item, str) and field in narratives and pattern is not None:
            item = pattern.sub(lambda _: "[REDACTED]", item)
        return item

    return mask(value)


class AuditLogger:
    def __init__(self, path: Path):
        self.path = Path(path).expanduser().resolve()

    def append(self, record: dict[str, Any]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(redact(record), sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        data = (payload + "\n").encode("utf-8")
        fd = os.open(self.path, os.O_APPEND | os.O_CREAT | os.O_WRONLY, 0o600)
        try:
            os.write(fd, data)
            os.fsync(fd)
        finally:
            os.close(fd)

    def records(self):
        try:
            handle = self.path.open("r", encoding="utf-8")
        except FileNotFoundError:
            return
        with handle:
            for line in handle:
                try:
                    value = json.loads(line)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
                if isinstance(value, dict):
                    yield value

    def has_executed_request(self, request_id: str) -> bool:
        return any(
            record.get("request_id") == request_id and record.get("execution_attempted") is True
            for record in self.records()
        )

    def has_executed_action(self, request_id: str, action_id: str) -> bool:
        return any(
            record.get("request_id") == request_id
            and record.get("action_id") == action_id
            and record.get("execution_attempted") is True
            for record in self.records()
        )
