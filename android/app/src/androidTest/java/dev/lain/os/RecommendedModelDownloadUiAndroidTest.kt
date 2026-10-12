package dev.lain.os

import android.app.Application
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lain.os.planner.ImportedGgufModel
import dev.lain.os.planner.RecommendedModelDownloadViewModel
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecommendedModelDownloadUiAndroidTest {
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for recommended model download UI")
    }

    private fun selectOnDevice(scenario: ActivityScenario<MainActivity>) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            activity.findViewById<Button>(R.id.planner_new).performClick()
            activity.findViewById<Spinner>(R.id.planner_mode).setSelection(2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test fun successfulRecommendedDownloadPinsModelIntoEditor() {
        val original = RecommendedModelDownloadViewModel.downloadActionFactory
        val hash = "a".repeat(64)
        RecommendedModelDownloadViewModel.downloadActionFactory = { _: Application ->
            { model, progress, _ ->
                progress(model.expectedBytes / 2, model.expectedBytes)
                ImportedGgufModel(hash, model.expectedBytes)
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                selectOnDevice(scenario)
                scenario.onActivity {
                    assertTrue(it.findViewById<Button>(R.id.planner_download_model).performClick())
                }
                await(scenario) {
                    it.findViewById<EditText>(R.id.planner_model).text.toString() == hash
                }
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.findViewById<TextView>(R.id.planner_download_status)
                            .text.toString().contains("downloaded and verified"),
                    )
                    assertEquals(
                        "Download recommended Qwen",
                        activity.findViewById<Button>(R.id.planner_download_model).text.toString(),
                    )
                }
            }
        } finally {
            RecommendedModelDownloadViewModel.downloadActionFactory = original
        }
    }

    @Test fun cancelStopsDownloadWithoutPublishingModelToEditor() {
        val original = RecommendedModelDownloadViewModel.downloadActionFactory
        val entered = CountDownLatch(1)
        RecommendedModelDownloadViewModel.downloadActionFactory = { _: Application ->
            { model, progress, cancelled ->
                progress(model.expectedBytes / 2, model.expectedBytes)
                entered.countDown()
                while (!cancelled()) Thread.sleep(10)
                throw InterruptedIOException("cancelled")
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                selectOnDevice(scenario)
                scenario.onActivity {
                    assertTrue(it.findViewById<Button>(R.id.planner_download_model).performClick())
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                await(scenario) {
                    it.findViewById<Button>(R.id.planner_download_model).text.toString()
                        .contains("Cancel Qwen download")
                }
                scenario.onActivity {
                    assertTrue(it.findViewById<Button>(R.id.planner_download_model).performClick())
                }
                await(scenario) {
                    it.findViewById<TextView>(R.id.planner_download_status).text.toString()
                        .contains("cancelled")
                }
                scenario.onActivity {
                    assertTrue(it.findViewById<EditText>(R.id.planner_model).text.toString().isBlank())
                }
            }
        } finally {
            RecommendedModelDownloadViewModel.downloadActionFactory = original
        }
    }

    @Test fun recreationRetainsOneDownloadAndDoesNotDuplicatePublication() {
        val original = RecommendedModelDownloadViewModel.downloadActionFactory
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hash = "b".repeat(64)
        RecommendedModelDownloadViewModel.downloadActionFactory = { _: Application ->
            { model, progress, cancelled ->
                calls.incrementAndGet()
                progress(model.expectedBytes / 2, model.expectedBytes)
                entered.countDown()
                while (!cancelled() && release.count > 0L) Thread.sleep(10)
                if (cancelled()) throw InterruptedIOException("cancelled")
                ImportedGgufModel(hash, model.expectedBytes)
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                selectOnDevice(scenario)
                scenario.onActivity {
                    assertTrue(it.findViewById<Button>(R.id.planner_download_model).performClick())
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                scenario.recreate()
                await(scenario) {
                    it.findViewById<Button>(R.id.planner_download_model).text.toString()
                        .contains("Cancel Qwen download")
                }
                assertEquals(1, calls.get())
                release.countDown()
                await(scenario) {
                    it.findViewById<EditText>(R.id.planner_model).text.toString() == hash
                }
                assertEquals(1, calls.get())
            }
        } finally {
            release.countDown()
            RecommendedModelDownloadViewModel.downloadActionFactory = original
        }
    }

    @Test fun failedRecommendedDownloadLeavesEditorUnchangedAndRetryable() {
        val original = RecommendedModelDownloadViewModel.downloadActionFactory
        RecommendedModelDownloadViewModel.downloadActionFactory = { _: Application ->
            { _, _, _ -> throw IllegalArgumentException("injected download failure") }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                selectOnDevice(scenario)
                scenario.onActivity {
                    assertTrue(it.findViewById<Button>(R.id.planner_download_model).performClick())
                }
                await(scenario) {
                    it.findViewById<TextView>(R.id.planner_download_status).text.toString()
                        .contains("injected download failure")
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<EditText>(R.id.planner_model).text.toString().isBlank())
                    assertEquals(
                        "Download recommended Qwen",
                        activity.findViewById<Button>(R.id.planner_download_model).text.toString(),
                    )
                }
            }
        } finally {
            RecommendedModelDownloadViewModel.downloadActionFactory = original
        }
    }

}
