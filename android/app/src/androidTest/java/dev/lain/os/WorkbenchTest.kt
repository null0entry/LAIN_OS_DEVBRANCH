package dev.lain.os

import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** History persists across launches and test methods, as it does for the owner. */
@RunWith(AndroidJUnit4::class)
class WorkbenchTest {
    private fun awaitReady(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.elapsedRealtime() + 60000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity {
                ready = it.findViewById<TextView>(R.id.connection).text.toString() == it.getString(R.string.connected)
            }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail("Runtime did not become ready")
    }

    private fun sessions(scenario: ActivityScenario<MainActivity>): Set<String> {
        var ids = emptySet<String>()
        scenario.onActivity {
            ids = java.io.File(it.filesDir, "lain/sessions").listFiles()
                ?.filter { file -> file.isDirectory }?.map { file -> file.name }?.toSet() ?: emptySet()
        }
        return ids
    }

    @Test fun rotationDoesNotSubmitAnotherTask() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val before = sessions(scenario)
            var title = ""
            scenario.onActivity { title = it.findViewById<TextView>(R.id.task_title).text.toString() }
            onView(withId(R.id.command_input)).check(matches(withText("")))
            scenario.recreate()
            awaitReady(scenario)
            onView(withId(R.id.task_title)).check(matches(withText(title)))
            assertEquals(before, sessions(scenario))
        }
    }

    @Test fun demoChoiceRequiresSeparateRunTap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitReady(scenario)
            val before = sessions(scenario)
            scenario.onActivity { activity ->
                val commands = activity.findViewById<android.widget.LinearLayout>(R.id.demo_commands)
                val choice = (0 until commands.childCount)
                    .map { commands.getChildAt(it) }
                    .filterIsInstance<TextView>()
                    .first { it.text.toString() == "Show battery" }
                assertTrue(choice.performClick())
            }
            onView(withId(R.id.command_input)).check(matches(withText("Show battery")))
            assertEquals(before, sessions(scenario))
        }
    }

    @Test fun showsPlannerAndVerificationLimitations() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(R.string.demo_notice)).check(matches(isDisplayed()))
            onView(withText(R.string.limited_notice)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }
}
