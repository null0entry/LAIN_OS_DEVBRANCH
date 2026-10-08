package dev.lain.os

import android.widget.Button
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDevicePlannerSettingsUiAndroidTest {
    @Test fun offlineModelModeDisplaysAnImportButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val modes = activity.findViewById<Spinner>(R.id.planner_mode)
                assertTrue((0 until modes.adapter.count).any {
                    modes.adapter.getItem(it).toString().contains("On-device")
                })
                modes.setSelection(2)
                val importButton = activity.findViewById<Button>(R.id.planner_import_model)
                assertEquals(android.view.View.VISIBLE, importButton.visibility)
                assertTrue(importButton.isEnabled)
            }
        }
    }
}
