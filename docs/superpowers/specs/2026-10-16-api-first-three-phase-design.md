# LAIN_OS v2: API-first voice, planner, unified conversation

**Status:** Proposed architectural redesign. Requires owner review before implementation.
**Date:** 2026-10-16
**Scope:** Android architecture, capability scaffolding, hosted model/planner, speech input, single shared conversation.
**Owner directive:** Speech-to-text input interpreted by a model for intent, planner executes the proper function. Defer/abandon local LLM support for this release. Build in order: (1) scaffolding, (2) planner, (3) integrated chat.
**Supersedes for active development:** the GGUF/on-device-model release path and TASK-081's two-screen UX. Older docs/PRs are historical evidence, not active acceptance criteria.

## 0. Decision / non-negotiable outcome

LAIN_OS is a **single voice-and-text Android assistant**, backed by a **hosted HTTPS model API**, that understands a user's intent, chooses only supported structured actions, executes them through trusted device adapters and permissions, validates real results, and responds conversationally inside **one persistent thread**.

The three stages are strictly ordered:

1. **Foundation:** every tool has a discoverable typed capability descriptor, its authorization/permission gate, an executor adapter contract, and an observable result/error contract. Add real Android implementations incrementally; registry entries never imply nonexistent privileges.
2. **Planner:** every accepted final user message, including "Show Demo Toast", goes through the chosen model API for intent detection. A trusted orchestration layer consumes a strict response, validates proposed actions against the capability catalog and policy, requests consent, executes, verifies, persists receipts, and loops back to the model.
3. **Chat:** replace Workbench and LocalChat with a single composer/timeline, one canonical transcript and bounded context that includes prior messages and authoritative tool outcomes. The selected model answers **every** accepted turn when available. When unavailable, show explicit failure instead of an invented answer.

**Separate concepts:** the language model proposes and speaks; the **planner controller** orchestrates and tracks actions; **executors** invoke Android APIs; verification observes effects. Model text never directly runs commands or grants permissions.

**Hard reality:** "any Android task" is an extensibility *goal*, not a promise to bypass Android sandboxing or inaccessible system APIs. Support capabilities permitted to ordinary apps, supported intents, authorized roles/consent, or explicitly separately installed integrations. Unknown or privileged-only requests terminate as UNSUPPORTED with explanation, never simulated success.

**Out of scope for this redesign:** shipping local GGUF inference, retaining model download/import/preload UI, arbitrary shell execution, invisible always-on microphone, unrestricted Accessibility, root escalation, unauthorized contacts/SMS access, or expanding to desktop/network nodes. Existing GGUF files and user-owned model downloads are not automatically deleted.

## 1. System boundary and event flow

```text
[Mic tap / trusted final STT]   [Keyboard Send]
            \                       /
               [one submit_turn()]
                       |
         [durable canonical user turn]
                       |
     [HTTPS model API: intent + tool proposals]
              /                     \
         ordinary chat         structured tool plan
              |                     |
              |           [trusted schema+catalog validation]
              |           [policy+permission/role check]
              |           [owner approval, if required]
              |           [bounded executor / device intent]
              |           [readback/verification + audit]
              |                     |
              +---------------------+
                       |
           [model API response synthesis]
                       |
     [persist assistant reply OR explicit failure]
                       |
              [ONE chat timeline]
```

- UI never independently invents tool receipts, model replies, execution results or consent.
- **All final messages enter model intent interpretation first** when API is configured and reachable; deterministic demo parsing is a test fixture only, not a hidden product bypass around the API.
- STT is an input adapter, **not** the hosted LLM; choose Android platform SpeechRecognizer when available, with appropriate privacy disclosure (it may use an external service); an explicit alternative hosted STT adapter may be configured later. No bundled speech or language model required. On-device STT if supplied by Android is permitted as a platform service and is **not** the abandoned local LLM feature.
- The model API is contacted over verified HTTPS for inference; actual device execution and secrets stay under trusted local Android control. Cloud input may include bounded transcript and redacted tool observations only with transparent disclosure.
- API/network unavailable => accepted turn and draft preserved, honest retryable failure; no silent provider substitution or execution based on unchecked text.

