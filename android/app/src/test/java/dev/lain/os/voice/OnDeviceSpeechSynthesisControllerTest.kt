package dev.lain.os.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnDeviceSpeechSynthesisControllerTest {
    private class FakeBackend : LocalSpeechSynthesisBackend {
        override val providerId = "android-tts"
        override val implementation = "fake-local-tts"
        var listedVoices = listOf(
            LocalSpeechVoice("offline-b", "fake.engine", requiresNetwork = false, installed = true),
            LocalSpeechVoice("offline-a", "fake.engine", requiresNetwork = false, installed = true),
        )
        var prepareCalls = 0
        var speakCalls = 0
        var stops = 0
        var lastText: String? = null
        var lastVoice: LocalSpeechVoice? = null
        var acceptSpeak = true
        var deferPreparation = false
        private var pendingReady: ((List<LocalSpeechVoice>) -> Unit)? = null

        override fun prepareVoices(onReady: (List<LocalSpeechVoice>) -> Unit) {
            prepareCalls += 1
            if (deferPreparation) {
                pendingReady = onReady
            } else {
                onReady(listedVoices)
            }
        }

        fun completePreparation() {
            pendingReady?.invoke(listedVoices)
            pendingReady = null
        }

        override fun speak(text: String, voice: LocalSpeechVoice): Boolean {
            speakCalls += 1
            lastText = text
            lastVoice = voice
            return acceptSpeak
        }

        override fun stop() {
            stops += 1
        }
    }

    @Test fun networkOnlyVoiceFailsClosedWithoutSpeaking() {
        val backend = FakeBackend().apply {
            listedVoices = listOf(
                LocalSpeechVoice("cloud", "fake.engine", requiresNetwork = true, installed = true),
            )
        }
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = null

        controller.speak("hello") { failure = it }

        assertEquals(LocalSynthesisFailure.PROVIDER_UNAVAILABLE, failure)
        assertEquals(0, backend.speakCalls)
    }

    @Test fun installedOfflineVoiceSpeaksDirectlyWithoutPcmHandoff() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = LocalSynthesisFailure.SYNTHESIS_FAILED

        controller.speak("hello") { failure = it }

        assertEquals(1, backend.prepareCalls)
        assertEquals(1, backend.speakCalls)
        assertEquals("hello", backend.lastText)
        assertEquals("offline-a", backend.lastVoice?.id)
        assertNull(failure)
    }

    @Test fun backendRejectionFailsWithoutFallback() {
        val backend = FakeBackend().apply { acceptSpeak = false }
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = null

        controller.speak("hello") { failure = it }

        assertEquals(LocalSynthesisFailure.SYNTHESIS_FAILED, failure)
        assertEquals(1, backend.speakCalls)
    }

    @Test fun oversizedTextNeverTouchesVoiceProvider() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = null

        controller.speak("x".repeat(MAX_LOCAL_SYNTHESIS_TEXT_BYTES + 1)) { failure = it }

        assertEquals(LocalSynthesisFailure.RESOURCE_LIMIT, failure)
        assertEquals(0, backend.prepareCalls)
        assertEquals(0, backend.speakCalls)
    }

    @Test fun stopInvalidatesDelayedVoicePreparationBeforeItCanSpeak() {
        val backend = FakeBackend().apply { deferPreparation = true }
        val controller = OnDeviceSpeechSynthesisController(backend)
        var callbackCalled = false

        controller.speak("progress") { callbackCalled = true }
        controller.stop()
        backend.completePreparation()

        assertEquals(0, backend.speakCalls)
        assertFalse(callbackCalled)
    }

    @Test fun stopDelegatesDirectlyToTextToSpeechBackend() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.stop()

        assertEquals(1, backend.stops)
    }
}
