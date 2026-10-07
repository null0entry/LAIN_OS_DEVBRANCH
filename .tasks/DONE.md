# Done

## TASK-014: Implement voice progress narration
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, progress
**Updated:** 2026-10-03

### Goal

Add bounded spoken progress events that narrate useful task state without turning speech delivery into durable workflow truth or making TTS availability a task dependency.

### Scope

- Define short progress-narration events derived from trusted durable task/session state.
- Keep spoken progress separate from durable workflow/task state and planner assertions.
- Route progress narration through the provider-neutral synthesis/playback boundaries from TASK-009/TASK-012.
- Coalesce/rate-limit repetitive progress speech so narration cannot starve work or create unbounded provider calls.
- Allow playback interruption through TASK-013 without cancelling the task.
- Ensure speech/TTS failure never marks the underlying task failed or complete.
- Preserve visual/text progress as the authoritative fallback.
- Do not implement Phase-3 workflow DAG state, publication narration, or provider-specific TTS behavior.

### Dependencies

- TASK-009 complete: speech provider interfaces.
- TASK-011 complete: turn semantics.
- TASK-012 complete: cancellable playback.
- TASK-013 complete: barge-in/echo protection.

### Plan

- Define a minimal progress-event schema sourced only from trusted runtime state.
- Add bounded event-to-utterance formatting with coalescing/rate limits.
- Send narration through synthesis/playback as a non-authoritative side channel.
- Make synthesis/playback errors local to narration and preserve underlying task state.
- Add deterministic tests for event provenance, coalescing, failure isolation, and interruption behavior.

### Acceptance

- Spoken progress is generated only from trusted current task/session state.
- Narration cannot change task status, grant approval, authorize capabilities, or fabricate completion.
- TTS/provider/playback failure leaves the task running or settled exactly as before.
- Repetitive progress events are bounded/coalesced.
- User barge-in can stop progress speech without stopping the task.
- Visual/text state remains available and authoritative when speech is unavailable.
- No raw provider credential enters progress events or narration artifacts.

### Verification

- Focused progress-event/provenance/coalescing tests.
- Negative tests proving narration failure does not alter task state.
- Regression proving spoken “complete” text cannot itself mark work complete.
- Android integration coverage for narration playback/interruption.
- Canonical verification after implementation.

### Expected result

LAIN_OS can speak concise progress while work continues, with speech treated as a fallible presentation channel rather than a source of execution truth.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R2.6 Voice progress narration: progress events separate from durable workflow state, speech failure never equals task failure, and tasks continue if TTS is unavailable.
- No current TaskPlanner task, open issue, or open PR represents R2.6.

### Projection basis

- Progress narration is the last functional voice slice before R2.7 acceptance and therefore should reuse already-stable turn/playback/barge-in contracts rather than introduce a new authority path.
- Failure isolation is necessary before voice acceptance can truthfully test provider outages and interruption.

### Risks / unknowns

- Exact wording/verbosity policy is presentation-level and should remain adjustable without changing durable state contracts.
- Aggregate speech-provider cost budgets may be refined later with Phase-3 workflow budgets; this task needs only local bounded/rate-limited behavior.
- Reference-device latency and full voice-session acceptance belong to R2.7.

### Completion evidence

- TDD RED head `0168bef487e2d08616ab4e3b8e7eef25c6ecfba9`: Android #545 failed on both API jobs because the progress policy types did not exist.
- PR #33 exact head `afcf2783a472f45de527cac6f2bddcea3a4ce468` passed whole-diff standards/spec review #5437621479 with zero unresolved threads.
- Verify #580 and Android #569 passed on the exact head, including API 24/API 35 build, JVM tests, lint, and instrumentation.
- Squash merge receipt: `b52523a3ab2e8f8f6510ef34b03c89230d4a0dd4`.
- Physical interruption latency and full installed voice-session acceptance remain separately owned by TASK-015/TASK-036.

---

## TASK-063: Apply accepted conversational revisions to active task sessions
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, revision, session, authority
**Updated:** 2026-10-04

### Goal
Complete the missing Phase-2 revision path so one accepted final conversational revision such as “make it shorter” can update the intended active task session through trusted runtime state, invalidate stale approval/planning authority, and replan safely without letting transcript text or ambiguous references mutate execution state.

### Scope
- Consume the exact final-turn revision_intent produced by TASK-011 only after active-task/reference resolution succeeds.
- Add the smallest trusted current-session revision operation needed by the existing bounded AgentSession/runtime controller; preserve the stable session identity while advancing trusted revision state where the existing model supports it.
- Invalidate stale approval/request/action authority derived from the prior session revision before any revised plan can execute.
- Replan from the accepted revision text under the session’s existing selected planner/provider binding and remaining budgets; never let the model choose the target session or regain consumed budgets.
- Reject partial speech, ambiguous/missing targets, terminal/non-revisable sessions, stale revision races, duplicate revision submission, or target-session substitution.
- Preserve typed fallback and the same route for typed and spoken accepted revisions.
- Keep Phase-3 workflow artifact invalidation under TASK-020; this task owns only the existing Phase-1/2 active AgentSession revision seam needed by the Phase-2 exit gate.
- Do not add new capabilities, generic session mutation, hidden restart/retry, or publication/workflow-artifact behavior.

