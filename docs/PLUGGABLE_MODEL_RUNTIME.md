> **Historical planning document (2026-10-01), superseded for the active release by [API-first three-phase LAIN_OS v2](superpowers/specs/2026-10-16-api-first-three-phase-design.md).** Preserve existing tested cloud transport/Keystore and trusted executor seams, but do not follow this document's Demo/Local/on-device provider expansion or earlier phase numbers as current priorities.

# LAIN_OS Phase 2 — Pluggable Model Runtime

**Status:** Planned  
**Role:** Planning artifact  
**Baseline:** `main@f3d7aa1d732430ad8a31a25f189fbf2d252f3048`  
**Date:** 2026-10-01

## Goal

Turn the existing Android GUI + embedded runtime from a deterministic demo into a real natural-language LAIN_OS client where the user can choose the source of model intelligence while keeping authorization, execution, verification, budgets, and audit local and authoritative.

Initial provider modes:

1. **Offline Demo** — existing deterministic planner.
2. **Cloud API** — user-supplied hosted model endpoint/credential.
3. **Local Endpoint** — user-supplied OpenAI-compatible model server reachable from the device.

True on-device inference is intentionally deferred to the following phase.

## Architecture

```text
                    USER SETTINGS
                         │
              ┌──────────┴──────────┐
              │                     │
         Cloud API             Local Endpoint
              │                     │
              └──────────┬──────────┘
                         │
                 Planner Transport
                         │
                 untrusted model
                         │
                 Planner Decision
                         │
                         ▼
             existing LAIN trusted core
       validation → policy → approval → execution
                  → verification → audit
```

### Core invariant

**The model provider is replaceable; the authority layer is not.**

The current hardcoded demo planner seam should be replaced, not bypassed. Provider-specific behavior must not leak into `RuntimeEngine`, capability policy, verification, Android action adapters, or audit authority.

## Existing integration seam

The current Android/Python path ultimately constructs:

```text
AgentPlanningService(DemoPlanner(...))
```

The portable core already accepts an `AgentPlanner`, and the repository already contains a real Groq adapter. Phase 2 should generalize that seam rather than redesign the trusted execution pipeline.

## Planner profiles

Introduce a provider-neutral configuration object:

```text
PlannerProfile
  id
  name
  mode                  DEMO | CLOUD | LOCAL
  protocol              OPENAI_COMPATIBLE_V1
  base_url
  model
  credential_ref        nullable
  timeout_seconds
  max_response_bytes
  response_mode
```

Raw credentials are never part of `PlannerProfile`.

### Session pinning

The active planner profile is captured when a session starts.

Changing settings during an active session applies to the next session only. This prevents provider/model changes halfway through an agent loop and makes audit state reproducible.

## Provider strategy

Use one **OpenAI-compatible transport contract** for the first release instead of adding provider SDKs one by one.

Target structure:

```text
Planner
 ├─ DemoPlanner
 └─ OpenAICompatiblePlanner
       └─ PlannerTransport
            ├─ Cloud profile
            └─ Local profile
```

This keeps provider differences in endpoint/model/credential configuration rather than duplicating planner logic.

The first implementation should support services exposing an OpenAI-compatible chat-completions style API. This covers hosted services and many self-hosted runtimes without creating provider-specific planner classes for each one.

## Android settings UX

Add a **Planner** section to Settings.

### Mode selector

- Offline Demo
- Cloud API
- Local Endpoint

### Cloud API fields

- Profile name
- Base URL / provider endpoint
- Model
- API key
- Timeout
- Test Connection
- Save

### Local Endpoint fields

- Profile name
- Base URL
- Model
- Optional credential
- Timeout
- Test Connection
- Save

### Workbench visibility

Show the selected source directly in the main workbench, e.g.:

```text
Planner: Local · qwen3.5-9b
```

The source of intelligence should never be invisible to the user.

## Secret handling

Secrets stay on the native Android side.

Use an Android Keystore-backed encryption key and persist only encrypted credential material. Planner configuration stores only an opaque `credential_ref`.

Raw API keys must never appear in:

- session JSON
- Python checkpoints
- Binder/IPC responses
- audit JSONL
- logs
- crash reports
- screenshots
- exported settings
- planner context

