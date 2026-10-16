package dev.lain.os.runtime

/**
 * Ordinary controller mutations stay responsive. An accepted, final automatic
 * turn may first need the selected model to classify chat vs task, so it has
 * a bounded inference allowance instead of the five-second controller budget.
 */
internal object RuntimeTimeoutPolicy {
    const val MODEL_ROUTE_SECONDS = 190L
    const val CONTROL_SECONDS = 5L
    const val WORKFLOW_RUN_SECONDS = 60L
    const val WORKFLOW_APPROVE_SECONDS = 30L

    fun seconds(command: String, kind: String?): Long =
        when {
            command == "turn_submit" && kind == "auto" -> MODEL_ROUTE_SECONDS
            command == "workflow_run" -> WORKFLOW_RUN_SECONDS
            command == "workflow_approve" -> WORKFLOW_APPROVE_SECONDS
            else -> CONTROL_SECONDS
        }
}
