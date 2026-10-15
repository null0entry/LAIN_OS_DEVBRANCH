package dev.lain.os.runtime

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.lain.os.R

/** A background request offers a visible tap route and never claims navigation completed. */
internal class DeviceNavigation(private val context: Context) {
    @Suppress("DEPRECATION")
    fun launchableApps(): List<LaunchableApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).mapNotNull { info ->
        val activity = info.activityInfo ?: return@mapNotNull null
        if (!activity.enabled || !activity.applicationInfo.enabled || !activity.exported) return@mapNotNull null
        LaunchableApp(activity.packageName, info.loadLabel(context.packageManager)?.toString().orEmpty())
    }

    fun target(page: String?, packageName: String?): Intent? {
        val manager = context.packageManager
        val intent = if (packageName != null) {
            if (!NativeDeviceControls.validPackage(packageName)) return null
            // Use only the installed package's actual launcher intent.
            if (launchableApps().none { it.packageName == packageName }) return null
            manager.getLaunchIntentForPackage(packageName) ?: return null
        } else {
            if (page !in NativeDeviceControls.pages) return null
            val appUri = Uri.parse("package:${context.packageName}")
            when (page) {
                "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                "apps" -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                "app_permissions" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
                "notifications" -> if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
                "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "write_settings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, appUri)
                // Some platforms expose this app-specific page. Use the public access
                // overview when that fixed optional action has no installed handler.
                "dnd" -> Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS", appUri)
                    .takeIf { it.resolveActivity(manager) != null }
                    ?: Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                else -> return null
            }
        }
        return intent.takeIf { it.resolveActivity(manager) != null }
    }

    fun offer(page: String?, packageName: String?): String {
        if (target(page, packageName) == null) return "handler_unavailable"
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return "permission_denied"
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "permission_denied"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Device navigation", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return "permission_denied"
            }
        }
        val launch = Intent(context, DeviceNavigationActivity::class.java).apply {
            // No arbitrary intents, components, URIs or flags are accepted as extras.
            putExtra("page", page)
            putExtra("package", packageName)
        }
        val pending = PendingIntent.getActivity(context, 4109, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL)
            else Notification.Builder(context)
        manager.notify(4109, notification.setSmallIcon(R.drawable.ic_lain)
            .setContentTitle("Open requested page or app")
            .setContentText("Tap to continue on your device")
            .setContentIntent(pending).setAutoCancel(true).build())
        return "background_launch_restricted"
    }

    companion object {
        const val CHANNEL = "lain-device-navigation"
    }
}

/** Private foreground Activity entered through the immutable user-tap PendingIntent. */
class DeviceNavigationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val page = intent.getStringExtra("page")
            val packageName = intent.getStringExtra("package")
            if ((page == null) == (packageName == null)) return
            DeviceNavigation(this).target(page, packageName)?.let { startActivity(it) }
        } catch (_: Exception) {
            // An app, handler or permission may change after the tap route was posted.
        } finally {
            finish()
        }
    }
}
