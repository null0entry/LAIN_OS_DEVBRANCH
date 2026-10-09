package dev.lain.os

import android.view.View
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lain.os.planner.OnDeviceModelStore
import dev.lain.os.planner.OnDevicePlannerSaveViewModel
import dev.lain.os.planner.PlannerProfileDraft
import dev.lain.os.planner.PlannerSettingsManager
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDevicePlannerSettingsUiAndroidTest {
    private fun await(scenario: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 60000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for on-device save state")
    }

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

    @Test fun verificationLeavesUiResponsiveAndSavesOnlyCapturedDraftAcrossRecreation() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val settings = PlannerSettingsManager(app)
        // Deliberately only a header candidate: saving verifies integrity, not inference.
        val bytes = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put("GGUF".toByteArray()).putInt(3).putLong(1).putLong(1).array() +
            UUID.randomUUID().toString().toByteArray()
        val imported = OnDeviceModelStore(app).importModel(ByteArrayInputStream(bytes))
        val name = "Offline save " + UUID.randomUUID().toString().take(8)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val onMainThread = AtomicBoolean(true)
        val captured = AtomicReference<PlannerProfileDraft>()
        val profileId = AtomicReference<String>()
        val originalFactory = OnDevicePlannerSaveViewModel.saveActionFactory
        OnDevicePlannerSaveViewModel.saveActionFactory = {
            val save: (PlannerProfileDraft) -> String = { draft ->
                calls.incrementAndGet()
                captured.set(draft)
                onMainThread.set(Looper.myLooper() == Looper.getMainLooper())
                entered.countDown()
                check(release.await(60, TimeUnit.SECONDS)) { "save gate timed out" }
                settings.save(draft).also {
                    profileId.set(it)
                    settings.select(it)
                }
            }
            save
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    activity.findViewById<Button>(R.id.planner_new).performClick()
                    activity.findViewById<Spinner>(R.id.planner_mode).setSelection(2)
                    activity.findViewById<EditText>(R.id.planner_name).setText(name)
                    activity.findViewById<EditText>(R.id.planner_model).setText(imported.sha256)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { it.findViewById<Button>(R.id.planner_save).performClick() }
                assertTrue("save must begin on a worker", entered.await(10, TimeUnit.SECONDS))
                assertFalse("model verification ran on the UI thread", onMainThread.get())
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<Button>(R.id.planner_save).isEnabled)
                    assertFalse(activity.findViewById<Button>(R.id.planner_new).isEnabled)
                    assertFalse(activity.findViewById<Button>(R.id.planner_import_model).isEnabled)
                    assertFalse(activity.findViewById<Spinner>(R.id.planner_profiles).isEnabled)
                    // A second callback must not enqueue another save, even if invoked directly.
                    activity.findViewById<Button>(R.id.planner_save).performClick()
                    activity.findViewById<EditText>(R.id.command_input).setText("Show battery")
                    assertEquals("Show battery", activity.findViewById<EditText>(R.id.command_input).text.toString())
                }
                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<Button>(R.id.planner_save).isEnabled)
                    assertEquals(name, activity.findViewById<EditText>(R.id.planner_name).text.toString())
                    assertEquals(imported.sha256, activity.findViewById<EditText>(R.id.planner_model).text.toString())
                }
                assertEquals(1, calls.get())
                assertEquals(name, captured.get().name)
                assertEquals(imported.sha256, captured.get().model)
                // Even an external view mutation during verification cannot alter its snapshot.
                scenario.onActivity { activity ->
                    activity.findViewById<EditText>(R.id.planner_name).setText("later editor value")
                    activity.findViewById<EditText>(R.id.planner_model).setText("f".repeat(64))
                }
                release.countDown()
                await(scenario) { activity ->
                    activity.findViewById<TextView>(R.id.active_planner).text.toString().contains(name) &&
                        activity.findViewById<Button>(R.id.planner_save).isEnabled
                }
                scenario.onActivity { activity ->
                    assertEquals(name, activity.findViewById<EditText>(R.id.planner_name).text.toString())
                    assertEquals(imported.sha256, activity.findViewById<EditText>(R.id.planner_model).text.toString())
                    activity.findViewById<Button>(R.id.planner_test).performClick()
                }
                await(scenario) {
                    it.findViewById<TextView>(R.id.planner_status).text.toString()
                        .contains("Model integrity verified. Inference has not been tested.")
                }
                assertEquals(1, calls.get())
            }
        } finally {
            release.countDown()
            OnDevicePlannerSaveViewModel.saveActionFactory = originalFactory
            profileId.get()?.let(settings::delete)
            File(app.filesDir, "models/${imported.sha256}.gguf").delete()
        }
    }

    @Test fun failedSaveAfterRecreationKeepsDisplayedProfileAsDeletionTarget() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val settings = PlannerSettingsManager(app)
        val originalActiveProfile = settings.snapshot().activeProfileId
        val bytes = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put("GGUF".toByteArray()).putInt(3).putLong(1).putLong(1).array() +
            UUID.randomUUID().toString().toByteArray()
        val imported = OnDeviceModelStore(app).importModel(ByteArrayInputStream(bytes))
        val suffix = UUID.randomUUID().toString().take(8)
        val nameA = "Offline failure A $suffix"
        val nameB = "Offline failure B $suffix"
        val profileA = settings.save(PlannerProfileDraft(
            name = nameA, mode = "on_device", baseUrl = "", model = imported.sha256,
        ))
        val profileB = settings.save(PlannerProfileDraft(
            name = nameB, mode = "on_device", baseUrl = "", model = imported.sha256,
        ))
        settings.select(profileA)
        val unsavedName = "Offline unsaved B $suffix"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val originalFactory = OnDevicePlannerSaveViewModel.saveActionFactory
        OnDevicePlannerSaveViewModel.saveActionFactory = {
            val save: (PlannerProfileDraft) -> String = {
                entered.countDown()
                check(release.await(60, TimeUnit.SECONDS)) { "save gate timed out" }
                throw IllegalArgumentException("injected profile save failure")
            }
            save
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val spinner = activity.findViewById<Spinner>(R.id.planner_profiles)
                    val indexB = (0 until spinner.adapter.count).first {
                        spinner.adapter.getItem(it).toString() == nameB
                    }
                    spinner.setSelection(indexB)
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(nameB, activity.findViewById<EditText>(R.id.planner_name).text.toString())
                    activity.findViewById<EditText>(R.id.planner_name).setText(unsavedName)
                    activity.findViewById<Button>(R.id.planner_save).performClick()
                }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(nameB, activity.findViewById<Spinner>(R.id.planner_profiles).selectedItem.toString())
                    assertEquals(unsavedName, activity.findViewById<EditText>(R.id.planner_name).text.toString())
                    assertFalse(activity.findViewById<Button>(R.id.planner_delete).isEnabled)
                }
                release.countDown()
                await(scenario) { activity ->
                    activity.findViewById<TextView>(R.id.planner_status).text.toString()
                        .contains("injected profile save failure") &&
                        activity.findViewById<Button>(R.id.planner_delete).isEnabled
                }
                // Let restoration's deferred Spinner callback run before checking the unsaved draft.
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(nameB, activity.findViewById<Spinner>(R.id.planner_profiles).selectedItem.toString())
                    assertEquals(unsavedName, activity.findViewById<EditText>(R.id.planner_name).text.toString())
                    assertTrue(activity.findViewById<TextView>(R.id.active_planner).text.toString().contains(nameA))
                    activity.findViewById<Button>(R.id.planner_delete).performClick()
                }
                val afterDeletion = settings.snapshot()
                assertTrue("active profile A must be preserved", afterDeletion.profiles.any { it.id == profileA })
                assertFalse("Delete must target displayed profile B", afterDeletion.profiles.any { it.id == profileB })
                assertEquals(profileA, afterDeletion.activeProfileId)
            }
        } finally {
            release.countDown()
            OnDevicePlannerSaveViewModel.saveActionFactory = originalFactory
            settings.delete(profileB)
            settings.delete(profileA)
            settings.select(originalActiveProfile)
            File(app.filesDir, "models/${imported.sha256}.gguf").delete()
        }
    }
}
