# In Progress

## TASK-066: Enforce release-state truth against repository evidence
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-0, release, verification, docs

**Updated:** 2026-10-07

### Goal

Make repository-facing implementation, release, and verification claims fail closed when they drift from the current package metadata, TaskPlanner state, or explicitly recorded evidence class.

### Scope

- Implement the smallest deterministic verifier for stable release-state invariants that can be derived from repository-native sources.
- Check version/package/API claims and current task/evidence references only where an authoritative machine-readable source exists.
- Preserve the distinction between automated, emulator, physical-device, owner-reported, live-service, UNVERIFIED, and unsupported evidence.
- Integrate the checks into the existing canonical verification path without creating a second release system.
- Report precise source/claim mismatches and permit explicit bounded exceptions only when their authoritative reason is repository-visible.
- Do not infer semantic truth from prose alone, rewrite documentation automatically, close acceptance gates, publish artifacts, or choose release/distribution policy.

### Dependencies

- Existing canonical verification scripts and Android/package metadata.
- Current .tasks/**, README/spec/roadmap sources, and immutable Git/CI receipts remain the applicable authorities for their declared scopes.
- TASK-047 may later extend the verifier with the formal candidate manifest; this task must remain useful before that dependency lands.

### Plan

- Inventory repository claims with stable machine-readable counterparts: package/version/API metadata, task state, candidate/source identity, and evidence-strength labels.
- Add focused failing fixtures for stale task status, mismatched package/version/API claims, and emulator/physical/live evidence promotion.
- Implement one minimal reusable check in the existing verifier path.
- Seed only high-confidence current invariants; leave semantic or owner-controlled decisions explicitly outside automation.
- Document the check’s authority boundaries and failure remediation.

### Acceptance

- A stale or contradictory stable claim fails canonical verification with the exact claim and authoritative source named.
- Automated/emulator evidence cannot satisfy a physical/live acceptance assertion.
- Task state referenced by checked current-status surfaces cannot contradict TaskPlanner’s canonical state.
- Package/version/API claims covered by the check match actual build/runtime metadata.
- Unknown, unstructured, or owner-controlled claims remain explicit rather than guessed or silently normalized.
- Existing unrelated verification, documentation, TaskPlanner schema, and product behavior remain unchanged.

### Verification

- RED→GREEN fixtures for stale task state, package/version/API mismatch, and evidence-strength promotion.
- Canonical portable verification with the new check enabled.
- Negative fixtures proving unstructured prose is not treated as authority.
- Repository-wide secret/credential scan remains unchanged.
- Fresh review of authority boundaries, false-positive risk, and failure messages.

### Expected result

The repository’s stable release-state claims stay mechanically aligned with authoritative project metadata, while physical/live/owner-controlled truth remains explicitly outside automation until real evidence exists.

### Evidence basis

- docs/ROADMAP_1.0.md R0.2 requires architecture/spec/README claims to match fresh evidence, release/version/migration metadata to remain consistent, and external orchestration/secrets to stay out of repository state.
- Existing tasks cover candidate manifests, packaging, documentation, migration, and final readiness, but none owns an always-on verifier for stable repository-state claims before those late gates.
- The current canonical verifier checks code/tests/security hygiene but does not represent R0.2 as a dedicated task.

### Projection basis

- Frequent queue, package, and acceptance changes can leave truthful-at-write-time claims stale before TASK-047/TASK-052/TASK-051 execute.
- A narrow machine-source-only verifier prevents false release-status drift without attempting brittle semantic validation of arbitrary prose.

### Risks / unknowns

- Over-broad prose parsing would create false authority and noisy failures; restrict checks to structured markers with authoritative counterparts.
- Historical audit records must not be rewritten merely because current state changed.
- Final candidate and distribution semantics remain owner-controlled and may extend, but must not weaken, this verifier.

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
