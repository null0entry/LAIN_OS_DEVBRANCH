package dev.lain.os.voice

const val ON_DEVICE_PROVIDER_ID = "android-on-device"
const val MAX_ON_DEVICE_TRANSCRIPT_BYTES = 32_768
const val MAX_ON_DEVICE_SYNTHESIS_TEXT_BYTES = 32_768
private const val CAPTURED_PCM_STT_API_FLOOR = 33
private const val PCM16_LE_MIME = "audio/pcm;codec=s16le"

enum class SpeechAdapterFailure {
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    CANCELLED,
    MALFORMED_RESPONSE,
    UNSUPPORTED_MEDIA,
    RESOURCE_LIMIT,
}

data class BackendTranscriptionResult(
    val text: String? = null,
    val language: String? = null,
    val failure: SpeechAdapterFailure? = null,
)

data class BackendSynthesisResult(
    val audio: SynthesizedAudio? = null,
    val failure: SpeechAdapterFailure? = null,
)

data class SpeechTranscriptionResult(
    val text: String? = null,
    val language: String? = null,
    val providerId: String? = null,
    val implementation: String? = null,
    val model: String? = null,
    val failure: SpeechAdapterFailure? = null,
) {
    val ok: Boolean get() = failure == null && !text.isNullOrBlank()
}

data class SpeechSynthesisResult(
    val audio: SynthesizedAudio? = null,
    val failure: SpeechAdapterFailure? = null,
) {
    val ok: Boolean get() = failure == null && audio != null
}

interface OnDeviceSpeechBackend {
    val transcriptionAvailable: Boolean
    val synthesisAvailable: Boolean
    val implementation: String
    val model: String?

    fun transcribe(
        audio: CapturedAudio,
        callback: (BackendTranscriptionResult) -> Unit,
    )

    fun synthesize(
        text: String,
        voiceId: String?,
        callback: (BackendSynthesisResult) -> Unit,
    )

    fun cancelTranscription()
    fun cancelSynthesis()
    fun close()
}

/**
 * Tiny provider-neutral seam for TASK-050.
 *
 * Captured PCM transcription is fail-closed below API 33 and never selects a
 * network fallback. Backend availability must be explicitly true before use.
 * Only completed backend callbacks are surfaced; cancellation invalidates stale
 * callbacks. Synthesis remains usable on older APIs when a local backend exists.
 */
