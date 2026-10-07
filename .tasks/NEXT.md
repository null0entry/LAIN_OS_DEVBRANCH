# Next

## TASK-068: Verify Android release compatibility matrix
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, android, compatibility, api, abi
**Updated:** 2026-10-07

### Goal

Prove that one exact release-candidate APK matches its declared Android API/ABI support and remains installable, launchable, and truthful across the supported automated/emulator matrix without promoting emulator evidence to physical-device acceptance.

### Scope

- Consume TASK-047 candidate identity and TASK-049 release packaging outputs; do not create a second packaging path.
- Derive the declared min/target SDK and packaged ABI set from authoritative build/package metadata and bind them to the exact candidate checksum.
- Exercise install, cold launch, runtime startup, bounded smoke interaction, and clean shutdown on the supported emulator/API matrix already represented by repository CI, extending only where the declared support contract requires it.
- Inspect packaged native libraries/ABI metadata and fail when declaration and artifact contents disagree.
- Record emulator/automated compatibility separately from TASK-015/TASK-036 physical/OEM evidence and TASK-057 upgrade/migration evidence.
- Do not broaden supported Android versions/ABIs, change signing policy, publish artifacts, or infer unsupported hardware compatibility.

### Dependencies

- TASK-047 release evidence manifest and evidence-strength rules.
- TASK-049 canonical release-candidate packaging/signing boundary.
- TASK-065 clean-environment reproducibility proof before final release-readiness consumption.
- Existing Android API-24/API-35 CI provides reusable matrix mechanics; TASK-057 separately owns installed upgrade/state migration.

### Plan

- Extract authoritative SDK/package/ABI declarations and candidate identity from the existing build/package path.
- Add the smallest candidate-bound compatibility evidence producer using existing Android CI/emulator helpers.
- Install and launch the exact artifact across declared representative API levels, exercise bounded startup/smoke/shutdown behavior, and independently inspect package/native metadata.
- Add negative fixtures for checksum drift, package/version mismatch, SDK mismatch, missing/unexpected ABI, install failure, startup failure, and evidence from a different candidate.
- Feed machine-readable results into TASK-047 without weakening evidence classes.

### Acceptance

- Compatibility evidence names exact source revision, candidate checksum, package/version, min/target SDK, declared ABI set, tested API level/architecture, and result.
- The exact candidate installs and cold-launches on every required automated/emulator matrix entry or the gate fails with the failing entry named.
- Artifact inspection and declared API/ABI metadata agree; missing, unexpected, or incompatible packaged native code cannot silently pass.
- Evidence from another checksum/source/version is rejected as stale.
- Emulator success is never labeled physical/OEM acceptance; unavailable hardware remains UNVERIFIED.
- No compatibility result grants capability, approval, signing, publication, or release authority.

### Verification

- Candidate-bound Android matrix runs at the declared minimum and current high API representative plus every additional level required by repository policy.
- Independent APK package/version/SDK/native-library inspection.
- Wrong-checksum, wrong-version, SDK-boundary, ABI-mismatch, install-failure, launch-failure, and stale-evidence negatives.
- Canonical Verify plus Android instrumentation/smoke checks on the exact candidate.
- Architecture/release/security review and TASK-047 manifest validation.

### Expected result

Release review receives reproducible, candidate-bound proof of what Android API/ABI compatibility was actually exercised, while physical/OEM and upgrade evidence remain separately truthful gates.

### Evidence basis

- `docs/ROADMAP_1.0.md` R1.0 release-candidate requirements require supported API/ABI declaration and compatibility decisions.
- TASK-049 requires package identity, checksum, supported API/ABI set, signing identity, and migration semantics to be bound into release evidence.
- Current Android CI repeatedly exercises API 24 and API 35, but no canonical task binds that matrix to the final release-candidate artifact and declared ABI contents.

### Projection basis

- TASK-051 cannot truthfully close installed 1.0 readiness from source-level CI alone when the packaged candidate may differ in SDK, ABI, signing, or install/launch behavior.
- Candidate-bound compatibility evidence catches packaging/toolchain drift before release without duplicating TASK-015/TASK-036 physical acceptance or TASK-057 migration testing.

### Risks / unknowns

- Final ABI support may remain architecture-neutral if the APK contains no native libraries; evidence must report that fact rather than invent ABI coverage.
- Emulator availability may not cover every OEM/runtime behavior; those remain physical UNVERIFIED evidence.
- Signing or packaging changes invalidate prior compatibility evidence through candidate identity.

---

## TASK-067: Implement bounded artifact-storage admission and pressure handling
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, phase-4, artifacts, storage, operability
**Updated:** 2026-10-07

### Goal

Prevent durable media and artifact-producing workflows from overcommitting local storage, promoting partial output, or deleting referenced evidence when free space becomes insufficient.

### Scope

- Add one trusted workspace-scoped storage-admission boundary that distinguishes configured workflow byte budgets from actual currently available local capacity.
- Preflight declared input, staging, and maximum-output requirements before dispatching artifact-producing nodes.
- Bind reservations to workflow, revision, node, and attempt identity; persist enough state to reconcile reservations after restart, cancellation, timeout, or crash.
- Charge actual committed artifact bytes into TASK-023 aggregate budgets and release unused reservations deterministically.
- Keep staging/partial files outside verified artifact identity until atomic commit and TASK-018 hash/provenance validation succeed.
- Reclaim only proven unreferenced temporary files or expired reservations under explicit conservative rules; never auto-delete referenced, active, recoverable, exported, or audit/evidence artifacts.
- Do not add a generic disk cleaner, arbitrary filesystem access, device-wide storage management, speculative compression, or hidden owner-limit increases.

### Dependencies

- TASK-018 immutable artifact workspace and reference-aware retention semantics.
- TASK-023 aggregate workflow byte budgets and persisted monotonic accounting.
- TASK-025 media artifact schemas provide bounded declared byte metadata for Phase-4 producers.
- Existing Android/app-private storage inspection and atomic file patterns remain authoritative where reusable.

### Plan

- Define the smallest storage snapshot, reservation, and settlement records required at the workspace boundary.
- Gate artifact-producing dispatch on both remaining workflow bytes and current capacity minus active reservations plus a documented safety floor.
- Persist reservation identity before work starts; settle actual committed bytes atomically with verified artifact registration.
- Reconcile orphaned reservations and partial staging files after restart without guessing success or deleting referenced content.
- Add concurrent-reservation, pressure-change, cancellation, crash, stale-revision, and conservative-cleanup tests.

### Acceptance

- A node whose declared bounded output cannot fit is not dispatched and produces an explicit non-success storage-pressure outcome naming required and available capacity.
- Concurrent workflows cannot reserve the same free bytes or bypass the stricter TASK-023 byte budget.
- Reservations survive or reconcile deterministically across restart; cancellation, timeout, and failed attempts cannot leak permanent capacity.
- Partial, corrupt, stale-revision, or oversized output never becomes a verified artifact or unlocks downstream nodes.
- Only proven unreferenced temporary content may be reclaimed automatically; active/recoverable/reference-held artifacts remain intact.
- Capacity changes between preflight and commit are rechecked and fail closed without fabricating completion.
- No storage record or file content grants capability, approval, execution, or publication authority.

### Verification

- Deterministic fake-capacity tests for admission, exact-boundary, safety-floor, and insufficient-space outcomes.
- Concurrent reservation and aggregate-byte-budget intersection tests.
- Crash/restart/orphan reconciliation plus cancellation/timeout settlement tests.
- Partial-output, stale-revision, hash mismatch, path escape, symlink, and referenced-artifact retention negatives.
- Android/app-private storage integration checks plus canonical verification and architecture/security/operability review.

### Expected result

Artifact-producing workflows fail early and recover truthfully under storage pressure, while verified active evidence remains preserved and later renderer/provider stages share one bounded storage-admission contract.

### Evidence basis

- `docs/ROADMAP_1.0.md` R4.4 requires renderer byte/storage limits and explicit resource-exhaustion failure; R7.4 requires insufficient-storage adversarial evidence.
- TASK-018 owns immutable storage and conservative retention, while TASK-023 owns declared workflow byte budgets; neither reserves real current capacity or reconciles concurrent storage pressure.
- TASK-028, TASK-031, provider downloads, and the installed golden workflow will create large staged files that require this shared boundary.

### Projection basis

- Without admission and reservation semantics, individually bounded producers can still overcommit the same free space, leave orphaned staging files after crashes, or discover disk exhaustion only after expensive work.
- Establishing the boundary before renderer/provider integration removes a foreseeable cross-stage bottleneck without choosing a renderer or widening filesystem authority.

### Risks / unknowns

- OS-reported free capacity can change concurrently; recheck at commit and use a documented conservative safety floor rather than promising perfect reservation.
- Exact default floors and per-artifact maxima should follow supported-device evidence; keep them configurable only within trusted owner policy.
- Cleanup must remain conservative until TASK-018 reference tracking exists; unavailable proof means leave content untouched.

---

## TASK-065: Prove clean-environment 1.0 build reproducibility
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, reproducibility, supply-chain
**Updated:** 2026-10-07

### Goal

Prove that one exact 1.0 source revision and declared build-input set produce the same release-candidate bytes and provenance in isolated clean environments, or expose every remaining nondeterministic input before release evidence is accepted.

### Scope

- Consume TASK-049's release packaging path and TASK-047's evidence identity contract.
- Pin and inventory build-critical toolchain, dependency, wrapper, plugin, SDK, and native-input identities without adding a second build system.
- Run at least two isolated clean builds from the same source and declared inputs.
- Compare release artifact bytes/checksums plus package/version/API/ABI and signer metadata when authorized signing inputs are available.
- Diagnose nondeterminism to bounded sources and fail the reproducibility gate explicitly rather than normalizing mismatched artifacts into a false pass.
- Emit machine-readable reproducibility evidence suitable for TASK-048/TASK-051.
- Do not create signing credentials, weaken signature checks, publish artifacts, choose a distribution channel, or claim 1.0 readiness.

