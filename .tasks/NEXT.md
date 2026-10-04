# Next

## TASK-044: Execute exact approved YouTube publication
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, publication, external-effect, approval, reconciliation

### Goal
Publish exactly one independently processed YouTube video by consuming the matching single-use TASK-043 approval through a narrow record-before-effect capability, then independently retrieve the resulting metadata and visibility without replaying an uncertain write or broadening authority.

### Scope
- Define a narrow `youtube.publish` effect that consumes one valid unexpired TASK-043 grant bound to the exact video, artifact, intent, channel, metadata, target visibility, profile, revision, nonce, and policy state.
- Persist a durable publication-attempt record and consume the approval atomically before the provider mutation.
- Perform only the exact approved visibility/metadata transition through the minimum official authenticated YouTube endpoint and scope.
- Independently retrieve the exact video after the mutation and verify channel, metadata, visibility, identity, and current revision.
- Route timeout, disconnect, crash, ambiguous response, duplicate invocation, or inconsistent retrieval through TASK-022 reconciliation; never blindly replay.
- Preserve TASK-023/TASK-035 budgets, TASK-037 retry rules, TASK-038 disclosure, cancellation semantics, redacted audit evidence, and credential isolation.
- Do not add upload/re-upload, deletion, arbitrary metadata editing, scheduling, playlists, comments, thumbnails, generic HTTP, or generic YouTube API authority.

### Dependencies
- TASK-018/TASK-020/TASK-021/TASK-022/TASK-023: immutable identity, revision invalidation, durable waits, reconciliation, and budgets.
- TASK-032/TASK-035/TASK-037/TASK-038: bounded provider lifecycle, spending, retry, and disclosure controls.
- TASK-039 through TASK-043: authorization profile, immutable intent, staged upload, independent processing evidence, and exact single-use publication approval.
- Current official YouTube mutation/retrieval requirements verified during implementation.

### Plan
- Specify versioned publication-attempt, uncertain-effect, reconciliation, and verified-terminal states.
- Canonicalize and revalidate the complete TASK-043 binding immediately before atomic approval consumption and attempt persistence.
- Implement the minimal YouTube-specific mutation transport with strict request/response bounds and safe error mapping.
- Persist effect identity before transmission; after any response or interruption, independently look up the exact video rather than trusting the mutation response.
- Reconcile ambiguous outcomes by exact video/channel/metadata/visibility identity and prohibit automatic duplicate writes.
- Add deterministic crash-boundary, replay, substitution, provider-failure, and Android confirmation/receipt tests.

### Acceptance
- Publication is impossible without one valid, unconsumed TASK-043 grant for a TASK-042 processing-succeeded video.
- Attempt identity is durably recorded and approval consumed before the first provider-side mutation byte can be authorized.
- The provider request cannot differ from approved video, channel, title, description, visibility, profile, workflow/policy revision, nonce, or expiry.
- Success requires independent post-effect retrieval confirming the exact final metadata and visibility; provider response or planner text alone cannot satisfy verification.
- Timeout, disconnect, crash, malformed/oversized response, auth/quota failure, stale revision, cancellation, duplicate invocation, and uncertain outcome settle explicitly without blind replay.
- Restart preserves attempt/approval/effect identity, consumed budgets, and uncertain state without resetting authority.
- The capability grants no upload, deletion, arbitrary mutation, generic authenticated HTTP, or reusable publication authority.
- Secrets remain absent from planner payloads, durable workflow state, audit, logs, IPC, and exported settings.

### Verification
- State-machine serialization and record-before-effect ordering tests.
- Exact-field approval/request substitution matrix and stale-revision/expiry/cancellation negatives.
- Crash matrix before persistence, after persistence/before send, during send, after provider receipt, and before/after independent lookup.
- Duplicate call, consumed approval, already-published, changed metadata/visibility, missing video, and cross-channel/profile negatives.
- Auth, quota, 429/5xx, timeout, disconnect, Retry-After, malformed/oversized response, and unknown provider-state bounds.
- Independent retrieval fixtures proving response-only success is rejected and exact retrieved state is required.
- Secret scans, canonical verification, Android matrix, whole-diff architecture/security/privacy review.
- Live publication remains UNVERIFIED unless separately owner-authorized for the exact payload and executed.

### Expected result
LAIN_OS can perform one exact approved YouTube publication as a crash-safe, non-replayable external effect and can report success only from independent retrieval of the precise final video state.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.6 requires publication only after exact approval, attempt persistence before the side effect, and independent lookup afterward.
- TASK-043 covers approval but no existing task or open PR represents the actual bounded publication mutation and post-effect verification.

### Projection basis
- R6.7 duplicate/uncertain upload reconciliation and the Phase-6 exit gate require a concrete publication effect identity and independently retrieved terminal state.
- A narrow publication capability prevents later acceptance work from bypassing approval, record-before-effect ordering, or reconciliation.

### Risks / unknowns
- Current YouTube endpoint semantics, minimum scopes, quota cost, metadata update behavior, and partial-failure response semantics may change and require official-document verification.
- A visibility/metadata mutation can succeed despite transport failure; all ambiguous post-send outcomes must remain reconciliation-required until independent lookup settles them.
- Provider normalization of titles/descriptions must not weaken exact approval binding or fabricate mismatch/success.
- Live owner publication is consequential and remains separately authorized; deterministic fixtures cannot be promoted to live evidence.

## TASK-043: Require exact YouTube publication approval
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, approval, publication, authorization, security

### Goal
Create a single-use, expiring approval boundary that authorizes publication only for the exact independently processed YouTube video and immutable upload intent selected by the owner, without allowing changed metadata, visibility, destination, artifact, or policy state to inherit earlier consent.

### Scope
- Define a narrow publication-approval record bound to the exact provider video ID, TASK-040 intent, artifact hash, destination channel, title, description, target visibility, authorization profile, workflow/policy revision, nonce, and expiry.
- Present the complete publication payload and current independently verified TASK-042 processing evidence for explicit owner confirmation.
- Issue approval only after the video is processing-succeeded and all bound identities match current trusted state.
- Consume approval atomically at the later publication attempt; reject reuse, expiry, cancellation, revision drift, metadata changes, destination/profile substitution, or visibility broadening.
- Persist request, grant/denial, consumption, invalidation, and redacted audit evidence without credentials or generic authenticated API authority.
- Do not implement the publication side effect, upload/re-upload, deletion, metadata mutation, scheduling, or generic YouTube access.

### Dependencies
- TASK-018, TASK-020, TASK-022, and TASK-023: immutable artifact identity, revision invalidation, effect reconciliation, and aggregate budgets.
- TASK-038 through TASK-042: disclosure, YouTube identity, immutable intent, staged upload, and independent processing verification.
- Current repository approval/policy/audit primitives; extend shared interfaces only where the exact binding cannot be represented safely.

### Plan
- Specify the versioned approval-request/grant/consumption state model and canonical payload digest.
- Build the trusted UI boundary that displays every approval-bound field and authoritative processing evidence.
- Generate a cryptographically strong nonce, bounded expiry, and single-use durable grant tied to the current workflow/policy revision.
- Validate the entire binding immediately before grant and again before consumption; fail closed on any drift.
- Integrate cancellation, restart, stale-revision invalidation, duplicate-submit protection, and redacted audit receipts.
- Add deterministic state-machine, lifecycle, substitution, replay, and Android UI tests.

### Acceptance
- Publication cannot be authorized until TASK-042 independently records processing-succeeded for the exact private/unlisted video.
- The confirmation surface shows exact video ID, artifact hash, channel, title, description, current and target visibility, profile, revision, nonce context, and expiry.
- A vague or earlier instruction such as “post it” cannot authorize a later or changed payload.
- Any bound-field change, stale revision, expired nonce, cancellation, denial, duplicate consumption, missing processing evidence, or mismatched identity yields explicit non-authorization.
- One grant can authorize at most one exact future publication attempt and grants no upload, deletion, arbitrary metadata mutation, or generic HTTP authority.
- Restart preserves valid unexpired grants and consumed/invalidated state without resetting expiry or enabling replay.
- Secrets remain absent from approval payloads, durable workflow state, audit, logs, IPC, and exported settings.

### Verification
- Canonical payload serialization/digest and round-trip tests.
- Exact-field substitution matrix covering video, artifact, intent, channel, title, description, visibility, profile, workflow, policy revision, nonce, and expiry.
- Grant/deny/cancel/expire/consume/replay/restart/crash-boundary state-machine tests.
- Missing, pending, failed, stale, or regressed TASK-042 processing evidence negatives.
- Concurrent duplicate approval/consumption and stale UI submission tests.
- Android UI assertions that the complete exact payload is visible before confirmation and changed state forces re-approval.
- Secret-leak scans, canonical verification, Android matrix, and architecture/security/privacy review.
- Live publication remains UNVERIFIED and out of scope unless separately authorized and executed.

### Expected result
LAIN_OS can obtain one durable, auditable, single-use approval for one exact processed video publication payload, ensuring later publication code cannot reuse vague consent or silently change what, where, or how it publishes.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.5 explicitly binds approval to video ID, artifact hash, destination channel, title, description, visibility, policy revision, nonce, and expiry.
- The roadmap states that a generic earlier phrase such as “post it” does not authorize a later changed payload.
- TASK-039 through TASK-042 represent authorization, intent, staging upload, and processing verification; no current TaskPlanner task or open PR represents exact publication approval.

### Projection basis
- R6.6 publication must consume explicit exact authority; defining this boundary first prevents publication transport from inventing weaker consent semantics.
- Durable single-use approval also supplies the identity required by R6.7 uncertain-write and duplicate-publication reconciliation.

### Risks / unknowns
- Existing approval records may not bind all metadata, expiry, nonce, revision, and provider identity fields; any extension must remain capability-agnostic where practical.
- Title/description normalization and Unicode rendering must be identical between displayed, hashed, and executed payloads.
- Clock rollback, process death, concurrent UI actions, and delayed provider state changes must not extend or replay authority.
- Live owner-account approval and publication evidence remain separate from deterministic fixtures.

## TASK-042: Verify YouTube processing status independently
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, processing, verification, polling, provenance

### Goal
Independently retrieve and durably verify the processing state of the exact private/unlisted YouTube upload produced by TASK-041 so LAIN_OS records the authoritative video identity, processing outcome, and failure reason without treating upload completion or planner/provider assertions as publication success.

### Scope
- Define a narrow `youtube.status` read capability bound to one TASK-040 upload intent, TASK-041 upload attempt/session, provider video ID, authorization profile, destination channel, artifact hash, and workflow revision.
- Query only the official authenticated YouTube status/metadata endpoint required to determine processing state, visibility, identity, and provider failure reason.
- Normalize provider states into explicit pending, processing, succeeded, failed, unavailable, cancelled/stale, and reconciliation-required trusted states.
- Persist independently retrieved observations, timestamps, attempt counters, deadlines, and terminal evidence without storing credentials or sensitive response bodies.
- Poll through TASK-021 durable waits under TASK-023/TASK-035 budgets and TASK-037 retry rules; restart must preserve deadlines, counters, and identity.
- Treat missing/mismatched video, channel, visibility, intent, artifact, profile, or revision as non-success and route ambiguous identity/effect outcomes through TASK-022 reconciliation.
- Do not add upload, re-upload, deletion, metadata mutation, thumbnails, playlists, scheduling, exact publication approval, visibility promotion, or publish behavior.

### Dependencies
- TASK-018, TASK-020, TASK-021, TASK-022, and TASK-023: immutable artifact identity, revision invalidation, durable waits, reconciliation, and aggregate budgets.
- TASK-032, TASK-035, TASK-037, and TASK-038: bounded provider lifecycle, spending controls, retry semantics, and data-sharing disclosure.
- TASK-039, TASK-040, and TASK-041: YouTube authorization, exact upload intent, and resumable private/unlisted upload with stable provider video identity.
- Current official YouTube processing-status and video-resource requirements verified during implementation.

### Plan
- Specify the minimal versioned processing-observation/status state machine and provider-to-trusted-state mapping.
- Bind every lookup and persisted observation to the exact upload operation, video ID, artifact hash, authorization profile/channel, visibility, and workflow revision.
- Implement a bounded YouTube-specific authenticated read transport with strict response validation and safe error mapping.
- Integrate durable polling intervals, deadlines, call/cost ceilings, retry eligibility, cancellation, restart, and stale-revision checks.
- Persist authoritative terminal evidence and provider failure detail in redacted normalized form.
- Add deterministic fake-provider polling/restart matrices and Android integration coverage; keep live owner-account evidence separately labeled.

### Acceptance
- A valid TASK-041 private/unlisted upload can be looked up independently and yields one durable normalized processing state tied to the exact video and intent.
- Pending/processing never becomes COMPLETE or published; only an independently retrieved provider terminal success becomes processing-succeeded.
- Provider failure reason is preserved safely and explicitly without leaking credentials or sensitive raw response bodies.
- Video ID, channel, artifact, intent, profile, visibility, operation, or workflow-revision substitution fails closed.
- Missing/deleted/inaccessible video, auth failure, quota/rate-limit, malformed/oversized response, timeout, cancellation, deadline exhaustion, stale revision, and uncertain identity settle explicitly.
- Restart preserves video identity, last authoritative observation, next poll deadline, attempts, and consumed budgets without resetting authority.
- The capability grants no upload, mutation, approval, publication, generic HTTP, or visibility-promotion authority.
- No planner/provider claim alone can mark processing verified or published.

### Verification
- Provider-state normalization and deterministic serialization/round-trip tests.
- Durable poll/wait/restart/deadline/cancellation matrix with monotonic attempt and budget accounting.
- Video/intent/artifact/profile/channel/visibility/operation/revision substitution negatives.
- Pending-to-terminal, terminal-regression, missing/deleted/private-inaccessible, and failure-reason fixtures.
- Auth, quota, 429/5xx, timeout, malformed/oversized payload, unknown-state, and Retry-After bound tests.
- Assertions that upload completion, planner text, cached data, or stale observations cannot satisfy verification.
- Secret/raw-response leakage scans, canonical verification, Android matrix, and architecture/security/privacy review.
- Live owner-authorized processing lookup remains UNVERIFIED unless separately executed against the exact private/unlisted upload.