## 2. Phase 1: foundation/scaffolding

### 2.1 Deliverables (backend before broad UI)

**Modules/interfaces, reusing existing repository seams when possible:**
- `CapabilityRegistry`: typed, versioned descriptors, availability probe, versioned schema, SDK/role/permission requirements, side-effect class and verification contract.
- `PermissionBroker`: current grant and eligibility state, Android-owned permission/settings/role prompts initiated only when required, denial/revocation handling; no "grant everything" first-run ritual.
- `ActionPolicy`: explicit ALLOW / REQUIRE_EXACT_CONFIRMATION / DENY per capability plus targets, scope, data sensitivity, reversibility, and context.
- `ActionExecutor`: well-defined native adapter interface; no global "device driver". Fixed intent and API surfaces, never raw arbitrary Intent fields from model.
- `ResultVerifier`: structured observed/limited/unavailable/failed outcomes separate from executor acceptance.
- `AuditRepository`: append-only non-secret lifecycle events with receipts and idempotency keys.
- `ConversationStore`: canonical transactionally persisted conversation events and task receipts (foundation schema, Phase 3 UI later).
- `ModelTransport`: one hosted API-backed profile, bounded HTTPS request/stream/cancel/timeout, KeyStore-protected secret. Reuse native cloud transport and existing provider-independent planner protocol.
- `SpeechInputPort`: microphone session lifecycle and trusted *final* transcript events; typed-input parity and platform recognizer diagnostics. Do not let partial or synthesized speech authorize anything.
- `PlatformInventory`: OS/API level, app install mode/role, package visibility, available providers, permissions and per-capability READY / NEEDS_PERMISSION / NEEDS_SETTINGS / NEEDS_USER_APP / RESTRICTED / UNSUPPORTED reasons.
- `ActionCoordinator`: durable plan/step IDs, budgets, single active lease, cancellation, dedupe, checkpoints and crash recovery.
- `Telemetry/diagnostics`: redacted health checks, model/STT/network/provider latency, build SHA, runtime errors and user-visible failure classification. No microphone audio or API keys in diagnostics.

**Do not scaffold alternative inference engines.** Preserve old model data read-only for upgrade/export, then remove local GGUF imports, JNI inference, downloads and resident model controls from active UI/build only after migration/tests confirm no dependency remains. No destructive uninstall cleanup.

### 2.2 Capability descriptor and tool variables

Every tool must be registered against this model or equivalent strongly typed definitions:

```json
{
  "capability_id": "android.toast",
  "version": 1,
  "description": "Request a transient toast",
  "input_schema": {"type":"object","properties":{"content":{"type":"string"}},"required":["content"],"additionalProperties":false},
  "min_api": 24,
  "max_api": null,
  "adapter": "native.android.toast",
  "availability": "READY | NEEDS_PERMISSION | NEEDS_SETTINGS | NEEDS_ROLE | NEEDS_EXTERNAL_APP | RESTRICTED | UNSUPPORTED",
  "permissions": [],
  "special_access": [],
  "required_role": null,
  "risk": 1,
  "approval": "policy_dependent",
  "side_effect": "transient",
  "verification": "LIMITED",
  "timeout_ms": 5000,
  "max_attempts": 1,
  "supported_in_foreground": true,
  "supported_in_background": false
}
```

