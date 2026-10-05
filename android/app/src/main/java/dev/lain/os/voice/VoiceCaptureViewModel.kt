package dev.lain.os.voice

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class VoiceCaptureViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        var engineFactory: (Application) -> AudioCaptureEngine = { AndroidAudioCaptureEngine() }

        @Volatile
        var permissionChecker: (Context) -> Boolean = { context ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private val mutable = MutableLiveData(MicrophoneState())
    val state: LiveData<MicrophoneState> = mutable
    private var lastPermissionGranted = permissionChecker(application)

    private val controller = MicrophoneCaptureController(
        engine = engineFactory(application),
        onState = ::publish,
        onCaptured = { audio ->
            // TASK-010 proves a provider-neutral in-memory handoff and intentionally
            // does not persist raw audio. TASK-011/adapter work will consume it.
            audio.bytes.fill(0)
        },
    )

    fun start(): Boolean {
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
    }

    internal fun closeForTest() {
        controller.close()
    }

    private fun publish(next: MicrophoneState) {
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
