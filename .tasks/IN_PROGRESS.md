# In Progress

## TASK-010: Implement Android microphone lifecycle
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, android-audio
**Updated:** 2026-10-04

### Goal

Add the bounded Android microphone lifecycle required for user-started voice sessions, with explicit permission, visible recording state, cancellation, and rotation/background safety while preserving typed input as a complete fallback.

### Scope

- Implement microphone permission request/revocation handling for explicit user-started capture only.
- Add start/stop capture lifecycle with visible recording state.
- Define bounded audio capture ownership and cleanup across activity/service lifecycle transitions.
- Handle rotation, rebind, foreground/background transitions, and permission revocation without leaving hidden capture active.
- Produce bounded audio input suitable for the TASK-009 transcription interface without embedding provider-specific logic.
- Default to no raw audio retention beyond the active bounded request unless an explicit later feature requires durable audio.
- Do not implement speech provider SDKs, turn management, playback, barge-in, or always-listening/wake-word behavior.

### Dependencies

- TASK-009 complete: bounded speech provider interfaces.
- Existing Android runtime/service lifecycle and Stop/rebind patterns.
- Existing permission and UI-state conventions where reusable.

### Plan

- Inventory current Activity/RuntimeService lifecycle and permission patterns.
- Add the smallest microphone capture controller/state model with explicit start/stop ownership.
- Route capture output only through the provider-neutral transcription seam.
- Make rotation/rebind/background/revocation transitions fail closed and release microphone resources deterministically.
- Add focused JVM/instrumentation coverage for permission, lifecycle, and no-hidden-capture invariants.

### Acceptance

- Recording starts only after an explicit user action and granted microphone permission.
- Recording state is visibly surfaced while capture is active.
- Stop releases microphone resources and prevents further audio delivery after cancellation settles.
- Rotation/rebind preserves truthful visible state or terminates capture cleanly according to the chosen lifecycle contract.
- Permission revocation terminates capture and surfaces a recoverable non-success state.
- Background transitions cannot create a hidden always-listening state.
- Captured audio is bounded and not durably retained by default.
- Typed interaction remains fully usable when microphone permission is denied or capture fails.

### Verification

- Focused microphone controller/state tests.
- Android instrumentation for permission denied/granted/revoked, start/stop, rotation/rebind, and background transitions.
- Resource-release assertions after cancellation and lifecycle teardown.
- Negative inspection proving no default raw-audio persistence.
- Canonical Android build/lint/instrumentation gates after implementation.

### Expected result

LAIN_OS can explicitly capture bounded user speech on Android and hand it to the provider-neutral transcription layer without hidden listening, lifecycle leaks, or coupling microphone state to provider or capability authority.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.2 Android microphone lifecycle immediately after R2.1 speech provider interfaces.
- The Phase-2 feature list requires user-started microphone sessions, visible recording state, permission/revocation handling, background/rotation behavior, typed fallback, and no raw audio retention by default.
- No current TaskPlanner task, open issue, or open PR represents R2.2.

### Projection basis

- Stabilizing microphone ownership and lifecycle before turn management and playback prevents later voice features from inheriting hidden-capture, permission, or rotation defects.
- R2.2 is a direct dependency for R2.3 turn management and the Phase-2 installed voice-session exit gate.

### Risks / unknowns

- Exact Android audio API choice may depend on latency and device support; prefer the smallest platform primitive that satisfies lifecycle/cancellation requirements.
- Background behavior may require a deliberate foreground-service policy; do not broaden scope unless existing Android constraints make it necessary.
- Physical-device latency and OEM microphone behavior remain separate acceptance evidence from emulator instrumentation.

---
