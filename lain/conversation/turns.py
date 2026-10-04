from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
import os
from pathlib import Path
import threading
from uuid import UUID

MAX_TURN_TEXT_BYTES = 4096
MAX_DURABLE_TURNS = 128


class TurnSource(str, Enum):
    TYPED = "typed"
    SPEECH = "speech"


class TurnKind(str, Enum):
    TASK = "task"
    REVISION = "revision"


class TurnFinality(str, Enum):
    FINAL = "final"


class TurnReference(str, Enum):
    NONE = "none"
    ACTIVE = "active"
    EXPLICIT = "explicit"
    AMBIGUOUS = "ambiguous"


class TurnRoute(str, Enum):
    START_TASK = "start_task"
    REVISION = "revision"
    CLARIFICATION_REQUIRED = "clarification_required"


def _bounded_text(value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("turn text must be a string")
    text = value.strip()
    if not text:
        raise ValueError("turn text must be non-empty")
    if len(text.encode("utf-8")) > MAX_TURN_TEXT_BYTES:
        raise ValueError("turn text exceeds byte limit")
    return text


def _canonical_uuid(value: str) -> str:
    if not isinstance(value, str) or str(UUID(value)) != value:
        raise ValueError("target session ID must be a canonical UUID")
    return value


@dataclass(frozen=True, slots=True)
class TurnRecord:
    turn_id: int
    source: TurnSource
    kind: TurnKind
    finality: TurnFinality
    text: str
    route: TurnRoute
    target_session_id: str | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.turn_id, int) or isinstance(self.turn_id, bool) or self.turn_id < 1:
            raise ValueError("turn_id must be a positive integer")
        if not isinstance(self.source, TurnSource):
            raise ValueError("turn source is invalid")
        if not isinstance(self.kind, TurnKind):
            raise ValueError("turn kind is invalid")
        if self.finality is not TurnFinality.FINAL:
            raise ValueError("accepted turn finality must be final")
        _bounded_text(self.text)
        if not isinstance(self.route, TurnRoute):
            raise ValueError("turn route is invalid")
        if self.target_session_id is not None:
            _canonical_uuid(self.target_session_id)
        if self.route is TurnRoute.REVISION and self.target_session_id is None:
            raise ValueError("revision route requires target_session_id")
        if self.route is not TurnRoute.REVISION and self.target_session_id is not None:
            raise ValueError("non-revision route cannot carry target_session_id")

    def to_dict(self) -> dict[str, object]:
        return {
            "turn_id": self.turn_id,
            "source": self.source.value,
            "kind": self.kind.value,
            "finality": self.finality.value,
            "text": self.text,
            "route": self.route.value,
            "target_session_id": self.target_session_id,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, object]) -> "TurnRecord":
        if not isinstance(raw, dict) or set(raw) != {
            "turn_id", "source", "kind", "finality", "text", "route", "target_session_id",
        }:
            raise ValueError("turn record fields are invalid")
        return cls(
            turn_id=raw["turn_id"],
            source=TurnSource(raw["source"]),
            kind=TurnKind(raw["kind"]),
            finality=TurnFinality(raw["finality"]),
            text=raw["text"],
            route=TurnRoute(raw["route"]),
            target_session_id=raw["target_session_id"],
        )


@dataclass(frozen=True, slots=True)
class ConversationSnapshot:
    next_turn_id: int
    active_task_id: str | None
    turns: tuple[TurnRecord, ...]
    partial_text: str | None = None

    def to_dict(self, *, max_turns: int | None = None) -> dict[str, object]:
        turns = self.turns if max_turns is None else self.turns[-max_turns:]
        return {
            "next_turn_id": self.next_turn_id,
            "active_task_id": self.active_task_id,
            "turns": [turn.to_dict() for turn in turns],
            "partial_text": self.partial_text,
        }


@dataclass(frozen=True, slots=True)
class TurnDecision:
    record: TurnRecord


