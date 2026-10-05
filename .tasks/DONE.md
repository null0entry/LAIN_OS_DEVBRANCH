# Done

## TASK-050: Implement first on-device Phase-2 speech adapter
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, speech, android, privacy
**Updated:** 2026-10-04

**Integration:** PR #29 exact head `c79b032e065522f978f037fac847be7c18a37278` passed Verify #487 and Android #476 on API 24/API 35, received fresh clear review with zero unresolved threads, and squash-merged as `4f10b082416f7ec95db8f55a4414d34e882128cf`.

### Goal
Close the Phase-2 implementation gap by providing one concrete Android speech path behind the TASK-009 contracts so a real spoken goal can become a final trusted turn and a spoken response can reach TASK-012 playback without depending on the later Phase-5 external-provider stack.

### Scope
- Implement the smallest Android-native transcription and synthesis adapters behind the existing provider-neutral speech contracts.
- Prefer explicitly on-device-capable Android speech services/engines; if an on-device implementation is unavailable, fail truthfully instead of silently falling back to cloud.
- Bridge TASK-010 bounded microphone output into final transcription results and bridge synthesis output into TASK-012 cancellable playback.
- Preserve provider/engine identity, locality/provenance, cancellation, timeout, bounded payloads, and lifecycle semantics.
- Keep partial recognition non-authoritative; only accepted final transcription may enter TASK-011 turn routing.
- Do not retain raw microphone audio by default, expose provider credentials, add generic network authority, or bypass policy/execution boundaries.
- Leave credentialed/cloud STT/TTS to TASK-033 rather than creating a parallel external-provider lifecycle.

### Dependencies
- TASK-009 complete: provider-neutral speech contracts.
- TASK-010 complete: bounded Android microphone lifecycle.
- TASK-011 complete: accepted-turn routing semantics.
- TASK-012 integrated: bounded cancellable playback path.
- Android platform capability detection must remain truthful across supported API levels.

### Done summary

- Added a bounded Android-native captured-PCM STT path behind the TASK-009 provider-neutral contract, fail-closed below API 33 and gated by positive same-intent recognition support on API 33+.
- Added installed/non-network-only Android TTS synthesis feeding the existing TASK-012 PCM playback boundary, including asynchronous engine readiness and cancellation-safe continuation.
- Routed only final accepted transcripts into the trusted turn-submit path; partial recognition remains non-authoritative.
- Preserved no-cloud-fallback, bounded payload, lifecycle, cancellation, and raw-audio zeroing semantics.
- Added JVM/instrumentation coverage for API-floor behavior, final-turn routing, async TTS readiness, cancellation, malformed/oversized output, and playback handoff.

### Acceptance
- On an Android device with compatible on-device speech services, one spoken phrase can produce exactly one accepted final TASK-011 turn through the trusted planning path.
- A bounded synthesis request can produce audio consumed by TASK-012, and Stop talking cancels playback without cancelling the task.
- Unsupported/missing speech engines produce explicit unavailable/unsupported state; no automatic network/cloud fallback occurs.
- Partial transcripts cannot authorize or execute anything.
- Raw audio is not persisted by default; provider/engine identity and locality evidence are retained without secrets.
- API-level/vendor differences fail closed rather than fabricating success.

### Verification
- Focused provider-contract and Android facade JVM tests.
- Android instrumentation with deterministic injectable speech-service doubles for transcript/synthesis/cancellation/lifecycle flows.
- Negative tests for partial-as-final, hidden fallback, unavailable engine, malformed/oversized result, background teardown, and authority-field leakage.
- Canonical Verify + Android API-matrix gates after integration.
- Physical live-engine behavior remains separately labeled and is consumed by TASK-036; emulator/fake evidence must not be promoted to physical acceptance.

### Expected result
Phase 2 has a real on-device speech implementation path instead of only interfaces and microphone/playback primitives, breaking the dependency cycle where TASK-033 external speech support otherwise arrives only after Phase-2 acceptance.

