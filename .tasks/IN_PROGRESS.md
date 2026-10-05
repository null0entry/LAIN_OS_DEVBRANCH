# In Progress

## TASK-050: Lock local-only Phase-2 voice loop
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

---