### Dependencies

- TASK-047 defines candidate-bound release evidence and evidence-strength rules.
- TASK-049 provides the canonical release-candidate packaging/signing boundary.
- TASK-052 provides dependency/license notice coverage for shipped components.
- Maintainer-controlled signing material is required only for the final signed comparison; unsigned/configuration checks remain independently actionable.

### Plan

- Inventory every build input that can alter release bytes or metadata.
- Add the smallest clean-build harness using the existing Gradle/repository path.
- Build twice in isolated workspaces with identical declared inputs and compare digests plus independently inspected package metadata.
- Add negative fixtures proving source, dependency, toolchain, version, package, signer, or environment drift invalidates the result.
- Record exact mismatches and evidence strength through TASK-047; keep unavailable signing/device evidence explicit.

### Acceptance

- Repeated isolated builds from the same source and declared inputs either produce byte-identical candidate artifacts or a deterministic FAIL naming every differing input/output.
- Source commit, dependency/toolchain identities, package/version, supported API/ABI set, artifact checksum, and signer identity are bound together.
- Hidden network-fetched or floating build inputs cannot silently satisfy the gate.
- Debug signing, missing signing inputs, mismatched signer identity, changed dependencies, and changed toolchain versions cannot pass as the same candidate.
- Reproducibility evidence is machine-readable, bounded, secret-free, and invalidated by any candidate identity change.
- No mismatch is concealed by comparing only normalized metadata when final release bytes differ.

### Verification

- Two clean-workspace builds and exact artifact digest comparison.
- Package/version/API/ABI/signature inspection on each produced candidate.
- Source/dependency/toolchain/signer/package/version drift negative tests.
- Offline or locked-input failure checks where supported by the existing build.
- Secret scan of build configuration, logs, provenance, and evidence outputs.
- Fresh build/release/security review and TASK-047 manifest validation.

### Expected result

Release review can prove whether the exact 1.0 candidate is reproducible from declared inputs instead of trusting a one-off build, with nondeterminism and unavailable signing evidence surfaced truthfully.

### Evidence basis

- `docs/ROADMAP_1.0.md` Release Engineering requires a reproducible release build bound to source, version, signing identity, API/ABI declaration, checksum, and evidence.
- TASK-049 creates the packaging path, but no canonical task independently proves repeated clean-environment byte reproducibility or detects hidden/floating build inputs.
- TASK-047 classifies evidence and TASK-051 consumes it; neither produces this reproducibility proof.

### Projection basis

- Candidate-bound automated, physical, media, and live-service evidence becomes stale if the same declared source cannot recreate the same artifact.
- An independent reproducibility gate prevents late dependency/toolchain drift from invalidating release evidence or substituting a different binary under the same version.

### Risks / unknowns

- APK/ZIP ordering, timestamps, native tooling, or signing behavior may expose nondeterminism; the task must report and bound it rather than waive it.
- Final signed comparison depends on owner-controlled signing material; absence remains UNVERIFIED and does not block clean unsigned/configuration diagnostics.
- Some remote repositories may be needed for dependency resolution; pin identities and record availability without vendoring speculative dependencies.

---

## TASK-064: Close Phase-5 external-provider acceptance gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-5, provider, acceptance, privacy, budgets, resilience

### Goal
Prove that one current-revision workflow can use configured remote providers through the shared bounded provider lifecycle while local trusted policy, approval, execution, verification, audit, privacy, budget, cancellation, and recovery semantics remain unchanged.

### Scope
- Integrate TASK-032 through TASK-038 across at least one bounded remote speech path and one bounded remote media path using the production provider-job abstraction.
- Exercise explicit provider/profile selection and TASK-038 disclosure before any text, audio, or image payload leaves the device.
- Prove durable submit/poll/cancel/terminal handling, aggregate call/cost ceilings, bounded retry/backoff, and uncertain-write reconciliation across restart.
- Verify remote outputs enter the same immutable artifact/provenance/revision validation boundaries as local outputs and never grant capability or approval authority.
- Demonstrate that Local selection never becomes Cloud automatically and provider failure preserves a truthful local/non-success outcome.
- Persist candidate-bound automated, emulator, and separately labeled live-provider evidence suitable for TASK-047.
- Do not add new provider features, generic authenticated HTTP, publication, silent fallback, unbounded payloads, or production credentials in fixtures.

### Dependencies
- TASK-024: durable-workflow acceptance foundation.
- TASK-032: bounded provider-job lifecycle.
- TASK-033 and TASK-034: bounded remote speech and media adapters.
- TASK-035: aggregate provider spending controls.
- TASK-037: bounded provider retry/backoff policy.
- TASK-038: explicit privacy disclosure and data-sharing state.
- TASK-018/TASK-020/TASK-021/TASK-022/TASK-023: artifact, revision, wait, reconciliation, and workflow-budget foundations.
- TASK-047 for final release-manifest consumption; this task produces Phase-5 evidence without pre-building release packaging.

### Plan
- Define the smallest cross-adapter acceptance fixture and evidence manifest inputs without creating a parallel provider framework.
- Compose speech and media fake-provider flows through the shared lifecycle, budgets, retry, disclosure, artifact, and revision seams.
- Add deterministic crash/restart, cancellation, retry, budget-exhaustion, stale-revision, malformed-result, and uncertain-write scenarios.
- Add Android disclosure/profile-selection assertions and prove Local-to-Cloud non-fallback.
- Run canonical and Android matrices, architecture/security/privacy review, secret scans, and separately authorize any live-provider acceptance.
- Record exact source/build/provider-environment identity and evidence strength for downstream TASK-047 consumption.

### Acceptance
- The same durable workflow can use configured remote speech and media providers without bypassing local policy, approval, executor, verifier, audit, or artifact authority.
- Every outbound payload is preceded by explicit current profile/destination disclosure; Local selection never silently switches to Cloud.
- Provider calls, bytes, time, retries, and actual/estimated cost remain under durable aggregate ceilings across restart and concurrency.
- Only safe idempotent/read operations retry automatically; uncertain writes enter reconciliation and never replay blindly.
- Cancellation, deadline, auth, quota, 429/5xx, malformed/oversized result, stale revision, and provider unavailability settle truthfully without fabricated success.
- Remote media is independently downloaded, bounded, decoded, hashed, provenance-bound, and current-revision validated before workflow use.
- Remote speech output remains provider data, never capability, approval, or task authority.
- Secrets and sensitive provider payloads remain absent from planner envelopes, durable workflow state, audit, logs, IPC, exports, and test fixtures.
- Automated/emulator evidence and separately authorized live-provider evidence are labeled distinctly; unavailable live evidence remains UNVERIFIED.

### Verification
- Shared fake-provider acceptance matrix across speech and media adapters.
- Durable submit/poll/cancel/restart and duplicate/uncertain-effect reconciliation tests.
- Aggregate call/cost/bytes/time/retry budget exhaustion and concurrency tests.
- Disclosure/profile/revision/destination substitution and Local-to-Cloud non-fallback negatives.
- Artifact retrieval, decoder, hash, provenance, stale-revision, malformed, truncated, oversized, and hostile-metadata fixtures.
- Auth/quota/429/5xx/Retry-After/timeout/cancellation/provider-unavailable cases.
- Secret and sensitive-payload scans; canonical Verify; Android API matrix; architecture/security/privacy review.
- Separately owner-authorized live smoke evidence, if available, bound to exact provider profile, build, date, and redacted receipt.

### Expected result
Phase 5 closes only when configured remote providers demonstrably operate through one bounded, privacy-visible, budgeted, restart-safe lifecycle while every trusted local authority boundary remains unchanged.

### Evidence basis
- `docs/ROADMAP_1.0.md` defines a distinct Phase 5 exit gate: the same workflow can use configured remote providers under bounded budgets and failure handling while trusted local policy/execution semantics remain unchanged.
- TASK-032 through TASK-038 represent the individual Phase-5 capabilities, but no current TaskPlanner task, issue, or open PR represents their integrated exit-gate proof.

### Projection basis
- TASK-047 and TASK-051 require phase-level evidence rather than assuming individually implemented adapters compose safely.
- A dedicated gate prevents remote-provider integration from bypassing disclosure, shared budgets, revision invalidation, or uncertain-write reconciliation late in release hardening.

### Risks / unknowns
- Live-provider credentials, quotas, pricing, and availability are external and may remain UNVERIFIED; deterministic fixtures must not be promoted to live evidence.
- Speech and media adapters may expose different provider metadata; normalize only what the shared lifecycle requires.
- Provider policy/API drift must be verified from current official documentation during implementation.
- This gate must not block independent local workflow work, but Phase 5 and final 1.0 readiness cannot be claimed without its evidence.

---


## TASK-062: Implement bounded media timeline assembly workflow
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, workflow, media, timeline
**Updated:** 2026-10-04

### Goal
Create the durable Phase-4 stage that assembles current-revision narration, visual-plan, visual-asset, caption, and output settings into one validated TASK-027 timeline for TASK-028.

### Scope
- Consume verified outputs from TASK-026, TASK-056, TASK-061, and TASK-059.
- Build TASK-027 scenes, caption timing, transitions, and output constraints.
- Persist one immutable revision-bound timeline with upstream identities and provenance.
- Reuse TASK-020 invalidation when upstream content changes.
- Rendering, inspection, preview/export, external generation, and publication remain separate tasks.

### Dependencies
TASK-024, TASK-025, TASK-026, TASK-027, TASK-056, TASK-061, TASK-059, plus existing artifact/revision foundations.

### Plan
- Reuse scheduler, artifact, revision, and media contracts.
- Map verified narration and visual outputs into deterministic scenes/captions.
- Validate the full TASK-027 timeline before persistence.
- Bind the result to exact upstream artifacts/revision.
- Add restart, cancellation, idempotency, and stale-input coverage.

