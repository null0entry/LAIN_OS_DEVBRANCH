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
    val message: String = "Connecting to local runtime..."
)
