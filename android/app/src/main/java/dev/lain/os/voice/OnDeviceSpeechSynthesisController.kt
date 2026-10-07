package dev.lain.os.voice

const val MAX_LOCAL_SYNTHESIS_TEXT_BYTES = 32_768

enum class LocalSynthesisFailure {
    PROVIDER_UNAVAILABLE,
    VOICE_UNAVAILABLE,
    RESOURCE_LIMIT,
    SYNTHESIS_FAILED,
}

data class LocalSpeechVoice(
    val id: String,
    val engineId: String,
    val requiresNetwork: Boolean,
    val installed: Boolean,
)

interface LocalSpeechSynthesisBackend {
    val providerId: String
    val implementation: String

    fun prepareVoices(onReady: (List<LocalSpeechVoice>) -> Unit)
    fun speak(text: String, voice: LocalSpeechVoice): Boolean
    fun stop()
    fun close() = stop()
}

/**
 * Minimal local-only TTS seam.
 *
 * This does not synthesize PCM or own playback state. It selects an installed
 * voice which does not require network access, then asks the platform TTS
 * engine to speak the text directly.
 */
class OnDeviceSpeechSynthesisController(
    private val backend: LocalSpeechSynthesisBackend,
) {
    private val gate = Any()
    private var generation = 0L

    fun availableVoices(onResult: (List<LocalSpeechVoice>) -> Unit) {
        try {
            backend.prepareVoices { voices -> onResult(eligibleVoices(voices)) }
        } catch (_: Exception) {
            onResult(emptyList())
        }
    }

    fun speak(
        text: String,
        selection: StoredSpeechVoice? = null,
        onResult: (LocalSynthesisFailure?) -> Unit = {},
    ) {
        val encodedSize = try {
            text.toByteArray(Charsets.UTF_8).size
        } catch (_: Exception) {
            onResult(LocalSynthesisFailure.RESOURCE_LIMIT)
            return
        }
        if (text.isBlank() || encodedSize > MAX_LOCAL_SYNTHESIS_TEXT_BYTES) {
            onResult(LocalSynthesisFailure.RESOURCE_LIMIT)
            return
        }

        val requestGeneration = synchronized(gate) {
            generation += 1
            generation
        }
        try {
            backend.prepareVoices { voices ->
                if (synchronized(gate) { requestGeneration != generation }) {
                    return@prepareVoices
                }
                val available = eligibleVoices(voices)
                val voice = if (selection == null) {
                    available.firstOrNull()
                } else {
                    available.firstOrNull {
                        it.id == selection.voiceId && it.engineId == selection.engineId
                    }
                }
                if (voice == null) {
                    onResult(
                        if (selection == null) {
                            LocalSynthesisFailure.PROVIDER_UNAVAILABLE
                        } else {
                            LocalSynthesisFailure.VOICE_UNAVAILABLE
                        }
                    )
                    return@prepareVoices
                }
                val accepted = synchronized(gate) {
                    if (requestGeneration != generation) {
                        null
                    } else {
                        try {
                            backend.speak(text, voice)
                        } catch (_: Exception) {
                            false
                        }
                    }
                } ?: return@prepareVoices
                onResult(if (accepted) null else LocalSynthesisFailure.SYNTHESIS_FAILED)
            }
        } catch (_: Exception) {
            onResult(LocalSynthesisFailure.PROVIDER_UNAVAILABLE)
        }
    }

    fun stop() {
        synchronized(gate) {
            generation += 1
            try {
                backend.stop()
            } catch (_: Exception) {
            }
        }
    }

    fun close() {
        synchronized(gate) {
            generation += 1
            try {
                backend.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun eligibleVoices(voices: List<LocalSpeechVoice>): List<LocalSpeechVoice> =
        voices
            .asSequence()
            .filter {
                it.installed &&
                    !it.requiresNetwork &&
                    it.id.isNotBlank() &&
                    it.engineId.isNotBlank()
            }
            .distinctBy { it.engineId to it.id }
            .sortedWith(compareBy<LocalSpeechVoice> { it.engineId }.thenBy { it.id })
            .toList()
}