### Evidence basis
`docs/ROADMAP_1.0.md` Phase 2 requires a normal spoken goal, spoken response, interruption, and typed fallback; its sequential development list separately calls for the first working STT and TTS implementations. Current repository search finds TASK-009 speech contracts and no concrete speech-provider implementation. TASK-033 is the Phase-5 external speech adapter and depends on TASK-009 through TASK-015, so it cannot satisfy the Phase-2 exit gate without a cycle.

### Projection basis
TASK-015 and TASK-036 require real installed voice behavior. Adding one bounded on-device baseline immediately after TASK-012 gives TASK-013/TASK-014 a concrete integration path while preserving later replaceable external providers.

### Risks / unknowns
Android on-device recognizer availability varies by API level, OEM, and installed engine; unsupported environments must remain explicit. Engine licensing/privacy/locality claims require runtime evidence and documentation rather than inference. No bundled speech model or new dependency should be added unless platform capability proves insufficient.

---

## TASK-012: Implement cancellable speech playback
**Integration:** PR #28 exact head `48523920d0f4c5eb1ffe7810b277547ade42fd41` squash-merged as `c6d6cf092571bf6ae67f1fde07df37e546dca590`.
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

### Completion

- Fresh whole-diff review on exact head `4852392` found no remaining correctness/security/authority blocker.
- Verify #451 passed.
- Android #440 passed API 24 and API 35 build, JVM tests, lint, and instrumentation.
- Regression coverage proved invalid replacement releases active playback/focus and stale completion cannot escape replacement generation boundaries.

---
## TASK-009: Define bounded speech provider interfaces
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, provider-contract
**Updated:** 2026-10-04

### Done summary

- Added provider-neutral bounded transcription/synthesis contracts, provenance, cancellation seam, media metadata, and explicit bounded failure vocabulary under `lain.speech`.
- Added deterministic speech contract tests and `specs/SPEECH_PROVIDER_PROTOCOL.md`; speech contracts contain no provider credentials or capability/policy/approval/execution authority.
- TDD evidence: Verify #370 failed at the test-only RED head because `lain.speech` was absent; exact implementation head `e6e671c121cd57818d8bbabad608582f493427b1` passed Verify #371 with 292 tests and Android #360 on API 24 and API 35.
- PR #25 merged to `main` as `414bccec30268a464ef2bd0c12bbad3a7b2992e7`.
- TASK-010 is promoted to begin Android microphone lifecycle work.

---

## TASK-008: Close Phase-1 planner acceptance and adversarial gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-1, acceptance, adversarial
**Updated:** 2026-10-04

### Done summary

- Phase-1 planner acceptance/adversarial implementation is integrated through PR #24, including repeatable demo-file behavior and packaged Android file/clipboard acceptance coverage.
- Exact PR #24 head `f05a9317dfea53a98d8ee4ecee4963cb4250b19a` passed Verify #365 and Android #354.
- On 2026-10-04 the owner reported the required physical TASK-008 acceptance matrix complete with all tests green. This records owner-provided hardware acceptance; the GitHub connector did not independently observe the physical-device traces.
- Phase 1 is accepted complete and TASK-009 is promoted to begin Phase 2 Voice Conversation.

---

## TASK-007: Build Planner Settings and inert connection diagnostics
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-1, android-ui
**Updated:** 2026-10-03

### Done summary

- Added the native Android Planner Settings surface for Demo, Cloud, and Local profiles, including create/edit/select/delete and bounded endpoint/model/timeout/response controls.
- Credential UI supports save, replacement, removal, and saved/missing state without rendering or returning raw secrets.
- Added diagnostic-only Test Connection and visible active provider/model identity; instrumentation verifies diagnostics do not create an agent session.
- Corrected the settings layout to preserve existing Workbench control visibility and made the screen-level test independent of emulator viewport scrolling.
- Verify workflow #230 succeeded.
- Android workflow #219 succeeded after one targeted rerun of an unrelated API-24 RuntimeFlow timing failure; API 24 and API 35 build, JVM tests, lint, and instrumentation gates are green at head `8ce8a99a720cc5f9dfc0e6310fd29bcf4ce58f3f`.
- Final post-CI review `5401183419` found no unresolved threads or actionable correctness/security findings.
- PR #12 squash-merged as `be4e1d273e05784beeb5783c811d19d40a8a1930`.
- Physical-device acceptance remains unverified and is not claimed.