### Acceptance
- Equivalent verified inputs produce exactly one deterministic validated timeline.
- Missing, stale, wrong-type, mismatched-revision, corrupt, oversized, or incomplete inputs fail explicitly.
- Caption/scene/transition timing satisfies TASK-027 bounds.
- Upstream revisions invalidate the current timeline and downstream render while preserving history.
- Restart cannot promote a partial or duplicate result.
- The stage cannot alter capability, approval, policy, or credential state.

### Verification
- Deterministic assembly and serialization tests using TASK-027 validation.
- Narration/visual/caption ordering fixtures.
- Hash/type/revision/stale/oversize/missing-input negatives.
- Revision invalidation, restart, cancellation, and idempotency tests.
- Provenance and authority-boundary review.
- Canonical verification plus architecture/security/media review.

### Expected result
Phase 4 gains one production producer for the renderer-ready timeline rather than relying on renderer-specific or fixture-specific composition.

### Evidence basis
R4.3 requires a deterministic scene/image/audio/caption/transition timeline. TASK-027 defines that representation but not the durable stage that creates it from TASK-026/TASK-056/TASK-061/TASK-059 outputs. No current task owns this assembly boundary.

### Projection basis
Without this stage, TASK-028 or TASK-031 would absorb orchestration and duplicate revision/artifact responsibilities.

### Risks / unknowns
Keep scene segmentation minimal and deterministic. Reuse narration timing for captions where available. Extend TASK-027 only if one required field is demonstrably missing.

---

## TASK-061: Implement bounded visual-planning workflow stage
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, workflow, visual-planning, artifacts
**Updated:** 2026-10-04

### Goal

Add the missing visual-planning stage between the verified script artifact and TASK-059 visual-asset production so the visual-planner role emits one bounded, revision-bound plan instead of letting asset generation infer scene requirements ad hoc.

### Scope

- Consume only the verified current-revision script artifact and explicitly allowed workflow context.
- Execute through the restricted visual-planner role from TASK-019; model output remains typed data with no capability, policy, approval, spending, or executor authority.
- Produce one immutable visual-plan artifact describing an ordered bounded set of scene/asset intents sufficient for TASK-059 and TASK-027.
- Bind each plan to workflow ID, revision, source script hash, producer identity, provenance, and deterministic ordering.
- Bound scene count, text fields, requested asset types, dimensions/aspect hints, and total serialized size using existing workflow/provider budget intersections.
- Reject unknown asset kinds, arbitrary paths/URLs/commands, stale script revisions, malformed/oversized output, and unsupported capability requests.
- Preserve prior plan artifacts on revision and invalidate only dependent visual assets/timeline/render outputs through TASK-020.
- Do not fetch/generate external media, render images, synthesize narration, construct the final timeline, or add provider-specific APIs here.

### Dependencies

- TASK-018 immutable artifact workspace.
- TASK-019 restricted specialist roles.
- TASK-020 workflow revision invalidation.
- TASK-023 aggregate workflow budgets.
- TASK-024 Phase-3 durable-workflow acceptance.
- TASK-025 media artifact schema vocabulary where asset-kind/shape references are required.
- TASK-056 bounded script-generation stage produces the verified source script.
- TASK-059 consumes the verified plan when producing local image/title-card artifacts.

### Plan

- Define the smallest typed visual-plan request/result and immutable artifact representation that TASK-059/TASK-027 demonstrably need.
- Reuse workflow role dispatch, artifact storage, revision, provenance, and budget primitives rather than adding a parallel planning subsystem.
- Validate source script identity/revision before dispatch and validate every returned scene/asset intent before artifact acceptance.
- Persist the verified plan artifact and expose its identity to downstream nodes.
- Wire script revision to invalidate the plan and its transitive dependent outputs without mutating prior history.
- Add deterministic fake-planner tests for ordering, malformed/oversized/hostile fields, stale revisions, restart/replay, cancellation, and budget limits.

### Acceptance

- One verified current-revision script produces exactly one verified current-revision visual-plan artifact with deterministic scene ordering and provenance.
- Every plan references the exact source script hash/revision and cannot outlive or silently rebind to a changed script.
- Unknown asset kinds, arbitrary path/URL/command instructions, malformed fields, oversized plans, stale revisions, timeout/cancellation, or exhausted budgets fail closed.
- Visual-planner output cannot grant capabilities, authorize external media calls, approve spending/publication, or select arbitrary executors.
- Revising the script preserves prior immutable visual plans while invalidating the current plan and only its dependent visual/timeline/render artifacts.
- TASK-059 can consume the typed plan without parsing free-form planner prose or inventing its own scene-plan format.
- Restart/resume cannot duplicate a completed current-revision visual-plan stage.

### Verification

- Typed request/result schema and deterministic-ordering unit tests.
- Restricted-role context/capability and hostile-output authority negatives.
- Source-script hash/revision mismatch and stale-plan invalidation tests.
- Restart/replay/cancellation/timeout/aggregate-budget tests through the durable workflow boundary.
- Deterministic fake visual-planner integration proving TASK-059 receives only verified plan artifacts.
- Canonical Verify plus architecture/security review of script -> visual planner -> artifact -> asset-stage boundaries.

### Expected result

LAIN_OS gains one durable visual-planning source of truth: a verified script becomes a bounded immutable scene/asset plan that TASK-059 and the timeline consume without hidden inference, parallel state, or provider authority.

### Evidence basis

- `docs/ROADMAP_1.0.md` defines a restricted **visual planner** role and the Phase-7 golden flow requires “visuals prepared” after script production/revision.
- `docs/specs/2026-10-01-voice-agent-suite-1.0-design.md` explicitly names a visual planner that exchanges durable typed artifacts rather than unlimited transcripts.
- TASK-059 currently consumes “script/visual-plan inputs” but no TaskPlanner item produces a visual-plan artifact; repository search found no visual-planning workflow-stage implementation task.

### Projection basis

- Without an explicit plan producer, TASK-059 must infer visual requirements directly from script/model prose, creating a second implicit planning boundary and making revision provenance ambiguous.
- A small typed stage decouples content planning from asset realization and lets local assets now and TASK-034 external generation later share the same verified intent contract.

### Risks / unknowns

- Keep the plan schema minimal and driven by TASK-059/TASK-027 consumers; do not build a general storyboard/NLE format.
- Exact asset-kind vocabulary should reuse TASK-025 media contracts where available instead of adding another enum hierarchy.
- Provider/model quality is not an acceptance criterion; only bounded lifecycle, provenance, authority isolation, and deterministic validation are.

---

## TASK-060: Establish 1.0 performance and resource acceptance evidence
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, performance, resources, release-evidence
**Updated:** 2026-10-04

### Goal
Produce repeatable, candidate-bound performance/resource evidence for roadmap R7.7 so 1.0 acceptance can distinguish measured behavior from assumptions across voice, workflow recovery, and long-running media work.

### Scope
- Define measurable evidence for voice interruption latency, trusted Stop receipt latency, memory pressure, thermal pressure, long-render responsiveness, background/foreground transitions, and restart timing.
- Reuse existing instrumentation and phase acceptance paths; add only the minimum measurement hooks/fixtures needed for reproducible observations.
- Separate emulator/CI measurements from physical-device measurements and never promote weaker evidence.
- Bind results to exact source/build/device/environment identity through TASK-047; candidate packaging identity comes from TASK-049.
- Do not introduce telemetry upload, background tracking, new provider dependencies, or release/publish effects.

### Dependencies
- TASK-015 Phase-2 integrated voice acceptance for voice/Stop behavior.
- TASK-024 durable-workflow acceptance for restart/background behavior.
- TASK-031 Phase-4 offline golden media fixture for long-render responsiveness.
- TASK-036 physical Voice-First preview acceptance for reference-device measurements.
- TASK-047 release evidence provenance contract and TASK-049 release-candidate identity.

### Plan
- Define measurement points, units, evidence-strength labels, and deterministic fixtures for each R7.7 cell.
- Add bounded automated pressure/lifecycle/restart cases where CI can truthfully exercise them.
- Record physical-only latency/thermal/resource observations separately on declared device/build identities.
- Emit one candidate-bound result set consumable by TASK-048 and TASK-051.

### Acceptance
- Every R7.7 item has an explicit PASS/FAIL/UNTESTED/UNAVAILABLE result with source/build/environment provenance.
- Voice interruption and trusted Stop measurements come from the real exercised path, not planner/provider assertions.
- Memory/thermal/long-render testing cannot hide task failure, reset authority/budgets, or fabricate completion under pressure.
- Background/foreground and restart timing preserve truthful durable state and cannot replay consequential effects.
- CI/emulator evidence and physical-device evidence remain mechanically distinguishable.
- Candidate identity changes invalidate prior candidate-bound performance evidence.

### Verification
- Deterministic lifecycle/restart/resource-pressure fixtures in existing JVM/Android verification surfaces.
- Exact-head Android API-matrix runs for automated cases.
- Long-render responsiveness against the verified offline Phase-4 fixture.
- Declared reference-device timing/resource capture when hardware is actually available.
- TASK-047 provenance/staleness checks and R7.7 integration into TASK-048.

### Expected result
Release review has a mechanically truthful R7.7 evidence package showing how the exact candidate behaves under interruption, resource pressure, long work, lifecycle transitions, and restart conditions.

### Evidence basis
- `docs/ROADMAP_1.0.md` explicitly defines R7.7 performance/resource behavior: voice interruption latency, Stop receipt latency, memory pressure, thermal pressure, long render responsiveness, background/foreground transitions, and restart timing.
- TASK-048 owns the integrated R7.1-R7.7 adversarial matrix but no canonical task currently owns generating the dedicated measurable R7.7 evidence inputs.

### Projection basis
- A separate bounded evidence producer prevents the terminal adversarial matrix from inventing ad-hoc thresholds/measurement plumbing late in release hardening and keeps physical measurements distinct from CI automation.

