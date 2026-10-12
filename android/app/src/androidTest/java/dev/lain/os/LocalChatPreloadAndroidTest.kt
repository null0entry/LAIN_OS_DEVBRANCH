package dev.lain.os

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lain.os.planner.PlannerProfile
import dev.lain.os.planner.PlannerProfileStore
import dev.lain.os.runtime.ResidentModelClient
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalChatPreloadAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PlannerProfileStore(context)
    private val missing = "0".repeat(64)
    private val id = "chat-test-" + UUID.randomUUID().toString()
    private var previous = PlannerProfile.DEMO_ID

    @Before fun selectMissingModel() {
        previous = store.active().id
        store.upsert(PlannerProfile(id, "Chat preload test", "on_device", "gguf_native_v1",
            "", missing, null, 120.0, 16384, "none", false))
        store.select(id)
    }

    @After fun restoreProfile() {
        store.select(previous)
        store.remove(id)
    }

    @Test fun chatWaitsForTheSelectedModelAndShowsRealPreloadFailure() {
        ActivityScenario.launch(LocalChatActivity::class.java).use { scenario ->
            await(scenario) { it.findViewById<Button>(R.id.chat_start_model).isEnabled }
            scenario.onActivity {
                val send = it.findViewById<Button>(R.id.chat_send)
                assertFalse(send.isEnabled)
                it.updateResidency("LOADING", missing)
                assertEquals(View.VISIBLE, it.findViewById<ProgressBar>(R.id.chat_model_progress).visibility)
                assertFalse(send.isEnabled)
                it.updateResidency("READY", missing)
                assertTrue(send.isEnabled)
                it.updateResidency("READY", "a".repeat(64))
                assertFalse(send.isEnabled)
                assertTrue(it.findViewById<Button>(R.id.chat_start_model).isEnabled)
                it.findViewById<Button>(R.id.chat_start_model).performClick()
            }
            await(scenario) {
                it.findViewById<TextView>(R.id.chat_model_status).text.toString().startsWith("FAILED:")
            }
            scenario.onActivity {
                assertFalse(it.findViewById<Button>(R.id.chat_send).isEnabled)
                assertTrue(it.findViewById<Button>(R.id.chat_start_model).isEnabled)
                assertEquals(View.GONE, it.findViewById<ProgressBar>(R.id.chat_model_progress).visibility)
            }
        }
    }

    @Test fun serviceRejectsChatBeforePreloadWithoutTryingColdGeneration() {
        val connected = CountDownLatch(1)
        val client = ResidentModelClient(context) { if (it) connected.countDown() }
        try {
            client.connect()
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val response = JSONObject(client.call(missing, "Hello").execute())
            assertFalse(response.getBoolean("ok"))
            assertEquals("PLANNER_MODEL_NOT_READY", response.getString("error"))
        } finally { client.disconnect() }
    }

    private fun await(scenario: ActivityScenario<LocalChatActivity>, predicate: (LocalChatActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Chat did not observe resident-model state")
    }
}