### Dependencies
- TASK-008: trusted bounded AgentSession planning/execution and exact approval semantics.
- TASK-011: monotonic turns, active-task reference, ambiguity handling, and non-mutating revision_intent.
- TASK-050: accepted final local speech can enter the same TASK-011 turn path.
- TASK-013: barge-in/echo protection must keep synthesized/partial speech from becoming revision authority before final voice acceptance.

### Plan
- Trace the current turn_submit → revision_intent → AgentSession boundary and identify the existing trusted session mutation/replan primitive or add the minimum explicit one if absent.
- Write failing tests proving the current revision intent leaves session revision/goal/planning state unchanged and that partial/ambiguous/stale turns cannot mutate it.
- Implement one revision application path bound to the target session, current trusted revision, accepted turn ID/text, existing planner binding, and remaining budgets.
- Atomically invalidate stale approvals/plans before revised planning resumes; preserve completed verified action history without replay.
- Wire Android/runtime flow so accepted typed and spoken revisions use the same trusted operation and explicit outcome.
- Add restart/race/duplicate/terminal-session negatives and run canonical + Android verification.

### Acceptance
- A final accepted typed or spoken revision for the uniquely resolved active task changes only that intended session through a trusted runtime operation and produces an incremented/new trusted revision state.
- Partial speech, synthesized echo, ambiguous reference, missing target, stale prior revision, duplicate submit, or cross-session substitution executes no revision.
- All approval/request/action authority bound to the prior session revision is invalid before revised planning can execute.
- Existing completed verified actions are not replayed merely because the request was revised.
- Planner/provider binding and consumed session budgets do not reset or switch silently during revision.
- The revised request enters the same policy/approval/execution/verification/audit path as any other trusted planning iteration.
- Typed fallback and spoken final turns converge on the same revision semantics.
- This task does not perform TASK-020 durable workflow-artifact invalidation or grant generic mutation authority.

### Verification
- TDD RED→GREEN around the current test that proves revision_intent is non-mutating.
- Target-session/current-revision/turn-ID binding and duplicate/stale-race tests.
- Partial/ambiguous/echo/cross-session/terminal-session negatives.
- Approval invalidation, consumed-budget preservation, planner-binding preservation, and no-replay assertions.
- App/runtime protocol tests for typed and speech-final revision outcomes.
- Canonical Verify plus Android API matrix and fresh whole-diff architecture/security review; physical spoken revision remains separate TASK-015/TASK-036 evidence.

### Expected result
Phase 2 has a real, bounded conversational revision operation: “make it shorter” can safely revise the intended active task and trigger trusted replanning without stale approval, replay, budget reset, ambiguous authority, or premature Phase-3 workflow semantics.

### Evidence basis
- docs/ROADMAP_1.0.md sequential item 29 explicitly requires voice-driven task revisions, and the Phase-2 exit gate requires the user to revise a spoken request.
- Current lain/app/control.py returns a revision_intent for TASK-011 revision turns but does not apply it.
- Current tests/test_app_turns.py explicitly asserts the target session revision is unchanged after a revision turn, proving routing exists while application remains absent.
- TASK-020 covers later durable workflow revision invalidation, not the current bounded AgentSession revision required before Phase-2 exit.

### Projection basis
- Without this bridge, TASK-015 can test revision routing but cannot prove the product actually revises active work, leaving a direct gap between the current turn manager and the stated voice-first milestone.
- Implementing the AgentSession seam before Phase-3 avoids forcing later workflow-artifact revision semantics into the Phase-2 controller.

### Risks / unknowns
- The existing AgentSession model may not yet expose a single explicit revision transition; extend it minimally rather than adding a parallel session type.
- Revising after consequential effects have already occurred cannot imply rollback; preserve observed effects and only change future planning.
- In-flight planner cancellation/restart ordering must fail closed so two revisions cannot race into concurrent planning.
- Physical speech quality and interruption latency remain separate acceptance evidence.

### Completion evidence
- PR #32 exact head `0ddf88a5992691c03d20342601c1990e32ca0c20` passed fresh whole-diff review #5431921582 with zero unresolved threads.
- Verify #547 and Android #536 passed on the exact head, including API 24 and API 35 instrumentation.
- Squash merge receipt: `808be986de087a1a678d4a2d1ec3431d3c01cb05`.
- Physical spoken-revision acceptance remains separately owned by TASK-015/TASK-036 and is not implied by emulator CI.

---


## TASK-013: Implement voice barge-in and echo protection
**Integration:** PR #31 exact head `f924bbedb7aa00856fae4d559133aab325a8dea8` passed Verify #528 and Android #517 (API 24 + API 35) and squash-merged as `af0dc19028f329e0f49e3290a3af8cee95a5ff89`.
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


