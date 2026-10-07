package dev.lain.os

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.lain.os.databinding.ActivityMainBinding
import dev.lain.os.planner.PlannerConnectionStatus
import dev.lain.os.planner.PlannerProfile
import dev.lain.os.planner.PlannerProfileDraft
import dev.lain.os.planner.PlannerProfileSummary
import dev.lain.os.planner.PlannerSettingsManager
import dev.lain.os.ui.WorkbenchState
import dev.lain.os.ui.WorkbenchViewModel
import dev.lain.os.voice.MicrophoneFailure
import dev.lain.os.voice.MicrophoneState
import dev.lain.os.voice.MicrophoneStatus
import dev.lain.os.voice.LocalSynthesisFailure
import dev.lain.os.voice.SpeechAdapterFailure
import dev.lain.os.voice.SpeechInputState
import dev.lain.os.voice.SpeechInputStatus
import dev.lain.os.voice.SpeechOutputViewModel
import dev.lain.os.voice.TrustedProgressSnapshot
import dev.lain.os.voice.VoiceCaptureViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding
    private val model: WorkbenchViewModel by viewModels()
    private val voice: VoiceCaptureViewModel by viewModels()
    private val speechOutput: SpeechOutputViewModel by viewModels()
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            voice.onPermissionResult(it)
        }
    private var lastHistory = ""
    private var lastResults: String? = null
    private lateinit var plannerSettings: PlannerSettingsManager
    private var plannerProfiles = emptyList<PlannerProfileSummary>()
    private var editingPlannerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityMainBinding.inflate(layoutInflater)
        setContentView(ui.root)
        ViewCompat.setOnApplyWindowInsetsListener(ui.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ui.runButton.setOnClickListener { model.run(ui.commandInput.text.toString()) }
        ui.stopButton.setOnClickListener { model.stop() }
        ui.approveButton.setOnClickListener { model.approve() }
        ui.resumeButton.setOnClickListener { model.resume() }
        ui.reconnectButton.setOnClickListener { model.reconnect() }
        ui.voiceRecordButton.setOnClickListener {
            if (voice.state.value?.status == MicrophoneStatus.RECORDING) {
                voice.stop()
            } else {
                // Barge-in is talk-back cancellation only. Stop app-owned TTS
                // before the microphone can begin capturing to avoid self-echo.
                speechOutput.stopTalking()
                if (!voice.start()) {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        plannerSettings = PlannerSettingsManager(this)
        setupPlannerSettings()
        val demos = listOf("Create demo file", "Show battery", "Show demo toast", "Vibrate briefly", "Copy demo text", "Share demo text")
        demos.forEach { command ->
            val button = Button(this).apply {
                text = command
                isAllCaps = false
                setOnClickListener { ui.commandInput.setText(command); ui.commandInput.setSelection(command.length) }
            }
            ui.demoCommands.addView(button)
        }
        model.state.observe(this) { render(it) }
        voice.state.observe(this) { renderVoice(it) }
        voice.speechState.observe(this) { renderSpeechInput(it) }
    }

    override fun onStart() { super.onStart(); model.attach() }
    override fun onResume() {
        super.onResume()
        voice.reconcilePermission()
    }
    override fun onStop() {
        voice.onActivityStop(isChangingConfigurations)
        model.detach(isChangingConfigurations)
        super.onStop()
    }

    private fun render(state: WorkbenchState) {
        val session = state.session
        val active = session?.optBoolean("active") == true
        val recovery = session?.optBoolean("recovery_required") == true
        ui.connection.text = getString(when {
            state.ready -> R.string.connected
            state.connected -> R.string.starting
            else -> R.string.disconnected
        })
        ui.reconnectButton.visibility = if (!state.connected || state.startupFailed) View.VISIBLE else View.GONE
        ui.message.text = state.message
        ui.runButton.isEnabled = state.ready && !state.pending && !recovery
        updateVoiceRecordEnabled()
        speakProgress(session)
        speakResponse(session)
        ui.stopButton.isEnabled = state.connected && (active || recovery)
        ui.commandInput.isEnabled = !state.pending && !recovery
        for (i in 0 until ui.demoCommands.childCount) ui.demoCommands.getChildAt(i).isEnabled = !active && !state.pending
        ui.taskTitle.text = session?.optString("label") ?: getString(R.string.no_task)
        val status = session?.optString("status") ?: "idle"
        ui.taskStatus.text = if (session?.optBoolean("stop_requested") == true)
            getString(R.string.stopping) else status.replace('_', ' ').uppercase()
        ui.progress.visibility = if (active && status != "paused_confirmation" && !recovery) View.VISIBLE else View.GONE
        ui.resumeButton.visibility = if (recovery && status != "paused_confirmation") View.VISIBLE else View.GONE
        ui.resumeButton.isEnabled = state.ready && !state.pending
        val approval = session?.optJSONObject("approval")
        ui.approvalCard.visibility = if (approval != null) View.VISIBLE else View.GONE
        ui.approveButton.isEnabled = state.ready && !state.pending
        ui.approvalText.text = approval?.let {
            "${it.optString("type")}\n${it.optJSONObject("arguments")?.toString(2)}\n\n${getString(R.string.approval_explanation)}"
        } ?: ""
        val actions = session?.optJSONArray("actions")
        val resultsKey = session?.optString("session_id") + actions?.toString()
        if (resultsKey != lastResults) {
            lastResults = resultsKey
            ui.results.removeAllViews()
            if (actions == null || actions.length() == 0) {
                ui.results.addView(resultText(getString(R.string.no_results)))
            } else for (i in 0 until actions.length()) {
                val action = actions.getJSONObject(i)
                val detail = action.optJSONObject("details")
                val text = "${action.optString("type")}\n" +
                    "Execution: ${action.optString("status")}  ·  Verification: ${action.optString("verification")}" +
                    if (detail != null && detail.length() > 0) "\n${detail.toString(2)}" else ""
                ui.results.addView(resultText(text))
            }
        }
        val historyKey = state.history.toString() + active
        if (historyKey != lastHistory) {
            lastHistory = historyKey
            ui.history.removeAllViews()
            if (state.history.length() == 0) ui.history.addView(resultText(getString(R.string.no_history)))
            for (i in 0 until state.history.length()) {
                val entry = state.history.getJSONObject(i)
                ui.history.addView(Button(this).apply {
                    text = "${entry.optString("label")}\n${entry.optString("status").replace('_', ' ')}"
                    isAllCaps = false
                    isEnabled = !active
                    setOnClickListener { model.select(entry.getString("session_id")) }
                })
            }
        }
    }

    private fun renderVoice(state: MicrophoneState) {
        val recording = state.status == MicrophoneStatus.RECORDING
        ui.voiceRecordButton.text = getString(
            if (recording) R.string.voice_stop else R.string.voice_record
        )
        ui.voiceStatus.text = when (state.status) {
            MicrophoneStatus.IDLE -> state.lastCaptureDurationMs?.let {
                getString(R.string.voice_captured, it)
            } ?: getString(R.string.voice_idle)
            MicrophoneStatus.PERMISSION_REQUIRED -> getString(R.string.voice_permission_required)
            MicrophoneStatus.RECORDING -> getString(R.string.voice_recording)
            MicrophoneStatus.FAILED -> getString(
                when (state.failure) {
                    MicrophoneFailure.PERMISSION_DENIED -> R.string.voice_permission_denied
                    MicrophoneFailure.PERMISSION_REVOKED -> R.string.voice_permission_revoked
                    MicrophoneFailure.RESOURCE_LIMIT -> R.string.voice_resource_limit
                    else -> R.string.voice_capture_failed
                }
            )
        }
        updateVoiceRecordEnabled()
    }

    private fun renderSpeechInput(state: SpeechInputState) {
        ui.voiceStatus.text = when (state.status) {
            SpeechInputStatus.IDLE -> {
                val microphone = voice.state.value ?: MicrophoneState()
                when (microphone.status) {
                    MicrophoneStatus.IDLE -> microphone.lastCaptureDurationMs?.let {
                        getString(R.string.voice_captured, it)
                    } ?: getString(R.string.voice_idle)
                    MicrophoneStatus.PERMISSION_REQUIRED -> getString(R.string.voice_permission_required)
                    MicrophoneStatus.RECORDING -> getString(R.string.voice_recording)
                    MicrophoneStatus.FAILED -> getString(R.string.voice_capture_failed)
                }
            }
            SpeechInputStatus.TRANSCRIBING -> getString(R.string.voice_transcribing)
            SpeechInputStatus.FAILED -> getString(
                if (state.result?.failure == SpeechAdapterFailure.PROVIDER_UNAVAILABLE) {
                    R.string.voice_transcription_unavailable
                } else {
                    R.string.voice_transcription_failed
                }
            )
            SpeechInputStatus.READY -> getString(R.string.voice_transcription_ready)
        }
        updateVoiceRecordEnabled()
        if (state.status == SpeechInputStatus.READY) {
            val text = state.result?.text
            if (text != null && model.submitSpeech(text)) voice.consumeFinalTranscript()
        }
    }

    private fun speakProgress(session: org.json.JSONObject?) {
        if (session == null) return
        speechOutput.narrateProgress(
            TrustedProgressSnapshot(
                sessionId = session.optString("session_id", ""),
                revision = session.optString("revision", ""),
                status = session.optString("status", ""),
                active = session.optBoolean("active"),
                stopRequested = session.optBoolean("stop_requested"),
                recoveryRequired = session.optBoolean("recovery_required"),
            )
        )
    }

    private fun speakResponse(session: org.json.JSONObject?) {
        if (session == null) return
        val text = session.optString("speech_text", "").trim()
        val sessionId = session.optString("session_id", "")
        val revision = session.optString("revision", "")
        if (text.isEmpty() || sessionId.isEmpty() || revision.isEmpty()) return
        speechOutput.speakOnce("$sessionId:$revision", text) { failure ->
            if (failure == null) return@speakOnce
            runOnUiThread {
                ui.voiceStatus.text = getString(
                    if (failure == LocalSynthesisFailure.PROVIDER_UNAVAILABLE) {
                        R.string.voice_talkback_unavailable
                    } else {
                        R.string.voice_talkback_failed
                    }
                )
            }
        }
    }

    private fun updateVoiceRecordEnabled() {
        val runtime = model.state.value
        val session = runtime?.session
        val active = session?.optBoolean("active") == true
        val recovery = session?.optBoolean("recovery_required") == true
        val recording = voice.state.value?.status == MicrophoneStatus.RECORDING
        val transcribing = voice.speechState.value?.status == SpeechInputStatus.TRANSCRIBING
        ui.voiceRecordButton.isEnabled = recording ||
            (runtime?.ready == true && !runtime.pending && !recovery && !transcribing)
    }

    private fun resultText(value: String) = TextView(this).apply {
        text = value
        setTextColor(getColor(R.color.text_secondary))
        setTextIsSelectable(true)
        setPadding(0, 12, 0, 20)
        textSize = 13f
    }

    private fun setupPlannerSettings() {
        ui.plannerMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Cloud", "Local"),
        )
        ui.plannerResponseMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("JSON schema", "JSON object"),
        )
        ui.plannerProfiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                plannerProfiles.getOrNull(position)?.let(::showPlannerEditor)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        ui.plannerNew.setOnClickListener { showNewPlannerEditor() }
        ui.plannerSave.setOnClickListener { savePlanner() }
        ui.plannerSelect.setOnClickListener { selectPlanner() }
        ui.plannerRemoveCredential.setOnClickListener { removePlannerCredential() }
        ui.plannerDelete.setOnClickListener { deletePlanner() }
        ui.plannerTest.setOnClickListener { testPlannerConnection() }
        refreshPlannerSettings()
    }

    private fun refreshPlannerSettings(preferredId: String? = null) {
        try {
            val snapshot = plannerSettings.snapshot()
            plannerProfiles = snapshot.profiles
            ui.plannerProfiles.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                plannerProfiles.map { it.name },
            )
            val active = plannerProfiles.first { it.id == snapshot.activeProfileId }
            ui.activePlanner.text = getString(R.string.active_planner, active.name, active.model)
            val selectedId = preferredId ?: snapshot.activeProfileId
            val index = plannerProfiles.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            ui.plannerProfiles.setSelection(index)
            showPlannerEditor(plannerProfiles[index])
        } catch (exc: Exception) {
            showPlannerError(exc)
        }
    }

    private fun showPlannerEditor(profile: PlannerProfileSummary) {
        val editable = profile.id != PlannerProfile.DEMO_ID
        editingPlannerId = profile.id.takeIf { editable }
        ui.plannerName.setText(profile.name)
        ui.plannerMode.setSelection(if (profile.mode == "local") 1 else 0)
        ui.plannerEndpoint.setText(profile.baseUrl)
        ui.plannerModel.setText(profile.model)
        ui.plannerTimeout.setText(profile.timeoutSeconds.toString())
        ui.plannerMaxResponse.setText(profile.maxResponseBytes.toString())
        ui.plannerResponseMode.setSelection(if (profile.responseMode == "json_object") 1 else 0)
        ui.plannerCredential.text.clear()
        ui.plannerCredentialState.text = getString(
            when {
                !editable -> R.string.planner_credential_not_used
                profile.credentialSaved -> R.string.planner_credential_saved
                else -> R.string.planner_credential_missing
            }
        )
        listOf(
            ui.plannerName,
            ui.plannerMode,
            ui.plannerEndpoint,
            ui.plannerModel,
            ui.plannerTimeout,
            ui.plannerMaxResponse,
            ui.plannerResponseMode,
            ui.plannerCredential,
            ui.plannerSave,
            ui.plannerRemoveCredential,
            ui.plannerDelete,
        ).forEach { it.isEnabled = editable }
        ui.plannerSelect.isEnabled = true
        ui.plannerTest.isEnabled = true
    }

    private fun showNewPlannerEditor() {
        editingPlannerId = null
        ui.plannerName.text.clear()
        ui.plannerMode.setSelection(0)
        ui.plannerEndpoint.text.clear()
        ui.plannerModel.text.clear()
        ui.plannerTimeout.setText("30")
        ui.plannerMaxResponse.setText("1048576")
        ui.plannerResponseMode.setSelection(0)
        ui.plannerCredential.text.clear()
        ui.plannerCredentialState.text = getString(R.string.planner_credential_missing)
        listOf(
            ui.plannerName,
            ui.plannerMode,
            ui.plannerEndpoint,
            ui.plannerModel,
            ui.plannerTimeout,
            ui.plannerMaxResponse,
            ui.plannerResponseMode,
            ui.plannerCredential,
            ui.plannerSave,
        ).forEach { it.isEnabled = true }
        ui.plannerSelect.isEnabled = false
        ui.plannerRemoveCredential.isEnabled = false
        ui.plannerDelete.isEnabled = false
        ui.plannerTest.isEnabled = false
        ui.plannerName.requestFocus()
    }

    private fun savePlanner() {
        val credential = ui.plannerCredential.text.toString().takeIf { it.isNotBlank() }
        try {
            val profileId = plannerSettings.save(
                PlannerProfileDraft(
                    profileId = editingPlannerId,
                    name = ui.plannerName.text.toString(),
                    mode = if (ui.plannerMode.selectedItemPosition == 1) "local" else "cloud",
                    baseUrl = ui.plannerEndpoint.text.toString(),
                    model = ui.plannerModel.text.toString(),
                    timeoutSeconds = ui.plannerTimeout.text.toString().toDouble(),
                    maxResponseBytes = ui.plannerMaxResponse.text.toString().toInt(),
                    responseMode = if (ui.plannerResponseMode.selectedItemPosition == 1) {
                        "json_object"
                    } else {
                        "json_schema"
                    },
                    credential = credential,
                )
            )
            plannerSettings.select(profileId)
            ui.plannerStatus.text = getString(R.string.planner_saved)
            refreshPlannerSettings(profileId)
        } catch (exc: Exception) {
            showPlannerError(exc)
        } finally {
            ui.plannerCredential.text.clear()
        }
    }

    private fun selectedPlanner(): PlannerProfileSummary? =
        plannerProfiles.getOrNull(ui.plannerProfiles.selectedItemPosition)

    private fun selectPlanner() {
        val profile = selectedPlanner() ?: return
        try {
            plannerSettings.select(profile.id)
            ui.plannerStatus.text = getString(R.string.planner_selected)
            refreshPlannerSettings(profile.id)
        } catch (exc: Exception) {
            showPlannerError(exc)
        }
    }

    private fun removePlannerCredential() {
        val profile = selectedPlanner() ?: return
        try {
            plannerSettings.removeCredential(profile.id)
            ui.plannerStatus.text = getString(R.string.planner_removed)
            refreshPlannerSettings(profile.id)
        } catch (exc: Exception) {
            showPlannerError(exc)
        }
    }

    private fun deletePlanner() {
        val profile = selectedPlanner() ?: return
        try {
            if (plannerSettings.delete(profile.id)) {
                ui.plannerStatus.text = getString(R.string.planner_deleted)
            }
            refreshPlannerSettings()
        } catch (exc: Exception) {
            showPlannerError(exc)
        }
    }

    private fun testPlannerConnection() {
        val profile = selectedPlanner() ?: return
        ui.plannerTest.isEnabled = false
        ui.plannerStatus.text = getString(R.string.planner_testing)
        Thread({
            val result = try {
                plannerSettings.testConnection(profile.id)
            } catch (_: Exception) {
                PlannerConnectionStatus.UNAVAILABLE
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                ui.plannerStatus.text = result.name.lowercase().replace('_', ' ')
                ui.plannerTest.isEnabled = true
            }
        }, "lain-planner-diagnostic").start()
    }

    private fun showPlannerError(exc: Exception) {
        val detail = exc.message?.takeIf { it.isNotBlank() } ?: exc.javaClass.simpleName
        ui.plannerStatus.text = getString(R.string.planner_error, detail)
    }
}