### Risks / unknowns
- Thermal and some latency behavior require owner-controlled physical hardware; absent hardware remains UNTESTED.
- Device/OEM variability may preclude universal numeric thresholds; evidence must record declared environment and project-approved acceptance targets rather than generalize unsupported guarantees.
- Long-render measurements depend on TASK-031 and the actual renderer path existing first.

---

## TASK-059: Implement bounded visual-asset workflow stage
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-4, workflow, media, visual-assets
**Updated:** 2026-10-04

### Goal
Add the missing bounded visual-asset workflow stage between script/visual planning and deterministic timeline construction so Phase 4 can consume verified local image/title-card artifacts without depending on the later external media-provider stack.

### Scope
- Consume current-revision script/visual-plan inputs and produce typed visual-asset requests/results through the durable workflow/artifact boundaries.
- Support user-provided images, bundled licensed fixtures, and deterministic generated text/title cards for the local/offline Phase-4 path.
- Normalize each accepted visual artifact into the TASK-025 media schema with hash, dimensions, MIME/format, provenance, ownership, and workflow revision.
- Reject corrupt, unsupported, oversized, missing, stale-revision, or provenance-incomplete assets before timeline consumption.
- Preserve immutable artifact history and TASK-020 downstream invalidation when a visual input or plan changes.
- Keep external image generation/provider network calls out of this task; TASK-034 later plugs into the same typed result boundary.

### Dependencies
- TASK-018 immutable artifact workspace.
- TASK-019 restricted visual-planner role boundary.
- TASK-020 workflow revision invalidation.
- TASK-023 aggregate workflow budgets.
- TASK-024 Phase-3 durable-workflow acceptance.
- TASK-025 media artifact schemas.
- TASK-056 bounded script-generation workflow stage where script-derived visual requirements are needed.

### Plan
- Reuse TASK-025 image artifact types and TASK-018 immutable storage rather than creating a parallel media store.
- Define the smallest durable workflow-stage input/output contract for local visual assets.
- Add deterministic adapters for bundled/user-selected image ingestion and generated title-card assets.
- Validate decode, dimensions, size, hash, provenance, and current workflow revision before success.
- Ensure revision/cancellation invalidates only dependent downstream outputs.
- Add focused workflow/artifact tests plus a real local fixture suitable for later TASK-027/TASK-031 consumption.

### Acceptance
- One current-revision visual plan can produce a bounded ordered set of verified image/title-card artifact references without cloud access.
- Every successful output has independently derived hash, format, dimensions, provenance, and workflow revision.
- Corrupt/unsupported/oversized/stale assets fail closed and cannot enter the timeline.
- Revisions preserve old immutable artifacts while invalidating only dependent current outputs.
- Cancellation and restart cannot fabricate completion or duplicate mutable outputs.
- No provider credential, arbitrary path authority, shell command, or capability grant is introduced.

### Verification
- Stage input/output schema and deterministic ordering tests.
- Real decoder tests for valid bundled fixture plus corrupt/truncated/unsupported/oversized negatives.
- Artifact hash/provenance/revision and stale-output invalidation tests.
- Restart/cancellation/idempotency tests over the durable workflow boundary.
- Path/authority/secret negative inspection.
- Canonical Verify + Android checks where the selected local image APIs are platform-sensitive; architecture/security/media review.

### Expected result
The offline media pipeline has a real verified visual-asset stage feeding TASK-027 timeline construction, while TASK-034 can later add external generation without changing artifact trust or workflow semantics.

### Evidence basis
`docs/ROADMAP_1.0.md` sequential item 44 explicitly requires “Build the image/visual-asset stage,” and the flagship workflow includes a visual-assets stage before render. Current TaskPlanner work covers script generation (TASK-056), narration (TASK-026), media schemas (TASK-025), timeline (TASK-027), and later external media generation (TASK-034), but no task represents the local Phase-4 visual-asset workflow stage.

### Projection basis
Without this stage, TASK-027/TASK-031 would either consume ad-hoc images directly or prematurely depend on Phase-5 external generation. A provider-neutral local stage closes that integration gap and lets external generation reuse the same verified artifact boundary later.

### Risks / unknowns
Exact Android image decode/title-card primitives should reuse platform APIs where possible. Licensing/provenance for bundled assets must be explicit. Do not broaden into arbitrary image editing, cinematic generation, or external provider selection.

---

## TASK-058: Define 1.0 app-data backup and export boundary
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, privacy, backup, export, android
**Updated:** 2026-10-04

### Goal
Define and verify the bounded 1.0 backup/export behavior for user-owned LAIN_OS state so release documentation and Phase-7 data-export review can distinguish intentionally private/non-exportable state from explicitly user-exportable data without leaking secrets or execution authority.

### Scope
- Inventory app-private durable state that exists by 1.0: workflow/session checkpoints, audit/evidence records, settings/profile references, artifact metadata, and user-created artifacts.
- Define which classes are exportable, backup-eligible, intentionally excluded, or unsupported; an explicit no-export/no-backup result is valid where required by authority or secret boundaries.
- Keep credentials, tokens, keystore material, approval authority, private provider state, and raw secret-bearing payloads out of export/backup surfaces.
- Define deterministic export manifest/version/provenance semantics for any supported user-controlled export without creating a generic filesystem/archive capability.
- Align Android backup/data-extraction configuration and operator documentation with the declared behavior; fail closed on stale, malformed, oversized, or mixed-authority data.
- Do not add cloud backup, automatic upload/sync, credential export, restore/migration behavior beyond TASK-057, or public distribution machinery.

### Dependencies
- TASK-024 durable-workflow acceptance establishes the persisted workflow/session state that must be classified.
- TASK-038 provider privacy/data-sharing state establishes provider-sensitive exclusions.
- TASK-047 supplies candidate/evidence provenance semantics.
- TASK-049/TASK-057 establish release package and installed-state boundaries consumed by final documentation/acceptance.

### Plan
- Inventory persisted files/records and current Android backup/data-extraction rules.
- Classify each state class as exportable, backup-eligible, excluded, or unsupported with explicit rationale and versioning.
- Add the smallest bounded export/manifest path only where the roadmap requires user-portable data; otherwise encode explicit unsupported behavior rather than inventing sync.
- Add negative tests for secrets, authority-bearing records, malformed/stale manifests, path traversal, oversized payloads, and cross-candidate restore confusion.
- Feed the declared behavior into TASK-048 data-export review, TASK-052 operator docs, and TASK-051 release evidence.

### Acceptance
- Every durable 1.0 state class has an explicit backup/export classification.
- Any supported export is user-initiated, bounded, versioned, path-safe, and contains no credential, keystore secret, authorization token, approval capability, or hidden execution authority.
- Android backup/data-extraction configuration matches the declared behavior; private state is not silently exported by platform defaults.
- Unsupported backup/export paths are explicit and cannot be presented as successful portability.
- Exported metadata cannot be imported or interpreted as current approval/capability authority merely because it came from a prior installation.
- TASK-048, TASK-052, and TASK-051 can mechanically consume the classification/evidence.

### Verification
- Persisted-state inventory assertions and deterministic manifest/schema tests.
- Secret/redaction and authority-leak negatives across checkpoints, audit, settings, provider state, and artifacts.
- Path traversal, hostile filename, oversized/corrupt/stale export negatives.
- Android manifest/data-extraction configuration checks.
- Candidate/provenance binding checks through TASK-047 plus architecture/security/privacy review.

### Expected result
LAIN_OS has one truthful, security-bounded 1.0 backup/export contract: user-portable data is explicitly identified and verifiable, sensitive authority-bearing state remains private, and release docs/matrix cannot imply portability that the installed product does not provide.

### Evidence basis
- `docs/ROADMAP_1.0.md` Release Engineering requires documented backup/export behavior and Phase-7 R7.6 requires a data-export review.
- Current Android manifest uses `android:allowBackup="false"` with app-private durable state, while repository/task search finds no dedicated app-state backup/export task; TASK-030 covers rendered media export only and TASK-052 documents behavior without defining the underlying state boundary.

### Projection basis
- Without a canonical backup/export boundary, TASK-048/TASK-052/TASK-051 could review or document an ambiguous surface, creating late privacy/authority gaps or implying unsupported portability.
- A bounded classification-first task is reversible and avoids prematurely adding cloud sync or generic archive machinery.

### Risks / unknowns
- Some durable state classes do not exist until later phases; classify them when their owning task lands rather than inventing schemas early.
- The correct 1.0 result may intentionally exclude most private state from export; user-owned data goals do not justify exporting credentials or authority.
- Restore/migration remains TASK-057 territory and must not be conflated with backup/export.

---

## TASK-057: Verify installed release upgrade and state-migration boundary
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, migration, android, recovery
**Updated:** 2026-10-04

### Goal
Prove the truthful installed upgrade/state-migration behavior for the 1.0 release candidate so package/signing changes cannot silently strand, corrupt, over-trust, or misrepresent existing private application state.

### Scope
- Consume the release package identity and migration semantics established by TASK-049.
- Exercise supported upgrade paths using exact candidate/source identities and representative durable state.
- When in-place upgrade is intentionally unsupported, prove the declared fresh-install boundary rather than weakening package/signing guarantees.
- Verify preserved state remains schema-valid, authority-bounded, and consistent with current secret/approval/revision semantics.
- Detect incompatible/corrupt/stale state explicitly and fail closed without fabricating successful migration.
- Record migration evidence through TASK-047 provenance rules for TASK-051 consumption.
- Do not create signing credentials, alter package identity merely to force compatibility, publish artifacts, or invent backup/import functionality outside the documented release contract.

### Dependencies
- TASK-047 release evidence manifest/provenance contract.
- TASK-049 release-candidate packaging path and its package/signing/migration semantics.
- Existing durable-state, secret-store, approval, audit, conversation, and workflow schema/version rules remain authoritative.

