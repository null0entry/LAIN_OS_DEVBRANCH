package dev.lain.os.voice

/**
 * Android SpeechRecognizer is a bounded request API, not a permanent hotword
 * engine. Restarting instantly after every silent session can produce repeated
 * OEM microphone-start chimes. Apply bounded cooldowns, and stop with a visible
 * reason when the recognizer repeatedly ends almost immediately.
 */
internal class SpeechRestartPolicy {
    data class Next(val delayMs: Long?, val reason: String? = null)

    private var silentOrIgnored = 0
    private var fastEnds = 0

    fun afterNoMatch(sessionDurationMs: Long): Next {
        fastEnds = if (sessionDurationMs in 0 until 1_500L) fastEnds + 1 else 0
        if (fastEnds >= 3) return Next(null, "recognizer_ended_too_quickly")
        return cooldown()
    }

    fun afterIgnoredFinal(): Next = cooldown()

    fun afterFinalRecognition() {
        silentOrIgnored = 0
        fastEnds = 0
    }

    private fun cooldown(): Next {
        silentOrIgnored = (silentOrIgnored + 1).coerceAtMost(4)
        val pauses = longArrayOf(2_000L, 4_000L, 8_000L, 12_000L)
        return Next(pauses[silentOrIgnored - 1])
    }
}