**Registry mandatory invariants:**
- Explicit schema types, enums, ranges, max string sizes, URL schemes, path/data scopes, reject unknown fields and invalid encodings.
- Each descriptor states API floor, package/feature dependencies, required normal/dangerous/special permissions, role, foreground/background eligibility, read/write scope, user approval class, readback mechanism, timeout, retry semantics and unsupported reason.
- Registry advertises **only capabilities whose implementation and validation are present** as executable; unimplemented catalog entries show PLANNED/UNSUPPORTED rather than callable. Never expose a fake "do anything" tool.
- Permissions and runtime grants checked **immediately before each execution**, not only at startup. Grant revocation, no handler, app background restrictions or API incompatibility fail closed.
- Model sees a *filtered available* catalog, not privileged capabilities that cannot be performed; trusted registry remains authority.
- Every step has stable IDs, cancellation token, idempotency strategy, bounded execution limits and redacted evidence.
- Tool result format: `{action_id, capability_id, status: COMPLETE|FAILED|DENIED|UNSUPPORTED|UNCERTAIN, execution: ACCEPTED|REJECTED|NOT_ATTEMPTED, verification: PASSED|FAILED|LIMITED|UNAVAILABLE, observed_at, evidence_summary_redacted, retry_safe}`. These are trusted runtime values only.

### 2.3 Capability surface inventory (scaffolding catalog, not implied full implementation)

Group / example capability IDs / Android path / authority:

| Family | Requested tools and safe implementation path | Permission or hard boundary |
| --- | --- | --- |
| Device feedback | `android.toast`, `android.vibrate`, `android.battery_status`, `android.notify` | Bounded native APIs; notification display needs runtime grant on supported Android versions; visibility often LIMITED |
| Audio/system settings | `android.volume.set`, `android.brightness.request`, `android.rotation.request`, `android.timeout.request`, `android.torch.set` | Native API if allowed; settings writes can require user-granted special access; some controls device/policy restricted |
| Apps/navigation | `android.app.launch`, `android.uri.open`, `android.share_text`, `android.intent.chooser` | Installed handler and package visibility; never arbitrary package private data or force-stop authority |
| Files | `file.read`, `file.write`, `file.copy`, `file.move`, `file.export` | Private workspace, Storage Access Framework or MediaStore grants; no unrestricted filesystem |
| Communications | `android.sms.compose`, `android.email.compose`, `android.dial`, `android.share` | Approved compose intents; **owner taps Send/Call**. Direct SMS, call or reading restricted user data are separate higher-risk capabilities needing appropriate Android authority |
| Contacts, calendar | `contacts.lookup`, `calendar.events.read`, `calendar.event.compose` | User-granted data scopes, app/provider support, protected read/write permissions or intents; fail when denied |
| Camera/media | `camera.capture`, `media.pick`, `media.record`, `media.play` | Use user-mediated pickers/intents first; direct recording needs matching grant and active user indication |
| Location/maps | `location.current`, `maps.open`, `maps.route` | Runtime fine/coarse scope as needed; location never silently collected and exact location never sent to LLM without disclosure |
| Radios/nearby | `bluetooth.scan`, `bluetooth.connect`, `wifi.settings.open` | API-specific Nearby Devices/Location grants; Android protects changes to many radio settings |
| Clock/tasks | `alarm.create`, `timer.create`, `reminder.schedule` | Android intents and OS scheduling limits/notification permission, user-visible app handling |
| Accessibility/other apps | `assist.ui.inspect`, `assist.ui.action` (optional, disabled by default) | Only if a legitimately scoped accessibility/assistive feature is approved, installed and explicitly enabled by owner in Android Settings; never unrestricted hidden UI automation |
| Device owner / privileged | `device.admin.*`, shell/root/private data, other apps' secure controls | NOT available to ordinary app. Separate managed-device/ADB/root integrations only if expressly provisioned; default UNSUPPORTED |

**Manifest/access policy:** compile in only permissions needed for *implemented and deliberately enabled* modules. Normal permissions are not user prompts; dangerous permissions requested just-in-time; special app access requires Android Settings UI; role/default-app privileges cannot be self-assigned; OEM policy restrictions may remain. Never ask every possible permission at initial install or conflate user consent with OS capability.

For API specifics, consult the current official Android documentation rather than fixing a universal permission table in code:
- Permissions: https://developer.android.com/training/permissions/requesting
- Intent surfaces: https://developer.android.com/guide/components/intents-common
- SpeechRecognizers: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Accessibility scope: https://developer.android.com/guide/topics/ui/accessibility/service

### 2.4 Runtime configuration and protected variables

