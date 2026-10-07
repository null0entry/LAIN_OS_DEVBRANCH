package dev.lain.os.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dev.lain.os.runtime.RuntimeClient
import org.json.JSONObject

class WorkbenchViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    companion object {
        @Volatile
        var runtimeClientFactory: (Application) -> RuntimeClient = { app -> RuntimeClient(app) }
    }

    private val client = runtimeClientFactory(application)
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableLiveData(WorkbenchState())
    val state: LiveData<WorkbenchState> = mutable
    private var visible = false
    private var refreshing = false
    private val terminal = setOf("complete", "cancelled", "failed", "blocked", "budget_exhausted")
    private val tick = object : Runnable {
        override fun run() {
            if (visible) { refresh(); main.postDelayed(this, 750) }
        }
    }

    init {
        client.onConnection = { connected ->
            refreshing = false
            change { it.copy(connected = connected, ready = false, startupFailed = false, pending = false,
                message = if (connected) "Starting embedded runtime…" else "Runtime disconnected; reconnect to inspect state") }
            if (connected) refresh()
        }
    }

    private fun current() = mutable.value ?: WorkbenchState()
    private fun change(update: (WorkbenchState) -> WorkbenchState) {
        val before = current()
        val after = update(before)
        // Polling identical JSON must not rebuild the owner's screen or clear
        // text selection. Real connection, approval, and result changes publish.
        if (before.connected == after.connected && before.ready == after.ready &&
            before.startupFailed == after.startupFailed && before.pending == after.pending &&
            before.message == after.message && before.session?.toString() == after.session?.toString() &&
            before.history.toString() == after.history.toString() &&
            before.conversation?.toString() == after.conversation?.toString()) return
        mutable.value = after
    }

    fun attach() {
        visible = true
        client.connect()
        main.removeCallbacks(tick)
        main.post(tick)
    }

    fun detach(changingConfiguration: Boolean) {
        main.removeCallbacks(tick)
        if (changingConfiguration) return
        visible = false
        val session = current().session
        if (session?.optBoolean("active") == true) {
            client.request("stop", JSONObject().put("session_id", session.getString("session_id"))) {
                change { it.copy(message = "Stop requested on backgrounding; inspect the settled outcome on return") }
                if (!visible) client.disconnect() else refresh()
            }
        } else client.disconnect()
    }

    fun run(goal: String) {
        val snapshot = current()
        if (snapshot.pending || !snapshot.ready) return
        if (goal.isBlank()) { change { it.copy(message = "Choose a demo command first") }; return }
        if (snapshot.session?.optBoolean("recovery_required") == true) return
        val text = goal.trim()
        if (snapshot.session?.optBoolean("active") == true) {
            submitRevision(text, "typed")
        } else {
            mutate("start", JSONObject().put("goal", text))
        }
    }

    fun submitSpeech(text: String): Boolean {
        val snapshot = current()
        val session = snapshot.session
        if (
            snapshot.pending ||
            !snapshot.ready ||
            text.isBlank() ||
            session?.optBoolean("recovery_required") == true ||
            text.toByteArray(Charsets.UTF_8).size > 4096
        ) return false
        val normalized = text.trim()
        if (session?.optBoolean("active") == true) {
            submitRevision(normalized, "speech")
        } else {
            mutate(
                "turn_submit",
                JSONObject()
                    .put("text", normalized)
                    .put("source", "speech")
                    .put("kind", "task")
                    .put("reference", "none")
                    .put("target_session_id", JSONObject.NULL),
            )
        }
        return true
    }

    private fun submitRevision(text: String, source: String) {
        mutate(
            "turn_submit",
            JSONObject()
                .put("text", text)
                .put("source", source)
                .put("kind", "revision")
                .put("reference", "active")
                .put("target_session_id", JSONObject.NULL),
        )
    }

    fun reconnect() {
        if (current().connected && current().ready) return
        // Inspect durable state after rebinding; no saved mutation is replayed.
        client.reconnect()
    }

    fun select(sessionId: String) {
        if (current().session?.optBoolean("active") == true || current().pending) {
            change { it.copy(message = "Stop or finish the active task before selecting another session") }
            return
        }
        saved["session_id"] = sessionId
        refresh()
    }

    fun stop() {
        val session = current().session ?: return
        // Stop is available independently of the ordinary mutation button state.
        client.request("stop", JSONObject().put("session_id", session.getString("session_id"))) { reply ->
            change { it.copy(message = if (reply.optBoolean("ok"))
                "Stop requested. An in-flight action may still settle." else "Stop not acknowledged; inspect state") }
            refresh()
        }
    }

    fun approve() {
        val session = current().session ?: return
        val approval = session.optJSONObject("approval") ?: return
        mutate("approve", JSONObject().put("session_id", session.getString("session_id"))
            .put("token", approval.getString("token")))
    }

    fun resume() {
        val session = current().session ?: return
        mutate("resume", JSONObject().put("session_id", session.getString("session_id")))
    }

    private fun mutate(command: String, arguments: JSONObject) {
        if (current().pending || !current().ready) return
        change { it.copy(pending = true, message = "Submitting owner request…") }
        client.request(command, arguments) { reply ->
            val session = reply.optJSONObject("session")
            if (session != null) saved["session_id"] = session.getString("session_id")
            change { it.copy(pending = false, session = session ?: it.session,
                message = if (reply.optBoolean("ok")) "Request accepted" else
                    reply.optString("message", "Request rejected; inspect state before retrying")) }
            refresh()
        }
    }

    private fun refresh() {
        if (!current().connected || refreshing || !visible) return
        refreshing = true
        client.request("sessions", JSONObject()) { reply ->
            if (!reply.optBoolean("ok")) {
                refreshing = false
                val error = reply.optString("error")
                change { it.copy(ready = false, startupFailed = error != "APP_STARTING",
                    message = when (error) {
                        "APP_STARTING" -> "Starting embedded runtime..."
                        "APP_START_FAILED" -> "Runtime startup failed. Reconnect to try initialization again."
                        else -> "Runtime could not return state. Reconnect to inspect; no action was retried."
                    }) }
                return@request
            }
            val history = reply.optJSONArray("sessions") ?: org.json.JSONArray()
            var sid = saved.get<String>("session_id")
            if (sid == null) {
                for (i in 0 until history.length()) {
                    val item = history.getJSONObject(i)
                    if (item.getString("status") !in terminal) { sid = item.getString("session_id"); break }
                }
                if (sid == null && history.length() > 0) sid = history.getJSONObject(0).getString("session_id")
                saved["session_id"] = sid
            }
            change { it.copy(history = history, startupFailed = false) }
            client.request("turns", JSONObject()) { turnReply ->
                val conversation = if (turnReply.optBoolean("ok")) turnReply.optJSONObject("conversation") else null
                if (conversation != null) change { it.copy(conversation = conversation) }

                if (sid == null) {
                    refreshing = false
                    change { it.copy(ready = true, message = if (!it.ready) "Local runtime ready" else it.message) }
                    return@request
                }
                client.request("inspect", JSONObject().put("session_id", sid)) { inspected ->
                    refreshing = false
                    val snapshot = inspected.optJSONObject("session")
                    if (sid == saved.get<String>("session_id")) {
                        if (snapshot != null) change { it.copy(session = snapshot, ready = true,
                            message = if (!it.ready) "Local runtime ready; durable state inspected" else it.message) }
                        else change { it.copy(ready = false, startupFailed = true,
                            message = "Could not inspect durable state; no action was retried") }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        main.removeCallbacksAndMessages(null)
        client.close()
        super.onCleared()
    }
}
