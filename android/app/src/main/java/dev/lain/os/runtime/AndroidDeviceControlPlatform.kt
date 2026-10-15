package dev.lain.os.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings

internal class AndroidDeviceControlPlatform(private val context: Context) : DeviceControlPlatform {
    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    override fun cameraPermission() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    override fun writeSettingsPermission() = Settings.System.canWrite(context)
    override fun fixedVolume() = audio.isVolumeFixed

    override fun readVolume(stream: String): VolumeReading? = try {
        val manager = audio
        val id = streamId(stream)
        VolumeReading(manager.getStreamVolume(id), if (Build.VERSION.SDK_INT >= 28) manager.getStreamMinVolume(id) else 0,
            manager.getStreamMaxVolume(id), manager.isStreamMute(id))
    } catch (_: Exception) { null }

    override fun setVolume(stream: String, index: Int) { audio.setStreamVolume(streamId(stream), index, 0) }
    override fun adjustVolume(stream: String, direction: String) {
        val adjustment = when (direction) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> throw IllegalArgumentException()
        }
        audio.adjustStreamVolume(streamId(stream), adjustment, 0)
    }
    override fun writeSetting(key: String, value: Int) = Settings.System.putInt(context.contentResolver, key, value)
    override fun readSetting(key: String): Int? = try { Settings.System.getInt(context.contentResolver, key) } catch (_: Exception) { null }
    override fun readBrightness(): BrightnessReading? {
        val value = readSetting(Settings.System.SCREEN_BRIGHTNESS) ?: return null
        val mode = readSetting(Settings.System.SCREEN_BRIGHTNESS_MODE) ?: return null
        if (value !in 0..255 || mode !in 0..1) return null
        return BrightnessReading(value, if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) "manual" else "automatic")
    }
    override fun navigate(page: String?, packageName: String?) = DeviceNavigation(context).offer(page, packageName)
    override fun launchableApps() = DeviceNavigation(context).launchableApps()

    override fun setTorch(enabled: Boolean): Boolean? {
        // Runtime calls run off-main. Never block the main looper waiting for callbacks.
        if (Looper.myLooper() == Looper.getMainLooper()) throw DeviceControlFailure("failure", "operation_rejected")
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw DeviceControlFailure("failure", "permission_denied")
        }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: throw DeviceControlFailure("unsupported", "hardware_unavailable")
        try {
            val camera = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: throw DeviceControlFailure("unsupported", "hardware_unavailable")
            val worker = HandlerThread("lain-torch-readback")
            worker.start()
            val handler = Handler(worker.looper)
            return TorchReadback().execute(object : TorchSession {
                private var callback: CameraManager.TorchCallback? = null
                override fun register(changed: (Boolean) -> Unit, unavailable: () -> Unit) {
                    val observer = object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            if (cameraId == camera) changed(enabled)
                        }
                        override fun onTorchModeUnavailable(cameraId: String) {
                            if (cameraId == camera) unavailable()
                        }
                    }
                    callback = observer
                    manager.registerTorchCallback(observer, handler)
                }
                override fun post(work: () -> Unit) = handler.post(work)
                override fun set(enabled: Boolean) = manager.setTorchMode(camera, enabled)
                override fun unregister() {
                    callback?.let { manager.unregisterTorchCallback(it) }
                }
                override fun close() { worker.quitSafely() }
            }, enabled)
        } catch (failure: CameraAccessException) {
            throw DeviceControlFailure("failure", when (failure.reason) {
                CameraAccessException.CAMERA_IN_USE, CameraAccessException.MAX_CAMERAS_IN_USE -> "camera_busy"
                CameraAccessException.CAMERA_DISABLED -> "policy_rejected"
                else -> "hardware_unavailable"
            })
        }
    }

    companion object {
        internal fun streamId(stream: String) = when (stream) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "system" -> AudioManager.STREAM_SYSTEM
            "voice_call" -> AudioManager.STREAM_VOICE_CALL
            else -> throw IllegalArgumentException()
        }
    }
}
