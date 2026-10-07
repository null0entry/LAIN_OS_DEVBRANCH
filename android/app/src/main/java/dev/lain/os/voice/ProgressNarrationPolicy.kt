package dev.lain.os.voice

const val DEFAULT_PROGRESS_NARRATION_INTERVAL_MS = 5_000L

data class TrustedProgressSnapshot(
    val sessionId: String,
    val revision: String,
    val status: String,
    val active: Boolean,
    val stopRequested: Boolean,
    val recoveryRequired: Boolean,
)

/**
 * Converts trusted runtime state into a small, non-authoritative speech channel.
 *
 * The policy accepts no planner/provider text, emits only fixed phrases, and
 * remembers only presentation timing. It cannot mutate or settle task state.
 */
class ProgressNarrationPolicy(
    private val minimumIntervalMs: Long = DEFAULT_PROGRESS_NARRATION_INTERVAL_MS,
) {
    private var lastKey: String? = null
    private var lastSpokenAtMs: Long? = null

    init {
        require(minimumIntervalMs >= 0)
    }

    fun next(snapshot: TrustedProgressSnapshot, nowMs: Long): String? {
        if (
            !snapshot.active ||
            snapshot.sessionId.isBlank() ||
            snapshot.revision.isBlank()
        ) return null

        val phrase = when {
            snapshot.recoveryRequired -> "recovery" to "Task recovery is required."
            snapshot.stopRequested -> "stopping" to
                "Stopping after the current action settles."
            snapshot.status == "created" || snapshot.status == "planning" ->
                "planning" to "Planning your task."
            snapshot.status == "running" -> "running" to "Working on your task."
            snapshot.status == "paused_confirmation" ->
                "approval" to "Your approval is needed."
            else -> return null
        }
        val key = "${snapshot.sessionId}:${snapshot.revision}:${phrase.first}"
        if (key == lastKey) return null
        val lastAt = lastSpokenAtMs
        val priority = phrase.first == "approval" ||
            phrase.first == "recovery" ||
            phrase.first == "stopping"
        if (
            !priority &&
            lastAt != null &&
            nowMs - lastAt < minimumIntervalMs
        ) {
            lastKey = key
            return null
        }

        lastKey = key
        lastSpokenAtMs = nowMs
        return phrase.second
    }
}