### Plan
- Inventory persisted app/runtime state affected by candidate install/upgrade.
- Define representative pre-upgrade fixtures plus incompatible/corrupt cases.
- Exercise the exact supported upgrade or declared fresh-install boundary.
- Verify post-transition state integrity, authority isolation, secret handling, and restart behavior.
- Emit candidate-bound PASS/FAIL/UNTESTED evidence for TASK-051.

### Acceptance
- Supported upgrade paths preserve only state valid under the candidate schema and authority model.
- Unsupported package/signing transitions are rejected or explicitly documented as fresh-install boundaries; no test weakens signing/package guarantees.
- Corrupt, stale, or incompatible state cannot become approval, capability, provider, or execution authority.
- Secrets do not enter logs, manifests, planner state, or migration evidence.
- Migration evidence is bound to exact source/package/artifact identity and becomes stale when candidate identity changes.
- TASK-051 can mechanically distinguish supported upgrade, required fresh install, and unverified migration cases.

### Verification
- Deterministic persisted-state fixtures and schema/version-transition tests.
- Android install/upgrade instrumentation where package/signing identity permits; explicit negative evidence where it does not.
- Restart/rebind plus approval/revision/secret-store integrity checks after transition.
- Corrupt/incompatible/stale-state negatives and no-authority-escalation checks.
- TASK-047 candidate/provenance validation plus canonical Verify and Android gates.

### Expected result
The 1.0 candidate has an evidence-backed upgrade/state-migration story: supported paths are proven, unsupported paths are explicit, and no release claim depends on silently discarding or over-trusting prior private state.

### Evidence basis
- `docs/ROADMAP_1.0.md` Release Engineering requires migration from current debug/private state where applicable.
- The 1.0 design requires reviewed update/state migration behavior and forbids a public release claim for a debug APK.
- TASK-049 defines packaging/migration semantics, but no existing task proves those semantics against installed persisted state.

### Projection basis
- TASK-051 is an installed release-readiness gate; without candidate-bound migration evidence it could certify a fresh install while upgrade/data-loss behavior remains unverified.
- Separating migration acceptance from packaging keeps build/signing mechanics and state-transition correctness independently reviewable.

### Risks / unknowns
- Existing debug/release identities may make in-place upgrade impossible; that is an acceptable truthful result when proven.
- Some state classes may not exist until later phases complete; mark them non-applicable rather than inventing coverage.
- Physical install/upgrade evidence may require owner-controlled signed artifacts; absent signing material remains UNTESTED.
- **Overseer assignment receipt (current run):** semantic dedupe found this concurrently-created task already covers the highest-value migration boundary; assignment is adopted in place rather than duplicated.

---

## TASK-056: Implement bounded script-generation workflow stage
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-3, phase-4, workflow, script, artifacts
**Updated:** 2026-10-04

### Goal

Implement the roadmap's missing script-generation workflow stage so a bounded writer role can turn an accepted workflow brief into a versioned, immutable, verifiable script artifact that downstream narration and revision invalidation can consume without granting the model execution authority.

### Scope

- Add one typed script-stage input/output contract using the durable workflow/artifact foundations from TASK-016 through TASK-024.
- Execute the writer through the restricted specialist-role boundary from TASK-019; model output remains data, never direct capability or policy authority.
- Store the accepted script as a current-revision immutable artifact with hash, provenance, source workflow/revision identity, and bounded metadata.
- Route user-requested script revisions through the existing revision/invalidation model so downstream narration/media artifacts become stale rather than silently mutated.
- Enforce bounded input/output size, timeout/provider budget intersections, cancellation, and explicit malformed/unavailable outcomes.
- Keep provider-specific remote transport behind existing planner/provider abstractions; do not add arbitrary tool execution, publication, or media rendering here.
- Do not implement narration generation, visual-asset generation, timeline/rendering, or publication in this task.

### Dependencies

- TASK-016 through TASK-024: durable workflow state, scheduling, immutable artifacts, restricted roles, revision invalidation, waits/reconciliation/budgets, and Phase-3 acceptance.
- Existing planner/provider boundaries supply bounded model execution when configured.
- TASK-026 consumes the verified script artifact for narration; this task must stabilize that input first.

### Plan

- Reuse the existing workflow-node, specialist-role, artifact, revision, and budget contracts; add only the minimum script-stage types and adapter glue.
- Define deterministic validation for script request/result identity, size, provenance, revision, and expected artifact type.
- Materialize a successful current-revision script into the immutable artifact workspace and expose its artifact identity to downstream nodes.
- Wire revision handling so a new script revision creates a new artifact and invalidates only dependent outputs.
- Add deterministic fake-writer tests for success, malformed/oversized output, timeout/cancel, stale revision, replay/restart, and budget exhaustion.

### Acceptance

- One accepted workflow brief can produce exactly one verified current-revision script artifact with hash and provenance.
- Script generation cannot grant capabilities, change policy, approve consequential work, or choose arbitrary executors.
- Malformed, oversized, timed-out, cancelled, budget-exhausted, or stale-revision output never becomes a verified script artifact.
- Restart/resume cannot duplicate an already completed current-revision script stage.
- A user revision produces a new revision-bound script artifact and deterministically invalidates dependent narration/media outputs without mutating prior immutable history.
- Downstream narration receives only the verified current-revision script artifact identity/content allowed by the artifact contract.
- No provider credential, raw authorization secret, or unrestricted transcript is persisted in the script artifact or audit surface.

### Verification

- Focused unit tests for typed script-stage validation, artifact hashing/provenance, and specialist-role authority boundaries.
- Revision/invalidation, stale-artifact, restart/replay, cancellation, timeout, and aggregate-budget negatives.
- Deterministic fake-writer integration through the real workflow scheduler/artifact path.
- Canonical portable verification plus architecture/security review of the workflow→role→artifact boundary.
- Live provider behavior, when unavailable, remains separately UNTESTED and cannot substitute for deterministic contract proof.

### Expected result

LAIN_OS gains the first real content-producing workflow node: an accepted idea/brief becomes a bounded, revision-aware, immutable script artifact that safely feeds narration and later media stages.

### Evidence basis

- `docs/ROADMAP_1.0.md` sequential product item 42 explicitly requires “Build the script-generation workflow stage.”
- The Phase-7 golden scenario requires “script produced” before verbal revision, downstream invalidation/rebuild, narration, visuals, rendering, and publication.
- Current TaskPlanner state through TASK-055 includes restricted specialist roles, durable workflow/artifact infrastructure, narration, timeline/rendering, release work, and Phase-2 presentation slices, but no dedicated script-generation workflow-stage task.
- Repository search finds the roadmap/design requirement and narration consumers, but no current task representing the script producer.

### Projection basis

- TASK-026 narration and the Phase-7 golden flow need a stable verified script artifact; without a dedicated producer, later media work would either invent a parallel script representation or couple narration directly to free-form planner/model output.
- Building this stage immediately after the durable workflow foundation preserves one source of truth for revision identity, artifact provenance, budgets, cancellation, and replay safety.

### Risks / unknowns

- Exact script schema richness should stay minimal for 1.0; avoid speculative screenplay/scene abstractions beyond what narration/timeline consumers demonstrably need.
- External provider quality and cost are provider concerns; this task owns bounded lifecycle and truthful failure, not content-quality guarantees.
- If TASK-019's restricted-role contract lacks one required writer seam, extend that seam minimally rather than introducing a parallel role framework.

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

## TASK-055: Implement progressive assistant speech delivery
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, streaming, speech
**Updated:** 2026-10-04

### Goal
Allow bounded assistant responses to begin speaking progressively where the active synthesis implementation supports it, while preserving cancellation, ordering, replay safety, and the rule that speech transport failure never equals task failure.

### Scope
- Extend the existing speech synthesis/playback seam to accept ordered bounded response segments or chunks without exposing arbitrary streaming transport to the executor.
- Preserve deterministic segment ordering, cancellation generation, audio-focus behavior, and Stop talking semantics.
- Surface unsupported/non-streaming engines truthfully and fall back only to bounded whole-response synthesis within the same selected provider/locality contract.
- Prevent late/stale segments from a cancelled or superseded turn from playing.
- Keep progress narration (TASK-014) logically distinct from assistant conversational response speech.
- Do not introduce provider-specific network streaming, hidden cloud fallback, or task authority.

### Dependencies
- TASK-009 speech contracts.
- TASK-011 ordered turn/session identity.
- TASK-012 cancellable playback.
- TASK-050 concrete synthesis implementation.
- TASK-053 explicit voice identity where supported.

### Plan
- Reuse existing turn IDs and playback cancellation generation as the ordering key.
- Add the minimum segment/chunk representation needed by the speech boundary.
- Gate playback on current turn/revision and selected speech profile.
- Cancel and discard queued/late segments on Stop talking, barge-in, or superseding turn.
- Add deterministic fake-synthesizer tests before Android integration.

### Acceptance
- A supported engine can begin speaking a multi-segment assistant response before the entire response is synthesized.
- Segments play exactly once in order for the current turn.
- Stop talking or superseding the turn prevents queued/late segments from resuming.
- Non-streaming engines remain functional through bounded whole-response synthesis without switching provider/locality.
- Speech failure leaves the underlying task state unchanged.

### Verification
- Segment ordering, cancellation, stale-turn, duplicate, and completion JVM tests.
- Android instrumentation for progressive playback, focus transitions, lifecycle teardown, and Stop talking separation.
- Unsupported-engine fallback-within-same-profile tests and no-cloud-fallback negatives.
- Canonical Verify + Android API matrix; latency measurements on physical device remain separate acceptance evidence.

### Expected result
Assistant conversation can speak responsively without weakening TASK-012 cancellation or TASK-011 turn truth, and engines without progressive support remain truthful and usable.

### Evidence basis
`docs/ROADMAP_1.0.md` Phase 2 requires “streaming/progressive spoken responses where supported” and sequential item 25 explicitly says “Add streaming/progressive assistant speech.” Existing TASK-014 covers progress narration, a separate short progress-event channel rather than progressive delivery of conversational assistant speech.