class OnDeviceSpeechAdapter(
    private val apiLevel: Int,
    private val backend: OnDeviceSpeechBackend,
) {
    private var transcriptionGeneration = 0L
    private var synthesisGeneration = 0L
    private var transcriptionActive = false
    private var synthesisActive = false
    private var transcriptionCallback: ((SpeechTranscriptionResult) -> Unit)? = null
    private var synthesisCallback: ((SpeechSynthesisResult) -> Unit)? = null

    @Synchronized
    fun transcribe(
        audio: CapturedAudio,
        callback: (SpeechTranscriptionResult) -> Unit,
    ) {
        transcriptionGeneration += 1
        val token = transcriptionGeneration

        if (apiLevel < CAPTURED_PCM_STT_API_FLOOR || !backend.transcriptionAvailable) {
            callback(
                SpeechTranscriptionResult(
                    failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE,
                )
            )
            return
        }

        transcriptionActive = true
        transcriptionCallback = callback
        try {
            backend.transcribe(audio) { result -> finishTranscription(token, result) }
        } catch (_: Exception) {
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.MALFORMED_RESPONSE),
            )
        }
    }

    @Synchronized
    fun cancelTranscription() {
        if (!transcriptionActive) return
        transcriptionGeneration += 1
        transcriptionActive = false
        val callback = transcriptionCallback
        transcriptionCallback = null
        try {
            backend.cancelTranscription()
        } catch (_: Exception) {
        }
        callback?.invoke(
            SpeechTranscriptionResult(failure = SpeechAdapterFailure.CANCELLED)
        )
    }

    @Synchronized
    fun synthesize(
        text: String,
        voiceId: String?,
        callback: (SpeechSynthesisResult) -> Unit,
    ) {
        synthesisGeneration += 1
        val token = synthesisGeneration
        val textBytes = try {
            text.toByteArray(Charsets.UTF_8).size
        } catch (_: Exception) {
            MAX_ON_DEVICE_SYNTHESIS_TEXT_BYTES + 1
        }
        if (text.isBlank() || textBytes > MAX_ON_DEVICE_SYNTHESIS_TEXT_BYTES) {
            callback(SpeechSynthesisResult(failure = SpeechAdapterFailure.RESOURCE_LIMIT))
            return
        }

        if (!backend.synthesisAvailable) {
            callback(SpeechSynthesisResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE))
            return
        }

        synthesisActive = true
        synthesisCallback = callback
        try {
            backend.synthesize(text, voiceId) { result -> finishSynthesis(token, result) }
        } catch (_: Exception) {
            finishSynthesis(
                token,
                BackendSynthesisResult(failure = SpeechAdapterFailure.MALFORMED_RESPONSE),
            )
        }
    }

    @Synchronized
    fun cancelSynthesis() {
        if (!synthesisActive) return
        synthesisGeneration += 1
        synthesisActive = false
        val callback = synthesisCallback
        synthesisCallback = null
        try {
            backend.cancelSynthesis()
        } catch (_: Exception) {
        }
        callback?.invoke(SpeechSynthesisResult(failure = SpeechAdapterFailure.CANCELLED))
    }

    @Synchronized
    fun close() {
        transcriptionGeneration += 1
        synthesisGeneration += 1
        transcriptionActive = false
        synthesisActive = false
        transcriptionCallback = null
        synthesisCallback = null
        try {
            backend.close()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun finishTranscription(
        token: Long,
        result: BackendTranscriptionResult,
    ) {
        if (!transcriptionActive || token != transcriptionGeneration) return
        transcriptionActive = false
        val callback = transcriptionCallback
        transcriptionCallback = null

        val failure = result.failure
        if (failure != null) {
            callback?.invoke(SpeechTranscriptionResult(failure = failure))
            return
        }

        val text = result.text
        if (text.isNullOrBlank()) {
            callback?.invoke(
                SpeechTranscriptionResult(failure = SpeechAdapterFailure.MALFORMED_RESPONSE)
            )
            return
        }

        val size = try {
            text.toByteArray(Charsets.UTF_8).size
        } catch (_: Exception) {
            MAX_ON_DEVICE_TRANSCRIPT_BYTES + 1
        }
        if (size > MAX_ON_DEVICE_TRANSCRIPT_BYTES) {
            callback?.invoke(
                SpeechTranscriptionResult(failure = SpeechAdapterFailure.RESOURCE_LIMIT)
            )
            return
        }

        callback?.invoke(
            SpeechTranscriptionResult(
                text = text,
                language = result.language,
                providerId = ON_DEVICE_PROVIDER_ID,
                implementation = backend.implementation,
                model = backend.model,
            )
        )
    }

    @Synchronized
    private fun finishSynthesis(
        token: Long,
        result: BackendSynthesisResult,
    ) {
        if (!synthesisActive || token != synthesisGeneration) return
        synthesisActive = false
        val callback = synthesisCallback
        synthesisCallback = null

        val failure = result.failure
        if (failure != null) {
            callback?.invoke(SpeechSynthesisResult(failure = failure))
            return
        }

        val audio = result.audio
        if (audio == null) {
            callback?.invoke(
                SpeechSynthesisResult(failure = SpeechAdapterFailure.MALFORMED_RESPONSE)
            )
            return
        }
        if (audio.mimeType != PCM16_LE_MIME) {
            callback?.invoke(
                SpeechSynthesisResult(failure = SpeechAdapterFailure.UNSUPPORTED_MEDIA)
            )
            return
        }

        callback?.invoke(SpeechSynthesisResult(audio = audio))
    }
}
