package dev.lain.os

import android.view.View
import android.widget.Button
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDevicePlannerSettingsUiAndroidTest {
    @Test fun newOnDeviceProfileDisplaysEnabledImportButtonAfterSelectionCallback() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // ActivityScenario.onActivity runs on the UI thread; Spinner selection callbacks
            // are dispatched on a later loop. Assert only after Android becomes idle.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val modes = activity.findViewById<Spinner>(R.id.planner_mode)
                assertTrue((0 until modes.adapter.count).any {
                    modes.adapter.getItem(it).toString().contains("On-device")
                })
                activity.findViewById<Button>(R.id.planner_new).performClick()
                modes.setSelection(2)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(2, activity.findViewById<Spinner>(R.id.planner_mode).selectedItemPosition)
                val importButton = activity.findViewById<Button>(R.id.planner_import_model)
                assertEquals(View.VISIBLE, importButton.visibility)
                assertTrue(importButton.isEnabled)
            }
        }
    }
}