### Projection basis
Progressive delivery reduces voice interaction latency and gives later barge-in/echo work a realistic queued-audio path to interrupt while preserving the provider-neutral boundary.

### Risks / unknowns
Android speech engines vary in incremental synthesis support. The implementation must feature-detect capability and stay bounded; provider-specific network streaming belongs to later external-provider work.

---

## TASK-052: Close 1.0 operator documentation and third-party notices
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, docs, licensing

### Goal
Produce the candidate-bound operator/release documentation and third-party notice surface required for a truthful 1.0 readiness decision.

### Scope
- Document install/upgrade, supported Android/API/device envelope, permissions, planner/provider configuration, privacy/data flow, local-vs-cloud/fallback semantics, recovery/Stop behavior, YouTube authorization, backup/export, troubleshooting, and known limitations.
- Generate or maintain third-party notices/licenses from actual shipped dependencies and packaged components.
- Bind documentation applicability to release/package/version behavior without embedding credentials or claiming unsupported environments.
- Add deterministic checks for required sections, stale package/version references, broken internal references, and notice coverage where repository metadata permits.
- Do not choose a distribution channel, publish a release, alter product behavior, or invent support guarantees.

### Dependencies
- Implemented behavior from phase tasks and final package semantics from TASK-049.
- TASK-047 evidence manifest for candidate/version provenance.
- Dependency/build metadata used by the actual release candidate.

### Plan
- Inventory existing README/docs against every Release Engineering documentation and release-artifact requirement.
- Consolidate or add the minimum canonical operator docs rather than duplicating guidance.
- Derive third-party notice entries from shipped dependency metadata and flag unknown license/provenance instead of guessing.
- Add bounded documentation/release checks suitable for CI and TASK-051 consumption.
- Record candidate-specific known limitations and unsupported paths explicitly.

### Acceptance
- Every roadmap-required documentation topic has one canonical, discoverable, behavior-matching source.
- Install/upgrade and supported-device/API guidance matches TASK-049 package behavior.
- Provider/privacy/local-vs-cloud and YouTube authority guidance matches implemented policy and does not expose secrets.
- Stop/recovery/backup/export behavior is documented without promoting unverified behavior.
- Third-party notices cover shipped dependencies/components or fail explicitly on unresolved license metadata.
- Known limitations remain explicit and candidate-bound; docs cannot serialize an unsupported path as supported.
- TASK-051 can mechanically verify documentation/notice presence and candidate applicability.

### Verification
- Required-section/link/reference checks.
- Package/version/API/device consistency checks against TASK-049 metadata.
- Dependency-to-notice coverage and unresolved-license negatives.
- Secret scan of documentation/examples.
- Review against observed UI/configuration/recovery/privacy behavior and the release evidence manifest.

### Expected result
The final 1.0 candidate has a complete, auditable operator/release documentation set and third-party notices that match shipped behavior and can be consumed as release evidence.

### Evidence basis
`docs/ROADMAP_1.0.md` Release Engineering explicitly requires install/upgrade, supported Android versions/devices, permissions, provider configuration, privacy/data flow, local-vs-cloud semantics, recovery/Stop, YouTube authorization, known limitations, backup/export, troubleshooting, migration notes, third-party notices/licenses, and known issues. Existing TASK-047/049/051 cover evidence, packaging, and final gating but not the production of this documentation/notices set.

### Projection basis
TASK-051 cannot truthfully satisfy “documentation matches shipped behavior” or the required release-artifact set unless candidate-bound docs/notices are produced and checked before the terminal gate.

### Risks / unknowns
Dependency license metadata may be incomplete or ambiguous; unresolved provenance must block notice completeness rather than be inferred. Final supported-device wording may remain provisional until physical acceptance and TASK-049 packaging evidence exist.

---

## TASK-051: Close installed 1.0 golden workflow and release-readiness gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, golden, acceptance
**Updated:** 2026-10-04

### Goal
Prove the complete installed-phone 1.0 golden scenario on one exact release candidate and close the final release-readiness gate only when every required automated, emulator, physical-device, media, provider, publication, security/privacy, documentation, and evidence condition is truthfully satisfied.

### Scope
- Execute the roadmap golden scenario end to end from installed voice session through workflow creation, verbal revision, dependent invalidation/rebuild, narration, visuals, render, inspection, preview, private YouTube staging, processing verification, exact publication approval, publication, independent final retrieval, and workflow close with audit + artifact manifest.
- Bind the run to one exact TASK-049 package/version/source/checksum/signing identity and TASK-047 evidence manifest.
- Consume TASK-048 adversarial results as a hard release gate and include TASK-050's concrete on-device speech path in the Phase-2 acceptance chain.
- Keep every live provider/upload/publication effect under its existing exact explicit authority.
- Validate all roadmap release documentation and known-limitations surfaces.
- Do not publish/deploy a public release, create a public release tag, rotate credentials, or claim 1.0 when evidence is missing.

### Dependencies
- TASK-015, TASK-024, TASK-031, TASK-036, TASK-046 phase exit/physical/live evidence.
- TASK-047 release evidence manifest.
- TASK-048 adversarial matrix.
- TASK-049 release-candidate packaging/signing identity.
- TASK-050 concrete on-device Phase-2 speech implementation.

### Plan
- Freeze one TASK-049 candidate identity and reject evidence from any other artifact/source revision.
- Run reproducible automated/emulator gates first and resolve deterministic failures before live/physical acceptance.
- Install the exact candidate on the declared reference device and execute the complete voice-to-video-to-publication scenario with separately authorized live effects.
- Independently verify media, YouTube processing/final metadata, Stop/interruption, revision invalidation, restart/reconciliation, privacy/fallback semantics, and audit/evidence completeness.
- Populate TASK-047 with exact evidence classes, checksums, environments, external-result identities, limitations, and required release docs.
- Emit READY only if all mandatory gates pass; otherwise emit NOT_READY with exact failed/missing evidence.

### Acceptance
- The exact installed release candidate completes the full spoken idea-to-video workflow and closes with durable audit + artifact manifest.
- Voice I/O, barge-in, Stop, revision, workflow restart/recovery, invalidation, render/inspection/preview, provider failure handling, and publication authority conform to phase contracts.
- YouTube publication uses the exact approved artifact/payload, occurs once, and final metadata/visibility are independently retrieved.
- Automated, emulator, physical-device, and live-service evidence remain separately typed and bound to the exact candidate.
- Critical unresolved security/recovery/data-loss findings are zero.
- Secrets are absent from planner/audit/log/IPC/export/evidence surfaces.
- Documentation and known limitations match observed shipped behavior.
- No debug APK, mocked external effect, skipped check, stale receipt, owner assertion alone, or planner/provider claim is promoted into release proof.

### Verification
- Canonical Python verification plus Android release build/lint/JVM/instrumentation on the supported API matrix.
- TASK-048 R7.1-R7.7 adversarial matrix with exact-candidate binding.
- Physical reference-device voice/interruption/Stop/restart/resource acceptance.
- Real offline golden video render plus independent media inspection.
- Authorized live provider/YouTube staging, processing verification, exact publication approval, publication, and independent final retrieval.
- Secret/redaction/export/package scans plus checksum/signature/source identity checks.
- Fresh architecture/security/privacy/release review and TASK-047 manifest validation.

### Expected result
A release reviewer gets one binary evidence-backed decision for LAIN_OS 1.0: READY only when one exact installed candidate satisfies the complete golden workflow and every mandatory evidence class, otherwise NOT_READY with precise unresolved evidence and no fabricated release claim.

### Evidence basis
- `docs/ROADMAP_1.0.md` Phase 7 defines the installed golden scenario; Release Engineering and the 1.0 definition of done require exact candidate identity, automated/emulator/physical/live evidence, adversarial matrix, artifact manifest, documentation, secret scan, and zero critical unresolved security/recovery/data-loss findings.
- TASK-047 through TASK-050 cover evidence semantics, adversarial hardening, release packaging, and the concrete speech gap, but no current task represents the terminal installed golden workflow/readiness decision.

### Projection basis
- A single terminal candidate-bound gate prevents individually green phases, debug/mock success, stale evidence, release-build drift, or incomplete docs from being mistaken for product readiness.

### Risks / unknowns
- Physical-device and live-provider/YouTube execution require owner-controlled hardware, credentials, quota, and explicit effect approval; unavailable prerequisites remain NOT_READY/UNTESTED.
- Candidate changes invalidate affected receipts and require rerun.
- Public distribution remains owner-authority work outside this task.

---

## TASK-049: Build reproducible 1.0 release-candidate packaging path
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, android, signing, reproducibility
**Updated:** 2026-10-04

### Goal
Create the bounded release-candidate packaging path required for 1.0 so one exact source commit deterministically produces a versioned Android release artifact whose package identity, checksum, supported API/ABI set, signing identity, and migration semantics can be bound into TASK-047 evidence without storing or inventing signing secrets.

### Scope
- Add/finish a reproducible Android release build path with explicit package/version identity and supported API/ABI declarations.
- Wire release signing configuration to maintainer-supplied key material by reference/environment only; never commit, generate, export, rotate, or persist private signing secrets.
- Define deterministic artifact checksum/provenance output consumable by TASK-047.
- Define migration/upgrade behavior from the current debug/private state where applicable, including explicit incompatibility/fresh-install cases.
- Keep debug and release identities visibly distinct; no debug artifact may satisfy release evidence.
- Do not publish, deploy, upload, create credentials, choose a distribution channel, or claim 1.0 readiness.

### Dependencies
- Existing Android build/package structure and current CI verification.
- TASK-047 release evidence manifest contract for the final provenance binding.
- Maintainer-provided signing material/configuration is required only for producing a release-signed artifact; local/CI structural verification must remain possible without exposing that key.

