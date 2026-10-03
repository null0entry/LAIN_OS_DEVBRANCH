# LAIN_OS 1.0 Roadmap

**LAIN_OS — Local Autonomous Intelligence Network Operating System**

LAIN_OS is the concrete open-source platform described by this roadmap. It implements the broader **LAIN — Loyal Autonomous Intelligence Network** philosophy through a local-first operating layer for autonomous intelligence.

Version: 1.0-roadmap-r5
Status: Active product roadmap
Date: 2026-10-03

## 1. North-star release outcome

LAIN_OS 1.0 is a standalone Android voice-first autonomous agent suite that turns ordinary spoken or typed goals into bounded, durable, verified multi-step work while preserving human authority over every consequential action.

The release should feel like an operating layer for AI-assisted work rather than a chat front-end:

    user intent
      -> conversation
      -> planning
      -> durable workflow
      -> permissioned capabilities
      -> deterministic execution
      -> verification
      -> artifacts
      -> audit
      -> spoken/visual result

The flagship 1.0 proof is:

    "Turn this idea into a YouTube video and post it."

A successful demonstration means LAIN_OS creates a real script, narration, visual assets, rendered video, preview evidence, private upload, processing evidence, exact publication approval, publication, and an independently retrieved final result. A generated plan, simulated upload, mocked renderer, or planner assertion is not completion.

### Near-term product milestone — Voice-First LAIN Preview

The roadmap distinguishes the **usable agent milestone** from the complete 1.0 flagship scope.

The next product milestone is a standalone, voice-first LAIN running on the reference Galaxy device with real selectable planner intelligence and the existing trusted execution substrate. This milestone does **not** require the later media-production or YouTube-publication phases.

Required exit path:

    finish Phase 1 planner acceptance
      -> Phase 2 voice conversation
      -> current physical-device acceptance
      -> Voice-First LAIN Preview

Preview acceptance requires:
- installed Android GUI using a real selected planner profile;
- before voice work begins, the complete applicable typed Workbench preset suite reaches terminal **COMPLETE** on the reference Galaxy, including the exact-approval share path rather than relying on a single battery smoke test;
- spoken and typed goals entering the same trusted planning path;
- policy, exact approval, deterministic execution, verification, audit, and durable sessions remaining authoritative;
- cancellable speech playback and user barge-in;
- Stop task remaining independent from Stop talking;
- no planner/provider credential leakage;
- physical-device evidence on the current Galaxy build, explicitly distinguished from CI/emulator evidence.

This preview is a meaningful usable release checkpoint, not a claim that the complete 1.0 roadmap is finished. Phases 3–7 remain required for the roadmap-defined 1.0 flagship release.

### Progress interpretation

Progress should be reported by subsystem rather than as a single misleading percentage:

- **Trusted execution foundation:** mature pre-1.0 substrate; maintain acceptance and regression evidence.
- **Usable voice-first Galaxy agent:** near-term critical path is Phase 1 completion, Phase 2, then current-device acceptance.
- **Complete roadmap-defined 1.0:** still includes durable workflow graphs, artifact/media production, external providers and budgets, YouTube publication, and release hardening.

Repository task state and fresh verification evidence remain authoritative over prose estimates.

## 2. Non-negotiable architecture

The trusted runtime remains authoritative throughout 1.0.

- Models plan; trusted code authorizes and executes.
- Model output is untrusted data until validated.
- ACTION_PROTOCOL and AGENT_PROTOCOL remain the execution/planning boundaries.
- Capability risk, policy, approval, executor selection, verification, audit, and durable lifecycle state are never delegated to a provider.
- Consequential actions require exact user confirmation.
- Completed external effects are never blindly replayed.
- Uncertain external effects enter reconciliation instead of automatic retry.
- Credentials stay outside planner context, audit, checkpoints, IPC responses, screenshots, exports, and logs.
- Local mode never silently falls back to cloud.
- Cancellation stops future work but never pretends an external effect was rolled back.
- The portable Python runtime and CLI remain supported while Android becomes the primary product surface.
- No arbitrary shell, arbitrary authenticated HTTP, or general-purpose model-controlled device driver is introduced to shortcut typed capabilities.

## 3. Current baseline

### Implemented foundation

The repository already contains substantial pre-1.0 infrastructure:

- Typed ACTION_PROTOCOL validation.
- Capability registry, policy, confirmation, deterministic execution, verification, and audit.
- Filesystem and bounded Android capabilities.
- Provider-independent AGENT_PROTOCOL.
- Durable autonomous sessions with budgets, checkpointing, resume, cancellation, and reconciliation behavior.
- External-process planner boundary and real hosted planner precedent.
- Standalone Android application with embedded Python runtime.
- Android text workbench, Run/Stop, exact approvals, session history, recovery controls, and private runtime service.
- Native Android capability adapters.
- Physical acceptance evidence for earlier Android capability slices.
- PlannerBinding/session pinning groundwork in the trusted agent model.
- Android Keystore-backed planner SecretStore completed as TASK-002.

### Active product work

**Current candidate update (2026-10-03):** PR #16 is now at `1a894fb4ac7936549362e857bdb92f21e28251b9`. Verify #297 and Android #286 are GREEN, including API 24/35 instrumentation. The candidate includes the Galaxy-discovered filesystem hardlink fallback and planner-token reduction: strict JSON-Schema prompts no longer duplicate the capability catalog, planner completions are capped at 1024, Groq GPT-OSS uses low reasoning, and JSON-object mode retains the full catalog. Exact API-35 APK SHA-256: `d6474f768ee15ba2a1418d92a48d13f9647ed86cb85c102ae3b96e50d3f92102`. Physical acceptance remains open until this exact binary passes the full Galaxy matrix with expected capability evidence and truthful terminal state.

The current implementation focus is **TASK-008 Phase-1 planner acceptance on the physical Galaxy**, after automated installed-workbench acceptance reached GREEN on PR #16 head `c700eed0b9de6964729a6b038eaa565daf44864d` (Verify #270; Android #259).

Current physical evidence from the exact GREEN debug build:
- the standalone APK installs and launches on the Galaxy reference device;
- Offline Demo **Show battery** reaches terminal **COMPLETE**, proving the embedded runtime/native battery path on-device;
- a real Groq Cloud profile using `openai/gpt-oss-120b` is stored and selected, and Groq receives the app's requests;
- real-planner tasks currently fail before model execution because Groq rejects LAIN's generated JSON Schema for a zero-argument capability such as `android.battery_status`;
- the current connection diagnostic also produces separate JSON-validation 400s because it requests structured JSON with only one completion token;
- provider request errors are currently flattened into the unhelpful `unavailable` status;
- the `Run locally` UI label is misleading when Cloud planning is selected;
- Stop/cancellation requires a deliberately blocking planner acceptance test before any production cancellation change is justified.

Immediate TASK-008 patch sequence:
1. correct zero-argument capability schema generation while preserving strict validation for normal arguments;
2. make the connection diagnostic a truthful bounded endpoint/auth/model probe;
3. surface safe request/rate-limit/server/transport failure classes instead of collapsing them into `unavailable`;
4. use planner-neutral Run-button copy;
5. prove Stop against a genuinely in-flight planner call;
6. cut a new debug APK only from the exact post-patch GREEN head and rerun the complete Galaxy matrix.

**Phase-1 device acceptance is suite-level, not a battery smoke test.** The current Workbench presets — **Create demo file, Show battery, Show demo toast, Vibrate briefly, Copy demo text, and Share demo text** — must each reach terminal **COMPLETE** through the intended real selected planner where applicable. Share must require exact approval and then complete without automatic sending. Each run must preserve truthful execution/verification status, including LIMITED where the capability contract cannot independently prove the physical/UI effect.

No later phase should bypass this planner/trusted-runtime boundary. Phase 2 voice work starts only after TASK-008 is reconciled against this gate.

## 4. Scope for 1.0

### Required

1. Real selectable model intelligence in the Android app.
2. Demo, hosted cloud, and user-provided local endpoint modes.
3. Secure provider credentials.
4. Voice input and spoken output.
5. Conversational interruption and revision while work continues.
6. Durable multi-stage workflows above the existing action-batch controller.
7. Versioned artifact workspace with hashes and provenance.
8. Real offline video rendering and inspection.
9. Replaceable speech/media provider adapters with bounded cost and retry behavior.
10. YouTube OAuth, private upload, processing verification, exact publication gate, and final lookup.
11. Crash/restart recovery without duplicate side effects.
12. Installed-device end-to-end acceptance.
13. Release signing, migration, packaging, documentation, and a reproducible release-readiness report.

### Explicitly deferred beyond 1.0

- Embedded GGUF/on-device LLM inference.
- Model download/management UI.
- Arbitrary cinematic video generation.
- Voice cloning.
- Always-listening microphone or wake word.
- Multiple simultaneous active workflows.
- Unrestricted AccessibilityService automation.
- General authenticated HTTP capability.
- Provider-managed tool execution outside LAIN protocols.
- RAG/vector database as a core requirement.
- Autonomous spending without explicit user-controlled budgets and authorization.
- Multi-device distributed execution as a release requirement.

