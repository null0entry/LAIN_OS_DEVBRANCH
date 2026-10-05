package dev.lain.os.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val MAX_LOCAL_SYNTHESIS_TEXT_BYTES = 32_768

enum class LocalSynthesisStatus {
    IDLE,
    SYNTHESIZING,
    READY,
    FAILED,
}

enum class LocalSynthesisFailure {
    PROVIDER_UNAVAILABLE,
    CANCELLED,
    UNSUPPORTED_AUDIO,
    RESOURCE_LIMIT,
    SYNTHESIS_FAILED,
}

data class LocalSpeechVoice(
    val id: String,
    val engineId: String,
    val requiresNetwork: Boolean,
    val installed: Boolean,
)

data class EncodedSpeechAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val voice: LocalSpeechVoice,
)

data class LocalSynthesisState(
    val status: LocalSynthesisStatus = LocalSynthesisStatus.IDLE,
    val failure: LocalSynthesisFailure? = null,
    val audio: SynthesizedAudio? = null,
)

interface LocalSpeechSynthesisBackend {
    val providerId: String
    val implementation: String
    fun voices(): List<LocalSpeechVoice>
    fun synthesize(
        text: String,
        voice: LocalSpeechVoice,
        onSuccess: (EncodedSpeechAudio) -> Unit,
        onFailure: () -> Unit,
    )
    fun stop()
    fun close() = stop()
}

/**
 * Selects an installed non-network voice and converts its encoded result into
 * the bounded PCM representation consumed by TASK-012. This layer never falls
 * back to a network-required voice.
 */