The UI should display only credential state, such as:

```text
API key saved
Replace
Remove
```

Do not reveal the stored key after saving.

### No automatic cloud fallback

If the user selected Local and the local endpoint is unavailable, report the planner as unavailable.

Do **not** silently send the request to a cloud provider. That would change privacy, cost, and trust semantics.

## Runtime bridge

Keep network/secrets ownership native and make the Python-facing bridge deliberately small.

```text
RuntimeService.kt
    │
    ├── NativeCapabilities
    │
    └── NativePlannerTransport
             │
             ├── reads active PlannerProfile
             ├── obtains credential from SecretStore
             ├── performs bounded HTTP request
             └── returns model response

                     ↓ Chaquopy

BridgePlanner.py
    │
    ├── constructs existing planner request
    ├── calls NativePlannerTransport
    ├── parses / normalizes response
    └── returns AgentPlannerDecision

                     ↓

AgentPlanningService
```

The trusted flow remains unchanged:

```text
model response
→ planner decision validation
→ capability lookup
→ argument validation
→ policy
→ confirmation
→ execution
→ verification
→ audit
```

A model never receives authority merely because it produced valid-looking JSON.

## Planner response contract

Preserve the strict planner decision contract already represented by the existing planner adapter:

```json
{
  "status": "continue | complete | blocked",
  "reason": "...",
  "actions": [
    {
      "type": "capability.name",
      "arguments": {}
    }
  ]
}
```

Providers supporting constrained JSON/schema generation may use it.

Providers that cannot support schema constraints may return ordinary JSON, but LAIN must still validate the result strictly and fail closed on:

- invalid JSON
- unexpected keys
- invalid lifecycle status
- unknown capabilities
- malformed capability arguments
- malformed action structure
- oversized responses
- protocol violations

Structured generation improves reliability. Validation remains the security boundary.

## Test Connection behavior

`Test Connection` must be inert with respect to agent capabilities.

It must not:

- create an agent session
- execute a capability
- modify the filesystem
- invoke Android actions

It performs only a bounded planner-endpoint diagnostic.

Expected user-facing states:

```text
Connected
Authentication rejected
Model unavailable
Endpoint unreachable
TLS failure
Response unsupported
Timed out
```

## Failure model

Provider errors should map to explicit runtime states rather than generic exceptions.

Minimum coverage:

- DNS/unreachable
- connection refused
- TLS failure
- HTTP 401/403
- HTTP 404/model missing
- HTTP 408/timeout
- HTTP 429/rate limit
- HTTP 5xx
- cancellation
- response exceeds configured bound
- malformed JSON
- schema-invalid planner output

A provider failure must never be reported as successful execution.

## Delivery plan

### P2-01 — Provider-neutral planner protocol

Extract request construction, decision schema, parsing, validation, and normalization from provider-specific code.

**Dependencies:** current main

**Acceptance:**
- Existing Groq behavior preserved.
- Demo planner still works.
- Invalid provider output fails closed.
- No policy/execution authority moves into provider code.

### P2-02 — Planner profiles and persistence

Add `PlannerProfile`, active-profile storage, validation, and session pinning.

**Dependencies:** P2-01

**Acceptance:**
- Demo remains the default.
- Profiles persist across app restart.
- Invalid endpoint/model/profile configuration is rejected.
- Active sessions retain their original provider selection.

### P2-03 — Keystore-backed secret store

Implement Android-native secure credential persistence.

**Dependencies:** P2-02

**Acceptance:**
- Credential survives restart.
- Raw secret never appears in Python durable state, IPC responses, audit, logs, or settings export.
- Replace/remove operations work.
- Missing credential produces an explicit configuration failure.

### P2-04 — Native planner transport

Implement cancellable, bounded HTTP transport for cloud/local OpenAI-compatible endpoints.

**Dependencies:** P2-01, P2-03

**Acceptance:**
- Cloud and local profiles use the same protocol abstraction.
- Timeout and response-size bounds are enforced.
- 401/403/404/429/5xx/offline/TLS/malformed-response cases return structured failures.
- Cancellation prevents subsequent action execution.

### P2-05 — Runtime planner factory/bridge

Replace hardcoded `DemoPlanner` construction with a profile-selected planner factory and Android/Python bridge.

