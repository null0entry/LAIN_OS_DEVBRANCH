package dev.lain.os.runtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import dev.lain.os.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** A closed callback used only by the trusted embedded executor, never a planner. */
class NativeCapabilities(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun execute(name: String, argumentsJson: String): String {
        return try {
            if (argumentsJson.toByteArray(Charsets.UTF_8).size > 65536) return reply("failure").toString()
            val args = JSONObject(argumentsJson)
            when (name) {
                "android.notify" -> {
                    requireFields(args, setOf("title", "content"))
                    val title = text(args, "title")
                    val message = text(args, "content")
                    notify(title, message)
                }
                "android.open_uri" -> {
                    requireFields(args, setOf("uri"))
                    val uri = Uri.parse(text(args, "uri"))
                    require(uri.scheme?.lowercase() in setOf("http", "https", "geo", "mailto"))
                    onMain {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            reply("success")
                        } catch (_: ActivityNotFoundException) { reply("unsupported") }
                    }
                }
                "android.battery_status" -> {
                    requireFields(args, emptySet())
                    battery()
                }
                "android.vibrate" -> {
                    requireFields(args, setOf("duration_ms"))
                    val duration = args.get("duration_ms")
                    require(duration is Int && duration in 1..5000)
                    vibrate(duration.toLong())
                }
                "android.toast" -> {
                    val content = content(args, 1024)
                    onMain { Toast.makeText(context, content, Toast.LENGTH_SHORT).show(); reply("success") }
                }
                "android.clipboard_set" -> {
                    val content = content(args, 16384)
                    onMain {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("LAIN_OS", content))
                        val readback = try {
                            val clip = clipboard.primaryClip
                            if (clip == null || clip.itemCount != 1) "unavailable"
                            else if (clip.getItemAt(0).text?.toString() == content) "matched" else "mismatch"
                        } catch (_: Exception) { "unavailable" }
                        // No observed clipboard value crosses this private boundary.
                        reply("success", JSONObject().put("readback", readback))
                    }
                }
                "android.sms.compose" -> {
                    requireFields(args, setOf("recipient", "content"))
                    val rawRecipient = text(args, "recipient")
                    val recipient = rawRecipient.replace(Regex("[ ()-]"), "")
                    require(Regex("^\\+?[0-9]{3,15}$").matches(recipient))
                    val message = contentField(args, "content", 4096)
                    onMain {
                        val compose = Intent(Intent.ACTION_SENDTO,
                            android.net.Uri.fromParts("smsto", recipient, null)).apply {
                            putExtra("sms_body", message)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(compose)
                            reply("success", JSONObject().put("handoff", "composer_opened"))
                        } catch (_: android.content.ActivityNotFoundException) {
                            reply("unsupported")
                        }
                    }
                }
                "android.share_text" -> {
                    val content = content(args, 16384)
                    onMain {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, content)
                        }
                        val chooser = Intent.createChooser(send, "Share with…")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                        reply("success")
                    }
                }
                else -> reply("unsupported")
            }.toString()
        } catch (_: Exception) {
            // Exceptions and platform output may contain payloads. Do not log them.
            reply("failure").toString()
        }
    }

    private fun requireFields(args: JSONObject, fields: Set<String>) {
        require(args.keys().asSequence().toSet() == fields)
    }

    private fun content(args: JSONObject, maxBytes: Int): String {
        requireFields(args, setOf("content"))
        return contentField(args, "content", maxBytes)
    }

    private fun contentField(args: JSONObject, name: String, maxBytes: Int): String {
        val value = args.get(name)
        require(value is String && value.isNotEmpty() && !value.contains('\u0000'))
        require(value.toByteArray(Charsets.UTF_8).size <= maxBytes)
        return value
    }

    private fun text(args: JSONObject, name: String): String {
        val value = args.get(name)
        require(value is String && !value.contains('\u0000'))
        return value
    }

    private fun notify(title: String, content: String): JSONObject {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return reply("unsupported")
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return reply("unsupported")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "lain-device-actions"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channel, "Device actions", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(channel).importance == NotificationManager.IMPORTANCE_NONE)
                return reply("unsupported")
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel)
            else Notification.Builder(context)
        manager.notify(4108, builder.setSmallIcon(R.drawable.ic_lain)
            .setContentTitle(title).setContentText(content).setAutoCancel(true).build())
        return reply("success")
    }

    private fun battery(): JSONObject {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return reply("unsupported")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0 || level > scale) return reply("failure")
        val reading = JSONObject().put("percentage", (level.toLong() * 100 / scale).toInt())
        reading.put("status", when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            else -> "UNKNOWN"
        })
        reading.put("plugged", when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            0 -> "UNPLUGGED"
            BatteryManager.BATTERY_PLUGGED_AC -> "PLUGGED_AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "PLUGGED_USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "PLUGGED_WIRELESS"
            else -> "UNKNOWN"
        })
        reading.put("health", when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        })
        if (intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
            if (temperature !in -100.0..200.0) return reply("failure")
            reading.put("temperature", temperature)
        }
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current != Int.MIN_VALUE) reading.put("current", current)
        return reply("success", JSONObject().put("battery", reading))
    }

    @Suppress("DEPRECATION")
    private fun vibrate(duration: Long): JSONObject {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return reply("unsupported")
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(duration)
        return reply("success")
    }

    private fun onMain(operation: () -> JSONObject): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper()) return operation()
        val ready = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val result = AtomicReference(reply("failure"))
        val deadline = SystemClock.uptimeMillis() + 2000
        val work = Runnable {
            try {
                if (!cancelled.get() && SystemClock.uptimeMillis() < deadline) result.set(operation())
            } catch (_: Exception) { result.set(reply("failure")) }
            finally { ready.countDown() }
        }
        main.post(work)
        if (!ready.await(2, TimeUnit.SECONDS)) {
            cancelled.set(true)
            main.removeCallbacks(work)
            return reply("failure")
        }
        return result.get()
    }

    private fun reply(status: String, details: JSONObject = JSONObject()) =
        JSONObject().put("status", status).put("details", details)
}
