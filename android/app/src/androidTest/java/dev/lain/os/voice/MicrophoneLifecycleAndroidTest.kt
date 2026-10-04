package dev.lain.os.voice

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lain.os.MainActivity
import dev.lain.os.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicrophoneLifecycleAndroidTest {
    private class FakeEngine : AudioCaptureEngine {
        override var recording = false
            private set
        var stops = 0
        private var onChunk: ((ByteArray) -> Unit)? = null

        override fun start(onChunk: (ByteArray) -> Unit, onFailure: () -> Unit) {
            recording = true
            this.onChunk = onChunk
        }

        override fun stop() {
            if (recording) stops += 1
            recording = false
        }
    }

    @After fun resetFactories() {
        VoiceCaptureViewModel.engineFactory = { AndroidAudioCaptureEngine() }
        VoiceCaptureViewModel.permissionChecker = { context ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @Test fun manifestDeclaresRecordAudioPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.RECORD_AUDIO))
    }

    @Test fun permissionDeniedNeverStartsCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val engine = FakeEngine()
        VoiceCaptureViewModel.engineFactory = { engine }
        VoiceCaptureViewModel.permissionChecker = { false }

        instrumentation.runOnMainSync {
            val model = VoiceCaptureViewModel(application)
            assertFalse(model.start())
            model.onPermissionResult(false)
            assertEquals(MicrophoneStatus.FAILED, model.state.value!!.status)
            assertEquals(MicrophoneFailure.PERMISSION_DENIED, model.state.value!!.failure)
            assertFalse(engine.recording)
            model.closeForTest()
        }
    }

    @Test fun permissionRevocationStopsActiveCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val engine = FakeEngine()
        var granted = true
        VoiceCaptureViewModel.engineFactory = { engine }
        VoiceCaptureViewModel.permissionChecker = { granted }

        instrumentation.runOnMainSync {
            val model = VoiceCaptureViewModel(application)
            assertTrue(model.start())
            assertTrue(engine.recording)
            granted = false
            model.reconcilePermission()
            assertFalse(engine.recording)
            assertEquals(MicrophoneFailure.PERMISSION_REVOKED, model.state.value!!.failure)
            model.closeForTest()
        }
    }

    @Test fun visibleRecordingSurvivesRotationAndBackgroundReleasesCapture() {
        val engine = FakeEngine()
        VoiceCaptureViewModel.engineFactory = { engine }
        VoiceCaptureViewModel.permissionChecker = { true }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
            }
            scenario.onActivity {
                assertTrue(
                    it.findViewById<android.widget.TextView>(R.id.voice_status).text.toString()
                        .contains("Recording", ignoreCase = true)
                )
                assertTrue(engine.recording)
            }

            scenario.recreate()

            scenario.onActivity {
                assertTrue(
                    it.findViewById<android.widget.TextView>(R.id.voice_status).text.toString()
                        .contains("Recording", ignoreCase = true)
                )
                assertTrue(engine.recording)
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(engine.recording)
            assertTrue(engine.stops >= 1)
        }
    }
}