### Expected result
LAIN_OS can truthfully distinguish uploaded, still processing, processed, and failed YouTube videos through durable independent evidence, providing the exact stable video identity required for later publication approval without publishing or mutating anything.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.4 explicitly requires independent authenticated lookup of processing state, video ID, and failure reason, and forbids a published claim while processing is incomplete.
- TASK-039 through TASK-041 cover authorization, exact intent, and resumable staging upload; no current task, open issue, or open PR represents independent processing-status verification.

### Projection basis
- R6.5 exact publication approval must bind a provider video whose processing readiness and identity were independently retrieved rather than inferred from upload transport completion.
- A durable read-only status boundary prevents later publication code from inventing its own polling, retry, identity, or truth semantics.

### Risks / unknowns
- YouTube processing/status fields, required scopes, quota cost, eventual-consistency behavior, and failure-detail availability can change and must be checked against current official documentation.
- Private/unlisted visibility and Brand Account channel identity must remain explicit and independently matched.
- Provider terminal-state regressions or missing resources after prior observation must fail closed and may require reconciliation.
- Live verification requires owner-controlled authorization, quota, and an exact private/unlisted upload and must remain distinct from deterministic fixtures.

## TASK-041: Implement resumable private YouTube upload
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, upload, resumable, reconciliation, privacy

### Goal
Upload the exact current TASK-040 intent artifact through one bounded YouTube-specific resumable transport into policy-selected private or unlisted staging, with durable progress, truthful cancellation, and crash-safe recovery that never invents success or blindly duplicates an uncertain upload.

### Scope
- Define a narrow `youtube.upload` execution boundary that consumes one immutable current-revision TASK-040 upload intent and one explicit TASK-039 authorization profile/channel.
- Start uploads as private or unlisted staging only, according to trusted local policy; never publish or broaden visibility.
- Persist resumable-session identity, acknowledged byte range, attempt state, deadlines, counters, and provider video identity as each becomes authoritative.
- Stream only the bound immutable artifact with bounded chunks, response sizes, elapsed time, bytes, calls, retries, and cost.
- Resume known-safe acknowledged progress after restart; route ambiguous post-write outcomes to TASK-022 reconciliation instead of restarting from byte zero.
- Make cancellation stop future local work and report remote uncertainty truthfully; never claim provider rollback.
- Expose safe progress/error state without tokens, upload-session URLs, credentials, or sensitive provider response bodies.
- Do not add processing-status verification, thumbnails, playlists, scheduling, exact publication approval, visibility promotion, or publish behavior.

### Dependencies
- TASK-018, TASK-020, TASK-021, TASK-022, and TASK-023: immutable artifacts, revision invalidation, durable waits, external-effect reconciliation, and aggregate budgets.
- TASK-032, TASK-035, TASK-037, and TASK-038: bounded provider jobs, spending controls, retry semantics, and data-sharing disclosure.
- TASK-039: explicit identity-bound YouTube authorization profile/channel.
- TASK-040: immutable exact upload-intent record and operation identity.
- Current official YouTube resumable-upload requirements verified during implementation.

### Plan
- Specify the minimal versioned upload-attempt/resumable-session state machine and invariants.
- Bind every attempt to the exact intent, artifact hash/size, workflow revision, authorization profile, destination channel, staging visibility, and operation identity.
- Implement a YouTube-specific bounded transport for session creation, chunk upload, status query, cancellation, and reconciliation handoff.
- Persist record-before-effect state and acknowledged progress atomically around every external write boundary.
- Integrate trusted budgets, privacy disclosure, retry eligibility, deadlines, and cancellation without generic authenticated HTTP.
- Add deterministic fake-provider crash/restart matrices plus bounded Android integration coverage; keep live owner-account upload evidence separately labeled.

### Acceptance
- One valid current TASK-040 intent can create one durable resumable upload attempt and transfer only its exact immutable artifact.
- Initial visibility is policy-selected private or unlisted staging and cannot become public in this task.
- Restart resumes only provider-acknowledged progress under the same operation/session identity; uncertain writes enter reconciliation and are never blindly replayed.
- Artifact, intent, workflow revision, authorization profile, destination channel, visibility, or operation substitution fails closed.
- Progress, cancellation, timeout, retry exhaustion, quota/rate-limit, provider rejection, and reconciliation-required states are explicit and durable.
- Cancellation stops future chunks but does not claim that already accepted bytes or a remote video were rolled back.
- No token, credential, upload-session URL, or sensitive provider body enters planner context, workflow exports, audit, logs, IPC, crash diagnostics, or UI.
- The capability grants no generic HTTP, processing verification, approval, publication, scheduling, playlist, or visibility-promotion authority.

### Verification
- Versioned state-machine, deterministic serialization, and record-before-effect ordering tests.
- Chunk-boundary, partial-acknowledgement, restart, duplicate-operation, stale-revision, and uncertain-write reconciliation matrix.
- Artifact hash/size, intent/profile/channel/visibility/session substitution negatives.
- Cancellation before session, between chunks, after provider acceptance, and during restart recovery.
- Deadline, bytes, calls, retries, cost, 429/5xx/auth/quota/malformed/oversized response, and Retry-After bound tests.
- Private/unlisted-only policy tests and assertions that no public visibility mutation exists.
- Secret/upload-session leakage scans, canonical verification, Android matrix, and architecture/security/privacy review.
- Live owner-authorized private upload remains UNVERIFIED unless separately executed with exact artifact and channel evidence.

### Expected result
LAIN_OS can durably stage the exact intended video as a bounded private or unlisted resumable upload, recover safely after interruption, and hand processing verification a stable video identity without leaking credentials, fabricating success, publishing, or duplicating uncertain external writes.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.3 explicitly requires private/unlisted staging, progress, cancellation semantics, resumable state, and crash recovery.
- TASK-039 covers authorization and TASK-040 covers record-before-effect intent; no current task, open issue, or open PR represents resumable upload execution.

### Projection basis
- R6.4 processing verification and R6.5 exact publication approval require a stable provider video identity created from the exact immutable intent.
- Building resumability atop TASK-022/TASK-037 before publication prevents network/crash ambiguity from creating duplicate uploads or hidden retry authority.

### Risks / unknowns
- YouTube resumable-upload protocol, quota accounting, required scopes, chunk rules, and session-expiry behavior can change and must be checked against current official documentation.
- Provider acknowledgement may be ambiguous after connection loss; unresolved writes must remain in reconciliation.
- Cancellation cannot guarantee deletion of provider-accepted bytes or a created private video.
- Live verification requires owner-controlled authorization, quota, and channel access and must remain distinct from deterministic fixtures.

## TASK-040: Persist exact YouTube upload intent records
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, upload, intent, idempotency, provenance

### Goal
Persist one immutable, revision-bound upload intent before any YouTube upload so later execution, reconciliation, approval, status polling, and publication refer to the exact same artifact, destination, metadata, and operation identity.

### Scope
- Define a strict YouTube upload-intent record containing artifact hash, destination channel identity, title, description, intended visibility, workflow revision, and stable operation/idempotency identity.
- Validate the referenced artifact through the immutable artifact workspace and bind the destination to the explicit TASK-039 authorization profile/channel.
- Persist the record before any upload transport call and preserve immutable historical revisions.
- Make intent replacement an explicit new revision with a new operation identity; never mutate an attempted intent in place.
- Expose safe provenance/status fields to workflow and UI surfaces without exposing credentials or granting upload/publication authority.
- Do not add upload HTTP, resumable transfer, status polling, thumbnail, playlist, scheduling, approval, or publication behavior.

### Dependencies
- TASK-016, TASK-018, and TASK-020: durable workflow state, immutable artifacts, and revision invalidation.
- TASK-022 and TASK-023: external-effect reconciliation and aggregate budgets.
- TASK-039: explicit identity-bound YouTube authorization profile.
- The current Phase-6 upload-intent contract in `docs/ROADMAP_1.0.md` R6.2.

### Plan
- Specify the minimal versioned upload-intent schema and invariants.
- Bind artifact hash/type/revision and authorized channel identity at intent creation.
- Allocate a stable operation identity suitable for later resumable-upload reconciliation without starting an external effect.
- Persist atomically before transport and keep attempted records immutable.
- Add deterministic creation, restart, revision, replacement, and serialization tests.
- Add adversarial tests for stale artifacts, channel/profile substitution, malformed metadata, duplicate identity, and secret leakage.

### Acceptance
- A valid current-revision video artifact can produce one durable upload-intent record with exact hash, channel, title, description, visibility, workflow revision, and operation identity.
- The record exists durably before any future upload call can begin.
- Artifact, workflow revision, authorization profile, and destination channel substitutions fail closed.
- Changing metadata, artifact, destination, or visibility creates a new revision/identity and cannot rewrite an attempted record.
- The record grants no upload, approval, publish, generic HTTP, or credential access authority.
- Credentials and raw provider tokens remain absent from durable state, audit, logs, IPC, crash diagnostics, and exports.

### Verification
- Schema/round-trip and deterministic serialization tests.
- Atomic persistence and crash/restart boundary tests proving record-before-effect ordering.
- Artifact hash/type/revision and authorization-profile/channel binding negatives.
- Immutable attempted-record and explicit replacement/revision tests.
- Duplicate operation/idempotency identity and reconciliation handoff tests.
- Metadata size/Unicode/visibility validation plus secret-surface scans.
- Canonical verification and architecture/security/privacy review.

### Expected result
Every future YouTube upload begins from one durable, inspectable, exact intent whose identity can be reconciled safely without conflating authorization, upload execution, or publication approval.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.2 explicitly requires persisting artifact hash, destination channel, title, description, and intended visibility before upload.
- TASK-039 covers only R6.1 authorization; no current task, open issue, or open PR represents the R6.2 durable upload-intent boundary.

### Projection basis
- Later resumable upload, status polling, exact-artifact approval, and uncertain-write reconciliation require a stable record-before-effect identity.
- Defining this boundary before transport prevents provider calls from inventing mutable or incompatible upload state.

### Risks / unknowns
- YouTube metadata and visibility validation rules can change and must be rechecked against official documentation during implementation.
- Brand Account/channel selection must remain explicit; authorization identity must not be guessed.
- The idempotency identity is internal reconciliation state and must not imply that YouTube guarantees duplicate suppression.

## TASK-039: Implement bounded YouTube OAuth account authorization
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, oauth, credentials, android

### Goal
Authorize one explicit YouTube account/channel through the current official OAuth flow using minimum scopes, Keystore-backed token protection, truthful identity display, and revocation without creating generic authenticated HTTP authority.

### Scope
- Define a narrowly scoped YouTube authorization profile and lifecycle for authorize, refresh where officially supported, inspect identity, revoke, and remove.
- Request only scopes required by the future bounded upload/status/publish capabilities.
- Protect refresh/access tokens through the existing Android Keystore secret boundary; durable Python/workflow state stores opaque references only.
- Display the authorized Google account/channel identity independently retrieved from the provider.
- Keep authorization distinct from upload or publication approval.
- Fail closed on state/nonce mismatch, redirect mismatch, expired/revoked credentials, missing browser/provider support, or profile substitution.
- Do not add generic OAuth, arbitrary authenticated HTTP, upload, publish, or implicit account switching.

### Dependencies
- TASK-008 complete: stable installed planner/runtime acceptance.
- TASK-018 and TASK-022 complete: artifact identity and external-effect reconciliation foundations.
- TASK-032, TASK-035, TASK-037, and TASK-038 complete: provider lifecycle, spending/retry bounds, and privacy disclosure.
- Current official YouTube/Google authorization requirements verified at implementation time.

### Plan
- Specify the minimal trusted OAuth state, PKCE/redirect, scope, token-reference, and channel-identity contracts.
- Implement Android authorization entry/return handling with exact state binding and no token exposure to planners or Python durable state.
- Exchange and refresh through a bounded YouTube-specific transport.
- Retrieve and display account/channel identity with explicit profile binding.
- Implement revoke/remove and fail-closed restart/expiry/error behavior.
- Add deterministic fake-provider tests plus Android lifecycle/redirect coverage and live authorization evidence only when owner credentials are available.

### Acceptance
- The installed app can authorize one explicit YouTube account using the official supported flow and minimum required scopes.
- State, PKCE, redirect, profile, and account/channel identity remain bound across lifecycle/restart.
- Raw access/refresh tokens are absent from planner payloads, workflow state, audit, logs, IPC responses, crash diagnostics, and exported settings.
- Revocation/removal prevents future authenticated calls and clears only the intended credential reference.
- Authorization grants no upload, publication, generic HTTP, or account-switching authority.
- Unsupported, cancelled, mismatched, expired, or revoked flows settle explicitly without fabricated success.

### Verification
- Deterministic OAuth state/PKCE/redirect/profile-binding tests.
- Scope-minimization and no-generic-transport assertions.
- Restart, cancellation, expiry, refresh, revocation, account-switch, and replay negatives.
- Secret-leak scans across durable, returned, audit, log, IPC, crash, and export surfaces.
- Android API/lifecycle/deep-link matrix and canonical verification.
- Architecture/security/privacy review; live owner-account authorization remains separately labeled when unavailable.

### Expected result
LAIN_OS can hold one explicit, revocable, identity-verified YouTube authorization profile that later bounded upload capabilities can consume without exposing credentials or granting publication authority.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.1 requires the current official authorization flow, minimum scopes, account/channel identity display, Keystore-backed token protection, and revocation/removal.
- TaskPlanner now covers Phase 5 through R5.6 as TASK-032 through TASK-038; no existing task, issue, or open PR represents R6.1.

### Projection basis
- Every later Phase-6 upload/status/publish task depends on a stable, secret-safe, identity-bound authorization primitive.
- Separating authorization from upload and publication prevents credentials from becoming implicit side-effect authority.

### Risks / unknowns
- Google OAuth policies, redirect mechanisms, verification requirements, and minimum scopes can change and must be checked against official documentation during implementation.
- Live acceptance requires owner-controlled Google/YouTube credentials and must not be inferred from deterministic fixtures.
- Account and Brand Account channel identity semantics may require explicit selection rather than guessing.

## CURRENT EXECUTION GATE — TASK-008 Phase-1 planner acceptance
**State:** IN PROGRESS | **PR:** #16 | **Candidate head:** `1a894fb4ac7936549362e857bdb92f21e28251b9`