```text
AppBuild: app_version, build_sha, application_id, target_sdk, min_sdk
DeviceProfile: sdk_int, features, os_build, default_handlers, install_source
APIProfile: provider_id, protocol=OPENAI_COMPATIBLE_V1, https_base_url,
            model_id, credential_ref, request_timeout_ms,
            max_request_bytes, max_response_bytes, max_output_tokens,
            max_context_tokens, cost_budget, retries=bounded
SpeechProfile: stt_provider, locale, session_timeout_ms,
               request_id, finality, user_consent, capture_state
Turn: conversation_id, turn_id, user_message_id, input_origin,
      submitted_at, transcript_final, model_profile_snapshot
Plan: plan_id, turn_id, max_steps, max_tool_calls, max_elapsed_ms,
      max_tokens, max_cost, execution_lease, cancelled
Action: action_id, capability_id, catalog_version, args_validated,
        policy_decision, consent_grant_ref, permission_snapshot,
        attempt_count, irreversible, verification_state
State: durable_event_seq, outcome_refs, secret_redaction_policy,
       retention_policy, recovery_checkpoint, audit_id
```

- Values are typed, bounded, schema-versioned and serialized atomically where durable. Never treat model-provided IDs, risk labels, permissions, targets or assertions as trusted.
- API keys never embedded in APK, repo, Python durable state or model prompt. Native Android Keystore stores encryption keys for encrypted credential material; exposed transport takes only opaque `credential_ref`.
- HTTPS endpoint allowlist/validation, TLS, bounded networking; no ambient cleartext or generic authenticated HTTP action capability. Server-side proxy only when necessary to protect centrally owned credentials; user-owned BYO key stays under user control.
- Explicitly disclose cloud inference and data sent, manage token/cost limits, and allow credential delete. API failure states (401/403, 404/model missing, 429, timeout, TLS, schema error, quota, canceled) are surfaced with recovery guidance.
- Checkpoint before and after irreversible effects; uncertain effects require manual reconciliation. No automatic replay after app/process restart.

### 2.5 Phase 1 acceptance gate

1. Cold factory-equivalent install builds and opens with a **single visible entry point** (final consolidated UI arrives Phase 3), no Termux/root/Shizuku/external Python or GGUF requirement.
2. Inert registry/permission CLI or debug inspector lists complete descriptors for **implemented** tools plus honest unsupported/planned entries; permission denial blocks only affected tools.
3. Model API Test Connection works without executing any device tool; credential save/revoke survives restart and never appears in logs/IPC/audit.
4. Execute one tightly bounded native tool through controller with policy, result, audit and stop; unsupported tool reports a clear error.
5. Deterministic tests cover denied/revoked grants, unsupported API, no external handler, invalid schema/unknown keys, cancellation, process death and non-duplication. Android CI on API24/API35 plus physical handset smoke.
6. STT adapter exposes real final transcript or a specific error and text fallback; simulated transcripts are never represented as real microphone success.

## 3. Phase 2: model-driven planner

### 3.1 Authority and protocol

**Model-mediated routing is mandatory.** Each accepted final typed/transcribed turn first goes to the hosted model with bounded recent context, sanitized available capability schemas and an explicit request to classify intent. Allowed typed decisions:

```json
{
  "kind": "chat | execute | clarify | unsupported",
  "user_facing_hint": "optional non-authoritative text",
  "plan": [
    {"capability_id": "android.toast", "arguments": {"content": "Hello from LAIN_OS."}}
  ]
}
```

The model **proposes**, never executes. Backend resolves capability IDs against the current registry, constrains args, inserts trusted plan/action/consent IDs, checks permission/role/policy, obtains exact owner approval for consequential effects, and executes one bounded step at a time. Invalid or unknown output fails closed and requests clarification, never tries arbitrary shell or tool names. Schema-constrained model output is desirable but trusted local validation is mandatory. Never accept model-generated status, permission, audit, verification or "already sent" claims as proof.

