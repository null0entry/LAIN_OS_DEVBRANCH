# In Progress

## TASK-013: Implement voice barge-in and echo protection
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, safety
**Updated:** 2026-10-03

### Goal

Allow user speech to interrupt active synthesis without confusing synthesized audio or partial recognition with authoritative user intent.

### Scope

- Detect user speech while synthesis/playback is active and stop or duck playback promptly.
- Keep “Stop talking” independent from “Stop task”.
- Prevent synthesized speech from being re-ingested as a user command.
- Ensure partial/interim recognition cannot authorize consequential actions.
- Preserve final-turn authority through TASK-011 turn-manager semantics.
- Do not implement provider-specific echo-cancellation SDKs, wake words, progress narration, or Phase-3 workflow semantics.

### Dependencies

- TASK-010 complete: microphone lifecycle.
- TASK-011 complete: authoritative turn manager.
- TASK-012 complete: cancellable playback.

### Plan

- Define the smallest barge-in coordinator across microphone, turn, and playback state.
- Stop/duck playback on confirmed user speech onset without cancelling task state.
- Gate recognized input so synthesized output and partial transcripts cannot enter the authoritative turn path.
- Add deterministic regressions for echo-loop rejection, partial-recognition non-authority, and Stop-talking vs Stop-task separation.
- Measure interruption-to-playback-stop timing in controlled tests; reserve reference-device acceptance for TASK-015/R2.7.

### Acceptance

- User speech during playback stops or ducks synthesis without cancelling the underlying task.
- Synthesized speech cannot become a user turn or capability request.
- Partial recognition cannot grant approval or authorize consequential work.
- Only final accepted user turns can enter the authoritative task-facing pipeline.
- Stop talking and Stop task remain independently observable operations.
- Failure of echo/barge-in handling never fabricates task completion.

### Verification

- Focused coordinator tests for playback interruption and authority separation.
- Negative echo-loop and partial-transcript authorization tests.
- Android integration/instrumentation around simultaneous capture/playback.
- Controlled interruption latency measurement without claiming physical-device acceptance unless actually run.
- Canonical verification after implementation.

### Expected result

LAIN_OS supports safe conversational interruption while keeping audio feedback, partial speech, task cancellation, and user authority sharply separated.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.5 barge-in and echo protection immediately after speech playback.
- No current TaskPlanner task, open issue, or open PR represents R2.5.

### Projection basis

- R2.5 is required before voice progress narration and the Phase-2 acceptance gate because interruption semantics must be stable before spoken progress can coexist with user speech.

### Risks / unknowns

- Device-level acoustic echo cancellation varies by hardware; software authority filtering must remain correct even if acoustic suppression is imperfect.
- Reference-device latency belongs to the Phase-2 acceptance gate, not this task.

---


