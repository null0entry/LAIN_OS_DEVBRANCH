package dev.lain.os

import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import java.io.File
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the packaged Python controller through the owner UI, not a mock. */
@RunWith(AndroidJUnit4::class)
class RuntimeFlowTest {
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for local runtime state")
    }

    private fun run(scenario: ActivityScenario<MainActivity>, goal: String) {
        await(scenario) { it.findViewById<android.view.View>(R.id.run_button).isEnabled }
        onView(withId(R.id.command_input)).perform(replaceText(goal), closeSoftKeyboard())
        onView(withId(R.id.run_button)).perform(click())
    }

    private fun resultsText(activity: MainActivity): String {
        val results = activity.findViewById<android.widget.LinearLayout>(R.id.results)
        return (0 until results.childCount).joinToString("\n") {
            (results.getChildAt(it) as TextView).text
        }
    }

    @Test fun demoFilePresetCompletesWithVerifiedArtifact() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            run(scenario, "Create demo file")
            await(scenario) {
                it.findViewById<TextView>(R.id.task_status).text.toString() == "COMPLETE" &&
                    resultsText(it).contains("file.write_text")
            }
            scenario.onActivity { activity ->
                val text = resultsText(activity)
                assertTrue(text.contains("file.write_text"))
                assertTrue(text.contains("Execution: success", ignoreCase = true))
                assertTrue(text.contains("Verification: passed", ignoreCase = true))
                val workspace = File(activity.filesDir, "lain/workspace")
                assertTrue(
                    workspace.listFiles()?.any {
                        it.name.matches(Regex("demo(?:-\\d+)?\\.txt")) &&
                            it.readText() == "Hello from LAIN_OS.\n"
                    } == true,
                )
            }
        }
    }

    @Test fun clipboardPresetCompletesWithTruthfulNativeEvidence() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            run(scenario, "Copy demo text")
            await(scenario) {
                it.findViewById<TextView>(R.id.task_status).text.toString() == "COMPLETE" &&
                    resultsText(it).contains("android.clipboard_set")
            }
            scenario.onActivity { activity ->
                val text = resultsText(activity)
                assertTrue(text.contains("android.clipboard_set"))
                assertTrue(text.contains("Execution: success", ignoreCase = true))
                assertTrue(
                    text.contains("Verification: passed", ignoreCase = true) ||
                        text.contains("Verification: limited", ignoreCase = true),
                )
                assertFalse(text.contains("Hello from LAIN_OS."))
            }
        }
    }

    @Test fun nativeBatteryProducesStructuredPassedResult() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            run(scenario, "Show battery")
            await(scenario) { it.findViewById<TextView>(R.id.task_status).text.toString() == "COMPLETE" }
            scenario.onActivity { activity ->
                val results = activity.findViewById<android.widget.LinearLayout>(R.id.results)
                val text = (0 until results.childCount).joinToString("\n") { (results.getChildAt(it) as TextView).text }
                assertTrue(text.contains("android.battery_status"))
                assertTrue(text.contains("Verification: passed", ignoreCase = true))
                assertTrue(text.contains("percentage"))
            }
            var displayed: android.view.View? = null
            scenario.onActivity { displayed = it.findViewById<android.widget.LinearLayout>(R.id.results).getChildAt(0) }
            SystemClock.sleep(1600) // Cross two unchanged runtime polls.
            scenario.onActivity {
                assertSame("Unchanged results must preserve selectable text views", displayed,
                    it.findViewById<android.widget.LinearLayout>(R.id.results).getChildAt(0))
            }
        }
    }

    @Test fun shareWaitsForApprovalAndStopCancelsWithoutChooser() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            run(scenario, "Share demo text")
            await(scenario) {
                it.findViewById<TextView>(R.id.task_status).text.toString() == "PAUSED CONFIRMATION" &&
                    it.findViewById<TextView>(R.id.approval_text).text.toString()
                        .contains("android.share_text")
            }
            scenario.onActivity { activity ->
                val text = activity.findViewById<TextView>(R.id.approval_text).text.toString()
                assertTrue(text.contains("android.share_text"))
                assertTrue(text.contains("[REDACTED]"))
                assertFalse(text.contains("Hello from LAIN_OS."))
                assertTrue(activity.findViewById<android.view.View>(R.id.approve_button).isEnabled)
            }
            onView(withId(R.id.stop_button)).perform(click())
            await(scenario) { it.findViewById<TextView>(R.id.task_status).text.toString() == "CANCELLED" }
        }
    }
}
