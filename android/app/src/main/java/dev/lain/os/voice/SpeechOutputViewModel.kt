package dev.lain.os.voice

import android.app.Application
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

    fun speakOnce(
        responseKey: String,
        text: String,
        onResult: (LocalSynthesisFailure?) -> Unit = {},
    ) {
        if (responseKey.isBlank() || responseKey == lastResponseKey) return
        lastResponseKey = responseKey
        controller.speak(text, onResult)
    }

    fun stop() {
        controller.stop()
    }

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }
}
