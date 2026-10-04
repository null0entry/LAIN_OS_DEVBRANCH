package dev.lain.os.voice

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

data class SpeechSynthesisUiState(
    val active: Boolean = false,
    val failure: SpeechAdapterFailure? = null,
)

class SpeechPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        var engineFactory: (Application) -> SpeechPlaybackEngine = {
            AndroidPcmPlaybackEngine()
        }

        @Volatile
        var focusFactory: (Context) -> AudioFocusController = { context ->
            AndroidAudioFocusController(context)
        }

        @Volatile
        var speechBackendFactory: (Application) -> OnDeviceSpeechBackend = {
            AndroidOnDeviceSpeechBackend(it)
        }
    }

    private val mutable = MutableLiveData(PlaybackState())
    val state: LiveData<PlaybackState> = mutable
    private val synthesisMutable = MutableLiveData(SpeechSynthesisUiState())
    val synthesisState: LiveData<SpeechSynthesisUiState> = synthesisMutable
    private val speech = OnDeviceSpeechAdapter(
        apiLevel = android.os.Build.VERSION.SDK_INT,
        backend = speechBackendFactory(application),
    )

    private val controller = SpeechPlaybackController(
        engine = engineFactory(application),
        focus = focusFactory(application),
        onState = ::publish,
    )

    fun play(audio: SynthesizedAudio) {
        controller.play(audio)
    }

    fun speak(text: String) {
        publishSynthesis(SpeechSynthesisUiState(active = true))
        speech.synthesize(text, null) { result ->
            publishSynthesis(SpeechSynthesisUiState(active = false, failure = result.failure))
            val audio = result.audio
            if (result.ok && audio != null) {
                controller.play(audio)
            } else if (result.failure != SpeechAdapterFailure.CANCELLED) {
                publish(
                    PlaybackState(
                        status = PlaybackStatus.FAILED,
                        failure = if (result.failure == SpeechAdapterFailure.PROVIDER_UNAVAILABLE) {
                            PlaybackFailure.SYNTHESIS_UNAVAILABLE
                        } else {
                            PlaybackFailure.SYNTHESIS_FAILED
                        },
                    )
                )
            }
        }
    }

    fun stopTalking() {
        speech.cancelSynthesis()
        publishSynthesis(SpeechSynthesisUiState())
        controller.stopTalking()
    }

    fun onActivityStop(changingConfigurations: Boolean) {
        if (!changingConfigurations) {
            speech.cancelSynthesis()
            publishSynthesis(SpeechSynthesisUiState())
        }
        controller.onActivityStop(changingConfigurations)
    }

    private fun publish(next: PlaybackState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mutable.value = next
        } else {
            mutable.postValue(next)
        }
    }

    private fun publishSynthesis(next: SpeechSynthesisUiState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            synthesisMutable.value = next
        } else {
            synthesisMutable.postValue(next)
        }
    }

    override fun onCleared() {
        speech.close()
        controller.close()
        super.onCleared()
    }
}