- Physical Galaxy run of earlier candidate proved real Groq planning but exposed: Android hardlink `EACCES` on Create demo file; follow-up HTTP 429 TPM exhaustion; action/session-status divergence; Share COMPLETE without observed chooser is not accepted.
- Filesystem TDD: RED `74c43e7e763e40d32189dcbe17d75057bd410aaf` reproduced Android hardlink `EACCES`; GREEN adds that errno to the existing exclusive-copy fallback.
- Planner-token TDD: RED `cdb7b9154da3780225b85dadfb5f00eab1566108` pinned duplicated capability catalog, 4096 completion ceiling, and missing Groq GPT-OSS low reasoning. Compatibility RED `c337b827ab875a8d9c16c4d22b4aefe822235a73` preserved JSON-object capability visibility.
- Final candidate `1a894fb4ac7936549362e857bdb92f21e28251b9`: JSON-Schema prompts no longer duplicate the capability catalog; JSON-object prompts retain it; completion ceiling is 1024; `api.groq.com` GPT-OSS uses `reasoning_effort=low`.
- Verify #297: **GREEN**. Android #286 API 24/35: **GREEN**, including instrumentation.
- Exact API-35 installable APK SHA-256: `d6474f768ee15ba2a1418d92a48d13f9647ed86cb85c102ae3b96e50d3f92102`.
- Physical acceptance remains open. Retest the six Workbench flows on this exact APK; require expected capability evidence, truthful session terminal state, exact approval for Share, and physically observed chooser for Share. Stop cancellation evidence remains accepted only when a real in-flight call yields truthful cancellation.

## TASK-038: Implement explicit provider privacy disclosure and data-sharing state
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, privacy, disclosure

### Goal
Make every off-device provider transfer explicit and profile-specific so users can see what text, audio, or images will leave the device and Local selection can never silently become Cloud.

### Scope
- Define a typed data-sharing state per planner, speech, and media provider profile.
- Show provider identity and the exact payload classes that may leave the device before first remote use and in active workflow status.
- Bind disclosure state to the selected profile and workflow revision.
- Require renewed acknowledgement when provider identity or disclosed data classes materially change.
- Preserve Local-only behavior without automatic Cloud fallback.
- Keep credentials and payload contents out of disclosure/audit surfaces; disclose categories and destination identity, not secrets.

### Dependencies
- TASK-032 provider-job abstraction.
- TASK-033 external speech adapter.
- TASK-034 external image/media adapter.
- TASK-035 provider spending controls.
- TASK-037 retry/backoff policy.

### Plan
- Define minimal provider identity, payload-class, destination, and acknowledgement models.
- Integrate disclosure checks at remote submit boundaries shared by planner/speech/media paths.
- Add UI/status presentation for configured and active provider sharing state.
- Persist acknowledgement provenance without persisting sensitive payloads.
- Add provider/profile/revision change and Local-to-Cloud fallback negatives.
- Verify restart, retry, reconciliation, and cancellation preserve the same disclosure authority.

### Acceptance
- Before remote submission, the user can see which provider receives text, audio, images, or generated artifacts.
- Disclosure and acknowledgement are bound to the exact provider profile, destination, payload classes, and workflow revision.
- Provider/profile/data-class changes invalidate stale acknowledgement.
- Local selection never becomes Cloud automatically.
- Retry, restart, or reconciliation cannot bypass disclosure.
- Raw credentials and payload contents remain absent from disclosure, audit, IPC, and exported settings.

### Verification
- Provider/payload-class disclosure matrix for planner, STT/TTS, and media generation.
- Profile/destination/revision-change invalidation tests.
- Local-only and no-implicit-fallback adversarial tests.
- Restart/retry/reconciliation/cancellation tests.
- Secret and payload-content leak scans.
- Installed Android UI/status acceptance plus canonical verification and architecture/security/privacy review.

### Expected result
Remote providers remain replaceable and bounded while users have durable, truthful control over which data categories leave the device and where they go.

### Evidence basis
- `docs/ROADMAP_1.0.md` R5.6 explicitly requires disclosure when text/audio/images leave the device, profile-specific sharing state, and no automatic Local-to-Cloud transition.
- Current TaskPlanner represents R5.1 through R5.5 as TASK-032 through TASK-037; no existing task, open issue, or open PR represents R5.6.

### Projection basis
- This is the final missing Phase-5 work package and is required before the Phase-5 exit gate can truthfully claim bounded remote-provider use without privacy-semantic drift.
- A shared disclosure boundary prevents planner, speech, and media adapters from implementing incompatible consent semantics.

### Risks / unknowns
- Provider routing may involve regional or proxy endpoints; unsupported destination ambiguity must remain explicit.
- Disclosures must be clear without persisting sensitive payload content.
- Acknowledgement lifetime must avoid both click fatigue and stale authority reuse.

## TASK-037: Implement bounded provider retry and backoff policy
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, retry, backoff, reconciliation
**Updated:** 2026-10-03

### Goal
Implement one durable retry/backoff policy for provider operations that automatically retries only known-safe idempotent/read work, routes uncertain writes through reconciliation, and bounds 429/5xx recovery without resetting workflow authority or budgets.

### Scope
- Classify provider operations as safe-idempotent/read, idempotency-keyed write, or uncertain external write.
- Allow automatic retry only where operation semantics and durable identity make replay safe.
- Route ambiguous writes to existing TASK-022/TASK-032 reconciliation instead of blind replay.
- Add bounded exponential/jitter-capable backoff state persisted with durable provider jobs.
- Count retries, provider calls, elapsed time, and applicable cost against TASK-023/TASK-035 budgets.
- Respect Retry-After only as a bounded scheduling hint, never as authority to exceed deadlines/budgets.
- Surface exhausted, non-retryable, reconciliation-required, and cancelled states explicitly.

### Dependencies
- TASK-022 external-effect reconciliation.
- TASK-023 aggregate workflow budgets.
- TASK-032 provider-job abstraction.
- TASK-035 provider spending controls.

### Plan
- Define typed retry eligibility and durable attempt/backoff state.
- Implement deterministic clock-driven policy with bounded caps.
- Integrate 429/5xx/transport failures without retrying auth/schema/validation failures.
- Bind retries to stable operation identity and persisted deadlines.
- Add crash/restart and uncertain-write reconciliation tests.
- Add budget/cost/call-count intersection tests.

### Acceptance
- Only explicitly safe/idempotent operations auto-retry.
- Uncertain external writes never replay automatically.
- Restart preserves attempt count, backoff deadline, operation identity, and consumed budgets.
- Retry-After/provider hints cannot extend trusted ceilings.
- Cancellation stops future retries truthfully.
- 429/5xx recovery is bounded and deterministic under test clocks.

### Verification
- Retry eligibility matrix.
- Deterministic backoff progression/cap tests.
- 429/5xx/timeout/network/auth/schema failure classification tests.
- Restart/reconciliation/duplicate-operation tests.
- Budget/deadline/cost/call-count exhaustion tests.
- Cancellation and stale-revision negatives.
- Canonical verification plus architecture/security review.

### Expected result
Provider recovery becomes durable and bounded without replaying uncertain writes or allowing retries to bypass time, action, call, byte, or spending authority.

### Evidence basis
- `docs/ROADMAP_1.0.md` R5.5 requires retry only for known-safe idempotent/read operations, reconciliation/idempotency for external writes, and bounded 429/5xx backoff.
- Current TaskPlanner represents R5.1–R5.4 as TASK-032 through TASK-035 and has no R5.5 task/open PR.

### Projection basis
- Phase-5 exit requires reliable remote-provider failure handling after provider jobs and budgets exist.
- Central retry semantics prevent each adapter from inventing incompatible replay rules.

### Risks / unknowns
- Provider idempotency guarantees differ and must be explicit per adapter.
- Some providers expose ambiguous transient failures after accepting work; those must reconcile rather than retry.
- Jitter must remain testable and bounded.

## TASK-008: Close Phase-1 planner acceptance and Galaxy real-planner gate — CURRENT
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-1, planner, android, galaxy, acceptance, groq
**Updated:** 2026-10-03

### Current evidence

- PR #16 head `c700eed0b9de6964729a6b038eaa565daf44864d` is CI GREEN: Verify #270 and Android #259 completed successfully, including the Android API 24/35 matrix.
- The debug APK built from that exact GREEN head was installed on the physical Galaxy reference device.
- Offline Demo physical smoke test reached terminal **COMPLETE** for **Show battery**, proving the installed embedded runtime and native battery capability path on the device.
- A real Cloud profile was created and selected for Groq at `https://api.groq.com/openai/v1` using model `openai/gpt-oss-120b`; the app displayed that profile as active.
- Real Groq traffic reaches the provider, but Phase-1 acceptance is blocked by HTTP 400 request-shape failures. Provider logs identify the main failure as an invalid generated JSON schema for a zero-argument capability: `arguments` contains `required` while the provider validator sees no `properties`. Separate diagnostic requests fail JSON validation after the current connection probe allows only one completion token.
- The current UI also collapses useful provider failures into `unavailable` and labels the planner action button `Run locally`, which is misleading when a Cloud planner is selected.
- Stop/cancellation is implemented but has not yet been proven against a deliberately in-flight real/cloud-style planner call; fast HTTP 400 failures are not evidence that Stop is broken or correct.

### Immediate patch scope

1. Fix the provider-neutral response-schema builder for zero-argument capabilities without weakening validation for argument-bearing capabilities.
2. Replace the one-token structured-output connection diagnostic with a bounded probe that can truthfully establish endpoint/auth/model reachability.
3. Map safe provider/transport failure classes such as request rejected, rate limited, server error, timeout, TLS, auth, and model unavailable instead of collapsing them into `unavailable`.
4. Replace the misleading `Run locally` label with neutral task-execution copy.
5. Add a blocking-planner cancellation regression and change Stop implementation only if that test reproduces a real defect.
6. Build a new debug APK only from the exact post-patch GREEN head and rerun physical Galaxy acceptance from that artifact.

### Phase-1 physical acceptance gate

The battery smoke test is **not sufficient**. On the physical Galaxy, the current installed Workbench preset suite must be exercised as fresh sessions through the intended real selected planner and each applicable flow must reach terminal **COMPLETE**:

- **Create demo file**
- **Show battery**
- **Show demo toast**
- **Vibrate briefly**
- **Copy demo text**
- **Share demo text** — must pause for exact approval, then complete after approval; opening the chooser is not permission to send.

For every preset, record the planner/session identity, terminal status, action execution status, verification status, and any truthful LIMITED semantics. In addition:

- **Test connection** must return the correct bounded status for the configured real planner and must not false-negative because of its own probe format.
- Cloud/Local installed GUI/runtime automated acceptance must remain GREEN; any live physical Local endpoint requirement that cannot be exercised must remain explicitly UNVERIFIED rather than inferred from Cloud or CI evidence.
- A deliberately in-flight planner request must prove **Stop task** cancellation end to end and settle to a truthful terminal state.
- Approval, rotation/rebind/background recovery, stale approval rejection, secret-surface isolation, and no silent Local-to-Cloud fallback remain required Phase-1/preview evidence.

### Exit condition

TASK-008 closes only after the bounded fixes are GREEN, the exact GREEN APK is installed, the full applicable Workbench preset matrix reaches terminal **COMPLETE** on the Galaxy through the real selected planner, cancellation is proven on an actually in-flight planner call, and all remaining evidence gaps are explicitly reconciled. Then TASK-009 may begin.

## TASK-036: Close Voice-First LAIN Preview physical Galaxy acceptance
**Priority:** P1 | **Tags:** overseer-assigned, developer, preview, physical-device, galaxy, acceptance
**Updated:** 2026-10-03

### Goal

After Phase 1 and Phase 2 are complete, prove the near-term Voice-First LAIN Preview on the declared Galaxy reference device with current-build physical evidence distinct from CI/emulator evidence.

### Scope

- Install the current candidate APK on the reference Galaxy device and record build/source SHA, Android/API/device identity, and environment.
- Exercise a real selected planner profile through both typed and spoken goals into the same trusted planning path.
- Verify exact approval, deterministic execution, verification, audit, durable sessions, planner/provider secret isolation, and no silent fallback.
- Verify microphone permission/revocation, speech playback, barge-in, Stop talking vs Stop task separation, rotation/rebind/background behavior, stale approval rejection, and interruption behavior.
- Measure user interruption to playback-stop latency and trusted Stop receipt latency against roadmap acceptance targets.
- Preserve truthful evidence strength: physical observations are recorded separately from CI/emulator automation.
- Do not claim the complete 1.0 media/publishing roadmap, publish externally, or weaken security/authority semantics to make acceptance pass.

### Dependencies

- TASK-008 complete: Phase-1 planner acceptance.
- TASK-009 through TASK-015 complete: Phase-2 voice interfaces, lifecycle, interruption, playback, narration, and acceptance.
- A current installable debug/release-candidate APK produced from the recorded source SHA.

### Plan

- Define one reproducible physical-device acceptance matrix from the roadmap preview gate and existing Android acceptance conventions.
- Install the exact current build and record device/build metadata before testing.
- Run typed and spoken planner happy paths plus approval/execution/verification/audit evidence.
- Exercise lifecycle, permission, interruption, barge-in, stale-approval, Stop-talking, and Stop-task cases.
- Measure required latencies using device-observed timestamps/automation where reliable.
- Record failures/limits explicitly and rerun only after bounded fixes with fresh build identity.
- Produce a concise preview-readiness evidence record without conflating it with full 1.0 release readiness.

### Acceptance

- Real selected planner works from the installed GUI for typed and spoken goals on the reference Galaxy.
- Speech can be interrupted; Stop talking and Stop task remain distinct and truthful.
- Exact approval/policy/execution/verification/audit remain authoritative through voice interaction.
- Restart/rotation/background/rebind behavior preserves or fails closed according to documented contracts.
- Raw planner/provider credentials are absent from user-visible/durable returned surfaces.
- Measured interruption/Stop behavior meets recorded Phase-2 targets or the task remains explicitly blocked.
- All physical evidence names device/API/build SHA; emulator/CI results are not substituted.
- Passing this task authorizes only the Voice-First LAIN Preview milestone, not the full 1.0 claim.

### Verification

- Current-build install and launch on declared Galaxy reference device.
- Typed real-profile happy path and spoken real-profile happy path.
- Permission denied/revoked, provider unavailable, rotation/rebind/background, interruption, barge-in, Stop-talking vs Stop-task, stale approval, and secret-surface checks.
- Recorded latency measurements and exact observed outcomes.
- Fresh canonical/Android CI evidence for the same source state where applicable.
- Final evidence reconciliation against the roadmap preview acceptance checklist.

