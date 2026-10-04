# In Progress

## TASK-012: Implement cancellable speech playback
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, android-audio
**Updated:** 2026-10-04

### Goal

Add bounded Android speech playback for synthesized responses with explicit audio-focus ownership, truthful playback state, and cancellation independent from underlying task cancellation.

### Scope

- Consume synthesis results from the TASK-009 provider-neutral speech interface.
- Implement start/stop playback with surfaced playback state.
- Acquire/release Android audio focus deterministically and handle focus loss/ducking safely.
- Provide explicit TTS/playback cancellation that does not cancel the underlying LAIN task.
- Keep synthesized output non-authoritative: playback cannot create user turns, approvals, or capability requests.
- Preserve typed/visual result delivery when playback fails.
- Do not implement microphone capture, barge-in/echo protection, provider-specific TTS SDKs, or workflow progress narration in this task.

### Dependencies

- TASK-009 complete: bounded speech provider interfaces.
- TASK-011 complete: turn manager distinguishes conversational turns from task state.
- Existing Android lifecycle/rebind patterns remain authoritative.

### Plan

- Define the smallest playback controller/state model around synthesized audio artifacts/streams.
- Add deterministic audio-focus acquisition, loss, duck, stop, and teardown paths.
- Keep “Stop talking” separate from “Stop task” in API/state semantics.
- Surface playback state to the Android UI boundary.
- Add focused JVM/instrumentation tests for start/stop/focus-loss/cancellation/lifecycle teardown.

### Acceptance

- Playback starts only from an explicit synthesized-response request.
- Audio focus is released on completion, cancellation, focus loss, and lifecycle teardown.
- “Stop talking” stops playback without cancelling or falsifying the underlying task.
- Playback failure leaves durable task state unchanged and preserves typed/visual output.
- Synthesized audio cannot be routed as a user command through this layer.
- Playback state is truthful and visible to the UI.
- No raw speech-provider credential enters playback state or artifacts.

### Verification

- Focused playback-state/audio-focus tests.
- Android instrumentation for start/stop, focus loss/ducking, cancellation, rotation/rebind, and teardown.
- Negative test proving playback cancellation does not alter task status.
- Negative inspection proving synthesized output cannot directly enter the authoritative turn pipeline.
- Canonical Android build/lint/instrumentation gates after implementation.

### Expected result

LAIN_OS can speak responses through a cancellable Android playback boundary while keeping audio lifecycle, user-turn authority, and task cancellation cleanly separated.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.4 Speech playback: audio focus, start/stop/duck, TTS cancellation, and playback state surfaced to UI.
- No current TaskPlanner task, open issue, or open PR represents R2.4.

### Projection basis

- A stable playback boundary is required before R2.5 barge-in/echo protection can safely interrupt speech without conflating “stop talking” with “stop task.”
- Separating playback state from task state prevents speech-provider or audio failures from becoming false task failures.

### Risks / unknowns

- Exact Android playback primitive and streaming buffer strategy may depend on the synthesis adapter contract; keep the controller bounded and provider-neutral.
- Reference-device latency belongs to R2.7 acceptance, not this task.
- Echo suppression and synthesized-speech command filtering belong to R2.5 and must not be pre-built here.

---

