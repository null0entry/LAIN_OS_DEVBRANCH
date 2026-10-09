# In Progress

## TASK-071: Harden offline GGUF cancellation and profile-save responsiveness
**Priority:** P1 | **Tags:** android, offline, gguf, lifecycle
**Updated:** 2026-10-09

### Goal
Address review findings in the draft offline planner integration without promoting unverified physical-device acceptance.

### Plan
- Review PR #42 and retrieve exact-head Android failure evidence.
- Move model-verifying profile saves off the Android UI thread with lifecycle-safe completion.
- Preserve Stop across native generation startup and abort model verification/load/decode cooperatively.
- Add focused cancellation and UI responsiveness regressions, run portable and available Android/native verification.
- Provide a truthful offline-task hardware acceptance procedure and record remaining device gates.

### Acceptance
- Stop cannot be lost before JNI generation begins, and cancellation resources are released.
- Model hashing and profile save cannot block the UI thread.
- API/emulator evidence and physical airplane-mode execution remain separately reported.

---

## TASK-070: Establish bounded GGUF header-preflight and native feasibility gate
**Priority:** P1 | **Tags:** owner-approved, developer, on-device, offline, gguf, feasibility, android
**Updated:** 2026-10-08

### Goal
Begin the approved on-device GGUF implementation with honest, deterministic safeguards, without claiming that a native inference backend or physical device has been proven.

### Scope
- Add an Android/Kotlin pure-JVM GGUF fixed-header preflight that reads at most 24 bytes, checks file-size budget, rejects unsupported versions and implausible counts, and returns an explicitly non-authoritative candidate status.
- Add test-first adversarial JVM cases for corrupted/truncated headers, hostile counts, short reads, no-progress streams and I/O failure.
- Keep current Demo/Cloud/Local planners and profile state untouched; do not wire header candidates into the planner as verified models.
- Separate and document the still-missing pinned llama.cpp JNI/NDK build, native model generation, API/ABI matrix and physical airplane-mode acceptance.

### Plan
- Inspect current Kotlin planner and JVM test seams.
- Implement red→green bounded header preflight in an isolated branch.
- Run local standalone Kotlin/JVM tests, then exact-head Android CI through a draft PR.
- Record precise evidence strength and unverified JNI/device gates; do not mark native inference delivered.

### Acceptance
- The preflight never reads past the 24-byte header and never accepts an invalid size budget or unsupported fixed-header fields.
- Candidate output does not grant model import or execution authority.
- All adversarial preflight tests run; unavailable Android CI/NDK or physical evidence is explicitly UNVERIFIED.
- Existing planner behavior is unchanged.
- The full Task 1 native GGUF feasibility gate remains OPEN until compiled JNI and hardware tests prove real token generation.