### Plan
- Inventory current Gradle package/version/signing/ABI/API behavior and existing build/CI outputs.
- Add the smallest release configuration that reuses current Android structure and accepts signing inputs without embedding secrets.
- Emit deterministic artifact identity/checksum metadata tied to source commit and version.
- Add migration/install-path documentation and tests for debug-vs-release identity boundaries.
- Add CI-safe checks that validate release configuration without requiring private key material.
- Document the exact owner-only step required to supply persistent signing material for final candidate production.

### Acceptance
- One documented command/path produces the release variant from a recorded source commit when authorized signing inputs are present.
- Package/application ID, version code/name, supported API/ABI declarations, source identity, signer identity/fingerprint, and artifact checksum are independently inspectable and bindable to TASK-047.
- Release configuration contains no committed secret, password, token, private key, or generated substitute credential.
- Missing signing inputs fail explicitly; they never silently fall back to debug signing.
- Debug artifacts cannot serialize or report themselves as release-signed evidence.
- Upgrade/fresh-install expectations from current debug/private builds are explicit and testable.
- No distribution, publication, deployment, or 1.0 claim occurs in this task.

### Verification
- Gradle configuration/build checks that do not expose signing secrets.
- Release-vs-debug package/signing identity negative tests.
- Deterministic version/source/checksum metadata tests.
- Supported API/ABI inspection on the produced candidate when authorized key material is available.
- Secret scan of repository/build logs/artifact metadata.
- Install/upgrade or explicit fresh-install acceptance on supported emulator/device paths when candidate signing material is available.
- Architecture/security/release review; any unavailable private-key/device evidence remains UNTESTED, never inferred.

### Expected result
LAIN_OS has a repeatable, evidence-producing release-candidate build boundary: exact source in, exact versioned artifact/checksum/signer identity out, with debug fallback forbidden and private signing authority remaining maintainer-controlled.

### Evidence basis
`docs/ROADMAP_1.0.md` Release Engineering requires a reproducible release build, maintainer-controlled persistent signing key, release package identity/versioning, supported ABI/API declaration, migration behavior, signed artifact, checksum, version/tag, and source commit. The 1.0 definition of done requires a release-signed Android artifact built from a recorded source commit and forbids presenting a debug APK as release evidence. No existing TaskPlanner task represents this packaging/signing boundary.

### Projection basis
TASK-047 can define evidence semantics and TASK-048 can exercise adversarial release checks, but the Phase-7 golden workflow and final 1.0 evidence need one concrete candidate artifact identity. Establishing this boundary before final golden acceptance prevents late-stage rebuild/signing drift from invalidating otherwise-good evidence.

### Risks / unknowns
Persistent signing material and any credential-bearing configuration remain owner-controlled and may block final artifact production. Existing debug application identity may make in-place upgrade impossible; if so, the task must document and verify the truthful fresh-install/migration boundary rather than weakening package or signing guarantees.

---

## TASK-048: Close Phase-7 adversarial release matrix
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, adversarial
**Updated:** 2026-10-04

### Goal

Exercise the complete Phase-7 adversarial release matrix against one exact candidate identity and produce mechanically classifiable evidence without allowing mocked, stale, weaker-environment, or planner-reported results to masquerade as release proof.

### Scope

- Cover roadmap R7.1–R7.7: conversation, planner, workflow/recovery, media, publication, security/privacy, and performance/resource failures.
- Bind every run to exact source, artifact, environment, provider/effect identity, and evidence strength through the TASK-047 release-evidence contract.
- Reuse existing focused tests and phase acceptance fixtures where they already prove a matrix cell; add only missing end-to-end/adversarial checks.
- Keep destructive or consequential live effects separately authorized; deterministic fake-provider cases may prove failure handling but cannot certify real external publication.
- Do not create release signing, tagging, deployment, or publication machinery in this task.

### Dependencies

- TASK-015, TASK-024, TASK-031, TASK-036, and TASK-046 provide the phase acceptance/evidence producers required by the matrix.
- TASK-047 defines the candidate-bound release evidence manifest and provenance/strength rules.
- Existing canonical verification, Android CI, audit redaction, artifact verification, and approval/reconciliation boundaries remain authoritative.

### Plan

- Map each R7.1–R7.7 roadmap failure to an existing proving check or one bounded missing test.
- Add the smallest missing deterministic tests/fixtures and candidate-bound evidence capture needed for uncovered cells.
- Run canonical Python, Android/JVM/instrumentation, secret-scan, recovery/replay, media, and provider/publication failure checks on the exact candidate revision.
- Record physical-device, performance/thermal, and live external-service cells separately when those environments are actually available and authorized.
- Produce one matrix result that distinguishes PASS, FAIL, UNTESTED/UNAVAILABLE, environment, source/artifact identity, and evidence provenance.

### Acceptance

- Every R7.1–R7.7 cell has an explicit result and evidence pointer or an explicit truthful UNTESTED/UNAVAILABLE reason.
- No automated/emulator/fake-provider result is promoted to physical-device or live-provider evidence.
- Crash/restart, uncertain external effects, duplicate resume/approval, stale revisions, exhausted budgets, and cancellation cannot silently replay consequential work.
- Secret scans, redaction, IPC/path/approval-replay/data-export checks produce no unresolved critical security/privacy finding.
- Performance/resource observations are tied to a declared device/build/environment and do not invent unsupported thresholds.
- The matrix is bound to one exact candidate and becomes stale when source/artifact identity changes.

### Verification

- Canonical portable verification plus Android build/lint/JVM/instrumentation on the supported API matrix.
- Focused deterministic R7.1–R7.7 failure suites, including crash/restart/reconciliation and provider/publication uncertainty cases.
- Secret/log/audit/export scans and security/privacy review.
- Candidate-identity mismatch/staleness negatives against TASK-047 manifest rules.
- Physical-device, latency/resource, and live-provider checks only with actual environment evidence and required authorization.

### Expected result

Release review can see, for one exact candidate, which adversarial failure classes are proven safe, which failed, and which remain honestly unverified, with no weaker evidence promoted into a 1.0 claim.

### Evidence basis

- `docs/ROADMAP_1.0.md` Phase 7 explicitly defines R7.1–R7.7 as the adversarial release matrix.
- Release engineering requires the adversarial matrix, physical acceptance, secret scan, artifact manifest, and end-to-end golden workflow before a 1.0 claim.
- Current TaskPlanner state through TASK-047 defines phase/component gates and the release-evidence manifest, but no canonical task represents the complete R7.1–R7.7 integrated matrix.

### Projection basis

- TASK-047 can classify evidence, but the release candidate still needs one integrated consumer that proves failure behavior across subsystem boundaries before golden-workflow/release readiness can be trusted.
- Centralizing only the matrix orchestration/evidence prevents later release work from duplicating per-phase tests or accidentally treating partial evidence as a complete gate.

### Risks / unknowns

- Physical-device thermal/latency behavior and live YouTube/provider failures may require owner-controlled hardware, credentials, quota, or explicit consequential-effect approval; absent environments remain UNTESTED rather than simulated success.
- The matrix can become large; reuse existing proving tests/receipts and add only missing cross-boundary coverage.
- Candidate changes invalidate evidence; do not preserve green status across source/artifact identity changes.

---

## TASK-047: Define 1.0 release evidence manifest and provenance contract
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-7, release, evidence, provenance

### Goal
Define one machine-readable release-evidence contract that binds a 1.0 candidate to its exact source, signed artifact, verification runs, installed-device evidence, external-service evidence, known limitations, and independently verified outputs without overstating weaker evidence.

### Scope
- Define a versioned manifest for source commit, package/version/signing identity, artifact checksum, supported API/ABI declarations, verification classes, evidence provenance, known limitations, and final external-result identities.
- Represent automated, emulator, physical-device, owner-reported, and live-service evidence as distinct typed classes with timestamps and source references.
- Require explicit UNTESTED/UNVERIFIED/FAILED states; prohibit promotion of stale, mocked, emulator-only, owner-reported, or planner assertions into stronger evidence.
- Bind Phase-2/3/4/5/6 acceptance receipts and the Phase-7 golden scenario to one release candidate without copying secrets or raw credentials.
- Define deterministic validation and redaction rules plus an artifact-manifest export suitable for release review.
- Do not implement signing infrastructure, distribution, deployment, publication, or the Phase-7 golden workflow itself.

### Dependencies
- Acceptance/evidence producers from TASK-015, TASK-024, TASK-031, TASK-036, and TASK-046.
- Existing audit, artifact hashing/provenance, verification, and redaction contracts.
- Release-candidate package identity/signing work may populate the contract later without changing evidence semantics.

### Plan
- Inventory current evidence identities and strength classes already emitted by canonical, Android, physical-device, media, provider, and publication gates.
- Specify a minimal versioned schema with immutable release/source/artifact identity and typed evidence records.
- Add deterministic validation rejecting missing identity, contradictory status, stale-source binding, strength escalation, duplicate evidence identity, and secret-bearing fields.
- Add fixtures covering mixed evidence classes, partial gates, failed gates, owner-reported hardware evidence, live-service evidence, and final independently retrieved external results.
- Document how release review consumes the manifest without treating it as execution authority.

### Acceptance
- One manifest identifies exactly one source commit and release artifact/checksum.
- Every required 1.0 verification class is present with explicit status and provenance; missing evidence cannot serialize as PASS.
- Evidence strength cannot be upgraded by aggregation or wording.
- Stale evidence from a different source/artifact/revision is rejected or explicitly non-applicable.
- Owner-reported physical evidence remains distinguishable from connector/CI/device-captured evidence.
- Secrets, credentials, authorization tokens, raw provider payloads, and private media content are excluded/redacted.
- Manifest validation is deterministic and does not itself grant capability, approval, signing, upload, publication, or release authority.

### Verification
- Schema round-trip and canonical serialization tests.
- Missing/stale/mismatched source-artifact identity negatives.
- Evidence-strength escalation and contradictory-status negatives.
- Duplicate/stale/live/owner-reported/emulator/physical evidence fixtures.
- Secret/redaction scans and bounded-size tests.
- Architecture/security/release review against the 1.0 definition of done.