These are post-1.0 expansion targets, not hidden release blockers.

## 5. Dependency graph

The critical path is intentionally sequential at trust-boundary transitions:

    Foundation
      -> P1 Pluggable Planner Runtime
      -> P2 Voice Conversation
      -> P3 Durable Workflow Graph
      -> P4 Offline Media Pipeline
      -> P5 External Provider Adapters
      -> P6 YouTube Publication
      -> P7 End-to-End Hardening
      -> 1.0 Release

Some implementation can overlap after interfaces stabilize:

    P2 Voice ----------------------+
                                   |
    P3 Workflow Graph -> P4 Media -+-> P5 Providers -> P6 YouTube
                                   |
    Android hardening -------------+-> P7 Release

Do not parallelize across an unstable public interface merely to increase throughput.

## Critical path to the next usable release

The immediate ordered product path is:

1. Close **TASK-008 / Phase 1 planner acceptance** by fixing the observed Groq schema/diagnostic defects, preserving Cloud/Local automated acceptance, proving in-flight Stop, and requiring the full applicable installed Workbench preset suite to reach terminal **COMPLETE** on the Galaxy through the real selected planner.
2. Complete **TASK-009 through TASK-015 / Phase 2 voice conversation**.
3. Run current-build **physical Galaxy acceptance**, including planner happy paths, approval transitions, rotation/rebind/background behavior, interruption, and measured Stop behavior.
4. Cut the **Voice-First LAIN Preview** only from evidence that passes those gates.
5. Continue Phases 3–7 toward the complete 1.0 flagship release.

Media production and YouTube publication must not block the Voice-First LAIN Preview, but the preview must not be relabeled as the complete 1.0 release.

## 6. Phase 0 — Foundation consolidation

Status: substantially complete; maintain and close remaining acceptance debt.

### Goal

Preserve a known-safe execution substrate before adding richer model, voice, media, and publishing surfaces.

### Existing feature set

- Runtime validation/policy/execution/verification/audit.
- Durable autonomous session controller.
- Android embedded runtime and UI.
- Exact approval model.
- Stop/cancel path.
- Filesystem and Android capability set.
- Secure planner credential store.

### Remaining foundation work

R0.1 — Physical Android interface acceptance
- Re-run device acceptance on the latest runtime-affecting APK.
- Verify Stop during confirmation transition and active work.
- Verify rebind/rotation/background behavior.
- Verify stale approval rejection.
- Record device/API/build SHA and observed limits.

R0.2 — Release-state hygiene
- Ensure architecture/spec/README claims match fresh evidence.
- Keep release/version/migration metadata consistent with the shipped product.
- Keep secrets and external orchestration context out of repository state.

R0.3 — Regression baseline
- Preserve canonical portable verification.
- Preserve Android assembly/lint/instrumentation workflows.
- Keep explicit distinction between automated, emulator, and physical-device evidence.

### Exit gate

Foundation can support later phases without changing planner authority, approval semantics, session recovery, or audit contracts.

## 7. Phase 1 — Pluggable Planner Runtime

Maps to the existing Phase 2/P2-01 through P2-07 plan.

Status: IN PROGRESS.

### Goal

Replace the Android app's hardcoded demo-only planning path with user-selectable Demo, Cloud, or Local model intelligence while keeping all authority local and deterministic.

### Planned feature slices

#### R1.1 — Provider-neutral planner contract

Objectives:
- centralize request construction;
- centralize lifecycle decision parsing;
- strictly validate status, actions, keys, bounds, and capability references;
- keep provider-specific transport outside the trusted planning service.

Tasks:
- inventory existing Groq and demo logic;
- remove duplicated provider-specific parsing where present;
- pin strict malformed/oversized/unknown-field failure tests;
- preserve AGENT_PROTOCOL semantics.

Acceptance:
- Demo and existing hosted planner behavior remain valid.
- Invalid model output fails closed.
- No provider can choose trusted IDs, policy, verification, or execution.

#### R1.2 — Planner profile persistence

Feature:
- provider-neutral PlannerProfile configuration.

Fields:
- profile ID/name;
- mode: DEMO, CLOUD, LOCAL;
- protocol;
- base URL;
- model;
- opaque credential reference;
- timeout;
- response-size bound;
- response mode;
- explicit local-network policy fields where supported.

Tasks:
- implement native profile persistence;
- validate endpoint/model/profile inputs;
- expose active profile;
- capture the selected binding when a session starts;
- ensure settings changes affect future sessions only.