### Evidence / status
Owner approval: design [PR #40](https://github.com/null0entry/LAIN_OS_DEVBRANCH/pull/40) and native execution decision in chat. Task 1 implementation plan: `docs/superpowers/plans/2026-10-08-on-device-gguf-inference-implementation.md` on design PR #40. This task is an **initial scaffold**, not the conclusion of Task 1.

---

## TASK-054: Add conversational transcript UI
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, transcript, android-ui
**Updated:** 2026-10-04

### Goal
Make the trusted conversation history visible in the Android client so typed and final spoken turns, assistant responses, revisions, and active-task references are inspectable without conflating transcript text with durable execution authority.

### Scope
- Render an ordered bounded conversation stream from existing TASK-011 turn state and accepted assistant/task-facing responses.
- Distinguish user typed input, final recognized speech, assistant response/progress, and system/recovery status.
- Keep partial speech visibly provisional and non-authoritative; never persist raw microphone audio.
- Preserve active-task/turn identity and make revision routing understandable.
- Bound in-memory/UI history and define restart behavior consistent with existing retention rules.
- Do not duplicate the durable audit log or turn transcript into policy/approval state.

### Dependencies
- TASK-011 bounded voice turn manager.
- TASK-050 concrete speech adapter for final speech turns.
- Existing Android Workbench UI/state patterns.

### Plan
- Reuse current turn/session models as the source of truth.
- Add the smallest bounded transcript view/state projection.
- Mark provisional versus accepted turns explicitly.
- Preserve typed fallback and recovery/error states.
- Add lifecycle/restart/rotation tests plus negative authority-boundary checks.

### Acceptance
- Typed and accepted spoken turns appear in deterministic order with clear speaker/status labeling.
- Partial recognition is visually distinct and cannot become an accepted task-facing turn through UI state alone.
- Rotation/rebind does not reorder or duplicate visible turns.
- Transcript rendering does not expose credentials, raw audio, approval tokens, or capability authority.
- History is bounded and retention behavior is explicit.

### Verification
- View-model/order/bounds JVM tests.
- Partial-to-final transition and duplicate-turn negative tests.
- Android instrumentation for typed+spoken transcript rendering, rotation/rebind, and active-task reference.
- Secret/authority-field inspection.
- Canonical Verify + Android API matrix.

### Expected result
LAIN_OS has a truthful user-readable conversation surface that makes the voice-first interaction inspectable while leaving execution authority in the trusted runtime.

### Evidence basis
`docs/ROADMAP_1.0.md` Phase 2 lists “text transcript” and sequential item 24 explicitly says “Add conversational transcript UI.” Current search finds turn-management transcript semantics but no dedicated transcript UI task or implementation surface.

### Projection basis
Visible conversation state is needed to debug and safely use revision/ambiguity handling, and it gives TASK-015/TASK-036 a concrete installed-app surface to verify rather than inferring turns from internal state.

### Risks / unknowns
Existing Workbench layout space is limited; prefer a minimal bounded list over a new navigation architecture. Durable transcript retention must not be invented here if the current product contract only requires session-visible history.

---


## TASK-015: Close Phase-2 voice acceptance gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, acceptance
**Updated:** 2026-10-03

### Goal

Prove the integrated voice conversation stack is safe, interruptible, recoverable, and truthfully observable before Phase 2 exits.

### Scope

- Exercise the integrated R2.1-R2.6 path across microphone/transcript, turn management, synthesis/playback, barge-in, and progress narration.
- Cover permission denied/revoked, provider unavailable/offline, rotation/rebind, interruption, Stop talking vs Stop task, and low-confidence consequential commands.
- Measure interruption-to-playback-stop and trusted pause/cancel latency only on a declared reference device when hardware is actually exercised.
- Verify typed fallback and that speech/TTS failure never becomes task failure or fabricated completion.
- Preserve the trusted policy/approval/execution/verification/audit path.
- Do not broaden into Phase-3 workflow/DAG features.

### Dependencies

- TASK-009 through TASK-014 complete.
- TASK-008 complete as the Phase-1 planner foundation.

### Plan

- Build a reproducible end-to-end voice acceptance matrix over the integrated components.
- Use deterministic fakes/emulator instrumentation for logic/failure cases and keep hardware-only evidence separate.
- Add negative authority tests for partial/low-confidence speech, echo, playback failure, and TTS outage.
- Exercise independent Stop talking and Stop task paths.
- Record latency only from actual declared reference-device runs.
- Run canonical portable/Android verification and fresh review.

### Acceptance

- User can start a voice session, speak a normal goal, hear a response, interrupt it, revise the request, and stop the underlying task independently of playback.
- Permission/provider/lifecycle failures are explicit and cannot create hidden listening or fabricated task state.
- Partial or low-confidence consequential speech cannot authorize work outside the final-turn/approval path.
- Typed fallback remains complete.
- TTS/playback failure does not fail an otherwise healthy task.
- Emulator evidence is never promoted to reference-device latency evidence.

### Verification

- Integrated Android voice acceptance/instrumentation suite.
- Negative authority/failure-mode matrix.
- Canonical portable verification and Android API matrix.
- Relevant secret/audio-retention inspection.
- Hardware latency measurement only if a reference device is actually exercised.
- Fresh whole-diff review.

### Expected result

Phase 2 has a reproducible exit gate demonstrating a safe voice-first interface with truthful interruption, recovery, fallback, and authority semantics.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.7 Voice acceptance and the Phase-2 exit gate.
- No exact current TaskPlanner task, open issue, or open PR represents R2.7.

### Projection basis

- This gate prevents unresolved audio/authority/lifecycle defects from propagating into Phase-3 durable workflow work.

### Progress evidence

- PR #34 exact head `a1377e1ac888336c89b7d64f60ed5eaa34696711` rejects explicit low-confidence and malformed Android recognizer confidence before either can become a turn, while preserving typed fallback.
- TDD RED: Android #579 failed because the confidence seam and `LOW_CONFIDENCE` state did not exist.
- Fresh review receipt `5440831381`; zero unresolved review threads.
- Verify #597 and Android #586 passed on the exact repaired head; squash merge `ae4b5b3fd6ebbff717031d54eb85359e0d1a931f`.
- This is one acceptance slice only. The integrated reference-device matrix, physical/OEM score availability, and latency evidence remain UNVERIFIED.

### Risks / unknowns

- Reference-device execution may be unavailable in CI; such latency evidence must remain explicitly UNVERIFIED.
- Live provider checks are supplementary; deterministic CI evidence remains the reproducible gate.

---
