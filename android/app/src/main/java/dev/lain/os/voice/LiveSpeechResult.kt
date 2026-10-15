package dev.lain.os.voice

/**
 * Validate a final, locally recognized utterance before it can become an
 * authoritative user turn. Spoken approval still cannot bypass policy.
 */
internal fun validateLiveSpeechFinal(
    raw: BackendTranscriptionResult,
): SpeechTranscriptionResult {
    raw.failure?.let {
        return SpeechTranscriptionResult(failure = it, diagnostic = raw.diagnostic)
    }
    if (!raw.isFinal) {
        return SpeechTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED,
            diagnostic = "Speech recognition did not return a final result")
    }
    val confidence = raw.confidence?.takeUnless { it == -1f }
    if (confidence != null && (!confidence.isFinite() || confidence < 0f || confidence > 1f)) {
        return SpeechTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED,
            diagnostic = "Speech provider returned invalid confidence")
    }
    if (confidence != null && confidence < MIN_ON_DEVICE_TRANSCRIPT_CONFIDENCE) {
        return SpeechTranscriptionResult(failure = SpeechAdapterFailure.LOW_CONFIDENCE)
    }
    val text = raw.text?.trim()
    if (text.isNullOrEmpty()) {
        return SpeechTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED,
            diagnostic = "Offline recognizer returned no final text")
    }
    if (text.toByteArray(Charsets.UTF_8).size > MAX_ON_DEVICE_TRANSCRIPT_BYTES) {
        return SpeechTranscriptionResult(failure = SpeechAdapterFailure.RESOURCE_LIMIT)
    }
    return SpeechTranscriptionResult(
        text = text,
        language = raw.language,
        providerId = ON_DEVICE_PROVIDER_ID,
        implementation = "android-live-on-device-stt",
        confidence = confidence,
    )
}
