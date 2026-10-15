package dev.lain.os.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationManagerCompat
import dev.lain.os.MainActivity
import dev.lain.os.runtime.RuntimeClient
import org.json.JSONObject
import java.util.Locale

/**
 * Explicitly user-started, on-device-only recognition; no network recognizer,
 * raw audio retention, task approvals, reboot receiver or hidden microphone.
 * Live listening requires the visible notification and an active runtime bind.
 */
class ContinuousVoiceService : Service() {
    companion object {
        const val ACTION_START = "dev.lain.os.voice.START"
        const val ACTION_STOP = "dev.lain.os.voice.STOP"
        private const val CHANNEL = "lain-continuous-voice"
        private const val NOTIFICATION_ID = 2927
        @Volatile var leaseActive: Boolean = false
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private val gate = WakeCommandGate(clock = { android.os.SystemClock.elapsedRealtime() })
    private lateinit var runtime: RuntimeClient
    private var running = false
    private var connected = false
    private var foregroundReady = false
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0L
    private var finalId = 0L
    private var errorStreak = 0
    private var commandPending = false
    private var nextStart: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        runtime = RuntimeClient(this)
        runtime.onConnection = { available ->
            connected = available
            leaseActive = available && running && foregroundReady
            if (leaseActive) scheduleListen(0L)
            else if (running && !available) shutdown()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            shutdown()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        if (Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) ||
            !NotificationManagerCompat.from(this).areNotificationsEnabled() ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            shutdown()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "LAIN listening", NotificationManager.IMPORTANCE_LOW))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                shutdown()
                return START_NOT_STICKY
            }
            val stop = PendingIntent.getService(this, 1,
                Intent(this, ContinuousVoiceService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 2,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(dev.lain.os.R.drawable.ic_lain)
                .setContentTitle("LAIN is listening locally")
                .setContentText("Say LAIN, then a command • Tap Stop to end")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            shutdown()
            return START_NOT_STICKY
        }
        running = true
        foregroundReady = true
        gate.reset()
        runtime.connect()
        return START_NOT_STICKY
    }

    private fun scheduleListen(delay: Long) {
        nextStart?.let(main::removeCallbacks)
        if (!running || !connected || !foregroundReady || commandPending) return
        val task = Runnable { beginListen() }
        nextStart = task
        main.postDelayed(task, delay)
    }

    private fun beginListen() {
        if (!running || !connected || !foregroundReady || commandPending) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            shutdown()
            return
        }
        endRecognizer()
        val token = ++generation
        var completed = false
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onResults(results: Bundle?) {
                    if (completed || token != generation || !running) return
                    completed = true
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    val score = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        ?.firstOrNull()
                    endRecognizer()
                    errorStreak = 0
                    val decision = gate.accept(++finalId, phrase, score)
                    when (decision.kind) {
                        WakeKind.STOP_LISTENING -> shutdown()
                        WakeKind.STOP_TASK -> stopTask()
                        WakeKind.COMMAND -> dispatchCommand(decision.command)
                        else -> scheduleListen(300L)
                    }
                }
                override fun onError(error: Int) {
                    if (completed || token != generation || !running) return
                    completed = true
                    endRecognizer()
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_CLIENT) {
                        shutdown()
                        return
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        errorStreak = 0
                        scheduleListen(300L)
                    } else {
                        val delays = longArrayOf(500L, 1000L, 2000L, 4000L, 8000L, 15000L)
                        if (errorStreak >= delays.size) shutdown()
                        else scheduleListen(delays[errorStreak++])
                    }
                }
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            speech.startListening(request)
        } catch (_: Exception) {
            endRecognizer()
            shutdown()
        }
    }

    private fun dispatchCommand(command: String) {
        commandPending = true
        // Inspect first. Busy active tasks are not revised implicitly by ambient speech.
        runtime.request("sessions", JSONObject()) { reply ->
            if (!running) return@request
            val sessions = reply.optJSONArray("sessions")
            if (!reply.optBoolean("ok") || sessions == null) {
                commandPending = false
                scheduleListen(300L)
                return@request
            }
            val busy = (0 until sessions.length()).any { n ->
                sessions.optJSONObject(n)?.optString("status") !in
                    setOf("complete", "cancelled", "failed", "blocked", "budget_exhausted")
            }
            if (busy) {
                commandPending = false
                scheduleListen(300L)
                return@request
            }
            val payload = JSONObject().put("text", command).put("source", "speech")
                .put("kind", "auto").put("reference", "none")
                .put("target_session_id", JSONObject.NULL)
            runtime.request("turn_submit", payload) {
                // Submission is exactly once; unknown outcomes must be inspected,
                // NEVER replayed. Approvals are handled only in the owner UI.
                commandPending = false
                if (running) scheduleListen(300L)
            }
        }
    }

    private fun stopTask() {
        commandPending = true
        runtime.request("sessions", JSONObject()) { reply ->
            if (!running) return@request
            val sessions = reply.optJSONArray("sessions")
            var id: String? = null
            if (sessions != null) for (i in 0 until sessions.length()) {
                val session = sessions.optJSONObject(i) ?: continue
                if (session.optString("status") !in
                    setOf("complete", "cancelled", "failed", "blocked", "budget_exhausted")) {
                    id = session.optString("session_id").takeIf(String::isNotBlank)
                    break
                }
            }
            if (id == null) {
                commandPending = false
                scheduleListen(300L)
            } else runtime.request("stop", JSONObject().put("session_id", id)) {
                commandPending = false
                if (running) scheduleListen(300L)
            }
        }
    }

    private fun endRecognizer() {
        generation++
        val old = recognizer
        recognizer = null
        if (old != null) {
            try { old.cancel() } catch (_: Exception) { }
            try { old.destroy() } catch (_: Exception) { }
        }
    }

    private fun shutdown() {
        running = false
        foregroundReady = false
        leaseActive = false
        commandPending = false
        gate.reset()
        nextStart?.let(main::removeCallbacks)
        nextStart = null
        endRecognizer()
        if (::runtime.isInitialized) runtime.close()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