### Completion
- Record intent now stops app-owned direct local TTS before microphone capture begins.
- Talk-back stop remains separate from runtime/task Stop.
- Existing final-only STT/partial non-authority seam remains unchanged.
- RED receipt: Android #514 API 35 observed [capture-start] without tts-stop.
- GREEN exact head: Verify #528 + Android #517 on API 24/API 35; review receipt `5429319384`; zero unresolved threads.
- Physical acoustic echo suppression and interruption latency remain TASK-015/TASK-036 evidence.

---

## TASK-050: Lock local-only Phase-2 voice loop
**Integration:** PR #30 exact head `dfe20f205509e43cc45877b1e23e65787df5678b` passed Verify #516 and Android #505 (API 24 + API 35) and squash-merged as `a4c11e6325e26732916bbcdcf0b3deca4d4810ff`.
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, speech, privacy
**Updated:** 2026-10-04

**Correction:** The approved behavior is a minimal voice conversation loop, not a synthesized-audio playback subsystem.

### Goal

Ship the smallest local-only Android voice path:

`microphone → captured PCM → on-device STT → final trusted turn → agent final reply → Android TextToSpeech.speak()`

Typed input remains independent and available.

### Contract

- Captured PCM STT is unavailable below API 33 and returns `PROVIDER_UNAVAILABLE` before touching recognizer availability or transcription.
- API 33+ requires explicit Android on-device recognition availability.
- No Local→Cloud or network recognizer fallback exists.
- Partial recognition is non-authoritative; only final recognized text may become a trusted turn.
- Raw microphone audio remains bounded and in memory only.
- Final agent reply text exposed to Android is redacted and bounded.
- Talk-back uses Android `TextToSpeech.speak()` directly.
- TTS may select only an installed voice that does not require network access; otherwise it fails closed.
- There is no synthesized PCM handoff, AudioTrack path, playback state machine, or playback UI in TASK-050.

### Scope

- Keep the provider-neutral STT seam and its API/finality tests.
- Add the Android on-device recognizer backend needed to feed captured PCM on API 33+.
- Wire final STT text into the existing trusted `turn_submit` runtime path.
- Expose one bounded/redacted final planner reply in the runtime snapshot.
- Add a thin local-TTS backend that selects an installed non-network voice and calls `TextToSpeech.speak()` directly.
- Remove TASK-050 playback controls/wiring from the workbench.
- Do not add provider SDKs, generic network speech authority, cloud fallback, synthesized-file/PCM playback, or a playback state machine.

### Acceptance

- API 32 returns `PROVIDER_UNAVAILABLE` with zero recognizer-availability reads and zero transcription calls.
- API 33+ refuses captured PCM unless on-device recognition is explicitly available.
- Partial transcripts produce no trusted turn; one final transcript produces exactly one speech turn.
- A completed task exposes one bounded/redacted `speech_text` reply.
- One final reply is sent once to direct local TTS using an installed non-network voice.
- Network-required or uninstalled voices never speak.
- The workbench contains no Stop talking/playback-status controls.
- Typed input continues to work independently.

### TDD receipts

- STT finality RED: `58b03b4`; GREEN: `c038ecf`.
- Direct TTS RED: `ef78668`; Android API 24 compile failed on the intentionally missing `speak` seam.
- Direct TTS GREEN: `12ed589`; exact-head Android JVM/build passed on API 24 and API 35.
- End-to-end reply RED: `2d9c62c` plus `b9cf348`; Verify failed on missing `speech_text`, and Android run 37255038645 failed on the intentionally missing speech backend/output wiring. Runtime reply GREEN: `f2b01a1` with canonical Verify passing.

### Verification

- `OnDeviceSpeechAdapterTest` locks the API/finality STT contract.
- `OnDeviceSpeechSynthesisControllerTest` locks direct local-only TTS selection and no PCM handoff.
- `test_app_control.py` locks the bounded terminal speech reply.
- `VoiceTalkBackAndroidTest` locks captured PCM → speech turn → final reply → direct talk-back.
- Canonical Verify + Android CI gate integration and the test APK.

### Expected result

TASK-050 ends with LAIN hearing a final local utterance and talking back through Android's installed local TTS voice, with no hidden cloud path and no playback subsystem.


### Completion

- Final whole-diff review receipt: `5428807541`; zero unresolved review threads.
- Verify #516 passed on exact head `dfe20f205509e43cc45877b1e23e65787df5678b`.
- Android #505 passed API 24 and API 35 build, JVM tests, lint, and instrumentation on the same exact head.
- Captured PCM requires positive `checkRecognitionSupport` before `startListening`; unsupported paths fail closed.
- Final local voice loop is microphone → captured PCM → on-device STT → trusted final turn → bounded reply → direct installed non-network TTS.
- Physical/OEM live-engine acceptance remains separate under TASK-015/TASK-036.

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

