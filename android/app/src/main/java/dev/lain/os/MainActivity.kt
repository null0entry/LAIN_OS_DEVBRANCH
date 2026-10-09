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
import dev.lain.os.planner.OnDevicePlannerSaveState
import dev.lain.os.planner.OnDevicePlannerSaveViewModel
import dev.lain.os.ui.WorkbenchState
import dev.lain.os.ui.WorkbenchViewModel
import dev.lain.os.voice.MicrophoneFailure
import dev.lain.os.voice.MicrophoneState
import dev.lain.os.voice.MicrophoneStatus
import dev.lain.os.voice.LocalSpeechVoice
import dev.lain.os.voice.LocalSynthesisFailure
import dev.lain.os.voice.SpeechAdapterFailure
import dev.lain.os.voice.SpeechInputState
import dev.lain.os.voice.SpeechInputStatus
import dev.lain.os.voice.SpeechOutputViewModel
import dev.lain.os.voice.SpeechVoiceSelectionState
import dev.lain.os.voice.TrustedProgressSnapshot
import dev.lain.os.voice.VoiceCaptureViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var ui: ActivityMainBinding
    private val model: WorkbenchViewModel by viewModels()
    private val voice: VoiceCaptureViewModel by viewModels()
    private val speechOutput: SpeechOutputViewModel by viewModels()
    private val onDevicePlannerSave: OnDevicePlannerSaveViewModel by viewModels()
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            voice.onPermissionResult(it)
        }
    private val ggufPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null || onDevicePlannerSave.isSaving) return@registerForActivityResult
            val editorRevision = plannerEditorRevision
            ui.plannerStatus.text = getString(R.string.planner_importing_model)
            Thread({
                try {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("GGUF document unavailable")
                    val model = plannerSettings.importOnDeviceModel(input)
                    runOnUiThread {
                        if (isDestroyed || plannerEditorRevision != editorRevision || onDevicePlannerSave.isSaving) {
                            return@runOnUiThread
                        }
                        ui.plannerMode.setSelection(2)
                        ui.plannerModel.setText(model.sha256)
                        ui.plannerStatus.text = getString(R.string.planner_imported_model, model.sha256.take(12))
                    }
                } catch (exc: Exception) {
                    runOnUiThread {
                        if (!isDestroyed && plannerEditorRevision == editorRevision && !onDevicePlannerSave.isSaving) {
                            showPlannerError(exc)
                        }
                    }
                }
            }, "lain-gguf-import").start()
        }

    private var lastHistory = ""
    private var lastTranscript = ""
    private var lastResults: String? = null
    private lateinit var plannerSettings: PlannerSettingsManager
    private var plannerProfiles = emptyList<PlannerProfileSummary>()
    private var editingPlannerId: String? = null
    private var plannerEditorRevision = 0L
    private var preservedDraftSelectionId: String? = null
    private var preservingPlannerDraft = false
    private var speechVoices = emptyList<LocalSpeechVoice>()
    private var speechVoiceOffset = 0
    private var renderingSpeechVoices = false

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
        setupSpeechVoiceSelection()
        plannerSettings = PlannerSettingsManager(this)
        setupPlannerSettings()
        onDevicePlannerSave.state.observe(this, ::renderOnDevicePlannerSave)
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
        renderTranscript(state, session)
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

    private fun renderTranscript(state: WorkbenchState, session: org.json.JSONObject?) {
        val conversation = state.conversation
        val turns = conversation?.optJSONArray("turns")
        val assistant = if (session == null || session.isNull("speech_text")) {
            ""
        } else {
            session.optString("speech_text", "").trim()
        }
        val partial = if (conversation == null || conversation.isNull("partial_text")) {
            ""
        } else {
            conversation.optString("partial_text", "").trim()
        }
        val key = listOf(
            conversation?.toString().orEmpty(),
            session?.optString("session_id", "").orEmpty(),
            session?.optString("revision", "").orEmpty(),
            assistant,
        ).joinToString("|")
        if (key == lastTranscript) return
        lastTranscript = key

        ui.conversationTranscript.removeAllViews()
        var rendered = 0
        if (turns != null) {
            for (index in 0 until turns.length()) {
                val turn = turns.optJSONObject(index) ?: continue
                val text = turn.optString("text", "").trim()
                val turnId = turn.optInt("turn_id", 0)
                val source = turn.optString("source", "typed")
                val kind = turn.optString("kind", "task")
                val target = if (turn.isNull("target_session_id")) {
                    ""
                } else {
                    turn.optString("target_session_id", "").trim()
                }
                if (turnId < 1 || text.isEmpty()) continue
                val label = buildString {
                    append("Turn ")
                    append(turnId)
                    append(" · You · ")
                    append(source)
                    if (kind == "revision") append(" · revision")
                    if (target.isNotEmpty()) {
                        append(" · task ")
                        append(target.take(8))
                    }
                }
                ui.conversationTranscript.addView(resultText("$label\n$text"))
                rendered += 1
            }
        }
        if (partial.isNotEmpty()) {
            ui.conversationTranscript.addView(
                resultText(getString(R.string.transcript_provisional, partial)).apply {
                    setTextColor(getColor(R.color.accent))
                }
            )
            rendered += 1
        }
        if (assistant.isNotEmpty()) {
            ui.conversationTranscript.addView(
                resultText(getString(R.string.transcript_assistant, assistant))
            )
            rendered += 1
        }
        if (rendered == 0) {
            ui.conversationTranscript.addView(resultText(getString(R.string.transcript_empty)))
        }

        ui.conversationTranscriptStatus.text = when {
            conversation == null -> getString(R.string.transcript_unavailable)
            (conversation.optInt("next_turn_id", 1) - 1) > (turns?.length() ?: 0) ->
                getString(R.string.transcript_latest, turns?.length() ?: 0)
            else -> getString(R.string.transcript_retention)
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
                when (state.result?.failure) {
                    SpeechAdapterFailure.PROVIDER_UNAVAILABLE ->
                        R.string.voice_transcription_unavailable
                    SpeechAdapterFailure.LOW_CONFIDENCE ->
                        R.string.voice_transcription_low_confidence
                    else -> R.string.voice_transcription_failed
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
        if (
            session == null ||
            voice.state.value?.status == MicrophoneStatus.RECORDING ||
            voice.speechState.value?.status == SpeechInputStatus.TRANSCRIBING
        ) return
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
                    when (failure) {
                        LocalSynthesisFailure.PROVIDER_UNAVAILABLE ->
                            R.string.voice_talkback_unavailable
                        LocalSynthesisFailure.VOICE_UNAVAILABLE ->
                            R.string.voice_talkback_voice_unavailable
                        else -> R.string.voice_talkback_failed
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

    private fun setupSpeechVoiceSelection() {
        ui.speechVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (renderingSpeechVoices) return
                speechVoices.getOrNull(position - speechVoiceOffset)?.let { selected ->
                    speechOutput.selectVoice(selected.engineId, selected.id)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        speechOutput.voiceSelection.observe(this) { renderSpeechVoiceSelection(it) }
    }

    private fun renderSpeechVoiceSelection(state: SpeechVoiceSelectionState) {
        speechVoices = state.voices
        speechVoiceOffset = if (state.staleSelection == null) 0 else 1
        val labels = buildList {
            state.staleSelection?.let { add(getString(R.string.speech_voice_missing, it.voiceId)) }
            addAll(state.voices.map { "${it.id} · ${it.engineId}" })
        }
        renderingSpeechVoices = true
        ui.speechVoice.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
        val selectedIndex = state.selectedVoice?.let { selected ->
            state.voices.indexOfFirst {
                it.id == selected.voiceId && it.engineId == selected.engineId
            }.takeIf { it >= 0 }
        }
        val displayIndex = if (state.staleSelection != null) 0 else (selectedIndex ?: 0)
        ui.speechVoice.setSelection(displayIndex, false)
        renderingSpeechVoices = false
        ui.speechVoice.isEnabled = !state.loading && state.voices.isNotEmpty()
        ui.speechVoiceStatus.text = getString(
            when {
                state.loading -> R.string.speech_voice_loading
                state.staleSelection != null -> R.string.speech_voice_stale
                state.voices.isEmpty() -> R.string.speech_voice_none
                else -> R.string.speech_voice_available
            }
        )
    }

    private fun setupPlannerSettings() {
        ui.plannerMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Cloud", "Local", "On-device Model"),
        )
        ui.plannerResponseMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("JSON schema", "JSON object"),
        )
        ui.plannerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) =
                updatePlannerModeFields()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        ui.plannerImportModel.setOnClickListener {
            if (!onDevicePlannerSave.isSaving) {
                ggufPicker.launch(arrayOf("application/octet-stream", "application/x-gguf", "*/*"))
            }
        }
        ui.plannerProfiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (onDevicePlannerSave.isSaving || position != ui.plannerProfiles.selectedItemPosition) return
                val profile = plannerProfiles.getOrNull(position) ?: return
                // Restoring a retained draft changes Spinner selection asynchronously.
                // Its callback must not replace unsaved fields with stored profile fields.
                if (preservingPlannerDraft && profile.id == preservedDraftSelectionId) return
                showPlannerEditor(profile)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        ui.plannerNew.setOnClickListener { if (!onDevicePlannerSave.isSaving) showNewPlannerEditor() }
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
        preservingPlannerDraft = false
        preservedDraftSelectionId = null
        plannerEditorRevision += 1
        ui.plannerProfiles.isEnabled = true
        ui.plannerNew.isEnabled = true
        val editable = profile.id != PlannerProfile.DEMO_ID
        editingPlannerId = profile.id.takeIf { editable }
        ui.plannerName.setText(profile.name)
        ui.plannerMode.setSelection(when (profile.mode) { "local" -> 1; "on_device" -> 2; else -> 0 })
        ui.plannerEndpoint.setText(profile.baseUrl)
        ui.plannerModel.setText(profile.model)
        ui.plannerTimeout.setText(profile.timeoutSeconds.toString())
        ui.plannerMaxResponse.setText(profile.maxResponseBytes.toString())
        ui.plannerResponseMode.setSelection(if (profile.responseMode == "json_object") 1 else 0)
        ui.plannerCredential.text.clear()
        ui.plannerCredentialState.text = getString(
            when {
                !editable -> R.string.planner_credential_not_used
                profile.mode == "on_device" -> R.string.planner_offline_credential
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
        updatePlannerModeFields()
    }

    private fun showNewPlannerEditor() {
        preservingPlannerDraft = false
        preservedDraftSelectionId = null
        plannerEditorRevision += 1
        ui.plannerProfiles.isEnabled = true
        ui.plannerNew.isEnabled = true
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
        updatePlannerModeFields()
    }

    private fun savePlanner() {
        if (onDevicePlannerSave.isSaving) return
        val offline = ui.plannerMode.selectedItemPosition == 2
        val credential = ui.plannerCredential.text.toString().takeIf { !offline && it.isNotBlank() }
        try {
            val draft = PlannerProfileDraft(
                profileId = editingPlannerId,
                name = ui.plannerName.text.toString(),
                mode = when (ui.plannerMode.selectedItemPosition) { 1 -> "local"; 2 -> "on_device"; else -> "cloud" },
                baseUrl = if (offline) "" else ui.plannerEndpoint.text.toString(),
                model = ui.plannerModel.text.toString(),
                timeoutSeconds = if (offline) 120.0 else ui.plannerTimeout.text.toString().toDouble(),
                maxResponseBytes = if (offline) 65536 else ui.plannerMaxResponse.text.toString().toInt(),
                responseMode = if (ui.plannerResponseMode.selectedItemPosition == 1) {
                    "json_object"
                } else {
                    "json_schema"
                },
                credential = credential,
            )
            if (offline) {
                plannerEditorRevision += 1
                onDevicePlannerSave.save(draft)
                return
            }
            val profileId = plannerSettings.save(draft)
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

    private fun renderOnDevicePlannerSave(state: OnDevicePlannerSaveState) {
        when (state) {
            OnDevicePlannerSaveState.Idle -> Unit
            is OnDevicePlannerSaveState.Saving -> {
                showOnDeviceSaveDraft(state.draft, true)
                ui.plannerStatus.text = getString(R.string.planner_verifying_save)
            }
            is OnDevicePlannerSaveState.Saved -> {
                refreshPlannerSettings(state.profileId)
                ui.plannerStatus.text = getString(R.string.planner_saved)
                onDevicePlannerSave.consumeResult()
            }
            is OnDevicePlannerSaveState.Failed -> {
                showOnDeviceSaveDraft(state.draft, false)
                ui.plannerStatus.text = getString(R.string.planner_error, state.message)
                onDevicePlannerSave.consumeResult()
            }
        }
    }

    private fun showOnDeviceSaveDraft(draft: PlannerProfileDraft, busy: Boolean) {
        editingPlannerId = draft.profileId
        preservingPlannerDraft = true
        val profileIndex = plannerProfiles.indexOfFirst { it.id == draft.profileId }
        preservedDraftSelectionId = if (profileIndex >= 0) draft.profileId else selectedPlanner()?.id
        if (profileIndex >= 0) ui.plannerProfiles.setSelection(profileIndex)
        ui.plannerName.setText(draft.name)
        ui.plannerMode.setSelection(2)
        ui.plannerEndpoint.text.clear()
        ui.plannerModel.setText(draft.model)
        ui.plannerTimeout.setText(draft.timeoutSeconds.toString())
        ui.plannerMaxResponse.setText(draft.maxResponseBytes.toString())
        ui.plannerCredential.text.clear()
        listOf(
            ui.plannerName, ui.plannerMode, ui.plannerEndpoint, ui.plannerModel,
            ui.plannerTimeout, ui.plannerMaxResponse, ui.plannerResponseMode,
            ui.plannerCredential, ui.plannerSave, ui.plannerNew, ui.plannerProfiles,
        ).forEach { it.isEnabled = !busy }
        val existing = draft.profileId != null
        ui.plannerSelect.isEnabled = !busy && existing
        ui.plannerDelete.isEnabled = !busy && existing
        ui.plannerTest.isEnabled = !busy && existing
        ui.plannerRemoveCredential.isEnabled = false
        updatePlannerModeFields()
    }

    private fun selectPlanner() {
        if (onDevicePlannerSave.isSaving) return
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
        if (onDevicePlannerSave.isSaving) return
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
        if (onDevicePlannerSave.isSaving) return
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
        if (onDevicePlannerSave.isSaving) return
        val profile = selectedPlanner() ?: return
        val editorRevision = plannerEditorRevision
        ui.plannerTest.isEnabled = false
        ui.plannerStatus.text = getString(R.string.planner_testing)
        Thread({
            val result = try {
                plannerSettings.testConnection(profile.id)
            } catch (_: Exception) {
                PlannerConnectionStatus.UNAVAILABLE
            }
            runOnUiThread {
                if (isDestroyed || plannerEditorRevision != editorRevision || onDevicePlannerSave.isSaving) {
                    return@runOnUiThread
                }
                ui.plannerStatus.text = if (result == PlannerConnectionStatus.MODEL_READY) {
                    getString(R.string.planner_model_integrity_verified)
                } else result.name.lowercase().replace('_', ' ')
                ui.plannerTest.isEnabled = true
            }
        }, "lain-planner-diagnostic").start()
    }

    private fun updatePlannerModeFields() {
        val offline = ui.plannerMode.selectedItemPosition == 2
        val visibility = if (offline) View.GONE else View.VISIBLE
        ui.plannerEndpoint.visibility = visibility
        ui.plannerTimeout.visibility = visibility
        ui.plannerMaxResponse.visibility = visibility
        ui.plannerResponseMode.visibility = visibility
        ui.plannerCredential.visibility = visibility
        ui.plannerRemoveCredential.visibility = visibility
        ui.plannerImportModel.visibility = if (offline) View.VISIBLE else View.GONE
        ui.plannerImportModel.isEnabled = offline && ui.plannerName.isEnabled
        ui.plannerModel.isEnabled = !offline && ui.plannerName.isEnabled
        if (offline) ui.plannerCredentialState.text = getString(R.string.planner_offline_credential)
    }

    private fun showPlannerError(exc: Exception) {
        val detail = exc.message?.takeIf { it.isNotBlank() } ?: exc.javaClass.simpleName
        ui.plannerStatus.text = getString(R.string.planner_error, detail)
    }
}