**Goal-oriented tasks:** a plan may contain dependent steps only with a maximum step/tool/time/token/cost budget and explicit dependencies. Before each step, refresh preconditions and authorization. Store a durable checkpoint after each effect. On error, stop/degrade/ask owner; no silent retries of irreversible operations. Avoid loops and unbounded autonomous execution.

**Every turn's closing answer:** after CHAT decision or terminal tool result/denial/unsupported state, issue a model response synthesis call with the **trusted redacted receipt**; persist the final assistant reply or an explicit model-failure event. If a confirmation is pending, display the trusted approval control inline and permit model to explain the request; after approval/denial, produce outcome response without rerunning. A failed synthesis is *not* a failed action. Retry reply **must never** retry executor effects.

### 3.2 Example acceptance traces

**"Show Demo Toast" (voice or text):**
```text
final transcript -> model: execute android.toast(content=...)
-> schema/policy -> Android toast request -> trusted LIMITED receipt
-> model: "I requested the toast; Android accepted it, but visibility is unverified."
```

**"How are you?" (voice or text):**
```text
final transcript -> model: chat -> NO device tool -> model conversational reply
```

**"Text +15551234567 saying hello":**
```text
model: execute android.sms.compose -> exact approval -> ACTION_SENDTO composer
-> trusted LIMITED receipt -> model: "The message is composed. You still need to tap Send."
```

**"Turn on a restricted system option":**
```text
model: request unsupported privileged setting -> registry: UNSUPPORTED
-> model explains Android boundary and an available Settings handoff, if any
```

### 3.3 Phase 2 acceptance gate

- On real phone with working API key, STT or typed "Show Demo Toast" invokes **model intent route**, not deterministic hidden bypass; safe typed native action executes; receipt and spoken/text reply are real.
- Multiple representative natural-language phrasings resolve to the same validated capability; ambiguous target requires clarification, no guessed contacts/apps/paths.
- Multi-step task performs strictly ordered validated actions with exact approval before irreversible actions, no duplicate effects under Stop/restart/retry.
- Unknown tool, malformed output, permission denied, provider 429/network loss, missing app, and failed API reply each surface correct classified result; none fabricated as a completed task.
- Test with user-approved hosted API endpoint, known model, recorded source SHA and privacy-redacted observed traces. CI fixtures complement, never replace, actual API/device acceptance.

## 4. Phase 3: unified chat/context

### 4.1 Single interface

Only one owner-facing Activity with one scrollable thread and one multiline composer with **Send** and optional **Mic**. No separate LocalChatActivity window, Workbench command box, Run Task, "chat versus execute" mode, multiple conversation memories, independent local-model selector, or competing reply destinations. Settings/onboarding are separate utility screens, **not** separate chat surfaces.

One user entry method for typed and final speech. Speech partials are visible as drafts only. No voice utterance, prior model reply, web page, tool output or quoted transcript may grant authority; explicit user approval is separate.

Timeline events: user utterance; optional recognition diagnostics; model thinking/route; durable plan/status; trusted tool receipt (execution vs verification clearly distinguished); confirmation card; assistant message; terminal error/retry. Status cards never masquerade as model answers.

Provider label displays **API: Connected / Rate limited / Unavailable**. Model response after each accepted user turn, even commands, if provider can answer. Never display a fake assistant answer for API failures.

### 4.2 Shared context contract

`ConversationTurnManager` plus existing trusted `AppController` state become the one durable event authority. Preserve `conversation_id`, `turn_id`, ordered message IDs, links to plan/action IDs and trusted receipt provenance. Build **one bounded context window** from user turns, actual assistant replies, and sanitized **read-only** tool results; no independent `LocalChatActivity` SharedPreferences history or direct GGUF chat path. Avoid duplicated turns on rotation, retries, STT callbacks or Activity recreation. Always keep the trusted full audit distinct from compressed conversation prompt.

Migrate old Local chat and Workbench message history once, by idempotent durable strategy, or show a safe export/recovery route before discarding; never silently erase users' threads or model files. Existing imported GGUF model files remain user-owned until explicitly removed.

### 4.3 Phase 3 acceptance gate

