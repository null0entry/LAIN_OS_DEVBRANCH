package dev.lain.os.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceSpeechSynthesisControllerTest {
    private class FakeBackend : LocalSpeechSynthesisBackend {
        override val providerId = "android-tts"
        override val implementation = "fake-local-tts"
        var listedVoices = listOf(
            LocalSpeechVoice("offline-b", "fake.engine", requiresNetwork = false, installed = true),
            LocalSpeechVoice("offline-a", "fake.engine", requiresNetwork = false, installed = true),
        )
        var starts = 0
        var stops = 0
        var lastVoice: LocalSpeechVoice? = null
        var success: ((EncodedSpeechAudio) -> Unit)? = null
        var failure: (() -> Unit)? = null

        override fun voices(): List<LocalSpeechVoice> = listedVoices

        override fun synthesize(
            text: String,
            voice: LocalSpeechVoice,
            onSuccess: (EncodedSpeechAudio) -> Unit,
            onFailure: () -> Unit,
        ) {
            starts += 1
            lastVoice = voice
            success = onSuccess
            failure = onFailure
        }

        override fun stop() {
            stops += 1
        }
    }

    private fun wavPcm16(
        sampleRate: Int = 16_000,
        channels: Int = 1,
        pcm: ByteArray = byteArrayOf(0, 0, 1, 0, 2, 0, 3, 0),
    ): ByteArray {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        return ByteBuffer.allocate(44 + pcm.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(36 + pcm.size)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1)
            .putShort(channels.toShort())
            .putInt(sampleRate)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(pcm.size)
            .put(pcm)
            .array()
    }

    @Test fun networkOnlyVoiceFailsClosedWithoutSynthesis() {
        val backend = FakeBackend().apply {
            listedVoices = listOf(
                LocalSpeechVoice("cloud", "fake.engine", requiresNetwork = true, installed = true),
            )
        }
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.synthesize("hello")

        assertEquals(LocalSynthesisStatus.FAILED, controller.state.status)
        assertEquals(LocalSynthesisFailure.PROVIDER_UNAVAILABLE, controller.state.failure)
        assertEquals(0, backend.starts)
        assertNull(controller.state.audio)
    }

    @Test fun offlineInstalledVoiceProducesBoundedTask012Audio() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.synthesize("hello")
        assertEquals("offline-a", backend.lastVoice?.id)
        backend.success?.invoke(
            EncodedSpeechAudio(
                bytes = wavPcm16(),
                mimeType = "audio/wav",
                voice = backend.lastVoice!!,
            )
        )

        assertEquals(LocalSynthesisStatus.READY, controller.state.status)
        val audio = controller.state.audio!!
        assertEquals("audio/pcm;codec=s16le", audio.mimeType)
        assertEquals(16_000, audio.sampleRateHz)
        assertEquals(1, audio.channels)
        assertEquals("android-tts", audio.providerId)
        assertEquals("fake-local-tts", audio.implementation)
        assertEquals("offline-a", audio.model)
        assertTrue(audio.bytes.contentEquals(byteArrayOf(0, 0, 1, 0, 2, 0, 3, 0)))
    }

    @Test fun cancelInvalidatesStaleCompletion() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.synthesize("hello")
        val stale = backend.success
        controller.cancel()
        stale?.invoke(
            EncodedSpeechAudio(
                bytes = wavPcm16(),
                mimeType = "audio/wav",
                voice = backend.lastVoice!!,
            )
        )

        assertEquals(LocalSynthesisStatus.IDLE, controller.state.status)
        assertEquals(LocalSynthesisFailure.CANCELLED, controller.state.failure)
        assertNull(controller.state.audio)
        assertEquals(1, backend.stops)
    }

    @Test fun malformedWaveFailsClosed() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.synthesize("hello")
        backend.success?.invoke(
            EncodedSpeechAudio(
                bytes = "not-wave".toByteArray(),
                mimeType = "audio/wav",
                voice = backend.lastVoice!!,
            )
        )

        assertEquals(LocalSynthesisStatus.FAILED, controller.state.status)
        assertEquals(LocalSynthesisFailure.UNSUPPORTED_AUDIO, controller.state.failure)
        assertNull(controller.state.audio)
    }

    @Test fun oversizedTextNeverReachesBackend() {
        val backend = FakeBackend()
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.synthesize("x".repeat(MAX_LOCAL_SYNTHESIS_TEXT_BYTES + 1))

        assertEquals(LocalSynthesisStatus.FAILED, controller.state.status)
        assertEquals(LocalSynthesisFailure.RESOURCE_LIMIT, controller.state.failure)
        assertEquals(0, backend.starts)
    }

    @Test fun uninstalledOfflineVoiceIsNotSelected() {
        val backend = FakeBackend().apply {
            listedVoices = listOf(
                LocalSpeechVoice("missing", "fake.engine", requiresNetwork = false, installed = false),
                LocalSpeechVoice("network", "fake.engine", requiresNetwork = true, installed = true),
            )
        }
        val controller = OnDeviceSpeechSynthesisController(backend)

        controller.synthesize("hello")

        assertFalse(controller.state.audio != null)
        assertEquals(LocalSynthesisFailure.PROVIDER_UNAVAILABLE, controller.state.failure)
        assertEquals(0, backend.starts)
    }
}
