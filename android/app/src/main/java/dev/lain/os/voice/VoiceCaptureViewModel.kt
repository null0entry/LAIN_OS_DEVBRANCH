package dev.lain.os.voice

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class SpeechInputStatus { IDLE, TRANSCRIBING, READY, FAILED }

data class SpeechInputState(
    val status: SpeechInputStatus = SpeechInputStatus.IDLE,
    val result: SpeechTranscriptionResult? = null,
)

class VoiceCaptureViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        var engineFactory: (Application) -> AudioCaptureEngine = { AndroidAudioCaptureEngine() }

        @Volatile
        var permissionChecker: (Context) -> Boolean = { context ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }

        @Volatile
        var speechBackendFactory: (Application) -> OnDeviceSpeechBackend = {
            AndroidOnDeviceSpeechBackend(it)
        }
    }

    private val mutable = MutableLiveData(MicrophoneState())
    val state: LiveData<MicrophoneState> = mutable
    private val speechMutable = MutableLiveData(SpeechInputState())
    val speechState: LiveData<SpeechInputState> = speechMutable
    private var lastPermissionGranted = permissionChecker(application)
    private val speech = OnDeviceSpeechAdapter(
        apiLevel = Build.VERSION.SDK_INT,
        backend = speechBackendFactory(application),
    )

    private val controller = MicrophoneCaptureController(
        engine = engineFactory(application),
        onState = ::publish,
        onCaptured = { audio ->
            publishSpeech(SpeechInputState(status = SpeechInputStatus.TRANSCRIBING))
            speech.transcribe(audio) { result ->
                publishSpeech(
                    SpeechInputState(
                        status = if (result.ok) SpeechInputStatus.READY else SpeechInputStatus.FAILED,
                        result = result,
                    )
                )
            }
            // The local STT backend takes its bounded in-memory copy before returning.
            audio.bytes.fill(0)
        },
    )

    fun start(): Boolean {
        if (speechMutable.value?.status != SpeechInputStatus.TRANSCRIBING) {
            publishSpeech(SpeechInputState())
        }
        val granted = permissionChecker(getApplication())
        lastPermissionGranted = granted
        controller.start(granted)
        return granted
    }

    fun onPermissionResult(granted: Boolean) {
        lastPermissionGranted = granted
        controller.onPermissionResult(granted)
    }

    fun stop() {
        controller.stop()
    }

    fun reconcilePermission() {
        val granted = permissionChecker(getApplication())
        if (lastPermissionGranted && !granted) {
            controller.permissionRevoked()
        } else {
            controller.reconcilePermission(granted)
        }
        lastPermissionGranted = granted
    }

    fun onActivityStop(changingConfigurations: Boolean) {
        controller.onActivityStop(changingConfigurations)
        if (!changingConfigurations && speechMutable.value?.status == SpeechInputStatus.TRANSCRIBING) {
            speech.cancelTranscription()
        }
    }

    fun consumeFinalTranscript() {
        if (speechMutable.value?.status == SpeechInputStatus.READY) {
            publishSpeech(SpeechInputState())
        }
    }

    internal fun closeForTest() {
        speech.close()
        controller.close()
    }

    private fun publish(next: MicrophoneState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            mutable.value = next
        } else {
            mutable.postValue(next)
        }
    }

    private fun publishSpeech(next: SpeechInputState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            speechMutable.value = next
        } else {
            speechMutable.postValue(next)
        }
    }

    override fun onCleared() {
        speech.close()
        controller.close()
        super.onCleared()
    }
}
