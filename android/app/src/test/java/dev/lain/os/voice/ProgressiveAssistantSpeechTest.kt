package dev.lain.os.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveAssistantSpeechTest {
    private class FakeBackend : LocalSpeechSynthesisBackend {
        override val providerId = "fixture-offline"
        override val implementation = "fixture-tts"
        override val supportsProgressiveSpeech: Boolean = true
        var voices = listOf(LocalSpeechVoice("offline", "local.engine", false, true))
        var deferred = false
        var callback: ((List<LocalSpeechVoice>) -> Unit)? = null
        var progressiveCalls = 0
        var wholeCalls = 0
        var stopCalls = 0
        var acceptSegments = true
        var chunks = emptyList<String>()
        var selected: LocalSpeechVoice? = null
        override fun prepareVoices(onReady: (List<LocalSpeechVoice>) -> Unit) {
            if (deferred) callback = onReady else onReady(voices)
        }
        fun deliver() {
            val ready = callback ?: return
            callback = null
            ready(voices)
        }
        override fun speak(text: String, voice: LocalSpeechVoice): Boolean {
            wholeCalls++
            chunks = listOf(text)
            selected = voice
            return true
        }
        override fun speakSegments(segments: List<String>, voice: LocalSpeechVoice): Boolean {
            progressiveCalls++
            chunks = segments.toList()
            selected = voice
            return acceptSegments
        }
        override fun stop() { stopCalls++ }
    }

    @Test fun longAssistantReplyUsesBoundedOrderedOfflineSegments() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)
        val text = ("A long local response. ").repeat(180)
        var result: LocalSynthesisFailure? = LocalSynthesisFailure.SYNTHESIS_FAILED

        controller.speakProgressively(text) { result = it }

        assertNull(result)
        assertEquals(1, backend.progressiveCalls)
        assertEquals(0, backend.wholeCalls)
        assertTrue(backend.chunks.size > 1)
        assertEquals(text, backend.chunks.joinToString(""))
        assertTrue(backend.chunks.all { it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size <= MAX_PROGRESSIVE_SEGMENT_BYTES })
        assertTrue(backend.chunks.size <= MAX_PROGRESSIVE_SEGMENTS)
        assertEquals("offline", backend.selected?.id)
    }

    @Test fun nonStreamingLocalEngineKeepsSameSelectedVoiceAndWholeResponse() {
        val backend = object : LocalSpeechSynthesisBackend {
            override val providerId = "offline"
            override val implementation = "legacy"
            var text: String? = null
            var voice: LocalSpeechVoice? = null
            override fun prepareVoices(onReady: (List<LocalSpeechVoice>) -> Unit) {
                onReady(listOf(LocalSpeechVoice("v2", "engine", false, true)))
            }
            override fun speak(text: String, voice: LocalSpeechVoice): Boolean {
                this.text = text
                this.voice = voice
                return true
            }
            override fun stop() = Unit
        }
        val controller = OnDeviceSpeechSynthesisController(backend)
        val reply = "Only this offline voice may speak."

        controller.speakProgressively(reply, StoredSpeechVoice("engine", "v2"))

        assertEquals(reply, backend.text)
        assertEquals("v2", backend.voice?.id)
    }

    @Test fun cancellationDiscardsDeferredChunksBeforeBackendSpeak() {
        val backend = FakeBackend().apply { deferred = true }
        val controller = OnDeviceSpeechSynthesisController(backend)
        var callbackCalled = false
        controller.speakProgressively("First sentence. Second sentence.") { callbackCalled = true }

        controller.stop()
        backend.deliver()

        assertEquals(0, backend.progressiveCalls)
        assertEquals(0, backend.wholeCalls)
        assertFalse(callbackCalled)
        assertEquals(1, backend.stopCalls)
    }

    @Test fun supersededTurnCannotSpeakAfterNewerTurn() {
        val backend = FakeBackend().apply { deferred = true }
        val controller = OnDeviceSpeechSynthesisController(backend)
        controller.speakProgressively("Obsolete response.")
        controller.speakProgressively("Current response.")
        backend.deliver()

        assertEquals(1, backend.progressiveCalls)
        assertEquals("Current response.", backend.chunks.joinToString(""))
    }

    @Test fun remoteOnlyVoiceCannotReceiveSegmentsOrSilentFallback() {
        val backend = FakeBackend().apply {
            voices = listOf(LocalSpeechVoice("network", "cloud.engine", true, true))
        }
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = null
        controller.speakProgressively("Do not leave device.") { failure = it }

        assertEquals(LocalSynthesisFailure.PROVIDER_UNAVAILABLE, failure)
        assertEquals(0, backend.progressiveCalls)
        assertEquals(0, backend.wholeCalls)
    }

    @Test fun progressiveFailureDoesNotFallBackToAnotherEngineOrReplay() {
        val backend = FakeBackend().apply { acceptSegments = false }
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = null
        controller.speakProgressively("Speak only once.") { failure = it }

        assertEquals(LocalSynthesisFailure.SYNTHESIS_FAILED, failure)
        assertEquals(1, backend.progressiveCalls)
        assertEquals(0, backend.wholeCalls)
    }

    @Test fun emojiAndMultibyteCodepointsRemainWholeAndOrdered() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)
        val text = ("🫧 限界! ").repeat(450)
        var failure: LocalSynthesisFailure? = null
        controller.speakProgressively(text) { failure = it }

        assertNull(failure)
        assertEquals(text, backend.chunks.joinToString(""))
        assertTrue(backend.chunks.all { it.toByteArray(Charsets.UTF_8).size <= MAX_PROGRESSIVE_SEGMENT_BYTES })
        assertTrue(backend.chunks.all { !it.contains('\uFFFD') })
    }

    @Test fun unboundedTextRejectsBeforeBackendOrVoiceSelection() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)
        var failure: LocalSynthesisFailure? = null
        controller.speakProgressively("x".repeat(MAX_LOCAL_SYNTHESIS_TEXT_BYTES + 1)) { failure = it }

        assertEquals(LocalSynthesisFailure.RESOURCE_LIMIT, failure)
        assertEquals(0, backend.progressiveCalls)
        assertEquals(0, backend.wholeCalls)
    }
}