Acceptance:
- restart preserves configuration;
- active sessions remain pinned to their original planner identity;
- raw credentials never enter profile state.

#### R1.3 — Secure credential store

Status: implemented in TASK-002.

Maintain:
- Android Keystore encryption;
- credential-reference binding;
- swap/rename failure behavior;
- concurrent initialization safety;
- replace/remove behavior;
- backup/device-transfer protections.

#### R1.4 — Native bounded planner transport

Status: active implementation focus.

Tasks:
- resolve Local HTTP security/compatibility contract;
- make malformed-response validation truthful in the tested runtime;
- retain connect/read timeout bounds;
- retain bounded streaming response reads;
- retain cancellation;
- retain redirect protections;
- retain structured provider failure codes;
- prove no local-to-cloud fallback.

Decision rule for plaintext Local HTTP:
- never globally enable cleartext merely for convenience;
- support it only if Android policy can constrain it to the documented local mode safely;
- otherwise reject it at configuration/binding time and require HTTPS for 1.0.

#### R1.5 — Android/Python planner bridge

Goal:
- replace hardcoded DemoPlanner construction with profile-selected planner creation.

Tasks:
- define the smallest native transport bridge;
- ensure Python receives only model response material and non-secret provider identity;
- feed response into existing AgentPlanningService;
- preserve policy/executor/verification/audit route unchanged;
- propagate cancellation from Android Stop to transport and controller.

Acceptance:
- Demo works offline.
- Cloud endpoint can produce a valid planner decision.
- Local endpoint can produce a valid planner decision.
- No bridge method grants capability authority.

#### R1.6 — Planner Settings UI

Features:
- Demo / Cloud / Local selection;
- create/edit/delete/select profile;
- model and endpoint fields;
- credential saved/replaced/removed state without secret reveal;
- Test Connection;
- active provider/model visibility in Workbench;
- structured recovery errors.

Test Connection constraints:
- no agent session;
- no capability execution;
- no filesystem/device action;
- bounded endpoint diagnostic only.

#### R1.7 — Planner acceptance and adversarial suite

Must cover:
- DNS failure;
- connection refused;
- TLS failure;
- 401/403;
- 404/model missing;
- timeout/408;
- 429;
- 5xx;
- cancellation;
- oversized response;
- malformed JSON;
- schema-invalid model output;
- invalid/unknown capability;
- provider outage during session;
- local endpoint loss;
- no cloud fallback;
- credential absence from durable artifacts;
- app restart/profile persistence.

### Phase 1 exit gate

From the installed Android GUI:

    select real model profile
      -> enter natural-language request
      -> model proposes typed action
      -> trusted validation
      -> policy/approval
      -> deterministic execution
      -> verification
      -> audit
      -> visible result

This must work without changing the trust model.

## 8. Phase 2 — Voice Conversation

Maps primarily to F2.

Status: planned after stable planner selection/transport.

### Goal

Make conversation the primary interface while keeping text as a complete fallback.

### Features

- user-started microphone sessions;
- visible recording state;
- speech-to-text adapter boundary;
- text transcript;
- selectable speech synthesis voice;
- streaming/progressive spoken responses where supported;
- playback cancellation;
- barge-in: user can interrupt synthesis;
- distinction between Stop talking and Stop task;
- active-task conversational reference;
- typed fallback at all times;
- microphone permission/revocation handling;
- no raw audio retention by default.

### Rough work packages

R2.1 — Speech provider interfaces
- TranscriptionRequest/Result.
- SynthesisRequest/Result.
- cancellation and timeout contracts.
- provider identity/provenance.

R2.2 — Android microphone lifecycle
- permission flow;
- capture start/stop;
- visible state;
- background/rotation behavior;
- no hidden always-listening mode.

R2.3 — Turn manager
- monotonic turn IDs;
- final vs partial transcript separation;
- active task reference;
- ambiguous referent clarification;
- revision routing.

R2.4 — Speech playback
- audio focus;
- start/stop/duck;
- TTS cancellation;
- playback state surfaced to UI.

R2.5 — Barge-in and echo protection
- speech during synthesis stops/ducks playback;
- prevent synthesized speech becoming a user command;
- verify partial recognition cannot authorize a consequential action.

R2.6 — Voice progress narration
- short progress events separate from durable workflow state;
- speech failure never equals task failure;
- task continues if TTS is unavailable.

R2.7 — Voice acceptance
- permission denied/revoked;
- offline/provider unavailable;
- rotation/rebind;
- interruption;
- Stop talking vs Stop task;
- low-confidence consequential command;
- latency measurement on declared reference device.

