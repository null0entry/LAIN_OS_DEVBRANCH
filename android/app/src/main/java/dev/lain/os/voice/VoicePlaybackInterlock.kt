package dev.lain.os.voice

/** A stale TTS completion must never resume the mic over newer playback. */
internal class VoicePlaybackInterlock {
    private val utterances = mutableSetOf<String>()

    fun queue(id: String, flush: Boolean = false): Boolean {
        require(id.isNotBlank())
        val wasIdle = utterances.isEmpty()
        if (flush) utterances.clear()
        utterances.add(id)
        return wasIdle
    }

    /** True only when a known final utterance released the last playback lease. */
    fun complete(id: String): Boolean = utterances.remove(id) && utterances.isEmpty()

    fun cancel(): Boolean {
        val held = utterances.isNotEmpty()
        utterances.clear()
        return held
    }
}
