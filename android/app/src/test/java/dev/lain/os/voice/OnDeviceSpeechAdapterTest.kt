package dev.lain.os.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceSpeechAdapterTest {
    private class FakeBackend : OnDeviceSpeechBackend {
        var transcriptionAvailabilityReads = 0
        override var transcriptionAvailable = true
            get() {
                transcriptionAvailabilityReads += 1
                return field
            }
        override var synthesisAvailable = true
        override val implementation = "fake-on-device"
        override val model = "fixture"
        var transcribeCalls = 0
        var synthesizeCalls = 0
        var cancelTranscriptionCalls = 0
        var cancelSynthesisCalls = 0
        private var transcriptionCallback: ((BackendTranscriptionResult) -> Unit)? = null
        private var synthesisCallback: ((BackendSynthesisResult) -> Unit)? = null

        override fun transcribe(
            audio: CapturedAudio,
            callback: (BackendTranscriptionResult) -> Unit,
        ) {
            transcribeCalls += 1
            transcriptionCallback = callback
        }

        override fun synthesize(
            text: String,
            voiceId: String?,
            callback: (BackendSynthesisResult) -> Unit,
        ) {
            synthesizeCalls += 1
            synthesisCallback = callback
        }

        override fun cancelTranscription() {
            cancelTranscriptionCalls += 1
        }

        override fun cancelSynthesis() {
            cancelSynthesisCalls += 1
        }

        override fun close() = Unit

        fun emitPartialTranscript(text: String) {
            transcriptionCallback?.invoke(
                BackendTranscriptionResult(
                    text = text,
                    language = "en-US",
                    isFinal = false,
                )
            )
        }

        fun finishTranscript(text: String) {
            transcriptionCallback?.invoke(
                BackendTranscriptionResult(
                    text = text,
                    language = "en-US",
                    isFinal = true,
                )
            )
        }

        fun finishSynthesis(audio: SynthesizedAudio) {
            synthesisCallback?.invoke(BackendSynthesisResult(audio = audio))
        }
    }

    private fun capture() = CapturedAudio(
        bytes = ByteArray(3_200) { 1 },
        durationMs = 100,
    )

    private fun audio() = SynthesizedAudio(
        bytes = ByteArray(3_200) { 2 },
        mimeType = "audio/pcm;codec=s16le",
        sampleRateHz = 16_000,
        channels = 1,
        durationMs = 100,
        providerId = ON_DEVICE_PROVIDER_ID,
        implementation = "fake-on-device",
        model = "fixture",
    )

    @Test fun api32RefusesCapturedPcmWithoutTouchingRecognizer() {
        val backend = FakeBackend()
        val adapter = OnDeviceSpeechAdapter(apiLevel = 32, backend = backend)
        var result: SpeechTranscriptionResult? = null

        adapter.transcribe(capture()) { result = it }

        assertEquals(0, backend.transcriptionAvailabilityReads)
        assertEquals(0, backend.transcribeCalls)
        assertEquals(SpeechAdapterFailure.PROVIDER_UNAVAILABLE, result?.failure)
        assertNull(result?.text)
    }

    @Test fun api33RequiresExplicitOnDeviceAvailability() {
        val backend = FakeBackend().apply { transcriptionAvailable = false }
        val adapter = OnDeviceSpeechAdapter(apiLevel = 33, backend = backend)
        var result: SpeechTranscriptionResult? = null

        adapter.transcribe(capture()) { result = it }

        assertEquals(1, backend.transcriptionAvailabilityReads)
        assertEquals(0, backend.transcribeCalls)
        assertEquals(SpeechAdapterFailure.PROVIDER_UNAVAILABLE, result?.failure)
        assertNull(result?.text)
    }

    @Test fun partialTranscriptNeverCrossesSeamAndFinalCrossesExactlyOnce() {
        val backend = FakeBackend()
        val adapter = OnDeviceSpeechAdapter(apiLevel = 33, backend = backend)
        val results = mutableListOf<SpeechTranscriptionResult>()

        adapter.transcribe(capture(), results::add)
        backend.emitPartialTranscript("Show")

        assertTrue(results.isEmpty())

        backend.finishTranscript("Show battery")

        assertEquals(1, results.size)
        assertEquals("Show battery", results.single().text)
        assertNull(results.single().failure)
    }

    @Test fun finalTranscriptCarriesExplicitOnDeviceProvenance() {
        val backend = FakeBackend()
        val adapter = OnDeviceSpeechAdapter(apiLevel = 33, backend = backend)
        var result: SpeechTranscriptionResult? = null

        adapter.transcribe(capture()) { result = it }
        backend.finishTranscript("Show battery")

        assertEquals(1, backend.transcribeCalls)
        assertEquals("Show battery", result?.text)
        assertEquals("en-US", result?.language)
        assertEquals(ON_DEVICE_PROVIDER_ID, result?.providerId)
        assertEquals("fake-on-device", result?.implementation)
        assertEquals("fixture", result?.model)
        assertNull(result?.failure)
    }

    @Test fun cancellationInvalidatesLateTranscript() {
        val backend = FakeBackend()
        val adapter = OnDeviceSpeechAdapter(apiLevel = 33, backend = backend)
        val results = mutableListOf<SpeechTranscriptionResult>()

        adapter.transcribe(capture(), results::add)
        adapter.cancelTranscription()
        backend.finishTranscript("must not become a turn")

        assertEquals(1, backend.cancelTranscriptionCalls)
        assertEquals(1, results.size)
        assertEquals(SpeechAdapterFailure.CANCELLED, results.single().failure)
        assertNull(results.single().text)
    }

    @Test fun unavailableBackendNeverAttemptsTranscriptionOrSynthesis() {
        val backend = FakeBackend().apply {
            transcriptionAvailable = false
            synthesisAvailable = false
        }
        val adapter = OnDeviceSpeechAdapter(apiLevel = 35, backend = backend)
        var transcription: SpeechTranscriptionResult? = null
        var synthesis: SpeechSynthesisResult? = null

        adapter.transcribe(capture()) { transcription = it }
        adapter.synthesize("done", null) { synthesis = it }

        assertEquals(0, backend.transcribeCalls)
        assertEquals(0, backend.synthesizeCalls)
        assertEquals(SpeechAdapterFailure.PROVIDER_UNAVAILABLE, transcription?.failure)
        assertEquals(SpeechAdapterFailure.PROVIDER_UNAVAILABLE, synthesis?.failure)
    }

    @Test fun synthesisCanFeedStrictTask012PlaybackProjection() {
        val backend = FakeBackend()
        val adapter = OnDeviceSpeechAdapter(apiLevel = 24, backend = backend)
        var result: SpeechSynthesisResult? = null

        adapter.synthesize("done", null) { result = it }
        backend.finishSynthesis(audio())

        assertEquals(1, backend.synthesizeCalls)
        assertTrue(result?.ok == true)
        assertEquals("audio/pcm;codec=s16le", result?.audio?.mimeType)
        assertEquals(ON_DEVICE_PROVIDER_ID, result?.audio?.providerId)
        assertEquals(3_200, result?.audio?.bytes?.size)
    }

    @Test fun malformedOrOversizedBackendResultsFailClosed() {
        val backend = FakeBackend()
        val adapter = OnDeviceSpeechAdapter(apiLevel = 35, backend = backend)
        val transcriptResults = mutableListOf<SpeechTranscriptionResult>()

        adapter.transcribe(capture(), transcriptResults::add)
        backend.finishTranscript("x".repeat(MAX_ON_DEVICE_TRANSCRIPT_BYTES + 1))

        assertEquals(SpeechAdapterFailure.RESOURCE_LIMIT, transcriptResults.single().failure)
        assertNull(transcriptResults.single().text)

        var synthesis: SpeechSynthesisResult? = null
        adapter.synthesize("done", null) { synthesis = it }
        backend.finishSynthesis(
            audio().copy(mimeType = "audio/mpeg")
        )

        assertFalse(synthesis!!.ok)
        assertEquals(SpeechAdapterFailure.UNSUPPORTED_MEDIA, synthesis!!.failure)
    }
}