Targets from the 1.0 design:
- user interruption to playback stop: at most 500 ms on reference device;
- trusted pause/cancel receipt: within two seconds in controlled tests.

### Phase 2 exit gate

The user can start a voice session, give a normal spoken goal, hear a spoken response, interrupt it, revise the request, and stop the underlying task independently of speech playback.

## 9. Phase 3 — Durable Workflow Graph and Artifact Workspace

Maps to F3.

### Goal

Move from bounded action batches to durable, inspectable multi-stage projects without replacing the existing trusted executor.

### Workflow model

Each workflow/task node should track:

- stable ID;
- dependencies;
- status;
- assigned restricted role;
- allowed capability families;
- input artifact references/hashes;
- output artifact references/hashes;
- acceptance checks;
- attempted operations;
- consumed budgets;
- retry counters;
- provider job IDs;
- deadlines;
- error/recovery state;
- workflow revision;
- user approvals;
- reconciliation state.

Recommended states:
- pending;
- ready;
- running;
- waiting_for_owner;
- succeeded;
- failed;
- cancelled;
- reconciliation.

### Rough work packages

R3.1 — Workflow persistence model
- schema/versioning;
- atomic state writes;
- migration support;
- corrupt-state failure behavior.

R3.2 — DAG scheduler
- dependency readiness;
- one active workflow initially;
- bounded worker leases;
- no downstream execution until required verified outputs exist.

R3.3 — Artifact workspace
- immutable artifact identity by hash;
- versions/revisions;
- metadata/provenance;
- user-visible workspace;
- atomic writes;
- retention/cleanup policy.

R3.4 — Restricted specialist roles
Initial logical roles:
- coordinator;
- writer;
- visual planner;
- narrator;
- renderer;
- publisher.

Roles are context/capability restrictions, not privileged independent executors.

R3.5 — Revision invalidation
Example:
- "Make it shorter" creates a new script revision;
- invalidates narration/render artifacts that depend on the old script;
- never mutates an already approved publication payload in place.

R3.6 — Durable wait/poll stages
- long provider/render/upload jobs;
- persisted deadlines;
- bounded polling;
- no silent extension of existing controller budget.

R3.7 — External-effect reconciliation
- persist operation identity before crossing side-effect gate;
- crash after attempted write enters reconciliation;
- completed effect cannot replay after restart;
- uncertain effect cannot automatically retry.

R3.8 — Aggregate budgets
- time;
- action count;
- provider call count;
- bytes;
- estimated/actual cost;
- retry allowance.

### Phase 3 exit gate

A multi-stage workflow survives process death, resumes without replaying completed effects, invalidates downstream artifacts correctly after revision, and exposes a truthful user-readable state.

## 10. Phase 4 — Offline Media Production Pipeline

Maps to F4.

### Goal

Prove LAIN_OS can produce a real useful media artifact locally before depending on external media-generation services.

### 1.0 media scope

A short 30–60 second narrated video using:
- user-provided or bundled licensed images;
- generated text/title cards;
- narration audio;
- captions;
- simple transitions;
- deterministic timeline/render settings.

Not required:
- cinematic generative video;
- avatars;
- voice cloning;
- complex NLE editing.

### Proposed capability families

- speech.synthesize
- media.render_video
- media.inspect_video

Capability names remain subject to repository contract review before registration.

### Rough work packages

R4.1 — Media artifact schemas
- image/audio/video metadata;
- MIME/codec expectations;
- dimensions;
- duration;
- hashes;
- provenance.

R4.2 — Narration pipeline
- script segments;
- synthesis;
- bounded audio file output;
- duration validation.

R4.3 — Timeline representation
- scenes;
- image/audio references;
- captions;
- transition durations;
- output constraints.

R4.4 — Renderer
- select Android-compatible or bundled rendering toolchain;
- no shell interpolation;
- bounded resources;
- cancellation;
- deterministic output path.

R4.5 — Video inspection
Verify actual rendered file:
- exists;
- decodes;
- has video stream;
- has audio when expected;
- dimensions;
- duration;
- file hash.

R4.6 — Preview/export UI
- show artifact;
- user can inspect;
- render progress;
- cancel;
- export/share only through explicit capability policy.

R4.7 — Offline golden fixture
- bundled licensed assets;
- real 30–60 second render;
- no cloud account;
- real inspection evidence.

### Phase 4 exit gate

A fresh installed APK can produce and inspect a real video from local assets while preserving app responsiveness, Stop behavior, durable workflow state, and artifact hashes.