---

## TASK-006: Wire profile-selected planner runtime bridge
**Priority:** P1
**Updated:** 2026-10-03

### Done summary

- Added the Android/Python planner bridge over the native bounded transport while preserving native ownership of raw credentials and HTTP.
- Planner selection is keyed from each session's pinned `PlannerBinding`; Demo stays offline and Cloud/Local never auto-fallback across modes.
- Stop/lifecycle cancellation propagates into the active native planner call and maps to an explicit cancelled agent state.
- Verify workflow #173 succeeded.
- Android workflow #162 succeeded after a targeted API 35 rerun; API 24 and API 35 build, unit, lint, and instrumentation gates are green.
- The first API 35 attempt failed with unrelated Espresso window-focus failures after emulator/ADB startup trouble; the isolated retry succeeded without product-code changes.
- Fresh post-CI review found no submitted review threads and no product-code overlap with newer main planning commits.
- PR #11 squash-merged as `f8cff695f4dfdac5cdf5bffa33b2d5dc70776bb2`.
- Physical-device acceptance remains unverified and is not claimed.

---

## TASK-005: Implement persistent PlannerProfile selection
**Priority:** P1
**Updated:** 2026-10-03

### Done summary

- Added strict native Demo/Cloud/Local PlannerProfile validation and atomic profile persistence with opaque credential references only.
- Active profile selection now persists across restart and supplies the existing trusted PlannerBinding provider at session creation, preserving per-session pinning.
- PR #10 head `1d35244b83292f309f3cb1eb0dd16e5254587abc` received fresh whole-diff review with no actionable correctness or over-engineering finding.
- Verify workflow #150 succeeded.
- Android workflow #139 succeeded on API 24 and API 35, including build, unit tests, lint, and instrumentation.
- Draft state was confirmed as the mergeability gate; marking ready changed GitHub mergeability to true without history rewrite or product-code reconciliation.
- PR #10 squash-merged as `16fd0aa363576f897282dd932b8a56609884ff86`.
- Physical-device acceptance remains unverified and is not claimed.

---

## TASK-003: Implement bounded native planner transport
**Priority:** P1
**Updated:** 2026-10-03

### Done summary

- PR #8 head `a39b69c768984fcbe0d2febca79eac99ae204b38` received fresh same-run whole-diff review PASS `5399559135` against `main@04e4a5b2b0fd947ca423415de5c469e9a7650c04`.
- Prior P1/P2 findings were verified addressed and all three review threads were resolved.
- Verify workflow #135 succeeded.
- Android workflow #124 succeeded, including API 24 and API 35 instrumentation.
- Native planner endpoints remain HTTPS-only; global cleartext denial remains intact.
- PR #8 squash-merged as `c217ca76cc41ae24eb14357bf8ce3022f29b8534` under the review-only merge policy.
- Tests were run and succeeded; no UNTESTED status applies to this merge.

---

## TASK-001: Reconcile PR #4 with current main and restore mergeability
**Priority:** P0
**Updated:** 2026-10-02 17:56

### Done summary

- PR #4 was reconciled without dropping its seven-file Android Stop wake-up fix.
- Fresh code review passed on head `6217be8ec2b3d4bd20993c46239bf0425543617c`.
- Android workflow #22 and Verify workflow #33 succeeded.
- The PR merged as `077a4aea1cea9e3ce638403886ceeab4c32d51b3`.
- Physical-device acceptance remained deferred and is not claimed passed.

---

## TASK-002: Implement Android Keystore-backed planner SecretStore
**Priority:** P1
**Updated:** 2026-10-02 17:56

Review corrections implemented on PR #7.

### Done summary

