# In Progress

## TASK-050: Implement first on-device Phase-2 speech adapter
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, speech, android, privacy
**Updated:** 2026-10-04

**Integration:** Draft PR #29 exact head `c79b032`; async TTS readiness RED was proven on `ce1cc72`, bounded GREEN implementation is present, Verify #487 passed, and Android #476 remains in progress on API 24/API 35. Merge is gated on exact-head Android completion.

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

### Plan
- Inventory reusable Android speech/cancellation/lifecycle seams and add injectable platform facades only where required for deterministic tests.
- Map bounded microphone audio/metadata into the transcription contract without durable raw-audio retention.
- Map synthesis requests/results into the bounded playback format expected by TASK-012.
- Require explicit on-device capability/locality evidence before using the baseline adapter; surface unavailable/unsupported states otherwise.
- Wire final transcript delivery into TASK-011 and synthesis playback into TASK-012 without granting either provider execution authority.
- Add deterministic JVM/instrumentation coverage for success, unavailable engine, cancellation, lifecycle teardown, partial/final separation, and no-cloud-fallback behavior.

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

