package dev.lain.os

import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelStartupProgressAndroidTest {
    @Test fun progressTracksLoadingReadinessFailureAndStop() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val progress = activity.findViewById<ProgressBar>(R.id.model_start_progress)
                val start = activity.findViewById<Button>(R.id.start_model_button)
                val status = activity.findViewById<TextView>(R.id.model_residency_status)
                activity.renderModelStatus("LOADING")
                assertEquals(View.VISIBLE, progress.visibility)
                assertTrue(progress.isIndeterminate)
                assertFalse(start.isEnabled)
                activity.renderModelStatus("READY")
                assertEquals(View.GONE, progress.visibility)
                assertEquals("Offline model ready", status.text.toString())
                assertFalse(start.isEnabled)
                activity.renderModelStatus("LOADING")
                activity.renderModelStatus("FAILED: GGUF unavailable or changed")
                assertEquals(View.GONE, progress.visibility)
                assertTrue(start.isEnabled)
                assertTrue(status.text.toString().contains("GGUF unavailable"))
                activity.renderModelStatus("STOPPING")
                assertEquals(View.VISIBLE, progress.visibility)
                activity.renderModelStatus("STOPPED")
                assertEquals(View.GONE, progress.visibility)
                assertTrue(start.isEnabled)
                activity.renderModelStatus("LOADING")
                activity.renderModelStatus("DISCONNECTED")
                assertEquals(View.GONE, progress.visibility)
                assertTrue(start.isEnabled)
            }
        }
    }

    @Test fun serviceStatusRefreshesAfterActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.findViewById<Button>(R.id.stop_model_button).performClick()
            }
            awaitStopped(scenario)
            scenario.recreate()
            awaitStopped(scenario)
        }
    }

    private fun awaitStopped(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var stopped = false
            scenario.onActivity {
                stopped = it.findViewById<TextView>(R.id.model_residency_status).text.toString() ==
                    "Offline model stopped" &&
                    it.findViewById<ProgressBar>(R.id.model_start_progress).visibility == View.GONE
            }
            if (stopped) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Workbench did not refresh resident-service status")
    }

}
