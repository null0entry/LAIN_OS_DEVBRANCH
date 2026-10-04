from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import math
from typing import Protocol


MAX_SPEECH_TIMEOUT_SECONDS = 120.0
MAX_AUDIO_BYTES = 16 * 1024 * 1024
MAX_AUDIO_DURATION_MS = 120_000
MAX_SAMPLE_RATE_HZ = 192_000
MAX_AUDIO_CHANNELS = 2
MAX_TRANSCRIPT_BYTES = 32_768
MAX_SYNTHESIS_TEXT_BYTES = 32_768

_MAX_IDENTIFIER_BYTES = 256
_MAX_FAILURE_MESSAGE_BYTES = 1024


def _utf8_size(value: str) -> int:
    try:
        return len(value.encode("utf-8"))
    except UnicodeEncodeError as exc:
        raise ValueError("speech text must be valid UTF-8") from exc


def _require_text(name: str, value: str, *, max_bytes: int) -> None:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{name} must be non-empty")
    if "\x00" in value or _utf8_size(value) > max_bytes:
        raise ValueError(f"{name} is invalid or too large")


def _require_positive_int(name: str, value: int, *, maximum: int) -> None:
    if (
        not isinstance(value, int)
        or isinstance(value, bool)
        or value < 1
        or value > maximum
    ):
        raise ValueError(f"{name} must be between 1 and {maximum}")


def _require_timeout(value: float) -> None:
    if (
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not math.isfinite(value)
        or value <= 0
        or value > MAX_SPEECH_TIMEOUT_SECONDS
    ):
        raise ValueError(
            f"timeout_seconds must be finite and between 0 and {MAX_SPEECH_TIMEOUT_SECONDS}"
        )


def _require_mime_type(value: str) -> None:
    _require_text("mime_type", value, max_bytes=127)
    if value.count("/") != 1 or any(character.isspace() for character in value):
        raise ValueError("mime_type must be a simple type/subtype value")
    major, minor = value.split("/", 1)
    if not major or not minor:
        raise ValueError("mime_type must be a simple type/subtype value")


@dataclass(frozen=True, slots=True)
class AudioMetadata:
    mime_type: str
    sample_rate_hz: int
    channels: int
    duration_ms: int
    byte_count: int

    def __post_init__(self) -> None:
        _require_mime_type(self.mime_type)
        _require_positive_int("sample_rate_hz", self.sample_rate_hz, maximum=MAX_SAMPLE_RATE_HZ)
        _require_positive_int("channels", self.channels, maximum=MAX_AUDIO_CHANNELS)
        _require_positive_int("duration_ms", self.duration_ms, maximum=MAX_AUDIO_DURATION_MS)
        _require_positive_int("byte_count", self.byte_count, maximum=MAX_AUDIO_BYTES)


@dataclass(frozen=True, slots=True)
class SpeechProvenance:
    provider_id: str
    implementation: str
    model: str | None = None

    def __post_init__(self) -> None:
        _require_text("provider_id", self.provider_id, max_bytes=_MAX_IDENTIFIER_BYTES)
        _require_text("implementation", self.implementation, max_bytes=_MAX_IDENTIFIER_BYTES)
        if self.model is not None:
            _require_text("model", self.model, max_bytes=_MAX_IDENTIFIER_BYTES)


class SpeechFailureCode(str, Enum):
    PROVIDER_UNAVAILABLE = "provider_unavailable"
    TIMEOUT = "timeout"
    CANCELLED = "cancelled"
    MALFORMED_RESPONSE = "malformed_response"
    UNSUPPORTED_MEDIA = "unsupported_media"
    RESOURCE_LIMIT = "resource_limit"


@dataclass(frozen=True, slots=True)
class SpeechFailure:
    code: SpeechFailureCode
    message: str
    provenance: SpeechProvenance

    def __post_init__(self) -> None:
        if not isinstance(self.code, SpeechFailureCode):
            raise ValueError("speech failure code is invalid")
        _require_text("message", self.message, max_bytes=_MAX_FAILURE_MESSAGE_BYTES)
        if not isinstance(self.provenance, SpeechProvenance):
            raise ValueError("speech failure provenance is invalid")