### Expected result
A release reviewer can mechanically determine exactly what was built, what evidence applies to it, which checks are weaker or missing, and whether a 1.0 claim is supportable without relying on prose or stale evidence.

### Evidence basis
`docs/ROADMAP_1.0.md` Phase 7 and Release Engineering require recorded source identity, signed artifact/checksum, automated/emulator/physical/external-service evidence kept separate and truthful, an artifact manifest, known limitations, and no skipped/mocked/debug evidence presented as release proof. No existing TaskPlanner task represents the cross-phase release-evidence contract.

### Projection basis
Phase-7 adversarial/golden acceptance and final release packaging will otherwise accumulate heterogeneous receipts with no single deterministic binding to the candidate artifact, increasing the risk of stale or overstated evidence at the release boundary.

### Risks / unknowns
The final signing/distribution mechanism is intentionally unresolved; the schema must bind opaque signing/package identities without selecting a distribution channel. Live-service and physical evidence may remain unavailable until late acceptance and must remain explicit rather than blocking local schema/test work.

---

## TASK-046: Close Phase-6 YouTube live acceptance gate
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, acceptance, external-effect

### Goal
Prove the complete Phase-6 YouTube path with user-provided authorization and separately authorized real effects, keeping staging private/unlisted until exact publication approval and independently retrieving the final result.

### Scope
- Exercise TASK-039 through TASK-045 as one installed-product acceptance path.
- Bind one exact verified artifact, authorization profile/channel, immutable upload intent, resumable staged upload, processing evidence, exact publication approval, publication attempt, and final independently retrieved metadata/visibility.
- Keep live uploads private/unlisted until explicit exact publication approval.
- Record exact source/build/workflow/artifact/video/effect identities, provider evidence, budgets, redacted audit receipts, and any reconciliation path.
- Do not create reusable credentials, generic YouTube/HTTP authority, automatic publication, or a release claim.

### Dependencies
- TASK-039 through TASK-045 complete.
- Required Phase-3/5 trust, reconciliation, budget, retry, privacy, and artifact dependencies already named by those tasks.
- User-provided YouTube authorization and separate explicit authorization for any real upload/publication effect.

### Plan
- Build deterministic fake-provider acceptance fixtures for the complete R6.1–R6.7 chain before live execution.
- Run installed Android acceptance through authorization, immutable intent, private/unlisted upload, processing lookup, exact approval, publication, and final independent retrieval.
- Exercise at least one bounded interrupted/uncertain-effect reconciliation path without duplicate writes.
- Persist a redacted evidence manifest that separates automated, emulator, physical-device, and live-service evidence.
- Fail closed on unavailable authorization, quota, processing failure, approval mismatch/expiry, uncertain write, or final metadata mismatch.

### Acceptance
- Exact artifact/channel/title/description/visibility/revision identities remain bound end-to-end.
- No upload/publication occurs without the required current explicit authority; no staged upload becomes public implicitly.
- Processing and final publication state are established by independent authenticated retrieval, not mutation responses.
- Ambiguous external effects reconcile without blind replay or duplicate upload.
- Credentials/tokens are absent from planner payloads, durable workflow state, audit, logs, IPC, exports, and evidence artifacts.
- Evidence records exact build/source identity and truthfully labels any unperformed live/physical checks.

### Verification
- Canonical Python and Android API-matrix checks for the integrated Phase-6 path.
- Fake-provider success/failure/crash/restart/duplicate/approval-substitution/visibility mismatch matrix.
- Installed-device live acceptance using user-provided authorization, with upload and publication separately authorized.
- Independent final metadata/visibility lookup and exact identity comparison.
- Secret scan plus architecture/security/privacy review of the integrated path.

### Expected result
LAIN_OS can truthfully demonstrate the Phase-6 exit gate: stage the exact artifact, verify processing, require exact publication approval, publish once, independently retrieve final state, and reconcile uncertainty without leaking credentials or duplicating effects.

### Evidence basis
`docs/ROADMAP_1.0.md` R6.8 explicitly requires live acceptance with user-provided authorization, separate authorization for real upload/publication, private live tests until explicit publication approval, and exact retrieved-result evidence. TASK-039 through TASK-045 represent R6.1–R6.7; no existing task represents R6.8.

### Projection basis
Phase 7 hardening and the 1.0 golden scenario should consume a proven publication vertical slice rather than independently implemented pieces with no integrated live acceptance.

### Risks / unknowns
Live execution depends on owner-provided authorization, current YouTube API/quota/policy behavior, and explicit approval for consequential writes. Those external dependencies may remain UNTESTED without blocking deterministic local/fake-provider implementation.

## TASK-045: Reconcile uncertain YouTube upload and publication effects
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-6, youtube, reconciliation, idempotency, external-effect

### Goal
Reconcile ambiguous, duplicate, or already-applied YouTube upload and publication outcomes against exact durable effect identities so LAIN_OS never creates a second upload, replays publication authority, or reports success without independent evidence.

### Scope
- Define versioned reconciliation records for TASK-041 upload sessions and TASK-044 publication attempts, keyed by immutable intent, artifact hash, provider video ID when known, channel/profile, metadata, visibility, workflow/policy revision, and effect identity.
- Resolve crash-after-send, network loss after response, unknown provider acknowledgement, duplicate approval/invocation, and already-published states through bounded independent provider lookup.
- Distinguish not-attempted, pending, confirmed-exact, confirmed-conflict, absent-after-bounded-search, and owner-decision-required outcomes.
- Reuse TASK-022 reconciliation, TASK-021 waits, TASK-023/TASK-035 budgets, TASK-037 retry rules, TASK-038 disclosure, and TASK-043 single-use approval semantics.
- Prohibit automatic second upload or publication replay whenever a prior effect may have occurred.
- Preserve redacted audit receipts and exact evidence provenance without storing credentials or broad provider responses.
- Do not add deletion, rollback, metadata repair, cross-channel migration, generic search, generic HTTP, or automatic conflict resolution.

### Dependencies
- TASK-018/TASK-020/TASK-021/TASK-022/TASK-023: immutable identities, revision invalidation, durable waits, reconciliation, and budgets.
- TASK-032/TASK-035/TASK-037/TASK-038: provider lifecycle, spending, retry, and disclosure controls.
- TASK-039 through TASK-044: authorized identity, immutable intent, staged upload, independent status, exact approval, and publication effect identity.
- Current official YouTube lookup semantics and searchable identity fields verified during implementation.

### Plan
- Specify canonical reconciliation keys and durable terminal/uncertain/conflict states for upload and publication effects.
- Enumerate every crash/network/duplicate boundary before, during, and after provider transmission and independent lookup.
- Implement the narrowest authenticated read paths needed to recover the exact existing video/effect without broad search authority.
- Match candidates against immutable intent, artifact, channel/profile, metadata, visibility, revision, upload session, and publication attempt evidence.
- Resume only safe reads/polls; keep all possibly-applied writes non-replayable until exact evidence settles them.
- Add deterministic duplicate, already-applied, ambiguous, conflicting, restart, budget, and Android receipt tests.

### Acceptance
- A crash or lost response after an upload/publication attempt cannot trigger an automatic second write.
- Reconciliation success requires independent evidence for the exact effect identity and all available immutable bindings.
- Duplicate approval, invocation, retry, restart, and concurrent workers converge on one durable reconciliation record.
- Already-uploaded or already-published exact state settles as confirmed without consuming new approval or write budget.
- Conflicting video/channel/profile/artifact/metadata/visibility/revision evidence fails closed and requests one explicit owner decision; it is never silently repaired.
- Bounded absence evidence does not prove a write never happened unless the provider contract supplies authoritative exact lookup semantics.
- Restart preserves uncertainty, consumed authority, counters, deadlines, and budgets.
- Secrets remain absent from planner payloads, durable workflow state, audit, logs, IPC, and exported settings.

### Verification
- State-machine serialization and canonical reconciliation-key tests.
- Crash matrix covering pre-send, partial send, provider receipt, lost response, post-response crash, and lookup interruption.
- Duplicate approval/invocation/retry/restart/concurrent-worker tests.
- Exact-match, already-applied, absent, multiple-candidate, cross-channel/profile, stale-revision, metadata/visibility drift, and unknown-video fixtures.
- Assertions that uncertain writes never replay and read-only reconciliation respects deadlines, cancellation, call/cost ceilings, and retry classes.
- Redacted audit/Android receipt tests plus canonical, Android, architecture, security, and privacy review.
- Live reconciliation remains UNVERIFIED unless separately exercised against an owner-authorized exact provider state.

### Expected result
Every uncertain YouTube upload or publication converges to an explicit, evidence-backed state without duplicate writes, reused authority, fabricated success, or silent cross-identity repair.

### Evidence basis
- `docs/ROADMAP_1.0.md` R6.7 explicitly requires handling crash after upload attempt, network loss after response, duplicate approval, and already-published state, and forbids blindly creating a second upload.
- TASK-041 and TASK-044 create durable upload/publication effect identities, but no current task, issue, or open PR represents their shared duplicate/uncertain reconciliation gate.

### Projection basis
- The Phase-6 exit gate cannot truthfully prove upload, processing, and publication correctness unless ambiguous write outcomes converge across restart without replay.
- A shared bounded reconciliation layer prevents upload and publication adapters from developing incompatible duplicate-detection and authority semantics.

### Risks / unknowns
- YouTube may not expose an authoritative exact lookup for every pre-video-ID upload boundary; such cases must remain uncertain rather than infer absence.
- Provider-side normalization and delayed indexing can create temporary mismatches; time bounds must not weaken exact identity checks.
- Multiple historical videos with similar metadata are not safe matches without stronger immutable evidence.
- Any destructive repair or compatibility policy requires separate owner authority and is out of scope.

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