## 11. Phase 5 — External Speech, Media, and Planner Providers

Maps to F5.

### Goal

Add replaceable external services without letting provider APIs become new authority surfaces.

### Features

- provider-neutral adapter contracts;
- fake transports for deterministic tests;
- bounded network requests;
- cancellation;
- timeout;
- rate-limit handling;
- retry policy;
- provider job polling;
- cost estimation and user budgets;
- provenance and provider identity;
- no implicit provider fallback that changes privacy/cost semantics.

### Rough work packages

R5.1 — Provider job abstraction
- submit;
- poll;
- cancel where supported;
- terminal status;
- result artifact retrieval.

R5.2 — Speech provider adapter
- STT/TTS through the Phase 2 interface;
- secret handling;
- throttling/errors.

R5.3 — Image/media generation adapter
- bounded dimensions/count;
- content/provenance metadata;
- download validation;
- decoder validation;
- hash before workflow consumption.

R5.4 — Budget and spending controls
- per-workflow spending ceiling;
- per-provider call count;
- user-visible estimate;
- actual cost recording where provider exposes it;
- no provider estimate grants spending authority.

R5.5 — Retry/backoff policy
- retry only known-safe idempotent/read operations automatically;
- external writes require idempotency identity or reconciliation;
- 429/5xx bounded backoff.

R5.6 — Provider privacy disclosure
- explicitly show when text/audio/images leave the device;
- profile-specific data-sharing state;
- Local selection never becomes Cloud automatically.

### Phase 5 exit gate

The same workflow can use configured remote providers under bounded budgets and failure handling while trusted local policy/execution semantics remain unchanged.

## 12. Phase 6 — YouTube Publication

Maps to F6.

### Goal

Add a narrowly scoped, user-authorized publishing path for the flagship workflow.

### Capability family

Proposed:
- youtube.upload
- youtube.status
- youtube.publish

Do not expose generic YouTube HTTP or arbitrary authenticated API access.

### Rough work packages

R6.1 — OAuth account authorization
- current official authorization flow;
- minimum required scopes;
- account/channel identity display;
- Keystore-backed token protection;
- revocation/removal.

R6.2 — Upload intent record
Persist before upload:
- artifact hash;
- destination channel;
- title;
- description;
- intended visibility;
- workflow revision;
- stable operation/idempotency identity.

R6.3 — Resumable private upload
- default first upload to private/unlisted staging as selected by policy;
- progress;
- cancellation semantics;
- resumable state;
- crash recovery.

R6.4 — Processing status verification
- independent authenticated lookup;
- processing state;
- video ID;
- failure reason;
- no "published" claim while processing is incomplete.

R6.5 — Exact publication approval
Approval binds:
- exact video ID;
- artifact hash;
- destination channel;
- title;
- description;
- visibility;
- policy revision;
- nonce;
- expiry.

A generic earlier phrase such as "post it" does not authorize a later changed payload.

R6.6 — Publish operation
- execute only after exact approval;
- persist attempt before side effect;
- independent lookup afterward.

R6.7 — Duplicate/uncertain upload reconciliation
- crash after upload attempt;
- network loss after response;
- duplicate approval;
- already-published state;
- never blindly create a second upload.

R6.8 — Live acceptance
- use user-provided authorization;
- separately authorize any real upload/publication;
- keep live tests private until explicit publication approval;
- record exact retrieved result.

### Phase 6 exit gate

LAIN_OS can upload the exact approved artifact, verify processing, require exact publication approval, publish, and independently retrieve the final metadata/visibility without leaking credentials or duplicating an uncertain external write.

## 13. Phase 7 — End-to-End 1.0 Hardening

Maps to F7.

### Goal

Turn individually working subsystems into one credible installed product and produce the evidence required for a 1.0 claim.

### Golden scenario

    launch installed app
      -> start voice session
      -> speak idea
      -> selected planner creates workflow
      -> script produced
      -> user revises script verbally
      -> downstream artifacts invalidated/rebuilt
      -> narration produced
      -> visuals prepared
      -> video rendered
      -> video inspected
      -> user previews
      -> private YouTube upload
      -> processing verified
      -> exact publication approval
      -> publish
      -> final metadata/link independently retrieved
      -> workflow closes with audit + artifact manifest

### Adversarial release matrix

R7.1 — Conversation failures
- microphone denial;
- microphone revocation;
- STT outage;
- TTS outage;
- user interruption;
- echo/self-command;
- ambiguous task reference.

R7.2 — Planner failures
- malformed output;
- hostile instructions;
- unknown capability;
- oversized response;
- timeout;
- 429;
- provider outage;
- local endpoint loss;
- no fallback.

