# Typed Workflow Foundation: Phase 1 Implementation Plan

**Date:** 2026-10-16
**Base:** `design/one-conversation-every-turn-20261016`
**Scope:** Runnable backend slice, not a completed Android user interface or 1.0 golden workflow.
**Spec:** `docs/WORKFLOW_ENGINE.md`

## Outcome
The trusted Python layer accepts a bounded structured multi-step proposal, checks all dependency and capability contracts before effects, persists a stable execution graph, and executes one verified Android/file adapter action per checkpoint. Interrupted work must never silently repeat a committed side effect. No model text is itself approval.

## Files
- `lain/workflows/__init__.py`: expose public compile/runner contracts
- `lain/workflows/engine.py`: strict compiler, deterministic topological order, atomic checkpointed runner
- `tests/test_workflow_engine.py`: real RuntimeEngine integration and adversarial input coverage

## Task 1: Define acceptance tests (RED)
- Run tests for file.write_text -> file.copy -> file.move with explicit dependencies; verify actual files, results, and action audit.
- Assert unknown capability, invalid typed args, cycles, unknown dependency, duplicate key, malformed steps and over-budget plans reject before any execution.
- Assert checkpoint resume never repeats completed effects; poison state in-flight blocks all automatic retries.
- Assert an overwrite pauses for explicit trusted confirmation, then executes once; no user goal text bypass.

## Task 2: Implement trust boundary (GREEN)
- Compile a list of `{"id", "capability", "arguments", "depends_on"}` records, rejecting any other keys or unsafe types. IDs only name graph nodes; trusted action IDs are assigned by runtime.
- Validate every step with `RuntimeEngine.preflight` *before* saving/running. Compute stable topological order; cycles and unresolved dependencies fail.
- Persist immutable plan and checkpoint files with restrictive permissions and atomic replace; never store credentials in test fixtures.
- Persist in-flight barrier *before* executing; use `RuntimeEngine.execute_action` and its existing audit/authorization path. Only count a step completed if the runtime reports SUCCESS and independent PASSED verification. Expose truthful blocked/approval statuses.
- On process restart: completed actions are skipped, in-flight state fails closed even if only part of its work finished.

## Task 3: Verify (GREEN and regression)
- Run `python -m unittest tests.test_workflow_engine -v`; `python -m unittest discover -s tests -q`; `python -m compileall -q lain`.
- Inspect PR Verify GitHub Actions and changed files, never claim a device-tested APK.
- Record limitations: no runtime artifact binding, no hosted-model schema translator to this graph, no Android chat wiring, no cross-process locking, no automatic replay of uncertain actions.

## Follow-on sequence
1. Bind typed immutable outputs to graph inputs with trusted ref resolution and schemas.
2. Wire structured HTTPS intent plans to compiler via canonical conversation.
3. Connect Android runtime and one unified voice/text timeline, including permission/approval UI.
4. Add screen capture, crop, group resolve, direct group send *only where actual Android transport permits*, verify end-to-end on physical device.