### Expected result

A reproducible device-native evidence package establishes whether the Voice-First LAIN Preview is ready without overstating CI, emulator, or full-1.0 status.

### Evidence basis

- `docs/ROADMAP_1.0.md` roadmap-r4 adds the Voice-First LAIN Preview milestone and explicitly orders Phase 1 → Phase 2 → current physical-device Galaxy acceptance → preview.
- The new milestone requires real selected planner, spoken+typed goals, trusted execution semantics, barge-in, independent Stop task, secret isolation, and physical Galaxy evidence.

### Projection basis

- This gate converts Phase-1/2 subsystem completion into a usable device milestone while allowing Phases 3–7 to continue independently toward full 1.0.
- A dedicated physical gate prevents emulator/CI success from being misreported as device readiness.

### Risks / unknowns

- Physical-device execution requires owner access to the Galaxy and may expose hardware/vendor-specific behavior not reproducible in CI.
- Live planner/speech-provider paths may require user credentials/network access.
- Device performance/thermal state can affect latency measurements and must be recorded with evidence.

## TASK-035: Implement aggregate provider spending controls
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, budget, spending
**Updated:** 2026-10-03

### Goal

Extend persisted workflow budgets into explicit provider-spending controls so remote provider calls remain bounded, user-visible, restart-safe, and incapable of granting themselves additional spending authority.

### Scope

- Add per-workflow provider spending ceilings and provider-call ceilings that intersect with existing TASK-023 aggregate budgets.
- Track user-visible estimated cost before provider submission and actual cost when a provider exposes trustworthy usage metadata.
- Persist consumed provider-call and cost counters monotonically across restart/reconciliation.
- Bind spending authority to trusted local workflow configuration/approval; provider estimates, retry hints, model output, job status, or metadata never increase ceilings.
- Ensure TASK-032/033/034 provider jobs consult the same budget authority before submit/retry/poll paths that incur billable work.
- Represent unknown pricing, missing actual usage, currency/unit mismatch, stale revision, exhausted budget, and reconciliation uncertainty explicitly.
- Do not implement autonomous purchasing, generic payment methods, provider billing-account mutation, or implicit budget expansion.

### Dependencies

- TASK-023 complete: aggregate workflow budgets.
- TASK-032 complete: provider job abstraction.
- TASK-033 and TASK-034 interfaces available for speech/media provider cost integration.

### Plan

- Define the smallest persisted provider-budget ledger layered on existing workflow budget state.
- Add trusted cost-estimate inputs and provider-call charging gates before billable submission.
- Record actual usage/cost only from adapter-validated provider metadata without treating it as authority.
- Make retries/reconciliation preserve operation identity and avoid double charging known-completed operations while never assuming uncertain writes are free.
- Surface remaining budget, estimate, actual usage when known, and explicit exhausted/unknown states.
- Add deterministic pricing/counter tests across restart, retry, reconciliation, stale revision, and concurrent provider-job attempts.

### Acceptance

- No provider submission or billable retry occurs when the trusted remaining workflow/provider ceiling is insufficient.
- Restart cannot reset provider call counts, estimated/actual consumed cost, or spending ceilings.
- Provider-supplied estimates/status/model output cannot expand authority or alter trusted ceilings.
- Unknown/missing pricing cannot be silently interpreted as zero cost where a configured cost ceiling requires accounting.
- Duplicate/reconciled operations do not double-charge known single effects, while uncertain external effects remain conservatively represented.
- User-visible budget state distinguishes estimate from actual usage and identifies provider/currency/unit provenance.
- Provider budget enforcement composes with time/action/bytes/retry budgets rather than replacing them.

### Verification

- Persisted monotonic provider-call/cost counter tests.
- Deterministic estimate-vs-actual and unknown-pricing tests.
- Budget exhaustion before submit/retry tests.
- Restart/reconciliation/duplicate-operation/stale-revision tests.
- Concurrent-job atomicity tests.
- Malicious provider estimate/status/model-output authority-bypass negatives.
- TASK-032/033/034 integration contract tests plus canonical verification and architecture/security review.

### Expected result

External providers operate under durable user-controlled call and spending ceilings with truthful estimates/actuals, without provider-controlled metadata creating new financial authority.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R5.4 with per-workflow spending ceilings, provider call counts, user-visible estimates, actual cost recording, and an explicit rule that provider estimates grant no spending authority.
- Current TaskPlanner represents R5.1–R5.3 as TASK-032 through TASK-034; no task/open issue/open PR represents R5.4.

### Projection basis

- Phase-5 acceptance requires bounded remote providers under explicit budgets before retry/backoff and privacy-disclosure work can be trusted end to end.
- Centralized budget authority prevents each provider adapter from inventing incompatible cost semantics.

### Risks / unknowns

- Providers expose pricing/usage with differing currencies, token/media units, timing, and accuracy; unsupported conversions must remain explicit rather than guessed.
- Some providers report actual cost only after completion; preflight estimates must remain conservative and clearly labeled.
- Live billing evidence may require user credentials and must remain separate from deterministic pricing fixtures.

## TASK-034: Implement bounded external image/media generation adapter
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, media, provenance
**Updated:** 2026-10-03

### Goal

Implement replaceable external image/media generation behind TASK-032's bounded provider-job lifecycle with strict request bounds and independent download, decode, hash, revision, and provenance validation before workflow consumption.

### Scope

- Map bounded image/media generation requests onto TASK-032 submit/poll/cancel/result contracts.
- Bound dimensions, output count, prompt/request bytes, downloaded bytes, timeout, provider calls, retries, and applicable workflow cost.
- Treat provider URLs, MIME labels, filenames, dimensions, status text, and hashes as untrusted claims.
- Download only through a constrained result-retrieval path; validate actual bytes with the approved decoder before artifact promotion.
- Hash validated bytes and commit them through TASK-018/TASK-025 artifact/provenance boundaries bound to the current workflow revision.
- Surface auth, 429/5xx, timeout, unsupported cancellation, expired/missing result, redirect/policy violation, malformed/oversized payload, decode failure, and metadata mismatch explicitly.
- No generic authenticated fetch capability, arbitrary URL-following, implicit provider fallback, publication authority, or provider-controlled workflow completion.

### Dependencies

- TASK-018, TASK-020 through TASK-025 complete: artifact, revision, wait/reconciliation/budget, acceptance, and media schema semantics.
- TASK-031 complete: proven local media pipeline.
- TASK-032 complete: bounded external provider job abstraction.

### Plan

- Define the smallest provider-neutral generation request/result mapping using existing media schemas.
- Reuse provider-job identity, deadlines, cancellation, reconciliation, and budget accounting.
- Add deterministic fake-provider tests for synchronous/asynchronous results and hostile provider metadata.
- Constrain result retrieval and validate decoded media bytes independently of provider assertions.
- Record immutable provider/model/request fingerprint/result hash/provenance without credentials.
- Verify stale revision, restart, duplicate operation, budget exhaustion, cancellation, malformed download, redirect abuse, and decoder failure.

### Acceptance

- Configured remote generation produces only bounded validated media artifacts compatible with existing workflow/media contracts.
- Dimensions/count/bytes/calls/time/retries/cost cannot exceed the intersection of adapter and persisted workflow ceilings.
- Provider-supplied metadata or URLs cannot bypass retrieval policy, decoder validation, artifact hashing, revision checks, or trusted workflow authority.
- Invalid, stale, partial, oversized, mismatched, or undecodable downloads never become workflow-consumable artifacts.
- Provider selection never silently crosses Local/Cloud/privacy boundaries.
- Raw credentials remain absent from durable and returned LAIN surfaces.

### Verification

- Request-boundary and schema tests for dimensions/count/bytes/time/cost limits.
- Fake-provider submit/poll/cancel/result tests including auth, 429, 5xx, timeout, expired result, redirects, malformed/oversized payloads.
- Real decoder validation against valid and corrupt/mismatched fixtures.
- Hash/revision/provenance and stale/duplicate/restart/reconciliation negatives.
- Secret-leak and hostile URL/metadata tests.
- Canonical verification plus architecture/security/privacy review; Android/platform checks where decoder/retrieval integration requires them.

### Expected result

Remote image/media generation becomes a bounded replaceable artifact source; only independently validated, hashed, current-revision media enters the workflow.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R5.3 as image/media generation with bounded dimensions/count, provenance metadata, download validation, decoder validation, and hashing before workflow consumption.
- Current TaskPlanner represents R5.1/R5.2 as TASK-032/TASK-033 but has no task/open issue/open PR for R5.3.

### Projection basis

- External generation must reuse the same durable provider lifecycle and media artifact trust boundaries before Phase-5 budget/retry/privacy controls can be accepted end to end.
- Independent decoding and hashing prevents provider metadata from becoming trusted artifact evidence.

### Risks / unknowns

- Provider result delivery varies across inline bytes, signed URLs, redirects, retention windows, and formats; unsupported retrieval semantics must fail explicitly.
- Decoder/platform format support may vary; accepted formats must remain an explicit bounded allowlist.
- Live-provider acceptance may require credentials/network access and must remain distinct from deterministic fixture evidence.

## TASK-033: Implement bounded external speech provider adapter
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, speech, privacy
**Updated:** 2026-10-03

### Goal

Implement replaceable external STT/TTS adapters through the Phase-2 speech interfaces and TASK-032 provider-job lifecycle while preserving local authority, secret isolation, bounded cost, cancellation, and explicit provider identity.

### Scope

- Adapt configured remote transcription and synthesis providers to the existing Phase-2 request/result/cancellation contracts.
- Route asynchronous provider work through TASK-032 rather than introducing provider-specific polling, retry, reconciliation, or durable-job state.
- Keep credentials opaque in the native/provider boundary; exclude raw secrets from workflow state, planner payloads, logs, audit, IPC, artifacts, and exported settings.
- Enforce request/audio/text/result size, timeout, provider-call, retry, and applicable cost ceilings inherited from workflow budgets.
- Validate returned audio/transcript metadata and provenance before downstream consumption.
- Surface throttling, authentication, timeout, provider failure, malformed/oversized result, unsupported cancellation, and privacy-relevant provider identity explicitly.
- No implicit Local-to-Cloud/provider fallback, generic authenticated HTTP capability, voice cloning, or always-listening behavior.

### Dependencies

- TASK-009 through TASK-015 complete: stable Phase-2 speech contracts and acceptance.
- TASK-018 and TASK-020 through TASK-024 complete: artifact, revision, wait/reconciliation/budget semantics.
- TASK-032 complete: bounded external provider job abstraction.

### Plan

- Map Phase-2 STT/TTS contracts onto TASK-032 submit/poll/cancel/result primitives.
- Define the minimum provider adapter configuration and opaque credential-reference boundary.
- Add deterministic fake-provider tests for transcription/synthesis success and provider failure classes.
- Verify cancellation, stale revision, restart/reconciliation, throttling/backoff interaction, budget exhaustion, and malformed/oversized result handling.
- Validate synthesis artifacts before workflow consumption and record provider/model/provenance without secret material.
- Exercise at least one replaceable adapter implementation or protocol-compatible fixture without coupling trusted workflow semantics to provider-specific responses.

### Acceptance

- The same Phase-2 speech interfaces operate with a configured remote STT/TTS adapter without changing turn-manager or trusted workflow authority.
- Provider credentials never enter durable/returned LAIN surfaces.
- Provider selection is explicit and never silently falls back across privacy/cost boundaries.
- Cancellation and failure states are truthful; unsupported remote cancellation is not reported as cancelled.
- Rate limits/retries/timeouts/call counts/bytes/cost intersect with persisted workflow budgets and cannot reset on restart.
- Remote synthesis output is validated, hashed, revision-bound, and provenance-recorded before use.
- Provider text/status/result fields cannot authorize capabilities, approvals, spending, or workflow completion.

### Verification

- Phase-2 interface compatibility tests using deterministic fake transports.
- STT/TTS success plus auth, 429, 5xx, timeout, cancellation, malformed/oversized-result negatives.
- Restart/reconciliation/stale-revision and budget-intersection tests.
- Secret-leak scans across durable state/log/audit/IPC/exported settings.
- Artifact validation/hash/provenance tests for returned audio.
- Canonical portable verification, Android API matrix where platform integration applies, and architecture/security/privacy review.

### Expected result

Configured external speech services become replaceable bounded adapters behind LAIN's existing speech and workflow contracts without becoming authority, secrecy, or budget bypasses.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines Phase 5 R5.2 as STT/TTS through the Phase-2 interface with secret handling and throttling/error behavior.
- Current TaskPlanner represents R5.1 as TASK-032 but has no task/open issue/open PR for R5.2.

### Projection basis

- Voice workflows need remote speech as an optional replaceable provider path after the local speech interface and durable provider-job lifecycle stabilize.
- Implementing R5.2 before media generation keeps provider integration incremental and exercises TASK-032 on bounded speech payloads before larger media results.

### Risks / unknowns

- Provider protocols differ in streaming, cancellation, billing, and audio format support; adapter-specific details must not leak into trusted speech/workflow contracts.
- Live-provider acceptance may require user credentials and network access; deterministic fake evidence must remain distinct from live-provider evidence.
- Provider privacy disclosure UI is sequenced separately under R5.6 and must not be falsely claimed complete here.

## TASK-032: Define bounded external provider job abstraction
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, workflow, network
**Updated:** 2026-10-03

### Goal

Define one provider-neutral, durable job contract for bounded external submit/poll/cancel/result retrieval without granting provider responses, job IDs, or retry hints any trusted execution authority.

### Scope

- Define typed submit, poll, cancel-support, terminal-status, and result-artifact retrieval contracts for asynchronous external providers.
- Bind every job to workflow/revision/provider identity, request fingerprint, deadlines, budgets, and durable operation identity.
- Reuse TASK-021 durable waits, TASK-022 reconciliation, TASK-023 budgets, and TASK-018 artifact provenance rather than creating provider-specific schedulers or stores.
- Represent unsupported cancellation, uncertain submission, provider loss, rate limiting, malformed status/result, stale revision, and result validation failure explicitly.
- Keep provider credentials opaque and outside planner payloads, durable workflow state, logs, audit, IPC results, and artifacts.
- Do not implement a concrete STT/TTS/media provider, generic authenticated HTTP capability, implicit fallback, or spending authority.