- Added AES-GCM AAD binding to exact credential_ref plus domain/version.
- Added swapped-record and concurrent-initialization regressions.
- Fixed first-use planner-secrets mkdir race by re-checking isDirectory.
- Implementation commits: c67d1a5f152e7e82e38f21a141727b43c4d8e99e, c26925ed76e1e02fd49bd5b7ec796957c36fc423.
- Fresh code review passed on head c26925ed76e1e02fd49bd5b7ec796957c36fc423 with no new actionable finding.
- Verify workflow #56 succeeded; Android workflow #45 was cancelled, so Android instrumentation remains explicitly untested.
- PR #7 merged as 2946e1a042d57eddffce4477f2136aea4da0ccde under the review-only merge policy.

---
## TASK-010: Implement Android microphone lifecycle
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, android-audio
**Updated:** 2026-10-04
**Integration:** PR #26 merged as `204a20d7ebb2e3321943f0153e4561e0086253a8`; exact implementation head passed Verify #386 and Android #375.

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

## TASK-011: Implement bounded voice turn manager
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, conversation
**Updated:** 2026-10-04
**Integration:** PR #27 merged as `cc03a4a4c4c0e3bd076109d8e821037a2dafef15`; exact implementation head `874c9b7` passed Verify #409 and Android #398 with fresh clear review.

### Goal

Add the durable conversation-turn coordination layer that converts final transcripts or typed revisions into ordered task-facing turns without allowing partial speech or ambiguous references to authorize consequential work.

### Scope

- Assign monotonic turn IDs for each accepted user turn.
- Keep partial/interim transcripts separate from final accepted transcripts.
- Track an explicit active-task reference for conversational follow-ups.
- Route clear revisions to the referenced task/workflow without mutating already-approved consequential payloads in place.
- Surface ambiguous referents as a clarification-required state rather than guessing.
- Preserve typed input as an equivalent complete turn source.
- Do not implement microphone capture, speech playback, barge-in, workflow DAG semantics, or provider-specific speech logic in this task.

### Dependencies

- TASK-009 complete: bounded speech provider interfaces.
- TASK-010 complete: Android microphone lifecycle producing final/partial capture results.
- Existing durable agent-session identifiers and approval semantics remain authoritative.

### Plan

- Define the smallest turn record/state model with monotonic IDs and explicit source/finality fields.
- Add active-task reference tracking without duplicating durable workflow authority.
- Route only final accepted turns into task/planner input; partial transcripts remain non-authoritative UI state.
- Add explicit ambiguous-reference and revision-routing outcomes.
- Add deterministic tests for turn ordering, partial/final separation, active-task reference, ambiguity, and revision routing.

### Acceptance

- Accepted turns receive strictly monotonic IDs.
- Partial transcripts cannot start work, grant approval, or alter durable task state.
- Final transcripts and typed messages enter the same bounded turn pipeline.
- Follow-up references resolve only when an active target is unambiguous.
- Ambiguous referents require clarification and execute nothing.
- Revision routing preserves prior approvals/effects and creates a new revision intent rather than silently mutating an approved consequential payload.
- Turn state contains no raw provider credential or hidden capability authority.

### Verification

- Focused unit tests for ordering, finality, reference resolution, ambiguity, and revisions.
- Negative tests proving partial transcripts and ambiguous turns cannot trigger planner/executor work.
- Regression proving typed input follows the same turn contract.
- Canonical portable verification and Android integration checks after platform wiring.

### Expected result

LAIN_OS has a deterministic conversation-turn boundary that later playback, barge-in, and workflow features can consume without conflating speech fragments with authoritative user intent.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.3 Turn manager with monotonic turn IDs, final-vs-partial transcript separation, active-task reference, ambiguous referent clarification, and revision routing.
- No current TaskPlanner task, open issue, or open PR represents R2.3.

### Projection basis

- A stable turn boundary is required before speech playback/barge-in can safely distinguish conversational interruption from task cancellation or authorization.
- Explicit finality and reference semantics prevent later voice features from treating low-confidence/partial speech as consequential intent.

### Risks / unknowns

- Full workflow revision invalidation belongs to Phase 3; this task should expose revision intent/reference only, not pre-build the DAG scheduler.
- Multi-workflow targeting is beyond the current single-active-workflow 1.0 scope and should not broaden this contract.
- Low-confidence STT scoring may be provider-specific later; this task should depend on explicit finality/clarification semantics rather than a hard-coded confidence model.

---

