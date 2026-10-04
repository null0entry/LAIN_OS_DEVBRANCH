package dev.lain.os.voice

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

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
    }

    private val mutable = MutableLiveData(PlaybackState())
    val state: LiveData<PlaybackState> = mutable

    private val controller = SpeechPlaybackController(
        engine = engineFactory(application),
        focus = focusFactory(application),
        onState = ::publish,
    )

    fun play(audio: SynthesizedAudio) {
        controller.play(audio)
    }

    fun stopTalking() {
        controller.stopTalking()
    }

    fun onActivityStop(changingConfigurations: Boolean) {
        controller.onActivityStop(changingConfigurations)
    }

    private fun publish(next: PlaybackState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mutable.value = next
        } else {
            mutable.postValue(next)
        }
    }

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }
}