### Dependencies

- TASK-018 complete: immutable artifact workspace.
- TASK-020 through TASK-024 complete: revision, durable wait, reconciliation, aggregate budgets, and Phase-3 acceptance.
- TASK-031 complete: Phase-4 local media acceptance before external-provider complexity.

### Plan

- Inventory existing planner/speech transport lifecycle and durable workflow primitives for reusable status/error/cancellation vocabulary.
- Specify the smallest typed provider job request/identity/state/result model.
- Map submit uncertainty to reconciliation and polling to persisted wait/deadline semantics.
- Require downloaded result bytes to enter existing artifact validation/hash/provenance boundaries before workflow consumption.
- Add deterministic fake-provider contract tests for success, async polling, cancellation, unsupported cancel, timeout, 429/5xx, malformed responses, stale revision, restart, uncertain submit, duplicate operation identity, and budget exhaustion.
- Document adapter obligations and explicit non-authority boundaries for later R5.2/R5.3 implementations.

### Acceptance

- One typed contract represents synchronous or asynchronous provider jobs without provider-specific workflow state.
- Restart cannot replay an uncertain submit or reset deadlines, retry counters, call counts, byte/cost ceilings, or operation identity.
- Provider job IDs/status text/retry hints/results cannot authorize capabilities, approvals, spending, or workflow success.
- Polling and cancellation are bounded and truthful; unsupported cancellation is distinct from success.
- Result artifacts are validated, hashed, revision-bound, and provenance-recorded before downstream use.
- Local/provider selection never silently changes to another provider.
- Raw credentials remain absent from durable and returned LAIN surfaces.

### Verification

- Contract/schema round-trip and strict invalid-input tests.
- Deterministic fake-provider submit/poll/cancel/result tests.
- Crash/restart/reconciliation and duplicate-operation tests.
- Deadline/rate-limit/retry/budget intersection tests with deterministic clocks.
- Stale-revision, malformed-status/result, oversized-result, hash/provenance, and secret-leak negatives.
- Canonical portable verification plus architecture/security review of the provider boundary.

### Expected result

Later speech and media adapters can plug into one durable bounded job lifecycle while local workflow authority, budgets, reconciliation, artifact verification, and privacy semantics remain authoritative.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines Phase 5 R5.1 as provider job abstraction with submit, poll, cancellation support, terminal status, and result artifact retrieval.
- Current TaskPlanner state ends Phase 4 at TASK-031; no existing task, open issue, or open PR represents R5.1.

### Projection basis

- R5.2 speech and R5.3 media adapters both need the same durable asynchronous lifecycle; establishing it once prevents duplicated provider-specific polling/retry/reconciliation state.
- Phase-5 exit requires replaceable remote providers under bounded budgets/failure handling without changing trusted local semantics.

### Risks / unknowns

- Concrete providers vary in idempotency, cancellation, billing, and result-retention semantics; the contract must represent unsupported/uncertain states rather than normalize them into false success.
- Cost fields may be estimates until providers expose actual usage and must never grant spending authority.
- Provider-specific privacy disclosures remain a later R5.6 concern.

## TASK-031: Close Phase-4 with a real offline golden video fixture
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, acceptance, golden-fixture
**Updated:** 2026-10-03

### Goal

Prove the complete local media path with bundled/licensed assets by producing, independently inspecting, and previewing a real 30–60 second video on the supported Android path without any cloud account.

### Scope

- Add a deterministic offline fixture using bundled or clearly licensed local image/audio/text inputs.
- Drive the existing media schema, narration, timeline, renderer, inspection, and preview contracts end to end; do not introduce a parallel fixture-only pipeline.
- Record exact input identities, workflow revision, renderer/toolchain identity, output hash, dimensions, duration, stream evidence, and inspection result.
- Exercise cancellation/Stop and durable workflow state without weakening existing authority, revision, artifact, or budget semantics.
- Keep cloud providers, YouTube, publication, generic shell execution, and new media-generation providers out of scope.

### Dependencies

- TASK-024 complete: durable workflow acceptance semantics.
- TASK-025 through TASK-030 complete: media schema, narration, timeline, renderer, inspection, and preview/export path.

### Plan

- Define one small reproducible licensed fixture and expected bounded output constraints.
- Execute it through the production media workflow interfaces.
- Persist immutable artifact/provenance evidence and independently inspect the rendered file.
- Exercise preview plus cancellation/Stop and restart-sensitive workflow state.
- Add deterministic fixture automation where portable and Android installed-artifact evidence where platform-specific.
- Document exact commands, hashes, environment, evidence strength, and any physical-device gaps.

### Acceptance

- A fresh supported build can produce a real 30–60 second video from only local fixture assets.
- The output is independently decodable and matches declared stream, dimensions, duration, revision, and hash constraints.
- Preview uses the exact verified artifact; stale/mismatched/corrupt outputs are rejected.
- Stop/cancellation leaves truthful durable state and cannot promote partial output to verified success.
- No cloud account, external media provider, publication authority, or generic shell capability is required.
- Automated/emulator/device evidence is labeled separately; physical-device acceptance is never inferred.

### Verification

- Run canonical Python verification plus relevant media workflow tests.
- Run Android build/lint/JVM/instrumentation checks on the supported API matrix.
- Execute the real fixture through renderer -> inspection -> preview and capture output hash/metadata/provenance.
- Negative-test corrupt/stale/mismatched artifacts, cancellation, restart, and insufficient/invalid fixture inputs.
- Perform architecture/security/license review of the whole fixture path.
- Record physical-device evidence separately when available.

### Expected result

Phase 4 has reproducible evidence that the production local workflow can create, inspect, and preview a genuine video artifact without cloud dependencies or fabricated success.

### Evidence basis

- `docs/ROADMAP_1.0.md` explicitly defines R4.7 Offline golden fixture and the Phase-4 exit gate after R4.6.
- Current TaskPlanner state represents R4.1–R4.6 as TASK-025 through TASK-030; no existing task, open issue, or open PR represents R4.7.

### Projection basis

- Phase 5 provider work should build on a proven local media pipeline so provider failures cannot hide renderer/inspection/workflow defects.
- The 1.0 definition of done requires a real offline video fixture and installed-artifact evidence.

### Risks / unknowns

- Renderer packaging/licensing and device codec behavior remain dependent on TASK-028 selection.
- Emulator evidence may not represent physical-device codec/resource behavior.
- Fixture assets must have repository-compatible licensing and deterministic provenance.

## TASK-030: Build verified video preview and explicit export UI
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, preview, export
**Updated:** 2026-10-03

### Goal

Let the user review a current-revision video that passed TASK-029 inspection, observe render/inspection progress, cancel safely, and explicitly export or share the exact verified artifact through narrow Android capability policy.

### Scope

- Display only immutable video artifacts with successful current-revision TASK-029 inspection evidence.
- Present trusted artifact identity, duration, dimensions, inspection status, provenance summary, and current/stale state without trusting planner-provided labels as verification.
- Expose render and inspection progress from durable workflow state and preserve the existing trusted Stop/cancellation path.
- Preview through a bounded Android media surface without copying the artifact into planner-visible or world-readable storage.
- Implement explicit user export/share initiation through a narrow content URI/capability boundary with exact artifact hash and destination intent.
- Treat missing/stale/failed inspection, unavailable decoder, revoked URI permission, cancelled export, or mismatched artifact identity as explicit non-success.
- Do not implement YouTube OAuth/upload/publication, generic file browsing, arbitrary URI grants, editor/NLE controls, or automatic sharing.

### Dependencies

- TASK-029 complete: independently verified video artifact and inspection evidence.
- TASK-028 complete: bounded renderer and durable render progress/cancellation.
- TASK-018 and TASK-020 complete: immutable artifact workspace and revision invalidation.
- TASK-024 complete: durable workflow acceptance semantics.

### Plan

- Define the smallest trusted preview-state model over current workflow, render, artifact, and inspection records.
- Bind UI state and playback source to exact artifact identity/hash/revision and expose stale/failed states distinctly.
- Integrate a lifecycle-safe Android playback surface with bounded error handling and no arbitrary path input.
- Route Stop through the existing trusted cancellation control path.
- Add a narrow explicit export/share capability using scoped content URIs and time-bounded least-privilege grants.
- Add state, lifecycle, stale-revision, cancellation, hostile-intent/path, URI-permission, and process-restart tests.

### Acceptance

- The UI previews only a verified, current-revision artifact and visibly identifies the exact hash, dimensions, duration, and inspection result.
- Rendering/inspection progress remains truthful across lifecycle changes and process restart; unavailable progress is not fabricated.
- Stop cancels the active render/inspection path through trusted control semantics and cannot silently report success.
- Stale, missing, corrupt, uninspected, hash-mismatched, or failed artifacts cannot be previewed as approved/current or exported.
- Export/share requires a fresh explicit user action bound to the exact artifact and uses a scoped content URI rather than a raw filesystem path.
- URI access is limited to the chosen operation/recipient and does not expose sibling artifacts, credentials, checkpoints, or private app storage.
- Cancellation, target-app failure, revoked permission, or process interruption produces an explicit non-success/recoverable state without duplicate export.
- Preview or export never grants upload, publication, provider, planner, or generic filesystem authority.

### Verification

- Android UI/state tests for verified/current, stale, failed, missing, and inspection-pending artifacts.
- Playback lifecycle, decoder failure, background/foreground, rotation/recreation, restart, and cancellation tests.
- Content-URI scope/permission, hostile filename/path, wrong-hash/revision, target failure, duplicate intent, and revoked-grant negatives.
- Assertions that raw private paths, credentials, planner payloads, and unrelated artifacts never enter exported intents or public UI state.
- Canonical portable verification plus Android build/lint/unit/instrumentation on the supported API matrix.
- Physical-device playback/share evidence remains separately labeled unless actually exercised.

### Expected result

Phase 4 gains a truthful user review surface for the exact verified local video and a narrow user-authorized export path without collapsing preview, sharing, and later publication into one authority boundary.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R4.6 Preview/export UI immediately after R4.5 inspection, requiring artifact display, render progress, cancellation, and export/share only through explicit capability policy.
- Current TaskPlanner, issue #6, and open PR set end at TASK-029/R4.5; no repository-native work item represents R4.6.

### Projection basis

- The Phase-4 exit gate requires the user to inspect a real produced video, while the later YouTube path needs a reviewed exact artifact identity before any upload or publication approval can be meaningful.
- Separating local preview/export from publication prevents media UI from becoming an implicit external-effect authority surface.

### Risks / unknowns

- Android playback and sharing behavior varies by API level and recipient app; emulator evidence must not be promoted to physical-device interoperability.
- Large videos may pressure memory or lifecycle handling; playback must stream from bounded scoped storage rather than load entire files.
- Content URI grant lifetime and duplicate chooser launches require explicit reconciliation so interruption cannot be reported as confirmed delivery.

---

## TASK-029: Implement real video inspection
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, inspection
**Updated:** 2026-10-03

### Goal

Independently inspect the actual renderer output and promote it to a verified video artifact only when the file exists, decodes, contains the required streams, matches declared dimensions and duration bounds, and has a recorded content hash.

### Scope

- Inspect only a completed TASK-028 output resolved through the immutable artifact workspace and current workflow revision.
- Use a bounded Android-compatible media probe/decoder path; do not trust filename extensions, planner claims, renderer exit status, or metadata sidecars as verification.
- Verify file existence/readability, container decode, at least one video stream, expected audio presence, pixel dimensions, duration tolerance, byte bounds, and full-file hash.
- Bind inspection evidence to the exact render attempt, timeline revision, renderer identity, input artifact hashes, output artifact identity, and workflow revision.
- Persist explicit inspection success/failure without mutating the rendered bytes or granting preview/export/publication authority.
- Do not implement rendering, preview/export UI, sharing, publication, cloud media generation, or generic file probing.

### Dependencies

- TASK-028 complete: a bounded renderer produces a candidate video artifact.
- TASK-025 complete: media artifact schema and immutable identity.
- TASK-018, TASK-020, TASK-023, and TASK-024 complete: immutable artifacts, revision invalidation, aggregate budgets, and durable-workflow acceptance.

### Plan

- Define a closed inspection request/result contract over immutable video artifact references.
- Select or reuse the smallest Android-compatible probe/decoder surface that exposes stream, dimension, duration, and decode evidence under cancellation and resource bounds.
- Compute the candidate file hash independently and compare all observed properties with the render/timeline contract.
- Promote verification atomically only after every required check passes for the current revision.
- Add real-fixture, malformed-container, corrupt/truncated, missing-stream, metadata-mismatch, stale-revision, cancellation, timeout, and duplicate-resume coverage.

### Acceptance

- A valid TASK-028 output is independently opened and decoded, with observed video stream, expected audio stream, dimensions, duration, byte size, and content hash recorded.
- Renderer exit success, file presence alone, extension/MIME claims, or sidecar metadata cannot produce inspection success.
- Missing, unreadable, empty, truncated, corrupt, oversized, unsupported-codec, no-video, unexpected-no-audio, wrong-dimension, duration-mismatch, or hash-mismatch output fails closed.
- Inspection evidence is bound to the exact candidate artifact, render attempt, timeline/input hashes, tool identity/version, and current workflow revision.
- Cancellation, timeout, process death, or stale revision cannot promote a candidate to verified state.
- Resume reuses only matching durable completed evidence or reruns inspection; it never guesses success from a partial record.
- Inspection exposes no arbitrary path, command, shell, preview, export, sharing, or publication surface.

### Verification

- Contract tests using one real valid offline video fixture with asserted streams, dimensions, duration tolerance, byte size, and hash.
- Corrupt, truncated, malformed-container, unsupported-codec, missing-audio/video, wrong-dimension/duration/hash, hostile-path, and oversized-file negatives.
- Cancellation, timeout, stale-revision, process-restart, partial-record, and duplicate-resume tests.
- Canonical portable verification, Android build/lint/tests, and supported emulator/device decode evidence kept explicitly separated.
- Fresh architecture/security review of path handling, resource bounds, evidence atomicity, and authority boundaries.

### Expected result

Phase 4 can distinguish a genuinely decodable, contract-matching video from a merely produced file and expose durable inspection evidence for preview and the offline golden fixture without widening execution or publication authority.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R4.5 Video inspection immediately after R4.4 Renderer and explicitly requires existence, decode, video/audio streams, dimensions, duration, and file hash.
- Current TaskPlanner, open issues, and open PRs end Phase-4 planning at TASK-028/R4.4; no current repository-native record represents R4.5.

