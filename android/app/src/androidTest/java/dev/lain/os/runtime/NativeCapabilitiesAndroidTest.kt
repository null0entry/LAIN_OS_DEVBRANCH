package dev.lain.os.runtime

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCapabilitiesAndroidTest {
    private fun execute(context: Context, name: String, args: JSONObject): JSONObject {
        var reply: JSONObject? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            reply = JSONObject(NativeCapabilities(context).execute(name, args.toString()))
        }
        return reply!!
    }

    @Test fun uriDispatchUsesOnlyAnApprovedViewIntent() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        var launched: Intent? = null
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val result = execute(context, "android.open_uri", JSONObject().put("uri", "https://example.com"))
        assertEquals("success", result.getString("status"))
        assertEquals(Intent.ACTION_VIEW, launched!!.action)
        assertEquals("https://example.com", launched!!.dataString)
        assertTrue(launched!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        for (uri in listOf("intent://settings", "file:///private", "javascript:alert(1)")) {
            launched = null
            val rejected = execute(context, "android.open_uri", JSONObject().put("uri", uri))
            assertEquals("failure", rejected.getString("status"))
            assertNull(launched)
        }
    }

    @Test fun notificationDispatchPostsARealNotificationWhenPermitted() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val permissionWasGranted = Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33 && !permissionWasGranted) {
            automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            val result = execute(app, "android.notify", JSONObject().put("title", "LAIN test").put("content", "Ready"))
            assertEquals("success", result.getString("status"))
            val deadline = SystemClock.elapsedRealtime() + 5000
            var posted = manager.activeNotifications.firstOrNull { it.id == 4108 }?.notification
            while (posted == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
                posted = manager.activeNotifications.firstOrNull { it.id == 4108 }?.notification
            }
            assertNotNull(posted)
            assertEquals("LAIN test", posted!!.extras.getCharSequence("android.title").toString())
            assertEquals("Ready", posted.extras.getCharSequence("android.text").toString())
        } finally {
            manager.cancel(4108)
        }
    }

    @Test fun notificationRejectsUnexpectedFields() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val result = execute(app, "android.notify", JSONObject()
            .put("title", "LAIN").put("content", "Ready").put("intent", "arbitrary"))
        assertEquals("failure", result.getString("status"))
    }
}