class ConversationTurnManager:
    def __init__(self, root: Path):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, mode=0o700, exist_ok=True)
        os.chmod(self.root, 0o700)
        self.path = self.root / "state.json"
        self._lock = threading.RLock()
        self._partial_text: str | None = None
        self._next_turn_id = 1
        self._active_task_id: str | None = None
        self._turns: tuple[TurnRecord, ...] = ()
        if self.path.exists():
            self._load()

    def snapshot(self) -> ConversationSnapshot:
        with self._lock:
            return ConversationSnapshot(
                next_turn_id=self._next_turn_id,
                active_task_id=self._active_task_id,
                turns=self._turns,
                partial_text=self._partial_text,
            )

    def update_partial(self, text: str) -> ConversationSnapshot:
        with self._lock:
            self._partial_text = _bounded_text(text)
            return self.snapshot()

    def set_active_task(self, session_id: str | None) -> ConversationSnapshot:
        with self._lock:
            self._active_task_id = None if session_id is None else _canonical_uuid(session_id)
            self._save()
            return self.snapshot()

    def accept(
        self,
        text: str,
        *,
        source: TurnSource,
        kind: TurnKind,
        reference: TurnReference,
        target_session_id: str | None = None,
    ) -> TurnDecision:
        text = _bounded_text(text)
        if not isinstance(source, TurnSource):
            raise ValueError("turn source is invalid")
        if not isinstance(kind, TurnKind):
            raise ValueError("turn kind is invalid")
        if not isinstance(reference, TurnReference):
            raise ValueError("turn reference is invalid")

        with self._lock:
            resolved_target: str | None = None
            if kind is TurnKind.TASK:
                if reference is not TurnReference.NONE or target_session_id is not None:
                    raise ValueError("new task turn cannot carry a reference")
                route = TurnRoute.START_TASK
            else:
                if reference is TurnReference.AMBIGUOUS:
                    if target_session_id is not None:
                        raise ValueError("ambiguous revision cannot carry a target")
                    route = TurnRoute.CLARIFICATION_REQUIRED
                elif reference is TurnReference.ACTIVE:
                    if target_session_id is not None:
                        raise ValueError("active revision cannot carry an explicit target")
                    if self._active_task_id is None:
                        route = TurnRoute.CLARIFICATION_REQUIRED
                    else:
                        route = TurnRoute.REVISION
                        resolved_target = self._active_task_id
                elif reference is TurnReference.EXPLICIT:
                    if target_session_id is None:
                        raise ValueError("explicit revision requires target")
                    route = TurnRoute.REVISION
                    resolved_target = _canonical_uuid(target_session_id)
                else:
                    if target_session_id is not None:
                        raise ValueError("revision target requires explicit reference")
                    route = TurnRoute.CLARIFICATION_REQUIRED

            record = TurnRecord(
                turn_id=self._next_turn_id,
                source=source,
                kind=kind,
                finality=TurnFinality.FINAL,
                text=text,
                route=route,
                target_session_id=resolved_target,
            )
            self._next_turn_id += 1
            self._partial_text = None
            self._turns = (*self._turns, record)[-MAX_DURABLE_TURNS:]
            self._save()
            return TurnDecision(record)

    def _load(self) -> None:
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            if not isinstance(raw, dict) or set(raw) != {
                "version", "next_turn_id", "active_task_id", "turns",
            }:
                raise ValueError("conversation state fields are invalid")
            if raw["version"] != 1:
                raise ValueError("conversation state version is invalid")
            next_turn_id = raw["next_turn_id"]
            if (
                not isinstance(next_turn_id, int)
                or isinstance(next_turn_id, bool)
                or next_turn_id < 1
            ):
                raise ValueError("next_turn_id is invalid")
            active = raw["active_task_id"]
            if active is not None:
                active = _canonical_uuid(active)
            turns_raw = raw["turns"]
            if not isinstance(turns_raw, list) or len(turns_raw) > MAX_DURABLE_TURNS:
                raise ValueError("turn history is invalid")
            turns = tuple(TurnRecord.from_dict(item) for item in turns_raw)
            ids = tuple(item.turn_id for item in turns)
            if ids and (tuple(sorted(ids)) != ids or len(set(ids)) != len(ids)):
                raise ValueError("turn IDs are not strictly monotonic")
            if ids and next_turn_id <= ids[-1]:
                raise ValueError("next_turn_id does not follow durable history")
        except (OSError, UnicodeError, json.JSONDecodeError, TypeError, ValueError) as exc:
            raise ValueError("conversation state is invalid") from exc

        self._next_turn_id = next_turn_id
        self._active_task_id = active
        self._turns = turns
        os.chmod(self.path, 0o600)

    def _save(self) -> None:
        payload = {
            "version": 1,
            "next_turn_id": self._next_turn_id,
            "active_task_id": self._active_task_id,
            "turns": [turn.to_dict() for turn in self._turns],
        }
        encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        temp = self.root / ".state.tmp"
        try:
            with temp.open("w", encoding="utf-8") as handle:
                os.chmod(temp, 0o600)
                handle.write(encoded)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp, self.path)
            os.chmod(self.path, 0o600)
        finally:
            try:
                temp.unlink()
            except FileNotFoundError:
                pass