### Projection basis

- R4.6 preview/export and R4.7 offline golden fixture need independently verified media properties; treating renderer completion as inspection would make later acceptance circular.

### Risks / unknowns

- Android decoder/container behavior can differ by API level and device codec availability; evidence must label platform/API/device and keep portable structural checks distinct.
- Full decode may be resource-expensive; bounded sampling versus full-stream validation must be chosen explicitly without overstating assurance.
- The inspection tool may share libraries with the renderer; independent observation still requires a separate verification path and result contract.

---

## TASK-028: Integrate a bounded Android-capable video renderer
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, renderer
**Updated:** 2026-10-03

### Goal

Select and integrate the smallest Android-compatible rendering toolchain that turns a validated TASK-027 timeline plus immutable media artifacts into one deterministic video artifact under trusted bounds, cancellation, and explicit failure semantics.

### Scope

- Evaluate only renderer options that can be packaged and licensed for the supported Android app and invoked through a fixed trusted boundary.
- Define a typed render request derived from the validated timeline; never accept planner-supplied commands or shell fragments.
- Resolve every input by immutable artifact identity/hash and current workflow revision before renderer launch.
- Enforce explicit output path, dimensions, frame rate, duration tolerance, byte/storage limits, active runtime budget, and cancellation.
- Persist render attempt/result provenance and add the output through TASK-018/TASK-025 artifact contracts only after bounded process completion.
- Treat process crash, timeout, cancellation, resource exhaustion, malformed output, or missing output as explicit stage failure.
- Do not implement video inspection, preview/export UI, cloud media generation, publication, or generic command execution.

### Dependencies

- TASK-027 complete: deterministic media timeline representation.
- TASK-025 complete: media artifact schemas.
- TASK-018, TASK-020, TASK-023, and TASK-024 complete: immutable artifacts, revision invalidation, aggregate budgets, and Phase-3 durability acceptance.

### Plan

- Record an evidence-based renderer/toolchain decision covering Android packaging, ABI/API support, licensing, deterministic invocation, cancellation, and output support.
- Reuse the existing constrained process/cancellation boundary or introduce only the minimum renderer-specific adapter required.
- Translate validated timeline records into fixed arguments/config without shell interpolation.
- Stage output atomically, enforce resource budgets, and attach exact input/timeline/toolchain provenance.
- Add fake-adapter contract tests plus one real offline render path using bounded licensed fixture inputs.

### Acceptance

- A validated current-revision timeline produces a real video file at a deterministic artifact path.
- Renderer selection has recorded Android packaging/licensing/API evidence and no generic shell or arbitrary command surface.
- Every render binds exact timeline revision, input artifact hashes, renderer identity/version, output constraints, and final content hash.
- Unknown codecs/transitions, stale or missing inputs, invalid paths, insufficient storage, timeout, cancellation, crash, nonzero exit, or missing/oversized output fail closed.
- Partial output cannot be promoted to a verified media artifact or unlock downstream workflow nodes.
- Duplicate resume reuses a verified identical result or restarts only under durable current-revision state; it never silently duplicates uncertain work.
- Existing Stop/cancellation and aggregate budgets remain authoritative.

### Verification

- Renderer adapter contract tests with fixed argv/config and hostile-input/path negatives.
- Cancellation, timeout, crash, disk/resource-limit, stale-revision, partial-output, and duplicate-resume tests.
- Determinism/provenance/hash assertions across repeated identical fixtures.
- One real Android-compatible offline fixture render in CI or a clearly separated supported emulator/device evidence path.
- Canonical portable verification, Android build/lint/tests, license/architecture/security review.

### Expected result

Phase 4 gains a real bounded renderer that converts the deterministic timeline into an immutable video artifact without exposing arbitrary execution or treating an uninspected file as verified output.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R4.4 Renderer immediately after R4.3 timeline representation.
- Current TaskPlanner ends Phase-4 planning at TASK-027/R4.3; no existing task, open issue, or open PR represents R4.4.

### Projection basis

- R4.5 video inspection and R4.7 offline golden fixture require a real renderer output, while selecting the toolchain before timeline/schema contracts would create avoidable coupling.

### Risks / unknowns

- The exact toolchain and supported codec/container set remain an evidence-backed implementation decision constrained by Android packaging and licensing.
- Emulator rendering may not represent physical-device performance; evidence must remain labeled by environment.
- Renderer binaries can materially affect APK size and ABI support; keep the integration replaceable and bounded without creating a generic process framework.

---

## TASK-027: Define deterministic media timeline representation
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, timeline
**Updated:** 2026-10-03

### Goal

Define a strict deterministic timeline model that binds scenes, image/audio artifacts, captions, transitions, and output constraints into one renderer-ready revision without embedding rendering authority or provider behavior.

### Scope

- Define ordered scene records with stable IDs and explicit start/duration semantics.
- Reference immutable image and narration/audio artifacts from TASK-025/TASK-026 rather than copying media payloads into timeline state.
- Represent captions/subtitles with bounded text and timing.
- Represent bounded transition type/duration and output constraints such as target dimensions, frame rate, duration, and audio expectations.
- Bind timeline identity to workflow revision and exact referenced artifact hashes.
- Reject overlaps/gaps/invalid timing combinations according to a documented deterministic contract.
- Do not implement rendering, codec invocation, preview UI, external providers, or publication.

### Dependencies

- TASK-025 complete: media artifact schemas.
- TASK-026 complete: bounded narration pipeline.
- TASK-018 immutable artifact workspace and TASK-020 revision semantics.

### Plan

- Define minimal closed timeline/scene/caption/transition models.
- Validate referenced artifact type/hash/revision compatibility.
- Compute deterministic total duration and normalized scene ordering.
- Enforce bounded dimensions/frame rate/transition/caption timing and exact output constraints.
- Add serialization/round-trip, invalid-reference, timing, revision, and determinism tests.

### Acceptance

- Equivalent inputs serialize to one deterministic timeline representation.
- Every scene references exact immutable artifact identities and current workflow revision.
- Caption and transition timing cannot exceed scene/timeline bounds.
- Missing, stale-revision, wrong-media-type, duplicate-scene, invalid-duration, or inconsistent output constraints fail closed.
- Total timeline duration is deterministic and derived from validated scene timing.
- Timeline data cannot grant capabilities, approve effects, invoke renderers, or bypass verification.
- Revision of any referenced upstream artifact requires a new timeline revision rather than silent in-place mutation.

### Verification

- Focused timeline validation/round-trip/determinism tests.
- Scene ordering, overlap/gap, caption, transition, and total-duration tests.
- Wrong-type/hash/revision artifact-reference negatives.
- Output-bound and malformed-metadata tests.
- Canonical portable verification plus fresh architecture review.

### Expected result

Phase 4 has a stable renderer-neutral timeline contract connecting verified media artifacts into a deterministic 30–60 second video plan without coupling representation to execution.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R4.3 Timeline representation immediately after R4.2 narration.
- Current TaskPlanner represents R4.1 and R4.2 as TASK-025 and TASK-026; no current task, issue, or open PR represents R4.3.

### Projection basis

- Renderer and inspection layers need one deterministic source of scene timing and exact artifact references before a rendering toolchain can be selected safely.

### Risks / unknowns

- Exact transition catalog and frame-rate choices should remain minimal until renderer support is proven.
- Advanced editing, keyframes, effects, and nonlinear tracks are outside 1.0 scope.

---

## TASK-026: Implement bounded narration pipeline
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, narration
**Updated:** 2026-10-03

### Goal

Turn bounded script segments into verified narration audio artifacts with explicit duration/provenance while preserving speech-provider failure as a recoverable stage failure rather than task or workflow authority.

### Scope

- Accept ordered bounded script segments and synthesize narration through the provider-neutral speech interface.
- Persist each produced audio file through the immutable artifact workspace using TASK-025 media artifact schemas.
- Record segment order, source text hash/reference, provider identity/provenance, audio MIME/codec, byte size, duration, artifact hash, and workflow revision.
- Enforce bounded segment/input/output sizes, cancellation, timeout, and total narration duration.
- Validate produced audio duration/metadata before declaring the narration stage successful.
- Preserve deterministic typed/script artifacts if synthesis is unavailable or fails.
- Do not implement timeline composition, video rendering, playback UI, voice cloning, provider-specific SDK logic, or external publication.

### Dependencies

- TASK-009 complete: provider-neutral speech synthesis contract.
- TASK-018 complete: immutable artifact workspace.
- TASK-024 complete: Phase-3 durable-workflow acceptance gate.
- TASK-025 complete: media artifact schemas.

### Plan

- Define the smallest narration-segment request/result contract over existing speech synthesis and artifact APIs.
- Synthesize segments in deterministic order under explicit byte/time/call bounds.
- Store verified audio as immutable artifacts with source-segment and workflow provenance.
- Validate duration/media metadata and reject corrupt, oversized, mismatched, or empty outputs.
- Add cancellation/provider-failure/restart tests proving partial artifacts cannot masquerade as a completed narration stage.

### Acceptance

- Ordered script segments produce ordered immutable audio artifact references.
- Every narration artifact is bound to exact source segment/revision/provider provenance and verified content hash.
- Empty, malformed, oversized, wrong-media, or invalid-duration audio fails explicitly.
- Cancellation/timeout/provider failure cannot produce a successful narration-stage result.
- Restart cannot duplicate already-verified segment artifacts or silently skip incomplete segments.
- Narration failure does not equal workflow success/failure outside the declared stage transition and does not grant execution authority.
- Raw provider credentials never enter script, narration metadata, artifacts, logs, or durable workflow state.

### Verification

- Focused segment-order/synthesis/artifact round-trip tests.
- Duration/media/hash validation tests.
- Cancellation, timeout, provider-unavailable, malformed/oversized-output negatives.
- Restart/idempotency tests for partial and verified segment sets.
- Secret/provenance inspection.
- Canonical portable verification plus fresh architecture review.

### Expected result

LAIN_OS can produce durable, bounded, verified narration audio from script segments as real workflow artifacts that later timeline/render stages can consume without provider-specific or authority coupling.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R4.2 Narration pipeline immediately after media artifact schemas: script segments, synthesis, bounded audio file output, and duration validation.
- Current TaskPlanner represents R4.1 as TASK-025; no current task, issue, or open PR represents R4.2.

### Projection basis

- R4.3 timeline composition requires stable narration artifact references and durations; implementing narration first removes ambiguity from scene timing and renderer inputs.

### Risks / unknowns

- Exact audio codec/container set should stay limited to formats supported by TASK-025 and later renderer evidence.
- Streaming synthesis and advanced prosody are out of scope until a real provider/use case requires them.
- Hardware playback latency is unrelated to offline media narration generation and must not broaden this task.

---

## TASK-025: Define media artifact schemas
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, media, artifacts
**Updated:** 2026-10-03

### Goal

Define strict durable image/audio/video artifact metadata contracts that Phase 4 media stages can exchange without guessing MIME, codec, dimensions, duration, hash, or provenance.

### Scope

- Define bounded schema records for image, audio, and video artifacts.
- Include immutable content hash, media type/MIME, codec/container identifiers where applicable, dimensions, duration, byte size, provenance, producer node, workflow revision, and verification state.
- Reuse TASK-018 immutable artifact identities and TASK-020 revision semantics.
- Fail closed on impossible/contradictory metadata, hash mismatch, invalid dimensions/duration, or unsupported type combinations.
- Keep metadata non-authoritative: it cannot grant capabilities, approvals, execution, or publication rights.
- Do not implement synthesis, timelines, rendering, inspection tooling, preview UI, or provider adapters.

### Dependencies

- TASK-024 complete: Phase-3 durable-workflow acceptance gate.
- TASK-018 artifact workspace contracts remain authoritative.

### Plan

- Inventory existing artifact metadata and hashing primitives.
- Define minimal closed image/audio/video metadata variants with shared immutable identity/provenance fields.
- Add strict validation for MIME/codec/container/dimensions/duration/size combinations.
- Add serialization/round-trip and corrupt/inconsistent metadata tests.
- Document the contract consumed by later narration, timeline, renderer, and inspector tasks.

### Acceptance

- Image artifacts require valid dimensions and image media identity.
- Audio artifacts require valid duration and audio media identity.
- Video artifacts require valid dimensions, duration, and video media identity.
- Every artifact references immutable verified content identity and workflow revision/provenance.
- Invalid negative/zero dimensions, non-finite duration, impossible MIME/type pairings, oversized metadata, or hash mismatch fail explicitly.
- Metadata cannot authorize capabilities, approval, publication, or execution.
- Existing generic artifact workspace behavior remains reusable rather than duplicated.

### Verification

- Focused schema validation and round-trip tests.
- Cross-type invalid-combination tests.
- Hash/provenance/revision integrity tests.
- Metadata size-bound/corruption tests.
- Canonical portable verification plus fresh architecture review.

### Expected result

Phase 4 gains one stable media-artifact contract that narration, timeline, rendering, inspection, preview, and export can share without parallel type systems.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R4.1 Media artifact schemas as the first Phase-4 work package.
- Current planning reaches the Phase-3 acceptance gate at TASK-024; no open task, issue, or PR represents R4.1.

### Projection basis

- Every later Phase-4 stage exchanges real media files; stabilizing media identity and metadata first prevents renderer/inspector/UI-specific schemas from diverging.

### Risks / unknowns

- Final supported codec/container list should remain minimal until the chosen Android-compatible rendering path is proven.
- Rich metadata extraction belongs to video inspection, not this schema task.

---

## TASK-024: Close Phase-3 durable-workflow acceptance gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, acceptance, adversarial
**Updated:** 2026-10-03

### Goal

Prove the integrated durable-workflow subsystem survives process death, resumes truthfully, prevents replay of completed or uncertain effects, invalidates stale downstream artifacts after revision, and exposes an accurate user-readable workflow state before Phase 4 media work begins.

### Scope

- Exercise the integrated TASK-016 through TASK-023 workflow stack end to end.
- Verify crash/restart at scheduler, wait/poll, artifact, revision, and external-effect boundaries.
- Verify completed external effects cannot replay and uncertain effects cannot auto-retry.
- Verify upstream revision invalidates only transitive dependents while preserving immutable prior artifacts/provenance.
- Verify aggregate budgets persist and continue constraining resumed workflows.
- Verify restricted roles cannot expand capability authority.
- Verify user-readable workflow state distinguishes ready/running/waiting/reconciliation/succeeded/failed/cancelled truthfully.
- Do not add new workflow features, media rendering, provider-specific adapters, or publication behavior.

