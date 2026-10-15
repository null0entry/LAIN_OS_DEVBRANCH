package dev.lain.os.voice

/** Pure, fail-closed final-transcript activation. No ambient text is retained. */
internal enum class WakeKind { IGNORE, ARMED, COMMAND, STOP_TASK, STOP_LISTENING }
internal data class WakeDecision(val kind: WakeKind, val command: String = "")

internal class WakeCommandGate(
    private val clock: () -> Long,
    private val laneAlias: Boolean = true,
) {
    private var lastFinalId = 0L
    private var armedUntil = 0L

    fun reset() {
        lastFinalId = 0L
        armedUntil = 0L
    }

    fun accept(finalId: Long, transcript: String, confidence: Float?): WakeDecision {
        if (finalId <= 0L || finalId <= lastFinalId) return WakeDecision(WakeKind.IGNORE)
        lastFinalId = finalId
        if (confidence != null && (confidence.isNaN() || confidence != -1f &&
                (confidence < 0.45f || confidence > 1f))) return WakeDecision(WakeKind.IGNORE)
        val trimmed = transcript.trim()
        if (trimmed.isEmpty() || trimmed.contains('\u0000') ||
            trimmed.contains('\n') || trimmed.contains('\r') ||
            trimmed.toByteArray(Charsets.UTF_8).size > 4096) return WakeDecision(WakeKind.IGNORE)

        val now = clock()
        val prefix = if (laneAlias) "(?:lain|lane)" else "lain"
        val match = Regex("^(?:hey[ ]+)?$prefix(?:[ ,:]+(.*))?$", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
        val suffix = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (match != null && suffix.isEmpty()) {
            armedUntil = now + 5_000L
            return WakeDecision(WakeKind.ARMED)
        }
        val command = if (match != null) suffix else {
            if (armedUntil == 0L || now > armedUntil) {
                armedUntil = 0L
                return WakeDecision(WakeKind.IGNORE)
            }
            trimmed
        }
        armedUntil = 0L
        val normalized = command.trim().trimEnd('.', '!').lowercase()
        return when (normalized) {
            "stop listening", "stop voice", "stop microphone" -> WakeDecision(WakeKind.STOP_LISTENING)
            "stop", "stop task", "cancel task" -> WakeDecision(WakeKind.STOP_TASK)
            "approve", "yes", "confirm", "allow", "grant permission" -> WakeDecision(WakeKind.IGNORE)
            else -> WakeDecision(WakeKind.COMMAND, command)
        }
    }
}