- **Five turn physical acceptance, one screen:** "Show Demo Toast", "Show battery", "Vibrate briefly", "How are you?", "Show Demo Toast". Each final input produces one user bubble, the correct supported action/no-action, and one real API model reply attempt in chronological order.
- Same tests with real STT final transcripts, typed fallback, stopped mic, rotation, process death, denied permission and revoked API credential. Confirm one context carries facts across chat and tool results.
- Permission approvals are bound to exact trusted action; no model text authorizes an action. Tool result remains visible independently from model narrative.
- Slow/rate-limited/offline API shows honest error; Retry reply never replays last tool or duplicate SMS compose. App stays responsive and preserves drafts.
- Physical Moto G Play test log records exact APK SHA, Git commit, phone/API, chosen hosted model, STT provider, transcript vs action/answer results and explicit observed success/failure. Do not declare complete from emulator or Linux-only tests.

## 5. Delivery sequencing and deferred work

```text
P1-01  Consolidate existing trusted components into explicit interfaces and SDK inventory
P1-02  Capability registry + per-capability policy/permission/availability contracts
P1-03  Secure hosted API profile + inert Test Connection + cancellation + error taxonomy
P1-04  Real STT final-turn adapter + typed parity + durable turn/event schema
P1-05  Native adapters for core current demos + real-device Phase 1 gate
     ↓
P2-01  Model-driven intent/plan JSON protocol + strict validation
P2-02  Trustworthy tool-call dispatcher and bounded sequential planner
P2-03  Permission/approval loops, result checkpoints, idempotency, verification
P2-04  Outcome-to-model response synthesis, retries, failure matrix
P2-05  Physical typed+voice API planning acceptance
     ↓
P3-01  Replace split UI with single conversation Activity and composer
P3-02  Canonical context/history migration + receipt-linked timeline
P3-03  Streaming assistant replies, STT/TTS controls, lifecycle + accessible UI
P3-04  Full cross-turn Moto physical acceptance / release-candidate evidence
```

**Ship evidence per phase:** exact PR/source SHA, Android CI, red/green unit/instrumentation tests, reproducible APK hash and **real phone receipts**. Pass phase gates before expanding the next phase. P0 bugs in working demo/voice path outrank optional new capability families.

The existing implementation includes local-model JNI, Qwen preload, LocalChat and Workbench; this document changes the **target**, not the already-installed APK. Update/close stale GGUF and TASK-081 two-view tasks as part of subsequent authorized implementation planning, preserving history/ownership.

## 6. Risks and explicit trade-offs

- **Cloud costs/privacy/network:** spoken transcripts and bounded context may leave the device for inference; obtain informed configuration and handle 429/quota/offline gracefully. Core actions still execute locally, but full offline assistant functionality is intentionally deferred.
- **Speech service availability:** Android's recognizer may be absent, disabled, language-incompatible or dependent on network/provider; report which layer failed and preserve typed fallback. Cloud LLM does not fix failed Android STT by itself.
- **Android platform constraints:** packages, roles, special access and OEM behavior determine reachable functions; no ordinary app can compel inaccessible operations. A capability framework is not a privilege bypass.
- **Overpromising:** model fluency does not prove execution, Android acceptance does not always prove user-visible outcome, and green CI does not prove a physical-phone experience.
- **Phases are product gates, not three simultaneously running isolated brains:** Phase 1 must establish only the minimal UI/debug surface to test backend; the **one unified user-facing UI** is Phase 3.
- **Future portability:** retain provider-independent tool and planner contracts even while v2 offers just hosted API inference. No local-model roadmap/UX/packaging now.

## 7. Decision log

- 2026-10-16: User prioritizes real speech-to-action, API inference instead of local GGUF, complete backend tool/permission scaffolding, model-driven planner and finally single shared-context chat.
- Reuse existing permissioned runtime, audit, cloud transport and canonical turn management; remove duplicated LocalChat and local inference from the release critical path.
- **Design only.** Owner reviews the spec before coding. No claim of successful Android functionality or APK acceptance.