R7.3 — Workflow/recovery failures
- process death between nodes;
- process death after external effect attempt;
- corrupt state;
- stale revision;
- exhausted budget;
- duplicate resume;
- duplicate approval;
- workflow cancel during provider wait.

R7.4 — Media failures
- corrupt image;
- corrupt audio;
- renderer crash;
- insufficient storage;
- oversized asset;
- invalid codec/output;
- render cancellation.

R7.5 — Publication failures
- OAuth revoked;
- quota exhausted;
- resumable upload interruption;
- processing failure;
- uncertain upload result;
- publication verification mismatch.

R7.6 — Security/privacy
- secret scan of durable state;
- log/audit redaction;
- IPC/Binder restrictions;
- path traversal;
- hostile filenames;
- unauthorized provider change;
- approval replay;
- data export review.

R7.7 — Performance and resource behavior
- voice interruption latency;
- Stop receipt latency;
- memory pressure;
- thermal pressure;
- long render responsiveness;
- background/foreground transitions;
- restart timing.

## 14. Release engineering

### R1.0 release candidate requirements

Build:
- reproducible release build;
- maintainer-controlled persistent signing key;
- release package identity/versioning;
- supported ABI/API declaration;
- migration from current debug/private state where applicable.

Verification:
- canonical Python verification;
- Android build;
- lint;
- JVM tests;
- instrumentation on supported API matrix;
- physical device acceptance;
- end-to-end golden workflow;
- adversarial matrix;
- secret scan;
- artifact manifest.

Documentation:
- install/upgrade;
- supported Android versions/devices;
- permissions;
- provider configuration;
- privacy/data flow;
- local-vs-cloud semantics;
- recovery/Stop behavior;
- YouTube authorization;
- known limitations;
- backup/export behavior;
- troubleshooting.

Release artifacts:
- signed APK or chosen distributable;
- checksum;
- version/tag;
- source commit;
- migration notes;
- test/evidence report;
- third-party notices/licenses;
- known-issues list.

### 1.0 definition of done

LAIN_OS 1.0 may be claimed only when all of the following are true:

1. A release-signed Android artifact is built from a recorded source commit.
2. The installed app can use Demo plus at least one real configurable planner mode.
3. Hosted/local selection obeys documented privacy and fallback semantics.
4. Voice input/output works on a declared physical reference device.
5. Barge-in and trusted Stop behavior meet recorded acceptance criteria.
6. Durable workflows survive restart without replaying completed effects.
7. Artifact revisions and downstream invalidation work.
8. A real offline video fixture renders and passes media inspection.
9. External provider failures cannot become fabricated success.
10. YouTube upload/publish uses exact authority and independent verification.
11. The golden spoken idea-to-video workflow succeeds through real installed artifacts.
12. Required automated, emulator, physical, and external-service evidence is recorded separately and truthfully.
13. Secrets are absent from durable planner/audit/log/export surfaces.
14. Critical unresolved security/recovery/data-loss findings are zero.
15. Documentation matches the shipped behavior.
16. No debug APK, mocked external effect, skipped test, or planner assertion is presented as release evidence.

## 15. Sequential product development sequence

The following 50 product-development items are the concrete forward path from the current app to the intended 1.0 experience. They describe application work, not organizational process.

