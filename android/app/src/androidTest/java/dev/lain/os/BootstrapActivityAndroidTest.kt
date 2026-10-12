package dev.lain.os

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import dev.lain.os.bootstrap.AvailabilityState
import dev.lain.os.bootstrap.BootstrapStateStore
import dev.lain.os.bootstrap.BootstrapViewModel
import dev.lain.os.bootstrap.PermissionBroker
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootstrapActivityAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(
        "bootstrap-test-${UUID.randomUUID()}",
        Context.MODE_PRIVATE,
    )
    private val store = BootstrapStateStore(prefs)

    private fun awaitCompletion() {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (store.isComplete()) return
            SystemClock.sleep(50)
        }
        fail("Setup did not persist completion")
    }

    private fun awaitWorkbench() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
            }
            if (resumed) return
            SystemClock.sleep(50)
        }
        fail("Setup did not reach the workbench")
    }

    @After fun resetFactories() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().forEach { it.finish() }
        }
        BootstrapViewModel.permissionBrokerFactory = PermissionBroker::android
        BootstrapViewModel.stateStoreFactory = { BootstrapStateStore(it) }
        prefs.edit().clear().commit()
    }

    @Test fun cleanInstallShowsBootstrapWithDemoAlwaysAvailable() {
        BootstrapViewModel.stateStoreFactory = { store }
        BootstrapViewModel.permissionBrokerFactory = {
            PermissionBroker(
                apiLevel = 35,
                hasRuntimePermission = { false },
                hasSettingsGrant = { false },
                hasOptionalDependency = { false },
            )
        }

        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<TextView>(R.id.bootstrap_heading)
                        .text.toString().contains("Set up LAIN_OS"),
                )
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_demo).isEnabled)
                val qwen = activity.findViewById<Button>(R.id.bootstrap_download_qwen)
                assertTrue(qwen.isEnabled)
                assertTrue(qwen.text.toString().contains("Download recommended Qwen"))
                val capabilities = activity.findViewById<TextView>(R.id.bootstrap_capabilities)
                    .text.toString()
                assertTrue(capabilities.contains("voice.input"))
                assertTrue(capabilities.contains(AvailabilityState.NEEDS_PERMISSION.name))
                assertTrue(capabilities.contains("android.battery_status"))
                assertTrue(capabilities.contains(AvailabilityState.READY.name))
            }
        }
    }

    @Test fun continueWithDemoCompletesSetupWithoutRequiringMicrophone() {
        BootstrapViewModel.stateStoreFactory = { store }
        BootstrapViewModel.permissionBrokerFactory = {
            PermissionBroker(
                apiLevel = 35,
                hasRuntimePermission = { false },
                hasSettingsGrant = { false },
                hasOptionalDependency = { false },
            )
        }

        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_demo).performClick())
            }
            awaitCompletion()
            awaitWorkbench()
            onView(withId(R.id.command_input)).check(matches(isDisplayed()))
        }

        assertTrue(store.isComplete())
        onView(withId(R.id.command_input)).check(matches(isDisplayed()))
    }

    @Test fun recreationBeforeFinishKeepsBootstrapIncomplete() {
        BootstrapViewModel.stateStoreFactory = { store }

        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(
                    "Set up LAIN_OS",
                    activity.findViewById<TextView>(R.id.bootstrap_heading).text.toString(),
                )
            }
        }

        assertTrue(!store.isComplete())
    }

    @Test fun launcherShowsBootstrapUntilExplicitCompletion() {
        BootstrapViewModel.stateStoreFactory = { store }
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        assertEquals(BootstrapActivity::class.java.name, launch.component?.className)
        ActivityScenario.launch<BootstrapActivity>(launch).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("Set up LAIN_OS", activity.findViewById<TextView>(R.id.bootstrap_heading).text)
            }
        }
        assertTrue(!store.isComplete())
    }

    @Test fun completedLauncherSkipsSetupWithoutChangingPlanner() {
        BootstrapViewModel.stateStoreFactory = { store }
        store.complete()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(instrumentation.waitForMonitorWithTimeout(monitor, 15000) is MainActivity)
            onView(withId(R.id.command_input)).check(matches(isDisplayed()))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun setupCompletionPersistsAcrossStoreInstances() {
        assertTrue(!store.isComplete())
        store.complete()
        assertTrue(BootstrapStateStore(prefs).isComplete())
        assertEquals(setOf("setup_version"), prefs.all.keys)
        prefs.edit().clear().commit()
        assertTrue(!BootstrapStateStore(prefs).isComplete())
    }

    @Test fun permissionGrantAndRevocationAreRescannedWithoutCompletingSetup() {
        BootstrapViewModel.stateStoreFactory = { store }
        var granted = false
        BootstrapViewModel.permissionBrokerFactory = {
            PermissionBroker(35, { granted }, { false }, { false })
        }
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.bootstrap_capabilities).text
                    .contains("voice.input: NEEDS_PERMISSION"))
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_microphone).isEnabled)
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            granted = true
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.bootstrap_capabilities).text
                    .contains("voice.input: READY"))
                assertTrue(!activity.findViewById<Button>(R.id.bootstrap_microphone).isEnabled)
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            granted = false
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.bootstrap_capabilities).text
                    .contains("voice.input: NEEDS_PERMISSION"))
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_demo).isEnabled)
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_finish).isEnabled)
            }
        }
        assertTrue(!store.isComplete())
    }

    @Test fun configurePlannerOpensExistingSettingsWithoutCompletingSetup() {
        BootstrapViewModel.stateStoreFactory = { store }
        ActivityScenario.launch(BootstrapActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_configure_planner).performClick())
            }
            // Opening MainActivity is asynchronous across activity lifecycles.
            // Wait for the destination to resume before matching its view tree.
            awaitWorkbench()
            onView(withId(R.id.planner_profiles)).check(matches(isDisplayed()))
            assertTrue(!store.isComplete())
            androidx.test.espresso.Espresso.pressBack()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<Button>(R.id.bootstrap_finish).performClick())
            }
            awaitCompletion()
            awaitWorkbench()
            onView(withId(R.id.command_input)).check(matches(isDisplayed()))
        }
        assertTrue(store.isComplete())
    }
}
