package dev.lain.os.runtime

import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeDeviceControlsAndroidTest {
    private val app get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun torchNeverWaitsOrMutatesOnMainLooper() {
        var rejected = false
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                AndroidDeviceControlPlatform(app).setTorch(true)
            } catch (error: DeviceControlFailure) {
                rejected = error.reason == "operation_rejected"
            }
        }
        assertTrue(rejected)
    }

    @Test fun rawJsonBoundaryRejectsAndroidLeniencyAndDuplicateArguments() {
        val native=NativeCapabilities(app)
        for (raw in listOf("{\"enabled\":true,\"enabled\":false}", "{'enabled':true}", "{enabled:true}", "{\"enabled\":true,}", "{\"enabled\":true}{}", "{\"enabled\":true} trailing", "{\"enabled\":1}", "{\"enabled\":\"\\uD800\"}")) {
            val result=JSONObject(native.execute("android.flashlight_set",raw))
            assertEquals(raw,"failure",result.getString("status"))
            assertEquals(raw,"operation_rejected",result.getJSONObject("details").getString("reason"))
        }
    }

    @Test fun actualReadOnlyVolumeUsesRequestedStreamsAndApiMinimum() {
        val manager=app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val controls=NativeDeviceControls(app)
        for (stream in listOf("media","ring","alarm","notification","system","voice_call")) {
            val result=controls.execute("android.volume_get",JSONObject().put("stream",stream))!!
            assertEquals("success",result.getString("status"))
            val details=result.getJSONObject("details")
            assertEquals(stream,details.getString("stream"))
            val observed=details.getJSONObject("observed")
            val id=AndroidDeviceControlPlatform.streamId(stream)
            assertEquals(manager.getStreamVolume(id),observed.getInt("index"))
            assertEquals(manager.getStreamMaxVolume(id),observed.getInt("max_index"))
            assertEquals(if(Build.VERSION.SDK_INT>=28) manager.getStreamMinVolume(id) else 0,observed.getInt("min_index"))
        }
    }

    @Test fun inventoryIsVisibleLaunchableBoundedAndHasClosedRecords() {
        val details=NativeDeviceControls(app).execute("android.app_list",JSONObject().put("query",""))!!.getJSONObject("details")
        val apps=details.getJSONArray("apps")
        assertTrue(apps.length()<=128)
        assertTrue(details.toString().toByteArray(Charsets.UTF_8).size<=32768)
        val packages=mutableListOf<String>()
        for(i in 0 until apps.length()) {
            val record=apps.getJSONObject(i)
            assertEquals(setOf("package","label"),record.keys().asSequence().toSet())
            val name=record.getString("package"); packages+=name
            assertNotNull(app.packageManager.getLaunchIntentForPackage(name))
            val label=record.getString("label")
            assertTrue(label.isNotEmpty()); assertTrue(label.codePointCount(0,label.length)<=256)
        }
        assertEquals(packages.distinct().sorted(),packages)
    }

    @Test fun settingsTargetsAreFixedAndPrivateActivityIsNotExported() {
        val navigation=DeviceNavigation(app)
        val wifi=navigation.target("wifi",null)
        assertNotNull(wifi)
        assertEquals(Settings.ACTION_WIFI_SETTINGS,wifi!!.action)
        val permissions=navigation.target("app_permissions",null)
        assertNotNull(permissions)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,permissions!!.action)
        assertEquals("package:${app.packageName}",permissions.dataString)
        assertNull(navigation.target("intent://malicious",null))
        assertNull(navigation.target(null,"com.not_installed.lain_test"))
        @Suppress("DEPRECATION")
        val activity=app.packageManager.getActivityInfo(android.content.ComponentName(app,DeviceNavigationActivity::class.java),0)
        assertFalse(activity.exported)
    }

    @Test fun permittedNotificationOffersImmutableUserTapWithoutClaimingAnOpenedPage() {
        assumeTrue(Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        assumeTrue(androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled())
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            val result = DeviceNavigation(app).offer("wifi", null)
            if (Build.VERSION.SDK_INT >= 26) {
                assumeTrue(manager.getNotificationChannel(DeviceNavigation.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
            }
            assertEquals("background_launch_restricted", result)
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            var notification = manager.activeNotifications.firstOrNull { it.id == 4109 }?.notification
            while (notification == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                android.os.SystemClock.sleep(25)
                notification = manager.activeNotifications.firstOrNull { it.id == 4109 }?.notification
            }
            assertNotNull(notification)
            val pending = notification!!.contentIntent
            assertNotNull(pending)
            assertEquals(app.packageName, pending.creatorPackage)
            if (Build.VERSION.SDK_INT >= 31) assertTrue(pending.isImmutable)
        } finally {
            manager.cancel(4109)
        }
    }

    @Test fun deniedNotificationRouteDoesNotLaunchAnActivity() {
        var launched=false
        val context=object : ContextWrapper(app) {
            override fun checkSelfPermission(permission: String): Int = if(permission==Manifest.permission.POST_NOTIFICATIONS) PackageManager.PERMISSION_DENIED else super.checkSelfPermission(permission)
            override fun startActivity(intent: Intent) { launched=true }
        }
        if(Build.VERSION.SDK_INT>=33) assertEquals("permission_denied",DeviceNavigation(context).offer("wifi",null))
        assertFalse(launched)
    }
}