1. Finish the native planner transport.
2. Resolve Local HTTP versus Android cleartext-security policy.
3. Make malformed planner-response validation platform-neutral.
4. Finish hosted OpenAI-compatible model transport.
5. Finish local OpenAI-compatible endpoint transport.
6. Build the Android-to-Python planner bridge.
7. Replace hardcoded DemoPlanner construction with runtime planner selection.
8. Add persistent PlannerProfile storage.
9. Add active-profile selection.
10. Add session-pinned model/provider identity.
11. Build Planner Settings UI.
12. Add Cloud / Local / Demo mode selection.
13. Add model-name and endpoint configuration UI.
14. Add secure credential create/replace/remove UI.
15. Add inert Test Connection diagnostics.
16. Display the active provider/model in the workbench.
17. Complete the first real natural-language action through the Android GUI.
18. Add microphone permission and capture lifecycle.
19. Add a speech-to-text provider interface.
20. Add the first working speech-to-text implementation.
21. Add a text-to-speech provider interface.
22. Add the first working text-to-speech implementation.
23. Add selectable agent voices.
24. Add conversational transcript UI.
25. Add streaming/progressive assistant speech.
26. Add barge-in so user speech interrupts synthesis.
27. Separate Stop talking from Stop task.
28. Add echo/self-command prevention.
29. Add voice-driven task revisions such as "make it shorter".
30. Build durable multi-step workflow DAG storage.
31. Add workflow dependency scheduling.
32. Add durable workflow revision numbers.
33. Build the versioned artifact workspace.
34. Hash every workflow artifact.
35. Add provenance metadata for generated and downloaded artifacts.
36. Add downstream invalidation when upstream artifacts change.
37. Add durable provider-job polling.
38. Add crash recovery for long-running workflows.
39. Add uncertain-external-effect reconciliation.
40. Add aggregate workflow budgets for time, actions, cost, bytes, and retries.
41. Add restricted specialist roles: writer, visual planner, narrator, renderer, and publisher.
42. Build the script-generation workflow stage.
43. Build the narration-generation stage.
44. Build the image/visual-asset stage.
45. Build the deterministic video timeline format.
46. Integrate a real Android-capable video renderer.
47. Build real video inspection: decode, streams, dimensions, duration, audio, and hash.
48. Build preview/review UI for generated videos.
49. Add YouTube OAuth, resumable upload, exact publication approval, and independent verification.
50. Ship the complete installed-phone flow: speak idea -> plan -> create assets -> render video -> preview -> approve -> publish -> verify -> report result.

## 16. Prioritization rules

When selecting the next engineering task:

1. Finish an already active unblocked task before creating a duplicate.
2. Choose critical-path trust-boundary work before cosmetic polish.
3. Prefer work that creates an independently testable vertical slice.
4. Resolve correctness/security contract mismatches before stacking features on them.
5. Reuse an existing interface before creating a parallel abstraction.
6. Keep provider-specific logic at adapter boundaries.
7. Keep voice/media/publishing state out of RuntimeEngine unless it is truly execution authority.
8. Do not let deferred physical acceptance block independent local implementation, but never convert deferred evidence into a pass claim.
9. Keep external credentials/cost/publication as late-bound acceptance dependencies so local work can continue without them.
10. Do not describe roadmap aspirations as implemented product state.

## 17. Key decisions to resolve during implementation

These require explicit design/security/compatibility choices before affected work can be declared complete:

### Local plaintext HTTP
Choose one truthful 1.0 contract:
- narrowly safe Android Local-LAN HTTP support; or
- HTTPS-only Local endpoints for 1.0.

Global cleartext enablement is not acceptable merely to make local endpoints convenient.

### Speech engines
Select initial STT/TTS providers after current capability, licensing, latency, Android compatibility, privacy, and cost review. The roadmap requires replaceable interfaces rather than a permanent provider commitment.

### Video renderer
Select a renderer that can be packaged/licensed for Android, supports cancellation and bounded execution, and produces inspectable real files. The offline fixture must use the actual selected renderer.

### External image/media generation
Choose a first provider only after the local media pipeline works. Provider-specific APIs must not become the workflow model.

### YouTube API
At implementation time, verify OAuth, scopes, quota, upload, processing, visibility, and policy behavior against then-current official documentation.

### Release distribution
Decide whether 1.0 ships as direct signed APK, alternative store package, or both. Do not let distribution choice weaken signing/update/state-migration requirements.

## 18. Post-1.0 trajectory

After the 1.0 trust/recovery/product loop is stable:

### 1.1 — On-device inference
- GGUF import/download;
- checksum/integrity;
- hardware compatibility;
- native inference runtime;
- load/unload lifecycle;
- context/token controls;
- thermal/memory limits;
- same AgentPlanner contract.

### 1.2 — Richer media
- generated video clips;
- richer transitions;
- music/audio mixing with licensing controls;
- optional avatar pipeline;
- reusable project templates.

### 1.3 — Advanced conversation
- optional wake word;
- more offline speech;
- user-defined voices where legally/licensably supported;
- longer-lived conversational context with explicit retention controls.

### 1.4 — Multi-workflow scheduling
- safe concurrent workflows;
- leases/output ownership;
- aggregate budgets;
- priority/preemption;
- resource arbitration.

### 1.5 — Multi-device LAIN networking
- authenticated peer discovery;
- capability advertisement;
- delegated typed actions;
- per-node policy;
- signed/audited inter-node requests;
- no implicit remote authority.

## 19. Success metric

The project has reached 1.0 when LAIN_OS is no longer merely a collection of agent primitives.

It must be a coherent Android product in which the user can speak an outcome, watch and interrupt bounded work, inspect intermediate artifacts, authorize consequential steps, survive failures/restarts without duplicate effects, and receive an independently verified real-world result.

That is the release target.
