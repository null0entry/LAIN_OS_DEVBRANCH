# In Progress

## TASK-053: Implement selectable agent speech voices
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, speech, android, ux
**Updated:** 2026-10-07

### Goal
Expose a bounded user-selectable speech-synthesis voice choice through the existing TASK-009 speech contract and the concrete TASK-050 adapter without letting voice metadata become execution authority.

### Scope
- Discover and present only voices actually available through the active speech synthesis implementation.
- Persist a stable non-secret voice selection per applicable local speech profile.
- Bind synthesis requests to an explicit selected voice identifier where supported; fail truthfully when a stored voice disappears.
- Preserve typed fallback, Stop talking semantics, provider/locality provenance, and TASK-012 playback boundaries.
- Do not add voice cloning, arbitrary downloadable models, provider credentials, cloud fallback, or new capability authority.

### Dependencies
- TASK-009 speech provider contracts.
- TASK-012 cancellable playback.
- TASK-050 first concrete on-device speech adapter.

### Plan
- Reuse the TASK-050 synthesis adapter's platform voice inventory rather than introducing a parallel registry.
- Add the smallest stable voice-selection state and UI control.
- Validate stored selections against current engine/provider identity before synthesis.
- Keep unsupported/no-voice state explicit and recoverable.
- Add focused JVM/instrumentation coverage for selection, disappearance, restart, cancellation, and authority isolation.

### Acceptance
- The user can see and select among synthesis voices actually exposed by the active supported engine.
- A selected voice is used by subsequent synthesis requests and survives app restart when still available.
- Missing/removed voices surface a truthful recoverable state and never silently switch provider/locality.
- Voice selection cannot grant task approval, policy, capability, credential, or executor authority.
- Stop talking and task cancellation remain separate.

### Verification
- Voice inventory/selection unit tests using injectable engine doubles.
- Restart and stale-voice negative tests.
- Android instrumentation for selection UI and synthesis binding.
- No-cloud-fallback and authority-field leakage tests.
- Canonical Verify + Android API matrix; physical voice quality/availability remains separately labeled.

### Expected result
LAIN_OS satisfies the roadmap's selectable-agent-voice requirement through the existing bounded speech path, with a user-visible choice that is stable, truthful, and authority-neutral.

### Evidence basis
`docs/ROADMAP_1.0.md` Phase 2 lists selectable speech synthesis voice as a feature and sequential item 23 explicitly says “Add selectable agent voices.” Current TaskPlanner work covers speech contracts, microphone, turn routing, playback, barge-in, narration, acceptance, and the first concrete speech adapter, but no task covers selectable voice state/UI.

### Projection basis
A dedicated bounded selection layer avoids baking one engine voice into TASK-050 and gives later narration/media work a reusable explicit voice identity without coupling it to provider credentials or execution authority.

### Risks / unknowns
Android/OEM engines expose different voice inventories and locality metadata. Persist stable identifiers only when the platform exposes them; otherwise surface unsupported/changed state rather than inventing portability.

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