@dataclass(frozen=True, slots=True)
class TranscriptionRequest:
    audio: bytes
    metadata: AudioMetadata
    timeout_seconds: float
    max_transcript_bytes: int = MAX_TRANSCRIPT_BYTES

    def __post_init__(self) -> None:
        if not isinstance(self.audio, bytes) or not self.audio:
            raise ValueError("transcription audio must be non-empty bytes")
        if not isinstance(self.metadata, AudioMetadata):
            raise ValueError("transcription metadata is invalid")
        if len(self.audio) != self.metadata.byte_count:
            raise ValueError("transcription audio length does not match metadata")
        _require_timeout(self.timeout_seconds)
        _require_positive_int(
            "max_transcript_bytes",
            self.max_transcript_bytes,
            maximum=MAX_TRANSCRIPT_BYTES,
        )


@dataclass(frozen=True, slots=True)
class TranscriptionResult:
    text: str | None = None
    language: str | None = None
    provenance: SpeechProvenance | None = None
    failure: SpeechFailure | None = None

    def __post_init__(self) -> None:
        if self.failure is not None:
            if not isinstance(self.failure, SpeechFailure):
                raise ValueError("transcription failure is invalid")
            if self.text is not None or self.language is not None or self.provenance is not None:
                raise ValueError("failed transcription cannot include success data")
            return

        if self.provenance is None or not isinstance(self.provenance, SpeechProvenance):
            raise ValueError("successful transcription requires provenance")
        _require_text("transcript", self.text, max_bytes=MAX_TRANSCRIPT_BYTES)
        if self.language is not None:
            _require_text("language", self.language, max_bytes=64)

    @property
    def ok(self) -> bool:
        return self.failure is None


@dataclass(frozen=True, slots=True)
class SynthesisRequest:
    text: str
    voice_id: str
    output_mime_type: str
    timeout_seconds: float
    max_audio_bytes: int = MAX_AUDIO_BYTES
    max_duration_ms: int = MAX_AUDIO_DURATION_MS

    def __post_init__(self) -> None:
        _require_text("synthesis text", self.text, max_bytes=MAX_SYNTHESIS_TEXT_BYTES)
        _require_text("voice_id", self.voice_id, max_bytes=_MAX_IDENTIFIER_BYTES)
        _require_mime_type(self.output_mime_type)
        _require_timeout(self.timeout_seconds)
        _require_positive_int("max_audio_bytes", self.max_audio_bytes, maximum=MAX_AUDIO_BYTES)
        _require_positive_int(
            "max_duration_ms",
            self.max_duration_ms,
            maximum=MAX_AUDIO_DURATION_MS,
        )


@dataclass(frozen=True, slots=True)
class SynthesisResult:
    audio: bytes | None = None
    metadata: AudioMetadata | None = None
    provenance: SpeechProvenance | None = None
    failure: SpeechFailure | None = None

    def __post_init__(self) -> None:
        if self.failure is not None:
            if not isinstance(self.failure, SpeechFailure):
                raise ValueError("synthesis failure is invalid")
            if self.audio is not None or self.metadata is not None or self.provenance is not None:
                raise ValueError("failed synthesis cannot include success data")
            return

        if not isinstance(self.audio, bytes) or not self.audio:
            raise ValueError("successful synthesis requires non-empty audio bytes")
        if self.metadata is None or not isinstance(self.metadata, AudioMetadata):
            raise ValueError("successful synthesis requires audio metadata")
        if self.provenance is None or not isinstance(self.provenance, SpeechProvenance):
            raise ValueError("successful synthesis requires provenance")
        if len(self.audio) != self.metadata.byte_count:
            raise ValueError("synthesis audio length does not match metadata")

    @property
    def ok(self) -> bool:
        return self.failure is None


class CancellationSignal(Protocol):
    def is_cancelled(self) -> bool: ...


class TranscriptionProvider(Protocol):
    def transcribe(
        self,
        request: TranscriptionRequest,
        *,
        cancellation: CancellationSignal | None = None,
    ) -> TranscriptionResult: ...


class SynthesisProvider(Protocol):
    def synthesize(
        self,
        request: SynthesisRequest,
        *,
        cancellation: CancellationSignal | None = None,
    ) -> SynthesisResult: ...