### Dependencies

- TASK-016 through TASK-023 complete.

### Plan

- Build the smallest deterministic acceptance harness over the integrated durable workflow APIs.
- Add process-restart fixtures around each critical durable transition.
- Exercise stale revision/hash, duplicate completion, uncertain effect, lease/wait, and budget exhaustion paths.
- Add user-readable state assertions derived from durable state rather than planner narration.
- Run canonical verification plus focused architecture/security review of the integrated Phase-3 boundary.

### Acceptance

- A multi-stage workflow resumes after simulated process death with exact durable state.
- Known completed effects cannot replay after restart.
- Attempted-but-uncertain effects enter reconciliation and never auto-retry.
- Revision invalidation rebuilds only dependent branches and preserves prior immutable artifacts.
- Wait/poll deadlines, scheduler leases, retries, and aggregate budgets survive restart truthfully.
- Restricted roles and workflow metadata cannot grant capability/policy/approval authority.
- Downstream nodes unlock only from verified/reconciled current-revision outputs.
- User-readable state matches durable workflow state without fabricated progress or completion.

### Verification

- End-to-end deterministic durable-workflow acceptance suite.
- Crash-boundary/restart matrix.
- Replay/uncertainty/reconciliation negatives.
- Revision/artifact invalidation and provenance checks.
- Budget/lease/wait/retry persistence checks.
- Canonical portable verification and fresh whole-diff architecture/security review.

### Expected result

Phase 3 has a reproducible gate proving durable workflows can survive interruption and revision without replay, stale authority, hidden budget reset, or false state before Phase 4 begins.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines an explicit Phase-3 exit gate after R3.1–R3.8.
- Current TaskPlanner represents R3.1–R3.8 as TASK-016 through TASK-023, but no existing task/issue/PR represents the exit-gate acceptance proof.

### Projection basis

- Phase 4 media pipelines create longer-running artifact-heavy workflows; carrying unresolved durability/replay defects into that layer would multiply integration cost and safety risk.

### Risks / unknowns

- Some external-provider reconciliation cases may require adapter-specific fixtures later; the core acceptance gate should use deterministic fakes and label anything not exercised against a live provider.
- Physical-device/UI acceptance remains separate unless explicitly exercised.

---

## TASK-023: Implement aggregate workflow budgets
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, budgets
**Updated:** 2026-10-03

### Goal

Enforce durable workflow-wide budgets for time, actions, provider calls, bytes, estimated/actual cost, and retries so long-running workflows cannot silently exceed owner-defined limits.

### Scope

- Persist workflow-level budget ceilings and consumed counters for time, action count, provider calls, bytes, estimated/actual cost, and retry allowance.
- Charge budget consumption at deterministic boundaries and persist it atomically with workflow state.
- Refuse new node execution, polling, provider work, or retry when the relevant remaining budget is insufficient.
- Preserve existing per-session/action budgets as lower-level limits; aggregate budgets may only further restrict execution.
- Carry budget state across restart, revision, wait/poll, and reconciliation states.
- Surface explicit budget-exhausted outcomes without treating them as successful completion.
- Do not implement billing APIs, dynamic price discovery, provider-specific token accounting, or automatic owner limit increases.

### Dependencies

- TASK-016 workflow persistence.
- TASK-017 DAG scheduler.
- TASK-021 durable wait/poll stages.
- TASK-022 external-effect reconciliation.

### Plan

- Define the smallest aggregate budget record and deterministic charging events.
- Persist counters/limits with workflow state and validate non-negative monotonic consumption.
- Gate scheduler dispatch, polling, provider calls, and retry before crossing each charge boundary.
- Preserve consumed state across crash/restart and workflow revision.
- Add fake-clock/counter tests for each budget dimension and combined exhaustion.

### Acceptance

- Workflow budgets survive restart exactly and consumed counters never decrease.
- Scheduler cannot dispatch work that would exceed a hard action/provider/retry limit.
- Time/deadline accounting cannot be reset by process restart or wait/poll transitions.
- Byte and cost accounting reject further bounded work once the configured ceiling is exhausted.
- Aggregate limits only reduce authority; they cannot expand lower-level runtime/session limits.
- Budget exhaustion produces an explicit non-success/waiting-for-owner outcome and cannot unlock downstream nodes.
- Revision and reconciliation preserve already-consumed budget unless an explicit future owner-authorized policy says otherwise.

### Verification

- Focused persisted-budget and monotonic-counter tests.
- Deterministic clock tests across wait/restart.
- Scheduler/provider/poll/retry gate tests for each budget dimension.
- Combined-limit and lower-level-budget-intersection negative tests.
- Canonical portable verification plus fresh architecture/security review.

### Expected result

LAIN_OS can run durable multi-stage workflows under explicit owner-visible aggregate resource limits without resets, hidden overages, or privilege expansion.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R3.8 Aggregate budgets after external-effect reconciliation.
- Current TaskPlanner state represents R3.1–R3.7 as TASK-016 through TASK-022; no open task, issue, or PR represents R3.8.

### Projection basis

- Media rendering, provider adapters, publishing, and long-running workflows require durable resource ceilings before broader 1.0 production pipelines can be considered safe and operable.

### Risks / unknowns

- Exact provider cost estimation may initially be caller-supplied or unavailable; unavailable estimates must not be fabricated.
- Provider-specific token accounting belongs in adapters, not the generic budget model.

---

## TASK-022: Implement external-effect reconciliation
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, reconciliation
**Updated:** 2026-10-03

### Goal

Make consequential external effects crash-safe by persisting exact operation identity before the side-effect boundary and requiring explicit reconciliation when the final outcome is uncertain.

### Scope

- Persist stable operation identity, workflow revision, node ID, effect type, payload hash, attempt state, and provider/external reference before crossing the effect boundary.
- Distinguish not-started, attempted/uncertain, confirmed-complete, failed-safe, and reconciliation-required states.
- Prevent automatic retry of uncertain effects after crash/restart.
- Prevent replay of effects already confirmed complete.
- Allow reconciliation to inspect external/provider state and settle the durable record without granting new authority.
- Preserve approval binding to exact workflow revision and payload hash.
- Do not implement provider-specific reconciliation adapters, publication, aggregate budgets, or multi-user conflict handling.

### Dependencies

- TASK-016 workflow persistence.
- TASK-017 DAG scheduler.
- TASK-020 revision invalidation.
- TASK-021 durable wait/poll stages.

### Plan

- Define the minimal durable external-operation record and state machine.
- Persist operation identity before invoking any consequential external write.
- Route crash/restart with attempted-but-unsettled state into reconciliation rather than retry.
- Add explicit settle transitions for externally confirmed success/failure.
- Reject stale revision/hash approvals and duplicate confirmed effects.
- Add deterministic crash-boundary and replay-prevention tests.

### Acceptance

- Operation identity is durably written before any external side effect is attempted.
- A crash after attempt but before confirmed receipt resumes in reconciliation-required state.
- An uncertain effect is never automatically retried.
- A confirmed completed effect cannot execute again after restart.
- Reconciliation cannot alter the approved payload, workflow revision, or capability authority.
- Stale approval/revision/hash combinations fail closed.
- Downstream workflow nodes unlock only after a reconciled/verified terminal result.

### Verification

- Deterministic crash-before/after-effect-boundary tests.
- Restart/replay-prevention tests.
- Duplicate operation identity and stale-revision/hash negative tests.
- Reconciliation settle tests with fake external state.
- Canonical portable verification plus fresh architecture/security review.

### Expected result

LAIN_OS can cross consequential external side-effect boundaries without replaying completed writes or guessing whether uncertain writes should be retried after process death.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R3.7 External-effect reconciliation immediately after durable wait/poll stages.
- Current TaskPlanner state represents R3.1–R3.6 as TASK-016 through TASK-021; no open task, issue, or PR represents R3.7.

### Projection basis

- Publishing, uploads, and external provider operations later in the 1.0 path require exact-once-oriented reconciliation semantics before those capabilities are introduced.

### Risks / unknowns

- Some providers lack idempotency or lookup APIs; such adapters may remain owner-reconciliation-only rather than pretending certainty.
- Provider-specific identifiers and status schemas must remain outside the generic reconciliation state machine.

---

## TASK-021: Implement durable wait and poll stages
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, waiting
**Updated:** 2026-10-03

### Goal

Add durable bounded waiting semantics for long provider, render, and upload stages without holding a worker indefinitely or silently extending controller budgets.

### Scope

- Persist explicit waiting state, wake/deadline metadata, provider/job identity references, poll attempt counts, and last observed provider state.
- Release active worker ownership while a node is waiting.
- Resume only when the persisted wake condition is reached or an external completion signal is reconciled.
- Bound poll intervals, total attempts, and deadline behavior under existing aggregate/runtime constraints.
- Preserve exact workflow revision and operation identity across process restart.
- Keep waiting metadata non-authoritative: it cannot grant capabilities, approve effects, or bypass verification.
- Do not implement provider-specific polling adapters, external-effect reconciliation, aggregate budget accounting, or publication behavior.

### Dependencies

- TASK-016 workflow persistence.
- TASK-017 bounded DAG scheduler.
- TASK-020 workflow revision invalidation.

### Plan

- Define a minimal durable waiting/poll state transition for workflow nodes.
- Persist wake deadline, attempt count, provider/job reference, and last safe observation atomically.
- Release scheduler lease when entering waiting and reacquire only when the node is eligible to poll.
- Enforce bounded backoff/poll count/deadline without consuming unbounded active controller time.
- Add restart, deadline, duplicate-poll, stale-revision, and cancellation tests.

### Acceptance

- Long-running nodes can enter a durable waiting state without retaining an active worker lease.
- Process restart preserves wait deadline, poll count, provider/job reference, and workflow revision exactly.
- Polling cannot occur before the persisted wake condition or after terminal deadline/cancellation.
- Poll attempts are bounded and cannot silently extend the node/controller budget.
- A stale workflow revision cannot resume or poll a superseded operation.
- Waiting state cannot authorize capabilities, approvals, or external effects.
- Completion observations remain subject to normal verification/reconciliation before downstream nodes unlock.

### Verification

- Focused wait-state transition and restart round-trip tests.
- Deadline/backoff/poll-limit tests with deterministic clocks.
- Lease-release/reacquisition tests against the scheduler.
- Negative stale-revision, cancellation, and duplicate-poll tests.
- Canonical portable verification and fresh architecture review.

### Expected result

LAIN_OS can pause durable workflow nodes for long external or rendering jobs and resume them truthfully after restart without blocking workers, replaying polls, or inventing extra runtime budget.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R3.6 Durable wait/poll stages directly after revision invalidation.
- Current TaskPlanner state represents R3.1–R3.5 as TASK-016 through TASK-020; no open task, issue, or PR represents R3.6.

### Projection basis

- Later media rendering, provider jobs, upload processing, and external reconciliation require persistent wait semantics that do not tie up execution workers or rely on in-memory timers.

### Risks / unknowns

- Provider-specific status schemas remain adapter concerns and must not leak into the core waiting model.
- External side-effect uncertainty belongs to TASK-022/R3.7 rather than being folded into polling.

---

## TASK-020: Implement workflow revision invalidation
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, revisions
**Updated:** 2026-10-03

### Goal
Add explicit workflow revisions and dependency-driven downstream invalidation so changing an approved upstream artifact creates a new revision without mutating or silently reusing stale dependent outputs.

### Scope
- Create a new workflow revision for accepted upstream changes.
- Compute the transitive set of dependent nodes/artifacts invalidated by that change.
- Preserve prior immutable artifacts and their provenance for audit/readback.
- Mark invalidated downstream work non-runnable until rebuilt from the current revision.
- Bind approvals and publication payloads to an exact workflow revision and artifact hashes.
- Reuse TASK-016 durable state, TASK-017 DAG dependencies, TASK-018 artifact identities, and TASK-019 restricted role metadata.
- Do not implement provider polling, external-effect reconciliation, publication, or generalized merge/conflict editing.

### Dependencies
- TASK-016 workflow persistence.
- TASK-017 DAG scheduler.
- TASK-018 artifact workspace.
- TASK-019 restricted specialist roles.

### Plan
- Define minimal revision and invalidation transitions over the persisted workflow graph.
- Derive transitive invalidation from declared dependency edges and artifact references.
- Preserve immutable prior artifacts while moving current logical references to new revision outputs.
- Reject stale approvals, node completions, and artifact references from older revisions.
- Add deterministic revision/invalidation/rebuild tests.

### Acceptance
- Revising one upstream artifact produces a new stable workflow revision.
- Every transitive dependent output is invalidated; unrelated branches remain valid.
- Invalidated nodes cannot run or report success from stale artifacts.
- Prior artifacts remain inspectable and immutable.
- Approval or publication records bound to an older revision/hash cannot authorize the new payload.
- Process restart preserves revision and invalidation state exactly.
- Revision metadata cannot grant capabilities or bypass policy, approval, execution, or verification.

### Verification
- Focused revision-transition and transitive-invalidation tests.
- Branch-isolation tests proving unrelated nodes stay valid.
- Restart/round-trip tests for invalidation state.
- Negative stale-approval, stale-artifact, and stale-completion tests.
- Canonical portable verification plus fresh architecture/security review.

### Expected result
A user revision such as “make it shorter” safely creates a new workflow revision and forces only dependent artifacts to rebuild, without mutating history or reusing stale authority.

### Evidence basis
- `docs/ROADMAP_1.0.md` defines R3.5 Revision invalidation immediately after R3.4 restricted specialist roles.
- Current TaskPlanner state covers R3.1 through R3.4 in TASK-016 through TASK-019; no open issue, PR, or task represents R3.5.

### Projection basis
- Voice-driven revisions, narration/render regeneration, and exact publication approval require deterministic stale-output invalidation before durable waits or external side effects are added.

### Risks / unknowns
- Concurrent edits and multi-user merge semantics remain out of scope for the single-active-workflow 1.0 path.
- Invalidation must stay graph-derived and minimal; broad “rebuild everything” behavior would hide dependency mistakes.

---

