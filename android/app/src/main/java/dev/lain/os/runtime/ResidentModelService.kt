package dev.lain.os.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import dev.lain.os.MainActivity
import dev.lain.os.R
import dev.lain.os.planner.GgufNativeProbe
import dev.lain.os.planner.GgufNativeProbeResult
import dev.lain.os.planner.OnDeviceModelStore
import dev.lain.os.planner.OnDevicePlannerCall
import dev.lain.os.planner.PlannerProfileStore
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * A user-started foreground service, deliberately hosted in :runtime alongside
 * NativePlannerBridge. Chat and voice-driven task planning share one JNI model.
 * No external listener, Termux dependency, Internet endpoint or shell access.
 */
class ResidentModelService : Service() {
    companion object {
        const val ACTION_START = "dev.lain.os.model.START"
        const val ACTION_STOP = "dev.lain.os.model.STOP"
        const val DESCRIPTOR = "dev.lain.os.model.Resident.v1"
        const val TRANSACTION_CHAT = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_CANCEL = IBinder.FIRST_CALL_TRANSACTION + 1
        const val TRANSACTION_STATUS = IBinder.FIRST_CALL_TRANSACTION + 2
        private const val NOTIFICATION_ID = 4107
        private const val CHANNEL_ID = "lain-local-model"
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val currentChat = AtomicReference<OnDevicePlannerCall?>(null)
    private val models by lazy { OnDeviceModelStore(this) }
    private val profiles by lazy { PlannerProfileStore(this) }
    @Volatile private var state = "STOPPED"

    private fun failure(code: String) = JSONObject().put("ok", false).put("error", code).toString()

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("Private model process")
            if (reply == null) return false
            data.enforceInterface(DESCRIPTOR)
            val answer = try {
                when (code) {
                    TRANSACTION_CHAT -> {
                        val digest = data.readString() ?: ""
                        val prompt = data.readString() ?: ""
                        require(data.dataAvail() == 0)
                        require(Regex("[0-9a-f]{64}").matches(digest))
                        require(prompt.isNotBlank() && prompt.toByteArray(Charsets.UTF_8).size <= 16384)
                        val selected = profiles.active()
                        require(selected?.mode == "on_device" &&
                            selected.protocol == "gguf_native_v1" && selected.model == digest)
                        val call = OnDevicePlannerCall.chat(models, digest, prompt)
                        if (!currentChat.compareAndSet(null, call)) failure("PLANNER_MODEL_BUSY")
                        else try { call.execute() } finally { currentChat.compareAndSet(call, null) }
                    }
                    TRANSACTION_CANCEL -> {
                        require(data.dataAvail() == 0)
                        currentChat.get()?.cancel()
                        JSONObject().put("ok", true).toString()
                    }
                    TRANSACTION_STATUS -> {
                        require(data.dataAvail() == 0)
                        JSONObject().put("ok", true).put("state", state).toString()
                    }
                    else -> return false
                }
            } catch (_: Exception) { failure("PLANNER_REQUEST_INVALID") }
            reply.writeNoException()
            reply.writeString(answer.take(20_000))
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                state = "LOADING"
                // Foreground promotion precedes heavy I/O; started only by an explicit UI tap.
                ensureChannel()
                try {
                    startForeground(NOTIFICATION_ID, notice("Loading offline Qwen…"))
                } catch (_: Exception) {
                    state = "FAILED: foreground service unavailable"
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                worker.execute {
                    val result = try {
                        val selected = profiles.active()
                        if (selected?.mode != "on_device" || selected.protocol != "gguf_native_v1") {
                            "FAILED: select an on-device GGUF profile"
                        } else {
                            val verifiedFile = models.resolve(selected.model)
                            when (val outcome = GgufNativeProbe.warm(verifiedFile.absolutePath)) {
                                is GgufNativeProbeResult.Generated -> "READY"
                                is GgufNativeProbeResult.Rejected -> "FAILED: " + outcome.reason.name
                            }
                        }
                    } catch (_: Exception) { "FAILED: GGUF unavailable or changed" }
                    state = result
                    updateNotification(result)
                    if (result != "READY") {
                        stopForegroundCompat()
                        stopSelf(startId)
                    }
                }
            }
            ACTION_STOP -> {
                state = "STOPPING"
                GgufNativeProbe.cancelWarm()
                currentChat.get()?.cancel()
                worker.execute {
                    GgufNativeProbe.unload()
                    state = "STOPPED"
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
        // The user must deliberately restart after process death / low memory.
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Offline model", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun notice(message: String): Notification {
        val launch = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_lain).setContentTitle("LAIN · resident local model")
            .setContentText(message).setOngoing(true).setContentIntent(pending).build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notice(message))
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
    }

    override fun onDestroy() {
        GgufNativeProbe.cancelWarm()
        currentChat.get()?.cancel()
        // The native lease serializes release after any in-flight generation.
        try { worker.execute { GgufNativeProbe.unload() } } catch (_: Exception) { }
        worker.shutdown()
        super.onDestroy()
    }
}
