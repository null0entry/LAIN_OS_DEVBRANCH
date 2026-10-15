package dev.lain.os.voice

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuousVoiceServiceManifestTest {
    @Test fun microphoneServiceIsPrivateAndUserStarted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, ContinuousVoiceService::class.java), 0)
        assertFalse("No third-party app may start microphone capture", info.exported)
        assertNull("No permission bypass or public binder API", info.permission)
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(info.foregroundServiceType and
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0)
        }
        assertEquals("Runtime lease is absent before owner start",
            false, ContinuousVoiceService.leaseActive)
    }
}