class OnDeviceSpeechSynthesisController(
    private val backend: LocalSpeechSynthesisBackend,
    private val onState: (LocalSynthesisState) -> Unit = {},
) {
    @Volatile
    var state = LocalSynthesisState()
        private set

    private var generation = 0L

    @Synchronized
    fun synthesize(text: String) {
        generation += 1
        val token = generation
        val encodedSize = try {
            text.toByteArray(Charsets.UTF_8).size
        } catch (_: Exception) {
            publish(LocalSynthesisState(LocalSynthesisStatus.FAILED, LocalSynthesisFailure.RESOURCE_LIMIT))
            return
        }
        if (text.isBlank() || encodedSize > MAX_LOCAL_SYNTHESIS_TEXT_BYTES) {
            publish(LocalSynthesisState(LocalSynthesisStatus.FAILED, LocalSynthesisFailure.RESOURCE_LIMIT))
            return
        }

        val voice = try {
            backend.voices()
                .asSequence()
                .filter { it.installed && !it.requiresNetwork && it.id.isNotBlank() && it.engineId.isNotBlank() }
                .sortedBy { it.id }
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (voice == null) {
            publish(LocalSynthesisState(LocalSynthesisStatus.FAILED, LocalSynthesisFailure.PROVIDER_UNAVAILABLE))
            return
        }

        publish(LocalSynthesisState(LocalSynthesisStatus.SYNTHESIZING))
        try {
            backend.synthesize(
                text = text,
                voice = voice,
                onSuccess = { encoded -> complete(token, encoded) },
                onFailure = { fail(token, LocalSynthesisFailure.SYNTHESIS_FAILED) },
            )
        } catch (_: Exception) {
            fail(token, LocalSynthesisFailure.SYNTHESIS_FAILED)
        }
    }

    @Synchronized
    fun cancel() {
        if (state.status != LocalSynthesisStatus.SYNTHESIZING) return
        generation += 1
        try {
            backend.stop()
        } catch (_: Exception) {
        }
        publish(
            LocalSynthesisState(
                status = LocalSynthesisStatus.IDLE,
                failure = LocalSynthesisFailure.CANCELLED,
            )
        )
    }

    @Synchronized
    fun close() {
        generation += 1
        try {
            backend.close()
        } catch (_: Exception) {
        }
        publish(LocalSynthesisState())
    }

    @Synchronized
    private fun complete(token: Long, encoded: EncodedSpeechAudio) {
        if (token != generation || state.status != LocalSynthesisStatus.SYNTHESIZING) return
        val decoded = decodeWavePcm16(encoded)
        if (decoded == null) {
            publish(LocalSynthesisState(LocalSynthesisStatus.FAILED, LocalSynthesisFailure.UNSUPPORTED_AUDIO))
            return
        }
        publish(LocalSynthesisState(status = LocalSynthesisStatus.READY, audio = decoded))
    }

    @Synchronized
    private fun fail(token: Long, failure: LocalSynthesisFailure) {
        if (token != generation || state.status != LocalSynthesisStatus.SYNTHESIZING) return
        publish(LocalSynthesisState(LocalSynthesisStatus.FAILED, failure))
    }

    private fun publish(next: LocalSynthesisState) {
        state = next
        onState(next)
    }

    private fun decodeWavePcm16(encoded: EncodedSpeechAudio): SynthesizedAudio? {
        if (encoded.mimeType != "audio/wav") return null
        val bytes = encoded.bytes
        if (bytes.size < 44 || bytes.size > MAX_PLAYBACK_BYTES + 4096) return null
        if (!matches(bytes, 0, "RIFF") || !matches(bytes, 8, "WAVE")) return null

        var offset = 12
        var channels: Int? = null
        var sampleRate: Int? = null
        var dataOffset: Int? = null
        var dataSize: Int? = null
        while (offset <= bytes.size - 8) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = intLe(bytes, offset + 4)
            if (size < 0) return null
            val start = offset + 8
            val end = start.toLong() + size.toLong()
            if (end > bytes.size) return null
            when (chunkId) {
                "fmt " -> {
                    if (size < 16) return null
                    val format = shortLe(bytes, start)
                    val parsedChannels = shortLe(bytes, start + 2)
                    val parsedRate = intLe(bytes, start + 4)
                    val blockAlign = shortLe(bytes, start + 12)
                    val bits = shortLe(bytes, start + 14)
                    if (format != 1 || parsedChannels !in 1..2 ||
                        parsedRate !in 1..MAX_PLAYBACK_SAMPLE_RATE_HZ ||
                        blockAlign != parsedChannels * 2 || bits != 16
                    ) return null
                    channels = parsedChannels
                    sampleRate = parsedRate
                }
                "data" -> {
                    dataOffset = start
                    dataSize = size
                }
            }
            offset = start + size + (size and 1)
        }

        val channelCount = channels ?: return null
        val rate = sampleRate ?: return null
        val pcmOffset = dataOffset ?: return null
        val pcmSize = dataSize ?: return null
        val frameSize = channelCount * 2
        if (pcmSize <= 0 || pcmSize > MAX_PLAYBACK_BYTES || pcmSize % frameSize != 0) return null
        val frameCount = pcmSize / frameSize
        val durationMs = ((frameCount.toLong() * 1000L) + rate - 1L) / rate
        if (durationMs !in 1..MAX_PLAYBACK_DURATION_MS) return null
        val pcm = bytes.copyOfRange(pcmOffset, pcmOffset + pcmSize)
        return SynthesizedAudio(
            bytes = pcm,
            mimeType = "audio/pcm;codec=s16le",
            sampleRateHz = rate,
            channels = channelCount,
            durationMs = durationMs,
            providerId = backend.providerId,
            implementation = backend.implementation,
            model = encoded.voice.id,
        )
    }

    private fun matches(bytes: ByteArray, offset: Int, value: String): Boolean {
        if (offset < 0 || offset + value.length > bytes.size) return false
        val expected = value.toByteArray(Charsets.US_ASCII)
        return expected.indices.all { bytes[offset + it] == expected[it] }
    }

    private fun shortLe(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) return -1
        return ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
    }

    private fun intLe(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) return -1
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