## TASK-019: Implement restricted specialist roles
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-3, workflow, roles
**Updated:** 2026-10-03

### Goal
Add bounded logical specialist roles for durable workflows while keeping all execution authority in the existing trusted runtime.

### Scope
- Define coordinator, writer, visual planner, narrator, renderer, and publisher roles.
- Restrict each role's visible context and capability families.
- Bind roles to durable workflow nodes and scheduler dispatch.
- Role restrictions may narrow existing authority only; they never grant policy, approval, execution, or verification authority.
- Reuse artifact references/provenance rather than exposing unrestricted workspace state.
- Do not add privileged independent executors or background daemons.

### Dependencies
- TASK-016 workflow persistence.
- TASK-017 DAG scheduler.
- TASK-018 artifact workspace.

### Plan
- Define a closed role catalog.
- Filter context and capability families at workflow-node dispatch.
- Intersect role allowances with existing registry/policy authority.
- Persist selected role and allowed families with workflow state.
- Add isolation and privilege-escalation tests.

### Acceptance
- Every specialist node uses one known restricted role.
- Role selection can only reduce available capabilities.
- Unrelated context, secrets, and artifacts remain unavailable.
- Unknown roles fail closed.
- Roles cannot approve work, alter policy, bypass verification, or select privileged executors.
- No parallel permission system is introduced.

### Verification
- Role catalog and context-filter tests.
- Capability-intersection tests.
- Cross-role isolation tests.
- Negative privilege-escalation tests.
- Canonical verification and architecture/security review.

### Expected result
LAIN_OS can dispatch bounded specialist perspectives inside a durable workflow while preserving one trusted authority layer.

### Evidence basis
- `docs/ROADMAP_1.0.md` defines R3.4 Restricted specialist roles after R3.1-R3.3.
- No current TaskPlanner task, open issue, or open PR represents R3.4.

### Projection basis
- Later revision, waiting, and media-production stages need explicit bounded stage roles without privileged autonomous sub-agents.

### Risks / unknowns
- Role-specific model/prompt choices remain deferred until actual stages require them.
- Capability-family granularity must reuse existing registry semantics.

---

## TASK-018: Implement immutable artifact workspace
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, artifacts
**Updated:** 2026-10-03

### Goal

Add the durable artifact workspace required for multi-stage workflows, using immutable content identity and explicit revision/provenance metadata without turning artifacts into executable authority.

### Scope

- Store workflow artifacts under immutable content hashes.
- Track logical artifact references, revisions, media/type metadata, provenance, producer node, and verification status.
- Use atomic writes and fail closed on hash mismatch or corrupt metadata.
- Expose user-visible workspace listing/readback without arbitrary path traversal.
- Define bounded retention/cleanup semantics that never delete artifacts still referenced by active/recoverable workflows.
- Keep artifact content/data non-authoritative: it cannot grant capabilities, approvals, or execution rights.
- Do not implement specialist roles, revision invalidation propagation, provider polling, or publication.

### Dependencies

- TASK-016 complete: durable workflow persistence model.
- TASK-017 complete: scheduler consumes artifact readiness.
- Existing safe filesystem/atomic-write/hash patterns should be reused.

### Plan

- Define artifact identity and metadata records around SHA-256 content hashes.
- Implement scoped atomic write/read/list APIs under a dedicated workspace root.
- Persist logical revision/provenance references in workflow-compatible metadata.
- Add reference-aware retention guards and explicit cleanup candidates.
- Add corruption/hash-mismatch/path-traversal and active-reference negative tests.
- Integrate only the minimal scheduler-facing readiness seam.

### Acceptance

- Artifact identity is immutable and derived from verified content bytes.
- Revisions create new identities rather than mutating prior content in place.
- Metadata preserves producer/provenance/type/revision/verification information.
- Hash mismatch or corrupt metadata fails explicitly.
- Artifact paths cannot escape the configured workspace root.
- Cleanup cannot remove artifacts still referenced by active or recoverable workflow state.
- Artifact content cannot authorize or execute capabilities.

### Verification

- Focused hash/round-trip/revision/provenance tests.
- Atomic-write and corruption/hash-mismatch tests.
- Path-traversal/symlink-escape tests.
- Retention tests with active/recoverable references.
- Canonical portable verification and architecture/security review.

### Expected result

LAIN_OS gains an inspectable, immutable artifact substrate for scripts, narration, media, and other workflow outputs without coupling content storage to execution authority.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R3.3 Artifact workspace after persistence and DAG scheduling.
- No current TaskPlanner task, open issue, or open PR represents R3.3.

### Projection basis

- Revision invalidation, specialist handoff, media production, and external publishing all require stable immutable artifact identities and provenance.

### Risks / unknowns

- Large media streaming/storage optimization should follow real workload evidence; v1 should prefer simple local bounded files.
- Retention policy must remain conservative until storage-pressure behavior is explicitly specified.

---

## TASK-017: Implement bounded DAG scheduler
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, scheduler
**Updated:** 2026-10-03

### Goal

Schedule ready workflow nodes from the durable TASK-016 model without allowing dependency violations, unbounded concurrency, or execution before required verified outputs exist.

### Scope

- Compute node readiness from explicit dependencies and durable node state.
- Support one active workflow initially.
- Use bounded worker leases with explicit acquisition/release/expiry semantics.
- Prevent downstream execution until required upstream outputs exist and satisfy their acceptance/verification predicates.
- Persist scheduler transitions through the workflow store before dispatching effects.
- Preserve trusted capability policy/approval/execution/verification/audit below the scheduler.
- Do not implement artifact storage, specialist role internals, revision invalidation, long polling, or external-effect reconciliation beyond the interfaces required to avoid unsafe replay.

### Dependencies

- TASK-016 complete: durable workflow persistence model.
- Existing trusted action runtime and verification semantics remain authoritative.

### Plan

- Define deterministic readiness rules over the persisted workflow graph.
- Detect dependency cycles and invalid/missing references before scheduling.
- Add bounded lease acquisition/renewal/release for ready nodes.
- Persist running/terminal scheduler transitions atomically around dispatch boundaries.
- Gate downstream readiness on verified required outputs, not planner claims.
- Add deterministic tests for ordering, cycles, lease contention/expiry, failure propagation, and verification-gated readiness.

### Acceptance

- A node becomes runnable only when all required dependencies satisfy their declared verified-output conditions.
- Cycles, missing dependencies, or invalid graph state fail closed.
- No two workers can hold the same active node lease simultaneously.
- Lease expiry cannot silently duplicate a known completed effect.
- Failed/cancelled/reconciliation upstream nodes do not incorrectly unlock dependents.
- Scheduler state cannot authorize capabilities or bypass policy/approval.
- One-active-workflow limit is enforced explicitly.

### Verification

- Focused DAG/readiness/cycle tests.
- Lease contention/expiry/recovery tests.
- Negative tests proving unverified upstream outputs cannot unlock downstream nodes.
- Crash-boundary tests around durable state transitions where deterministic.
- Canonical portable verification and fresh architecture review.

### Expected result

LAIN_OS can advance a durable workflow graph in dependency order with bounded ownership and verified-output gating while leaving effect authority in the existing trusted runtime.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R3.2 DAG scheduler immediately after the workflow persistence model.
- No current TaskPlanner task, open issue, or open PR represents R3.2.

### Projection basis

- Artifact, specialist, revision, wait/poll, and reconciliation layers all need deterministic dependency readiness and bounded node ownership.

### Risks / unknowns

- Cross-process/distributed leases are out of scope unless runtime topology actually requires them; start with the smallest local durable lease semantics.
- Exact reconciliation behavior for uncertain external effects belongs to R3.7 and must not be pre-implemented here.

---

## TASK-016: Define durable workflow persistence model
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, workflow, persistence
**Updated:** 2026-10-03

### Goal

Introduce the versioned durable workflow state model that Phase 3 can build on without weakening the existing trusted executor or replay protections.

### Scope

- Define a workflow schema covering stable ID, dependencies, status, restricted role, allowed capability families, artifact references/hashes, acceptance checks, attempted operations, budgets, retries, provider job IDs, deadlines, error/recovery state, revision, approvals, and reconciliation state.
- Implement atomic local persistence with strict validation, schema versioning, and fail-closed corrupt-state handling.
- Define migration boundaries for compatible future schema revisions.
- Preserve existing agent sessions and trusted action execution as lower-level primitives; do not replace policy/executor/verification/audit.
- Do not implement DAG scheduling, artifact storage, specialist execution, or external-effect retry in this task.

### Dependencies

- TASK-015 complete: Phase-2 voice acceptance gate.
- Existing atomic file/session-store patterns and audit/error models should be reused where appropriate.

### Plan

- Inventory current durable session/checkpoint patterns for reuse.
- Define the smallest versioned workflow record/state schema needed by R3.2-R3.8.
- Add strict serialization/deserialization and atomic write/read behavior.
- Fail closed on unknown versions, malformed fields, invalid transitions, duplicate IDs, or corrupt persisted state.
- Add migration hook structure without speculative migrations.
- Add focused persistence/corruption/version tests.

### Acceptance

- A valid workflow round-trips through durable storage without losing authoritative state.
- Unknown schema versions and corrupt state fail explicitly without silent reset or replay.
- Atomic writes cannot expose a partially written valid-looking workflow.
- Stable IDs/revisions/dependencies/status and reconciliation state are preserved exactly.
- No workflow record can grant capabilities or bypass policy/approval merely by persisted content.
- Existing agent/session persistence remains compatible and independently authoritative for its current scope.

### Verification

- Focused schema/round-trip/corruption/version tests.
- Atomic-write interruption/recovery tests where deterministic.
- Negative tests for duplicate IDs, invalid dependency references, invalid transitions, malformed budgets/approvals/reconciliation fields.
- Canonical portable verification after implementation.
- Fresh architecture review before integrating scheduler work.

### Expected result

LAIN_OS gains a strict durable workflow-state foundation that can support dependency scheduling, artifacts, revisions, long waits, and reconciliation without inventing those higher layers prematurely.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines R3.1 Workflow persistence model as the first Phase-3 work package.
- No current TaskPlanner task, open issue, or open PR represents R3.1.

### Projection basis

- Every later Phase-3 work package depends on a stable persisted workflow identity/state contract; defining it first prevents scheduler/artifact/revision layers from inventing incompatible durable state.

### Risks / unknowns

- Migration semantics beyond the initial version are intentionally deferred until a real second schema exists.
- Cross-process locking needs should follow actual runtime topology rather than speculative multi-writer support.

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

### Risks / unknowns

- Reference-device execution may be unavailable in CI; such latency evidence must remain explicitly UNVERIFIED.
- Live provider checks are supplementary; deterministic CI evidence remains the reproducible gate.

---

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

---

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

## TASK-012: Implement cancellable speech playback
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, android-audio
**Updated:** 2026-10-03

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

## TASK-011: Implement bounded voice turn manager
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, conversation
**Updated:** 2026-10-03

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

## TASK-010: Implement Android microphone lifecycle
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, android-audio
**Updated:** 2026-10-03

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

## TASK-009: Define bounded speech provider interfaces
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, provider-contract
**Updated:** 2026-10-03

### Goal

Establish the provider-neutral speech contracts that Phase 2 can build on without coupling Android microphone/playback lifecycle or trusted task authority to any specific STT/TTS vendor.

### Scope

- Define bounded transcription request/result and synthesis request/result contracts.
- Include provider identity/provenance, explicit timeout/cancellation semantics, media format metadata, and bounded payload/duration fields needed by later Android adapters.
- Keep speech providers outside capability authorization, policy, approval, execution, verification, audit, and durable workflow authority.
- Preserve typed text as a complete fallback path.
- Define failure categories sufficient for unavailable provider, timeout, cancellation, malformed response, unsupported media, and bounded-resource rejection.
- Do not implement microphone capture, playback, provider SDKs, or vendor credentials in this task.

### Dependencies

- TASK-008 complete: Phase-1 planner acceptance/adversarial gate.
- Existing trusted session/controller contracts must remain authoritative.
- Existing secret-handling boundary remains unchanged for any future provider credential references.

### Plan

- Inventory existing voice/speech references and reusable cancellation/error patterns.
- Define the smallest provider-neutral speech request/result interfaces and error model.
- Specify cancellation/timeout ownership and provenance fields without granting provider-side execution authority.
- Add deterministic contract tests for valid, malformed, cancelled, timed-out, and oversized inputs/results.
- Document the seam expected by later Android microphone lifecycle and playback tasks without pre-implementing those layers.

### Acceptance

- Transcription and synthesis each have explicit provider-neutral request/result contracts.
- Contracts carry enough format/provenance metadata for later Android adapters without embedding vendor-specific fields.
- Cancellation and timeout produce explicit non-success outcomes.
- Oversized or malformed provider data fails closed.
- Speech provider output cannot authorize capabilities, approve consequential actions, or mutate durable task state directly.
- Typed interaction remains independent of speech-provider availability.
- No raw provider credential is introduced into speech request/result payloads or durable artifacts.

### Verification

- Focused unit tests for request/result validation, bounds, cancellation, timeout, and malformed provider data.
- Static inspection confirming no capability/policy/executor authority is exposed through the speech interface.
- Canonical portable verification after implementation.
- Android build remains a downstream verification requirement when the interface is wired into platform adapters.

### Expected result

Phase 2 has a stable, bounded speech-provider seam that can support replaceable STT/TTS adapters while preserving LAIN_OS authority boundaries and allowing microphone, turn-management, and playback work to proceed independently.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines Phase 2 Voice Conversation and explicitly lists R2.1 Speech provider interfaces as the first work package.
- The 1.0 dependency graph sequences Phase 2 after the Phase-1 planner runtime gate.
- No current TaskPlanner task, open issue, or open PR represents R2.1.

### Projection basis

- Stabilizing speech request/result, cancellation, timeout, provenance, and failure contracts before microphone/playback integration reduces cross-module churn across R2.2-R2.6.
- This interface is a necessary dependency seam for replaceable speech providers and the voice-first 1.0 release outcome.

### Risks / unknowns

- Concrete codec/container choices may need adjustment when Android capture/playback constraints are implemented; keep the initial interface minimal and extensible rather than provider-specific.
- Streaming/partial-transcript support may require a later compatible extension; do not over-specify it before turn-manager requirements are implemented.
- Provider credential storage/selection may share later settings infrastructure, but this task must not invent that UI or persistence prematurely.

---

