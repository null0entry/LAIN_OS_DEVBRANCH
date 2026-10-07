package dev.lain.os.voice

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel

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

    private val controller = OnDeviceSpeechSynthesisController(backendFactory(application))
    private var lastResponseKey: String? = null
    private val progressNarration = ProgressNarrationPolicy()

    fun speakOnce(
        responseKey: String,
        text: String,
        onResult: (LocalSynthesisFailure?) -> Unit = {},
    ) {
        if (responseKey.isBlank() || responseKey == lastResponseKey) return
        lastResponseKey = responseKey
        controller.speak(text, onResult)
    }

    fun narrateProgress(snapshot: TrustedProgressSnapshot) {
        val text = progressNarration.next(snapshot, SystemClock.elapsedRealtime()) ?: return
        // Progress speech is best-effort presentation. Its result is deliberately
        // not fed back into runtime state or the authoritative visual status.
        controller.speak(text)
    }

    /** Stop app-owned talk-back only; this never cancels runtime task state. */
    fun stopTalking() {
        controller.stop()
    }

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }
}
