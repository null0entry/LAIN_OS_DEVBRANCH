package dev.lain.os

import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lain.os.bootstrap.AvailabilityState
import dev.lain.os.bootstrap.BootstrapStateStore
import dev.lain.os.bootstrap.BootstrapViewModel
import dev.lain.os.bootstrap.PermissionBroker
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @After fun resetFactories() {
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
        }

        assertTrue(store.isComplete())
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
}
