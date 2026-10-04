# In Progress

## TASK-009: Define bounded speech provider interfaces
**Priority:** P2 | **Tags:** overseer-assigned, developer, phase-2, voice, provider-contract
**Updated:** 2026-10-04

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