**Dependencies:** P2-02, P2-04

**Acceptance:**
- Offline Demo remains functional.
- Cloud profile can produce a valid `AgentPlannerDecision`.
- Local profile can produce a valid `AgentPlannerDecision`.
- Result still traverses the existing policy/execution/verification/audit pipeline.

### P2-06 — Planner Settings UI

Add configuration UI, connection testing, provider/model visibility, and recovery states.

**Dependencies:** P2-05

**Acceptance:**
- User can select Demo/Cloud/Local.
- User can create/edit/select a profile.
- Credential state is visible without exposing credential content.
- Test Connection is non-executing.
- Workbench clearly shows active provider/model.
- Settings survive process/app restart.

### P2-07 — Android acceptance and adversarial testing

Add end-to-end tests around both planner modes and provider failures.

**Dependencies:** P2-06

**Acceptance:**
- Debug APK builds.
- Existing Android UI/runtime tests remain green.
- Cloud and local happy paths pass.
- Invalid model output cannot bypass validation.
- Provider outage cannot produce fabricated completion.
- Local mode never silently falls back to cloud.
- Secret scanning confirms credentials are absent from durable runtime artifacts.

## Golden acceptance flow

```text
Install debug APK
→ Settings
→ choose Cloud or Local
→ configure model
→ Test Connection succeeds
→ return to Workbench
→ type:
   "Create a file named hello.txt containing hello"
→ selected model proposes file.write_text
→ LAIN validates it
→ policy evaluates it
→ action executes
→ verification passes
→ audit records it
→ UI shows the verified result
→ restart app
→ planner setting survives
→ credential survives securely
→ raw secret is absent from durable LAIN state
```

Repeat with a local endpoint, then disconnect the endpoint during a session and verify that LAIN fails safely rather than falling back or fabricating progress.

## Non-goals for this phase

Defer all of the following:

- embedded/on-device GGUF inference
- model download/management
- voice UI
- speech-to-text / text-to-speech
- RAG
- embeddings/vector storage
- arbitrary provider SDK proliferation
- automatic cloud fallback
- provider-managed tool execution
- tool calling outside LAIN ACTION_PROTOCOL
- planner authority over policy or confirmation
- background autonomous cloud loops without explicit existing runtime controls

## Risks

### Credential leakage

Mitigation: native secret store, opaque credential refs, log/audit redaction, adversarial tests.

### Provider incompatibility

Mitigation: support one documented compatibility protocol first; validate responses strictly; expose explicit unsupported-response errors.

### Local HTTP policy

Local inference servers may commonly be exposed over LAN HTTP rather than TLS. If plaintext LAN endpoints are supported, they should require an explicit local-endpoint mode and narrow Android network-security configuration rather than globally permitting cleartext traffic.

### Long-running requests

Mitigation: cancellation, timeout, response-size bounds, lifecycle-aware transport.

### Model behavior drift

Mitigation: planner schema validation, deterministic authority layer, and audit of the selected profile/model identity.

## Deferred next phase — On-device inference

After Planner Profiles v1 stabilizes, add a separate provider implementation for embedded inference.

Likely responsibilities:

```text
ModelManager
  → download/import GGUF
  → storage accounting
  → checksum/integrity
  → compatibility inspection
  → load/unload lifecycle

EmbeddedInferenceProvider
  → JNI/native runtime
  → token generation
  → cancellation
  → context limits
  → thermal/memory handling
```

The same `AgentPlanner` contract should allow this future provider to slot into the architecture without changing trusted policy/execution code.

## Definition of Done

Phase 2 is complete when:

1. A user can configure and persist Demo, Cloud, or Local planner modes.
2. Hosted and local OpenAI-compatible model endpoints can generate LAIN planner decisions.
3. Credentials are protected by Android-native secret storage.
4. Provider selection is pinned per session and visible in the UI.
5. Provider failures are structured and cannot become execution success.
6. No automatic cloud fallback exists.
7. All model output still crosses the existing validation/policy/approval boundary.
8. Existing deterministic demo behavior remains available.
9. Debug APK builds and acceptance tests pass.
10. A natural-language request can travel from the Android GUI through a selected real model and complete a verified, audited LAIN capability action.
