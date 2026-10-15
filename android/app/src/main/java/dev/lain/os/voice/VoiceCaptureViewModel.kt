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
import java.util.concurrent.atomic.AtomicLong

enum class SpeechInputStatus { IDLE, TRANSCRIBING, READY, FAILED }

data class SpeechInputState(
    val status: SpeechInputStatus = SpeechInputStatus.IDLE,
    val result: SpeechTranscriptionResult? = null,
    val finalId: Long = 0L,
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

        @Volatile
        var liveSpeechFactory: (Application) -> LiveSpeechInput = {
            AndroidLiveSpeechInput(it)
        }
    }

    private val mutable = MutableLiveData(MicrophoneState())
    val state: LiveData<MicrophoneState> = mutable
    private val speechMutable = MutableLiveData(SpeechInputState())
    private val nextFinalId = AtomicLong(0L)
    val speechState: LiveData<SpeechInputState> = speechMutable
    private var lastPermissionGranted = permissionChecker(application)
    private val speech = OnDeviceSpeechAdapter(
        apiLevel = Build.VERSION.SDK_INT,
        backend = speechBackendFactory(application),
    )
    private val liveSpeech = liveSpeechFactory(application)
    private var liveActive = false
    private var liveGeneration = 0L
    private var liveStopRequested = false
    private var liveEarlyFinal: BackendTranscriptionResult? = null

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
                        finalId = if (result.ok) nextFinalId.incrementAndGet() else 0L,
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
        if (!granted) {
            controller.start(false)
            return false
        }
        if (liveSpeech.available) {
            startLive()
            return true
        }
        // Older devices have no API31 on-device live recognizer. Retain the
        // bounded PCM adapter for compatibility, but never select cloud STT.
        controller.start(true)
        return true
    }

    private fun startLive() {
        val token = ++liveGeneration
        liveStopRequested = false
        liveEarlyFinal = null
        liveActive = true
        publish(MicrophoneState(MicrophoneStatus.RECORDING))
        val started = try {
            liveSpeech.start { raw ->
                if (token == liveGeneration) {
                    if (!liveStopRequested && raw.failure == null && raw.isFinal) {
                        // SpeechRecognizer may stop on its own at a pause. A
                        // final callback alone is NOT user authorization.
                        liveEarlyFinal = raw
                    } else {
                        publishLiveFinal(raw)
                    }
                }
            }
        } catch (_: Exception) { false }
        if (!started && token == liveGeneration &&
            speechMutable.value?.status != SpeechInputStatus.FAILED) {
            liveActive = false
            publish(MicrophoneState())
            publishSpeech(SpeechInputState(
                status = SpeechInputStatus.FAILED,
                result = SpeechTranscriptionResult(
                    failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE,
                    diagnostic = "Android could not start the installed on-device speech service",
                ),
            ))
        }
    }

    private fun publishLiveFinal(raw: BackendTranscriptionResult) {
        liveActive = false
        liveEarlyFinal = null
        publish(MicrophoneState())
        val result = validateLiveSpeechFinal(raw)
        publishSpeech(SpeechInputState(
            status = if (result.ok) SpeechInputStatus.READY else SpeechInputStatus.FAILED,
            result = result,
            finalId = if (result.ok) nextFinalId.incrementAndGet() else 0L,
        ))
    }

    fun onPermissionResult(granted: Boolean) {
        lastPermissionGranted = granted
        if (granted && liveSpeech.available) {
            controller.stop()  // Clear pending permission state without AudioRecord.
            start()
        } else controller.onPermissionResult(granted)
    }

    fun stop() {
        if (liveActive) {
            liveStopRequested = true
            publish(MicrophoneState())
            publishSpeech(SpeechInputState(status = SpeechInputStatus.TRANSCRIBING))
            val early = liveEarlyFinal
            if (early != null) {
                publishLiveFinal(early)
                return
            }
            try { liveSpeech.stop() } catch (_: Exception) {
                liveActive = false
                publishSpeech(SpeechInputState(
                    status = SpeechInputStatus.FAILED,
                    result = SpeechTranscriptionResult(
                        failure = SpeechAdapterFailure.PROVIDER_FAILED,
                        diagnostic = "On-device speech service failed to finalize recording",
                    ),
                ))
            }
            return
        }
        controller.stop()
    }

    fun reconcilePermission() {
        val granted = permissionChecker(getApplication())
        if (lastPermissionGranted && !granted) {
            if (liveActive) {
                liveGeneration++
                liveActive = false
                liveEarlyFinal = null
                liveSpeech.cancel()
                publishSpeech(SpeechInputState(
                    status = SpeechInputStatus.FAILED,
                    result = SpeechTranscriptionResult(
                        failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE,
                        diagnostic = "Microphone permission was revoked",
                    ),
                ))
            }
            controller.permissionRevoked()
        } else {
            controller.reconcilePermission(granted)
        }
        lastPermissionGranted = granted
    }

    fun onActivityStop(changingConfigurations: Boolean) {
        if (!changingConfigurations && liveActive) {
            liveGeneration++
            liveActive = false
            liveEarlyFinal = null
            liveSpeech.cancel()
            publish(MicrophoneState())
            publishSpeech(SpeechInputState())
        }
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
        liveGeneration++
        liveSpeech.close()
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
        liveGeneration++
        liveSpeech.close()
        speech.close()
        controller.close()
        super.onCleared()
    }
}
