package dev.lain.os.runtime

/**
 * Ordinary controller mutations stay responsive. An accepted, final automatic
 * turn may first need the selected model to classify chat vs task, so it has
 * a bounded inference allowance instead of the five-second controller budget.
 */
internal object RuntimeTimeoutPolicy {
    const val MODEL_ROUTE_SECONDS = 190L
    const val CONTROL_SECONDS = 5L

    fun seconds(command: String, kind: String?): Long =
        if (command == "turn_submit" && kind == "auto") MODEL_ROUTE_SECONDS
        else CONTROL_SECONDS
}
