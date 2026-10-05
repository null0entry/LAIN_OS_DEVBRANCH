# In Progress

## TASK-050: Lock local-only Phase-2 speech seam
**Priority:** P1 | **Tags:** overseer-assigned, developer, phase-2, voice, speech, privacy
**Updated:** 2026-10-04

**Correction:** PR #29 merged broader Android/UI/runtime plumbing than the approved TASK-050 boundary. TASK-050 is reopened to trim back to the already-proven local speech seam and lock its missing captured-PCM finality contract.

### Goal

Define the smallest provider-neutral local speech seam needed by Phase 2, with no silent network fallback and no UI/runtime/provider integration.

### Contract

- Captured PCM STT is unavailable below API 33 and returns `PROVIDER_UNAVAILABLE` before touching recognizer availability or transcription.
- API 33+ requires explicit on-device availability before captured PCM may be submitted.
- No Local→Cloud or network recognizer fallback exists in this seam.
- Partial recognition is non-authoritative and never crosses the seam; only a final result may surface as transcript output.
- Typed input remains independent and always available.
- TTS may proceed only through the existing local synthesis seam when an installed voice does not require network access; otherwise it returns unavailable.

### Scope

- Keep the existing local-TTS controller and its red/green receipt.
- Keep one JVM adapter test class for the STT seam.
- Keep one provider-neutral production adapter file for captured-PCM STT policy/finality.
- Do not add Activity/ViewModel wiring, manifest queries, Android recognizer/TTS service implementations, instrumentation plumbing, provider SDKs, generic network authority, or cloud fallback.

### Plan

- Restore PR #29's product delta to the pre-integration baseline plus the proven local-TTS controller and `OnDeviceSpeechAdapter` seam.
- Strengthen the single JVM adapter test class for API-32 no-touch, API-33 explicit availability, and partial-vs-final behavior.
- Run the focused test as RED before changing production.
- Add only the minimum adapter change required to make finality explicit and GREEN.
- Run exact-head canonical Android/Verify CI.

### Acceptance

- API 32 returns `PROVIDER_UNAVAILABLE` with zero recognizer-availability reads and zero transcription calls.
- API 33+ with unavailable on-device recognition returns `PROVIDER_UNAVAILABLE` and never transcribes.
- Partial transcript events produce no result; a later final event produces exactly one result.
- Existing local TTS selection rejects network-required or uninstalled voices.
- Typed input code is untouched.
- The correction diff contains no UI/runtime/platform-provider plumbing.

### Verification

- `OnDeviceSpeechAdapterTest` is the STT seam witness.
- Existing `OnDeviceSpeechSynthesisControllerTest` preserves the local-only TTS receipt.
- RED must be observed before production finality support; GREEN must pass the focused JVM suite.
- Canonical Verify + Android CI gate the correction PR.

### Expected result

TASK-050 ends as a narrow fail-closed local speech contract: API 33+ captured PCM only when explicitly on-device, final-only transcript output, local-installed-only TTS, typed fallback untouched, zero hidden cloud path.

---

