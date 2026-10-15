package dev.lain.os.voice

import org.junit.Assert.*
import org.junit.Test

class ManualLiveSpeechResultTest {
    @Test fun acceptedFinalCarriesOfflineProvenance() {
        val result = validateLiveSpeechFinal(
            BackendTranscriptionResult(text = "Show battery", confidence = null),
        )
        assertTrue(result.ok)
        assertEquals("Show battery", result.text)
        assertEquals(ON_DEVICE_PROVIDER_ID, result.providerId)
        assertEquals("android-live-on-device-stt", result.implementation)
    }

    @Test fun partialsNeverBecomeCommands() {
        val result = validateLiveSpeechFinal(
            BackendTranscriptionResult(text = "delete", isFinal = false),
        )
        assertFalse(result.ok)
        assertNull(result.text)
    }

    @Test fun rejectsUncertainOrMalformedRecognition() {
        assertEquals(SpeechAdapterFailure.LOW_CONFIDENCE,
            validateLiveSpeechFinal(BackendTranscriptionResult(
                text = "Delete files", confidence = 0.3f)).failure)
        assertEquals(SpeechAdapterFailure.PROVIDER_FAILED,
            validateLiveSpeechFinal(BackendTranscriptionResult(
                text = "Delete files", confidence = Float.NaN)).failure)
        assertEquals(SpeechAdapterFailure.RESOURCE_LIMIT,
            validateLiveSpeechFinal(BackendTranscriptionResult(
                text = "x".repeat(MAX_ON_DEVICE_TRANSCRIPT_BYTES + 1))).failure)
    }

    @Test fun errorDiagnosticIsNotLostOnFailure() {
        val result = validateLiveSpeechFinal(BackendTranscriptionResult(
            failure = SpeechAdapterFailure.PROVIDER_FAILED,
            diagnostic = recognizerErrorDiagnostic(13, false),
        ))
        assertFalse(result.ok)
        assertTrue(result.diagnostic.orEmpty().contains("Android error 13"))
    }
}
