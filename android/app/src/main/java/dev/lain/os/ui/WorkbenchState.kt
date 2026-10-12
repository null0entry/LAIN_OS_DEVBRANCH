package dev.lain.os.ui

import org.json.JSONArray
import org.json.JSONObject

data class WorkbenchState(
    val connected: Boolean = false,
    val ready: Boolean = false,
    val startupFailed: Boolean = false,
    val pending: Boolean = false,
    val session: JSONObject? = null,
    val history: JSONArray = JSONArray(),
    val conversation: JSONObject? = null,
    val conversationResponse: String? = null,
    val message: String = "Connecting to local runtime…"
) {
    val replyPending: Boolean
        get() {
            val replies = conversation?.optJSONArray("assistant_replies") ?: return false
            return (0 until replies.length()).any {
                replies.optJSONObject(it)?.optString("state") == "pending"
            }
        }
}
