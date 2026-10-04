# Speech Provider Protocol

TASK-009 defines the provider-neutral speech boundary used by later LAIN_OS voice work.

## Authority boundary

Speech providers are untrusted data adapters. They may convert bounded audio to text or bounded text to audio. They do not authorize capabilities, approve consequential actions, mutate task state, change policy, or grant execution authority.

Raw provider credentials are deliberately absent from every request, result, provenance, and failure type in this protocol. Provider-specific credential resolution belongs outside the contract.

## Contracts

`TranscriptionRequest` carries:
- non-empty audio bytes;
- strict `AudioMetadata`;
- a finite positive timeout;
- a bounded maximum transcript size.

`TranscriptionResult` is exactly one of:
- final transcript text plus optional language and provider provenance; or
- an explicit `SpeechFailure`.

Streaming and partial transcripts are intentionally deferred to a compatible later extension. TASK-011 owns turn finality and conversational routing.

`SynthesisRequest` carries:
- bounded UTF-8 text;
- an opaque stock/provider voice identifier;
- requested output MIME type;
- a finite positive timeout;
- maximum audio bytes and duration.

`SynthesisResult` is exactly one of:
- non-empty audio bytes whose length matches strict `AudioMetadata`, plus provider provenance; or
- an explicit `SpeechFailure`.

## Bounds

The initial portable contract uses conservative global ceilings:
- timeout: 120 seconds;
- audio payload: 16 MiB;
- audio duration: 120 seconds;
- sample rate: 192 kHz;
- channels: 2;
- transcript: 32 KiB UTF-8;
- synthesis text: 32 KiB UTF-8.

A request may choose lower output limits but cannot raise these ceilings. Later platform/provider adapters may impose stricter limits.

## Cancellation and failure semantics

`CancellationSignal.is_cancelled()` is the cooperative cancellation seam. Adapters receive the signal explicitly and must surface cancellation as a non-success result. They must not treat cancellation as task success.

The portable failure vocabulary is:
- `provider_unavailable`;
- `timeout`;
- `cancelled`;
- `malformed_response`;
- `unsupported_media`;
- `resource_limit`.

Provider-specific HTTP/status/error details remain adapter concerns. They must be mapped to this bounded vocabulary before crossing the speech boundary.

## Provenance

Every success and failure identifies the selected provider configuration through `SpeechProvenance`:
- opaque `provider_id`;
- implementation/adapter identity;
- optional model/engine identity.

Provenance is evidence about where speech data came from. It is never permission or execution authority.

## Data handling

The protocol permits audio bytes in memory so Android capture/playback adapters can use one provider-neutral seam. Nothing in this contract persists raw audio. TASK-010 must keep capture user-started and non-durable by default, and later provider adapters must keep secrets outside these values.

Typed interaction remains independent because this package does not alter the existing text workbench, planner, controller, policy, executor, or audit paths.
