package dev.lain.os.voice

const val MAX_LOCAL_SYNTHESIS_TEXT_BYTES = 32_768
const val MAX_PROGRESSIVE_SEGMENT_BYTES = 1_024
const val MAX_PROGRESSIVE_SEGMENTS = 64

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
    /** Optional same-provider ordered TTS queue. False means delivery failed, not unsupported. */
    val supportsProgressiveSpeech: Boolean get() = false
    fun speakSegments(segments: List<String>, voice: LocalSpeechVoice): Boolean = false
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
    ) = requestSpeech(text, selection, progressive = false, onResult)

    /** Assistant responses may use bounded ordered chunks. Progress narration uses speak(). */
    fun speakProgressively(
        text: String,
        selection: StoredSpeechVoice? = null,
        onResult: (LocalSynthesisFailure?) -> Unit = {},
    ) = requestSpeech(text, selection, progressive = true, onResult)

    private fun requestSpeech(
        text: String,
        selection: StoredSpeechVoice?,
        progressive: Boolean,
        onResult: (LocalSynthesisFailure?) -> Unit,
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

        // Segment before touching the provider, preserving text and Unicode code points.
        val segments = if (progressive) segmentText(text) else null
        if (progressive && segments == null) {
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
                            if (progressive && backend.supportsProgressiveSpeech) {
                                backend.speakSegments(requireNotNull(segments), voice)
                            } else {
                                // Unsupported engines keep the same exact offline selection.
                                backend.speak(text, voice)
                            }
                        } catch (_: Exception) {
                            false
                        }
                    }
                } ?: return@prepareVoices
                if (synchronized(gate) { requestGeneration != generation }) return@prepareVoices
                // Presentation status only. Never reports task execution success/failure.
                onResult(if (accepted) null else LocalSynthesisFailure.SYNTHESIS_FAILED)
            }
        } catch (_: Exception) {
            if (synchronized(gate) { requestGeneration == generation }) {
                onResult(LocalSynthesisFailure.PROVIDER_UNAVAILABLE)
            }
        }
    }

    private fun segmentText(text: String): List<String>? {
        val segments = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = start
            var bytes = 0
            var boundary = start
            while (end < text.length) {
                val cp = Character.codePointAt(text, end)
                val cpBytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
                if (bytes + cpBytes > MAX_PROGRESSIVE_SEGMENT_BYTES) break
                bytes += cpBytes
                end += Character.charCount(cp)
                if (Character.isWhitespace(cp) || cp == '.'.code || cp == '!'.code || cp == '?'.code) {
                    boundary = end
                }
            }
            if (end == start) return null
            val cut = if (end < text.length && boundary > start &&
                !text.substring(start, boundary).isBlank()) boundary else end
            val chunk = text.substring(start, cut)
            if (chunk.isBlank()) return null
            segments.add(chunk)
            if (segments.size > MAX_PROGRESSIVE_SEGMENTS) return null
            start = cut
        }
        return segments
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
