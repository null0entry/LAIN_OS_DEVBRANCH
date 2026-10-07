package dev.lain.os.voice

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

data class SpeechVoiceSelectionState(
    val loading: Boolean = true,
    val voices: List<LocalSpeechVoice> = emptyList(),
    val selectedVoice: StoredSpeechVoice? = null,
    val staleSelection: StoredSpeechVoice? = null,
)

/**
 * Owns only the platform TTS lifetime and duplicate suppression for one runtime
 * response. There is no synthesized-audio playback pipeline.
 */
class SpeechOutputViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        var backendFactory: (Application) -> LocalSpeechSynthesisBackend = {
            AndroidLocalSpeechSynthesisBackend(it)
        }
    }

    private val backend = backendFactory(application)
    private val controller = OnDeviceSpeechSynthesisController(backend)
    private val selectionStore = SpeechVoiceSelectionStore(application)
    private val _voiceSelection = MutableLiveData(SpeechVoiceSelectionState())
    val voiceSelection: LiveData<SpeechVoiceSelectionState> = _voiceSelection
    private var lastResponseKey: String? = null
    private val progressNarration = ProgressNarrationPolicy()

    init {
        refreshVoiceInventory()
    }

    fun refreshVoiceInventory() {
        updateVoiceSelection((_voiceSelection.value ?: SpeechVoiceSelectionState()).copy(loading = true))
        controller.availableVoices { voices ->
            val stored = selectionStore.load(backend.providerId)
            val selected = stored?.let { saved ->
                voices.firstOrNull { it.id == saved.voiceId && it.engineId == saved.engineId }
            }
            updateVoiceSelection(
                SpeechVoiceSelectionState(
                    loading = false,
                    voices = voices,
                    selectedVoice = selected?.let { StoredSpeechVoice(it.engineId, it.id) }
                        ?: if (stored == null) {
                            voices.firstOrNull()?.let { StoredSpeechVoice(it.engineId, it.id) }
                        } else {
                            null
                        },
                    staleSelection = stored?.takeIf { selected == null },
                )
            )
        }
    }

    fun selectVoice(engineId: String, voiceId: String): Boolean {
        val current = _voiceSelection.value ?: return false
        val selected = current.voices.firstOrNull { it.id == voiceId && it.engineId == engineId } ?: return false
        selectionStore.save(backend.providerId, selected)
        updateVoiceSelection(
            current.copy(
                selectedVoice = StoredSpeechVoice(selected.engineId, selected.id),
                staleSelection = null,
            )
        )
        return true
    }

    fun speakOnce(
        responseKey: String,
        text: String,
        onResult: (LocalSynthesisFailure?) -> Unit = {},
    ) {
        if (responseKey.isBlank() || responseKey == lastResponseKey) return
        lastResponseKey = responseKey
        controller.speak(text, voiceSelectionForSpeech(), onResult)
    }

    fun narrateProgress(snapshot: TrustedProgressSnapshot) {
        val text = progressNarration.next(snapshot, SystemClock.elapsedRealtime()) ?: return
        // Progress speech is best-effort presentation. Its result is deliberately
        // not fed back into runtime state or the authoritative visual status.
        controller.speak(text, voiceSelectionForSpeech())
    }

    /** Stop app-owned talk-back only; this never cancels runtime task state. */
    fun stopTalking() {
        controller.stop()
    }

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }

    private fun voiceSelectionForSpeech(): StoredSpeechVoice? {
        val state = _voiceSelection.value ?: return null
        return state.staleSelection ?: state.selectedVoice
    }

    private fun updateVoiceSelection(state: SpeechVoiceSelectionState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _voiceSelection.value = state
        } else {
            _voiceSelection.postValue(state)
        }
    }
}
