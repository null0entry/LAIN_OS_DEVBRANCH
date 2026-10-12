package dev.lain.os

import android.content.Intent
import android.os.Build
import android.widget.ProgressBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.lain.os.runtime.ResidentModelService
import android.graphics.Color
import android.text.InputFilter
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.lain.os.planner.PlannerProfile
import dev.lain.os.planner.PlannerProfileStore
import dev.lain.os.planner.QwenChatPrompt
import dev.lain.os.planner.QwenChatTurn
import dev.lain.os.runtime.ResidentModelClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Private on-device conversation. Calls the same :runtime model process as task planning.
 * The selected GGUF must have been imported in LAIN's Planner Settings (Termux storage is isolated).
 */
class LocalChatActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("native-gguf-chat-v1", MODE_PRIVATE) }
    private val profiles by lazy { PlannerProfileStore(this) }
    private var profile: PlannerProfile? = null
    private var messages = mutableListOf<QwenChatTurn>()
    private val client: ResidentModelClient by lazy {
        ResidentModelClient(this) { connected ->
            if (!isDestroyed) {
                if (!connected) updateResidency("DISCONNECTED", null)
                else setBusy(current != null)
            }
        }
    }
    private var modelState = "DISCONNECTED"
    private var residentDigest: String? = null
    private lateinit var startModel: Button
    private lateinit var stopModel: Button
    private lateinit var preloadProgress: ProgressBar
    private var current: ResidentModelClient.ChatCall? = null
    private var revision = 0L
    private lateinit var history: LinearLayout
    private lateinit var historyScroll: ScrollView
    private lateinit var modelLabel: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var stop: Button

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "LAIN · Local chat"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(17, 22, 29))
        }
        val heading = TextView(this).apply {
            text = "LAIN · OFFLINE CHAT"
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        root.addView(heading)
        modelLabel = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 12f }
        root.addView(modelLabel)
        val residencyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startModel = Button(this).apply {
            id = R.id.chat_start_model
            text = "Start model"
            isAllCaps = false
            setOnClickListener {
                if (profile == null || current != null) return@setOnClickListener
                try {
                    val request = Intent(this@LocalChatActivity, ResidentModelService::class.java)
                        .setAction(ResidentModelService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(request) else startService(request)
                    updateResidency("LOADING", profile?.model)
                } catch (_: Exception) { updateResidency("FAILED: could not start model service", null) }
            }
        }
        stopModel = Button(this).apply {
            id = R.id.chat_stop_model
            text = "Stop model"
            isAllCaps = false
            setOnClickListener {
                cancelCurrent()
                try {
                    startService(Intent(this@LocalChatActivity, ResidentModelService::class.java)
                        .setAction(ResidentModelService.ACTION_STOP))
                    updateResidency("STOPPING", residentDigest)
                } catch (_: Exception) { updateResidency("FAILED: could not stop model service", residentDigest) }
            }
        }
        residencyRow.addView(startModel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        residencyRow.addView(stopModel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(residencyRow)
        preloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = R.id.chat_model_progress
            isIndeterminate = true
            visibility = View.GONE
            contentDescription = getString(R.string.model_start_progress)
        }
        root.addView(preloadProgress)
        val newChat = Button(this).apply {
            text = "New chat"
            isAllCaps = false
            setOnClickListener {
                cancelCurrent()
                messages.clear()
                if (!saveMessages()) { status.text = "Could not save chat history."; return@setOnClickListener }
                status.text = "New conversation"
                renderMessages()
            }
        }
        root.addView(newChat)
        historyScroll = ScrollView(this).apply { isFillViewport = true }
        history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        historyScroll.addView(history)
        root.addView(historyScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            id = R.id.chat_model_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            textSize = 12f
            setTextColor(Color.LTGRAY)
            text = "Select On-device Model in Planner Settings."
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(status)
        input = EditText(this).apply {
            hint = "Message LAIN..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            minLines = 2
            maxLines = 5
            filters = arrayOf(InputFilter.LengthFilter(4096))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        root.addView(input)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        send = Button(this).apply {
            id = R.id.chat_send
            text = "Send"
            isAllCaps = false
            setOnClickListener { submitMessage() }
        }
        row.addView(send, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        stop = Button(this).apply {
            text = "Stop response"
            isAllCaps = false
            isEnabled = false
            setOnClickListener {
                cancelCurrent()
                status.text = "Response cancelled"
            }
        }
        row.addView(stop, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        setContentView(root)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val response = withContext(Dispatchers.IO) { client.status() }
                    val value = try { JSONObject(response) } catch (_: Exception) { JSONObject() }
                    updateResidency(value.optString("state", "DISCONNECTED"),
                        value.optString("model").takeIf { it.matches(Regex("[0-9a-f]{64}")) })
                    delay(500)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val selected = try { profiles.active() } catch (_: Exception) { null }
        val ready = selected?.takeIf { it.mode == "on_device" && it.protocol == "gguf_native_v1" }
        if (profile?.id != ready?.id || profile?.model != ready?.model) {
            cancelCurrent()
            profile = ready
            val selectedId = ready?.let { it.id + ":" + it.model } ?: ""
            val storedId = preferences.getString("profile_id", "") ?: ""
            if (storedId != selectedId) {
                messages.clear()
                saveMessages()
            } else {
                loadMessages()
            }
            renderMessages()
        }
        modelLabel.text = if (ready == null) {
            "No on-device GGUF selected · return to Planner Settings to import and select Qwen."
        } else {
            "Model: " + ready.name + " · " + ready.model.take(12) + "… · local only"
        }
        if (ready == null) status.text = "Chat unavailable: select an On-device Model profile."
        else if (current == null) renderResidency()
        setBusy(current != null)
    }

    override fun onStart() {
        super.onStart()
        client.connect()
    }

    private fun loadMessages() {
        messages.clear()
        try {
            val array = JSONArray(preferences.getString("messages", "[]") ?: "[]")
            require(array.length() <= 32)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val role = item.getString("role")
                val content = item.getString("content")
                require(role == "user" || role == "assistant")
                require(content.isNotBlank() && content.toByteArray(Charsets.UTF_8).size <=
                    if (role == "user") 4096 else 16384)
                messages.add(QwenChatTurn(role, content))
            }
        } catch (_: Exception) {
            messages.clear()
            status.text = "Chat history was unreadable; started a new conversation."
        }
    }

    private fun saveMessages(): Boolean {
        val array = JSONArray()
        for (message in messages.takeLast(32)) {
            array.put(JSONObject().put("role", message.role).put("content", message.text))
        }
        return preferences.edit()
            .putString("profile_id", profile?.let { it.id + ":" + it.model } ?: "")
            .putString("messages", array.toString())
            .commit()
    }

    private fun submitMessage() {
        val selected = profile ?: return
        if (current != null || !modelReady()) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (text.toByteArray(Charsets.UTF_8).size > 4096) {
            status.text = "Message is larger than 4096 UTF-8 bytes"
            return
        }
        val previous = messages.toMutableList()
        val candidate = (messages + QwenChatTurn("user", text)).takeLast(32)
        val prompt = try { QwenChatPrompt.build(candidate) } catch (_: Exception) {
            status.text = "Message exceeds local model context"
            return
        }
        messages = candidate.toMutableList()
        if (!saveMessages()) {
            messages = previous
            status.text = "Cannot save chat message; request not sent"
            return
        }
        input.text.clear()
        renderMessages()
        val call = client.call(selected.model, prompt)
        val generation = ++revision
        current = call
        setBusy(true)
        status.text = "Generating with the preloaded offline model…"
        executor.execute {
            val response = try { JSONObject(call.execute()) } catch (_: Exception) {
                JSONObject().put("ok", false).put("error", "PLANNER_MODEL_LOAD_FAILED")
            }
            runOnUiThread {
                if (isDestroyed || generation != revision || current !== call) return@runOnUiThread
                current = null
                setBusy(false)
                if (response.optBoolean("ok")) {
                    val textResponse = response.optString("body", "")
                        .substringBefore("<|im_end|>").trim()
                    if (textResponse.isNotBlank() &&
                        textResponse.toByteArray(Charsets.UTF_8).size <= 16384) {
                        messages = (messages + QwenChatTurn("assistant", textResponse)).takeLast(32)
                            .toMutableList()
                        status.text = if (saveMessages()) "Ready · local reply" else
                            "Reply shown but could not be saved"
                        renderMessages()
                    } else status.text = "Model returned an empty or invalid response"
                } else {
                    status.text = when (response.optString("error")) {
                        "PLANNER_CANCELLED" -> "Response cancelled"
                        "PLANNER_MODEL_BUSY" -> "Model busy; wait for the previous generation to finish"
                        "PLANNER_MODEL_NOT_FOUND" -> "Imported GGUF missing or changed; reimport in Planner Settings"
                        "PLANNER_MODEL_NOT_READY" -> "Model is not ready · tap Start model and wait for preload"
                        "PLANNER_MODEL_LOAD_FAILED" -> "Model could not load on this device"
                        "PLANNER_TIMEOUT" -> "Local model timed out"
                        "PLANNER_REQUEST_INVALID" -> "Prompt rejected by local model limits"
                        else -> "Local model returned invalid output"
                    }
                }
            }
        }
    }

    private fun cancelCurrent() {
        revision++
        val inFlight = current
        current = null
        inFlight?.cancel()
        setBusy(false)
    }

    private fun modelReady(): Boolean = modelState == "READY" && residentDigest == profile?.model &&
        profile != null && client.isConnected

    internal fun updateResidency(state: String, digest: String?) {
        if (modelState == state && residentDigest == digest) return
        modelState = state
        residentDigest = digest
        renderResidency()
        setBusy(current != null)
    }

    private fun renderResidency() {
        preloadProgress.visibility = if (modelState == "LOADING" || modelState == "STOPPING") View.VISIBLE else View.GONE
        if (current != null || profile == null) return
        status.text = when (modelState) {
            "LOADING" -> "Loading offline model into memory…"
            "STOPPING" -> "Stopping offline model…"
            "READY" -> if (modelReady()) "Preloaded model ready · no internet required"
                else "Selected model changed · tap Start model to load it"
            "STOPPED" -> "Model stopped · tap Start model before sending"
            "DISCONNECTED" -> "Connecting to offline model service…"
            else -> if (modelState.startsWith("FAILED:")) modelState else "Model status unavailable"
        }
    }

    private fun setBusy(busy: Boolean) {
        startModel.isEnabled = !busy && profile != null && client.isConnected &&
            modelState != "LOADING" && modelState != "STOPPING" && !modelReady()
        stopModel.isEnabled = client.isConnected && modelState != "STOPPED" && modelState != "DISCONNECTED"
        send.isEnabled = !busy && modelReady()
        stop.isEnabled = busy
        input.isEnabled = !busy && profile != null
    }

    private fun renderMessages() {
        history.removeAllViews()
        if (messages.isEmpty()) {
            history.addView(TextView(this).apply {
                text = "Start a conversation with your imported Qwen GGUF. No task execution."
                textSize = 14f
                setTextColor(Color.LTGRAY)
            })
        }
        for (message in messages.takeLast(32)) {
            history.addView(TextView(this).apply {
                val label = if (message.role == "user") "YOU" else "LAIN"
                text = label + "\n" + message.text
                textSize = 15f
                setTextColor(if (message.role == "user") Color.rgb(153, 209, 255) else Color.WHITE)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                gravity = Gravity.START
            })
        }
        historyScroll.post { historyScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onStop() {
        if (current != null) {
            cancelCurrent()
            status.text = "Generation interrupted when chat was backgrounded"
        }
        client.disconnect()
        updateResidency("DISCONNECTED", null)
        super.onStop()
    }

    override fun onDestroy() {
        cancelCurrent()
        executor.shutdownNow()
        super.onDestroy()
    }
}
