"""Bounded app protocol over the existing durable, policy-controlled runtime.

Mutation/advance run on the service's one worker. Inspection and Stop may run on
the Binder thread: neither takes the worker's execution lease. Only trusted app
construction can supply the native adapter. No arbitrary callable dispatcher.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import secrets
import threading
from pathlib import Path
from time import monotonic
from uuid import UUID

from lain.agent.controller import AgentController
from lain.agent.models import (
    AgentBudget,
    AgentSessionStatus,
    OFFLINE_DEMO_BINDING,
    PlannerBinding,
    TERMINAL_AGENT_STATUSES,
)
from lain.agent.planning import AgentPlanningService
from lain.agent.store import AgentSessionStore
from lain.app.demo import COMMANDS, DemoPlanner
from lain.app.planner_bridge import AndroidPlannerFactory
from lain.app.native import NativeAndroidAdapter
from lain.audit.logger import redact, redact_android_narratives
from lain.config import RuntimeConfig
from lain.conversation import (
    ConversationTurnManager,
    TurnKind,
    TurnReference,
    TurnRoute,
    TurnSource,
)
from lain.errors import LainError
from lain.protocol.models import ActionStatus
from lain.runtime.engine import RuntimeEngine

MAX_MESSAGE_BYTES = 65536
MAX_SPEECH_TEXT_BYTES = 32768
APPROVAL_TTL_SECONDS = 120
TURN_RESPONSE_WINDOW = 3
_OPAQUE_CREDENTIAL_REF = re.compile(r"^cred_[0-9a-f]{32}$")
_FIELDS = {
    "start": {"goal"},
    "inspect": {"session_id"},
    "sessions": set(),
    "approve": {"session_id", "token"},
    "stop": {"session_id"},
    "resume": {"session_id"},
    "turn_partial": {"text"},
    "turn_submit": {"text", "source", "kind", "reference", "target_session_id"},
    "turns": set(),
}
_SESSION_COMMANDS = {"inspect", "approve", "stop", "resume"}


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def _session_id(value):
    if not isinstance(value, str) or str(UUID(value)) != value:
        raise ValueError("invalid session ID")
    return value


def _revision(session):
    encoded = json.dumps(session.to_dict(), sort_keys=True, ensure_ascii=False,
                         separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _planner_binding_provider(native_profiles):
    if native_profiles is None:
        return lambda: OFFLINE_DEMO_BINDING

    def current_binding():
        payload = str(native_profiles.activeBindingJson())
        if len(payload.encode("utf-8")) > 8192:
            raise ValueError("planner binding response too large")
        raw = json.loads(
            payload,
            object_pairs_hook=_unique_object,
            parse_constant=lambda _: (_ for _ in ()).throw(ValueError("nonfinite JSON")),
        )
        if not isinstance(raw, dict):
            raise ValueError("planner binding must be an object")
        credential_ref = raw.get("credential_ref")
        if credential_ref is not None and (
            not isinstance(credential_ref, str)
            or _OPAQUE_CREDENTIAL_REF.fullmatch(credential_ref) is None
        ):
            raise ValueError("planner credential reference must be opaque")
        return PlannerBinding.from_dict(raw)

    return current_binding


class AppController:
    def __init__(self, root: Path, native=None, planner_profiles=None, planner_bridge=None):
        root = Path(root).resolve()
        root.mkdir(parents=True, mode=0o700, exist_ok=True)
        os.chmod(root, 0o700)
        workspace = root / "workspace"
        workspace.mkdir(mode=0o700, exist_ok=True)
        config = RuntimeConfig.for_workspace(workspace)
        self.store = AgentSessionStore(root / "sessions")
        self.turns = ConversationTurnManager(root / "conversation")
        self.runtime = RuntimeEngine(config, native_android=NativeAndroidAdapter(native) if native else None)
        self._planner_bridge = planner_bridge
        self.controller = AgentController(
            AgentPlanningService(DemoPlanner(workspace), max_actions=1), self.runtime,
            self.store, AgentBudget(12, 1, 32, 900.0),
            planner_binding_provider=_planner_binding_provider(planner_profiles),
            planner_factory=AndroidPlannerFactory(workspace, planner_bridge),
        )
        self._lock = threading.RLock()
        self._active = None
        self._stopped = threading.Event()
        self._grants = {}
        self._confirmed = frozenset()
        self._advancing = False

    def dispatch(self, payload: str) -> str:
        try:
            if not isinstance(payload, str) or len(payload.encode("utf-8")) > MAX_MESSAGE_BYTES:
                raise ValueError("invalid message size")
            message = json.loads(payload, object_pairs_hook=_unique_object,
                                 parse_constant=lambda _: (_ for _ in ()).throw(ValueError("nonfinite JSON")))
            if not isinstance(message, dict) or set(message) != {"version", "command", "arguments"}:
                raise ValueError("invalid request fields")
            if type(message["version"]) is not int or message["version"] != 1:
                raise ValueError("unsupported protocol version")
            command, arguments = message["command"], message["arguments"]
            if not isinstance(command, str) or command not in _FIELDS:
                raise ValueError("unknown command")
            if not isinstance(arguments, dict) or set(arguments) != _FIELDS[command]:
                raise ValueError("invalid command arguments")
            if command in _SESSION_COMMANDS:
                _session_id(arguments["session_id"])
            if command == "start":
                result = self._start(arguments["goal"])
            elif command == "inspect":
                result = {"session": self._snapshot(self.store.load(arguments["session_id"]))}
            elif command == "sessions":
                sessions = self.store.list_sessions()
                result = {"sessions": [self._summary(s) for s in sessions[:20]],
                          "demo_commands": list(COMMANDS), "planner": "offline_demo"}
            elif command == "turn_partial":
                result = {"conversation": self.turns.update_partial(arguments["text"]).to_dict(max_turns=TURN_RESPONSE_WINDOW)}
            elif command == "turn_submit":
                result = self._submit_turn(
                    arguments["text"],
                    source=arguments["source"],
                    kind=arguments["kind"],
                    reference=arguments["reference"],
                    target_session_id=arguments["target_session_id"],
                )
            elif command == "turns":
                result = {"conversation": self.turns.snapshot().to_dict(max_turns=TURN_RESPONSE_WINDOW)}
            elif command == "approve":
                result = self._approve(arguments["session_id"], arguments["token"])
            elif command == "resume":
                result = self._resume(arguments["session_id"])
            else:
                result = self._stop(arguments["session_id"])
            response = {"version": 1, "ok": True, **result}
        except LainError as exc:
            response = {"version": 1, "ok": False, "error": exc.code.value,
                        "message": "Runtime request could not be completed."}
        except (ValueError, TypeError, UnicodeError, RecursionError):
            response = {"version": 1, "ok": False, "error": "APP_REQUEST_INVALID",
                        "message": "Invalid or stale request."}
        except Exception:
            response = {"version": 1, "ok": False, "error": "APP_UNAVAILABLE",
                        "message": "Runtime unavailable; inspect durable state before retrying."}
        encoded = json.dumps(response, ensure_ascii=True, separators=(",", ":"))
        if len(encoded.encode("utf-8")) > MAX_MESSAGE_BYTES:
            return '{"version":1,"ok":false,"error":"APP_RESPONSE_TOO_LARGE"}'
        return encoded

    def _unfinished(self):
        return [s for s in self.store.list_sessions() if s.status not in TERMINAL_AGENT_STATUSES]

    def _start(self, goal):
        return self._submit_turn(
            goal,
            source=TurnSource.TYPED.value,
            kind=TurnKind.TASK.value,
            reference=TurnReference.NONE.value,
            target_session_id=None,
            legacy_start=True,
        )

    def _start_session(self, goal):
        if not isinstance(goal, str) or not goal.strip() or len(goal.encode("utf-8")) > 4096:
            raise ValueError("invalid goal")
        with self._lock:
            if self._active or self._unfinished():
                raise ValueError("another session needs attention")
            session = self.controller.create(goal.strip())
            self._active = session.session_id
            self._stopped.clear()
            self._confirmed = frozenset()
            self._grants.clear()
            self.turns.set_active_task(session.session_id)
        return session

    def _submit_turn(
        self,
        text,
        *,
        source,
        kind,
        reference,
        target_session_id,
        legacy_start=False,
    ):
        source_value = TurnSource(source)
        kind_value = TurnKind(kind)
        reference_value = TurnReference(reference)

        if kind_value is TurnKind.TASK:
            if self._active or self._unfinished():
                raise ValueError("another session needs attention")
        elif reference_value is TurnReference.EXPLICIT:
            target_session_id = _session_id(target_session_id)
            self.store.load(target_session_id)

        decision = self.turns.accept(
            text,
            source=source_value,
            kind=kind_value,
            reference=reference_value,
            target_session_id=target_session_id,
        )
        record = decision.record

        if record.route is TurnRoute.START_TASK:
            session = self._start_session(record.text)
            result = {"session": self._snapshot(session)}
            if not legacy_start:
                result["turn"] = record.to_dict()
            return result
        if record.route is TurnRoute.REVISION:
            return {
                "turn": record.to_dict(),
                "revision_intent": {
                    "turn_id": record.turn_id,
                    "target_session_id": record.target_session_id,
                },
            }
        return {
            "turn": record.to_dict(),
            "clarification_required": True,
        }

    def _resume(self, sid):
        with self._lock:
            session = self.store.load(sid)
            if self._active or session.status in TERMINAL_AGENT_STATUSES:
                raise ValueError("session cannot resume")
            if session.status is AgentSessionStatus.PAUSED_CONFIRMATION:
                raise ValueError("fresh exact approval required")
            unfinished = self._unfinished()
            if len(unfinished) != 1 or unfinished[0].session_id != sid:
                raise ValueError("multiple unfinished sessions require owner attention")
            self._active = sid
            self._stopped.clear()
            self._confirmed = frozenset()
            self._grants.clear()
        return {"session": self._snapshot(session)}

    def _stop(self, sid):
        with self._lock:
            session = self.store.load(sid)
            if session.status in TERMINAL_AGENT_STATUSES:
                return {"session": self._snapshot(session), "stop_requested": False}
            if self._active not in {None, sid}:
                raise ValueError("another session is active")
            self._active = sid
            self._stopped.set()
            self._grants.clear()
            self._confirmed = frozenset()
        self._cancel_planner()
        return {"stop_requested": True, "session_id": sid,
                "message": "Stop requested. An in-flight action may still settle."}

    def _pending(self, session):
        if session.status is not AgentSessionStatus.PAUSED_CONFIRMATION or not session.iterations:
            return None
        for record in session.iterations[-1].actions:
            if record.result and record.result.status is ActionStatus.CONFIRMATION_REQUIRED:
                return record.action
        return None

    def _approve(self, sid, token):
        if not isinstance(token, str) or not 1 <= len(token) <= 128:
            raise ValueError("invalid approval")
        with self._lock:
            session = self.store.load(sid)
            pending = self._pending(session)
            grant = self._grants.get(sid)
            if (not pending or not grant or self._advancing or self._stopped.is_set()
                    or self._active not in {None, sid}):
                raise ValueError("stale approval")
            expected, revision, action_id, expiry = grant
            if (not secrets.compare_digest(token, expected) or monotonic() >= expiry
                    or revision != _revision(session) or pending.id != action_id):
                raise ValueError("stale approval")
            self._grants.pop(sid)
            self._confirmed = frozenset({action_id})
            self._active = sid
        return {"approval_accepted": True, "session_id": sid}

    def advance(self) -> bool:
        with self._lock:
            sid = self._active
            if sid is None or self._advancing:
                return False
            self._advancing = True
            confirmed = self._confirmed
            self._confirmed = frozenset()
        try:
            session = self.store.load(sid)
            if session.status in TERMINAL_AGENT_STATUSES:
                self._release(sid)
                return False
            if self._stopped.is_set():
                self.controller.cancel(sid)
                self._release(sid)
                return False
            if session.status is AgentSessionStatus.PAUSED_CONFIRMATION and not confirmed:
                return False
            session = self.controller.step(sid, confirmed_action_ids=confirmed)
            if self._stopped.is_set() and session.status not in TERMINAL_AGENT_STATUSES:
                session = self.controller.cancel(sid)
            if session.status in TERMINAL_AGENT_STATUSES:
                self._release(sid)
                return False
            return session.status is not AgentSessionStatus.PAUSED_CONFIRMATION
        finally:
            with self._lock:
                self._advancing = False

    def _release(self, sid):
        with self._lock:
            if self._active == sid:
                self._active = None
                self._grants.clear()
                self._confirmed = frozenset()
                self._stopped.clear()

    def _cancel_planner(self):
        if self._planner_bridge is not None:
            try:
                self._planner_bridge.cancel()
            except Exception:
                pass

    def stop_active(self):
        """Private lifecycle hook; not a Binder-dispatchable planner command."""
        with self._lock:
            if self._active is not None:
                self._stopped.set()
                self._grants.clear()
                self._confirmed = frozenset()
        self._cancel_planner()

    def _summary(self, session):
        # Do not persist/display arbitrary goal text in the app's history surface.
        selected = next((c for c in COMMANDS if c.lower() == session.goal.lower()), "Custom task")
        return {"session_id": session.session_id, "label": selected,
                "status": session.status.value, "updated_at": session.updated_at}

    def _snapshot(self, session):
        raw = session.to_dict()
        safe = redact_android_narratives(redact(raw), raw)
        actions = []
        for iteration in safe["iterations"]:
            for record in iteration["actions"]:
                result = record.get("result")
                actions.append({"id": record["action"]["id"], "type": record["action"]["type"],
                                "status": result["status"] if result else "pending",
                                "verification": result["verification"]["status"] if result else "not_applicable",
                                "details": result["details"] if result else {}})
        speech_text = None
        if session.status in {AgentSessionStatus.COMPLETE, AgentSessionStatus.BLOCKED} and safe["iterations"]:
            candidate = safe["iterations"][-1]["planner_reason"].strip()
            if candidate and len(candidate.encode("utf-8")) <= MAX_SPEECH_TEXT_BYTES:
                speech_text = candidate
        snapshot = {**self._summary(session), "revision": _revision(session),
                    "actions": actions[-32:], "attempted_actions": session.total_attempted_actions,
                    "iterations": session.iteration_count, "speech_text": speech_text, "planner": {
                        "profile_id": session.planner_binding.profile_id,
                        "mode": session.planner_binding.mode,
                        "model": session.planner_binding.model,
                    }}
        with self._lock:
            snapshot["active"] = self._active == session.session_id
            snapshot["stop_requested"] = snapshot["active"] and self._stopped.is_set()
            snapshot["recovery_required"] = (not snapshot["active"]
                                               and session.status not in TERMINAL_AGENT_STATUSES)
            pending = self._pending(session)
            if pending and not self._stopped.is_set() and not self._advancing and not self._confirmed:
                grant = self._grants.get(session.session_id)
                revision = _revision(session)
                if not grant or grant[1] != revision or monotonic() >= grant[3]:
                    grant = (secrets.token_urlsafe(32), revision, pending.id,
                             monotonic() + APPROVAL_TTL_SECONDS)
                    self._grants[session.session_id] = grant
                snapshot["approval"] = {"token": grant[0], "action_id": pending.id,
                                        "type": pending.type, "arguments": redact(pending.arguments),
                                        "expires_in_seconds": max(0, int(grant[3] - monotonic()))}
        return snapshot


def create_controller(root: str, native, planner_profiles=None, planner_bridge=None) -> AppController:
    return AppController(Path(root), native, planner_profiles, planner_bridge)
