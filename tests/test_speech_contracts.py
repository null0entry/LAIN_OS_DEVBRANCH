import dataclasses
import unittest

from lain.speech import (
    AudioMetadata,
    CancellationSignal,
    MAX_AUDIO_BYTES,
    MAX_AUDIO_CHANNELS,
    MAX_AUDIO_DURATION_MS,
    MAX_SAMPLE_RATE_HZ,
    MAX_SPEECH_TIMEOUT_SECONDS,
    MAX_SYNTHESIS_TEXT_BYTES,
    MAX_TRANSCRIPT_BYTES,
    SpeechFailure,
    SpeechFailureCode,
    SpeechProvenance,
    SynthesisProvider,
    SynthesisRequest,
    SynthesisResult,
    TranscriptionProvider,
    TranscriptionRequest,
    TranscriptionResult,
)


class SpeechContractTests(unittest.TestCase):
    def audio_metadata(self, **overrides):
        values = {
            "mime_type": "audio/wav",
            "sample_rate_hz": 16000,
            "channels": 1,
            "duration_ms": 250,
            "byte_count": 4,
        }
        values.update(overrides)
        return AudioMetadata(**values)

    def provenance(self):
        return SpeechProvenance(
            provider_id="local-stock",
            implementation="android-stock",
            model="en-US-default",
        )

    def test_transcription_request_accepts_bounded_audio(self):
        request = TranscriptionRequest(
            audio=b"RIFF",
            metadata=self.audio_metadata(),
            timeout_seconds=10.0,
            max_transcript_bytes=4096,
        )
        self.assertEqual(request.audio, b"RIFF")
        self.assertEqual(request.max_transcript_bytes, 4096)

    def test_transcription_request_rejects_metadata_length_mismatch(self):
        with self.assertRaises(ValueError):
            TranscriptionRequest(
                audio=b"RIFF",
                metadata=self.audio_metadata(byte_count=3),
                timeout_seconds=10.0,
                max_transcript_bytes=4096,
            )

    def test_audio_metadata_enforces_resource_bounds(self):
        invalid = [
            {"byte_count": MAX_AUDIO_BYTES + 1},
            {"duration_ms": MAX_AUDIO_DURATION_MS + 1},
            {"sample_rate_hz": MAX_SAMPLE_RATE_HZ + 1},
            {"channels": MAX_AUDIO_CHANNELS + 1},
            {"mime_type": "not-a-mime"},
        ]
        for override in invalid:
            with self.subTest(override=override), self.assertRaises(ValueError):
                self.audio_metadata(**override)

    def test_request_timeouts_and_text_limits_are_finite_and_bounded(self):
        for timeout in (0, -1, float("inf"), MAX_SPEECH_TIMEOUT_SECONDS + 0.1):
            with self.subTest(timeout=timeout), self.assertRaises(ValueError):
                TranscriptionRequest(
                    audio=b"RIFF",
                    metadata=self.audio_metadata(),
                    timeout_seconds=timeout,
                    max_transcript_bytes=4096,
                )

        with self.assertRaises(ValueError):
            SynthesisRequest(
                text="x" * (MAX_SYNTHESIS_TEXT_BYTES + 1),
                voice_id="stock-1",
                output_mime_type="audio/wav",
                timeout_seconds=10.0,
                max_audio_bytes=4096,
                max_duration_ms=1000,
            )

    def test_transcription_success_is_final_data_with_provenance(self):
        result = TranscriptionResult(
            text="turn the lights on",
            language="en-US",
            provenance=self.provenance(),
        )
        self.assertTrue(result.ok)
        self.assertIsNone(result.failure)
        self.assertEqual(result.text, "turn the lights on")

    def test_synthesis_success_requires_matching_audio_metadata(self):
        result = SynthesisResult(
            audio=b"WAVE",
            metadata=self.audio_metadata(byte_count=4),
            provenance=self.provenance(),
        )
        self.assertTrue(result.ok)

        with self.assertRaises(ValueError):
            SynthesisResult(
                audio=b"WAVE",
                metadata=self.audio_metadata(byte_count=3),
                provenance=self.provenance(),
            )

    def test_failure_categories_are_explicit_non_success_outcomes(self):
        expected = {
            "provider_unavailable",
            "timeout",
            "cancelled",
            "malformed_response",
            "unsupported_media",
            "resource_limit",
        }
        self.assertEqual({item.value for item in SpeechFailureCode}, expected)

        failure = SpeechFailure(
            code=SpeechFailureCode.TIMEOUT,
            message="speech provider timed out",
            provenance=self.provenance(),
        )
        result = TranscriptionResult(failure=failure)
        self.assertFalse(result.ok)
        self.assertIsNone(result.text)

        with self.assertRaises(ValueError):
            TranscriptionResult(
                text="must not coexist",
                provenance=self.provenance(),
                failure=failure,
            )

    def test_contract_types_contain_no_credentials_or_authority_fields(self):
        forbidden = {"credential", "secret", "token", "capability", "approval", "policy", "action"}
        contract_types = (
            AudioMetadata,
            SpeechProvenance,
            SpeechFailure,
            TranscriptionRequest,
            TranscriptionResult,
            SynthesisRequest,
            SynthesisResult,
        )
        for contract_type in contract_types:
            names = {field.name.lower() for field in dataclasses.fields(contract_type)}
            with self.subTest(contract=contract_type.__name__):
                self.assertFalse(any(any(word in name for word in forbidden) for name in names))

    def test_provider_protocols_keep_cancellation_explicit(self):
        self.assertIn("transcribe", TranscriptionProvider.__dict__)
        self.assertIn("synthesize", SynthesisProvider.__dict__)
        self.assertIn("is_cancelled", CancellationSignal.__dict__)

    def test_transcript_and_synthesis_output_limits_are_bounded(self):
        self.assertGreater(MAX_TRANSCRIPT_BYTES, 0)
        self.assertGreater(MAX_SYNTHESIS_TEXT_BYTES, 0)

        with self.assertRaises(ValueError):
            TranscriptionResult(
                text="x" * (MAX_TRANSCRIPT_BYTES + 1),
                provenance=self.provenance(),
            )

        with self.assertRaises(ValueError):
            SynthesisRequest(
                text="speak",
                voice_id="stock-1",
                output_mime_type="audio/wav",
                timeout_seconds=10.0,
                max_audio_bytes=MAX_AUDIO_BYTES + 1,
                max_duration_ms=1000,
            )


if __name__ == "__main__":
    unittest.main()
