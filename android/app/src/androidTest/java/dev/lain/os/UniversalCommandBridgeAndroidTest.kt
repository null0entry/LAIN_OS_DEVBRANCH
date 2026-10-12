package dev.lain.os

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lain.os.runtime.RuntimeBinding
import dev.lain.os.runtime.RuntimeClient
import dev.lain.os.runtime.RuntimeProtocol
import dev.lain.os.ui.WorkbenchViewModel
import java.util.Collections
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The final typed and final voice inputs share one exact IPC envelope.
 * This test simulates an already accepted FINAL transcript; microphone
 * permissions/ASR confidence gates are independently tested by the voice suite.
 */
@RunWith(AndroidJUnit4::class)
class UniversalCommandBridgeAndroidTest {
    private class RecordingBinding : RuntimeBinding {
        val requests = Collections.synchronizedList(mutableListOf<JSONObject>())
        fun snapshot(): List<JSONObject> = synchronized(requests) { requests.toList() }
        private val id = UUID.randomUUID().toString()
        private fun session() = JSONObject()
            .put("session_id", id)
            .put("status", "complete")
            .put("label", "Show battery")
            .put("revision", "v1")
            .put("active", false)
            .put("recovery_required", false)
            .put("stop_requested", false)
            .put("actions", JSONArray())
            .put("attempted_actions", 0)
            .put("iterations", 0)
            .put("speech_text", JSONObject.NULL)

        private val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(RuntimeProtocol.DESCRIPTOR)
                    return true
                }
                if (code != RuntimeProtocol.REQUEST || reply == null) return false
                data.enforceInterface(RuntimeProtocol.DESCRIPTOR)
                val request = JSONObject(data.readString() ?: error("missing request"))
                requests += request
                val response = when (request.getString("command")) {
                    "sessions" -> JSONObject().put("version", 1).put("ok", true)
                        .put("sessions", JSONArray())
                    "turns" -> JSONObject().put("version", 1).put("ok", true)
                        .put("conversation", JSONObject()
                            .put("next_turn_id", 1)
                            .put("active_task_id", JSONObject.NULL)
                            .put("turns", JSONArray())
                            .put("partial_text", JSONObject.NULL))
                    "inspect" -> JSONObject().put("version", 1).put("ok", true)
                        .put("session", session())
                    "turn_submit" -> JSONObject().put("version", 1).put("ok", true)
                        .put("turn", JSONObject().put("route", "start_task"))
                        .put("session", session())
                    else -> JSONObject(RuntimeProtocol.failure("APP_REQUEST_INVALID"))
                }
                reply.writeNoException()
                reply.writeString(response.toString())
                return true
            }
        }

        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(ComponentName("dev.lain.os", "BridgeTestRuntime"), binder)
            return true
        }
        override fun unbind(connection: ServiceConnection) = Unit
    }

    @After fun resetFactory() {
        WorkbenchViewModel.runtimeClientFactory = { app -> RuntimeClient(app) }
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(50)
        }
        throw AssertionError("timed out waiting for command bridge")
    }

    @Test fun typedAndAcceptedFinalSpeechShareTheSameAutoRoute() {
        val binding = RecordingBinding()
        WorkbenchViewModel.runtimeClientFactory = { app -> RuntimeClient(app, binding) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                ViewModelProvider(it)[WorkbenchViewModel::class.java].state.value?.ready == true
            }
            scenario.onActivity {
                ViewModelProvider(it)[WorkbenchViewModel::class.java].run("Show battery")
            }
            await(scenario) {
                binding.snapshot().count { it.optString("command") == "turn_submit" } == 1 &&
                    ViewModelProvider(it)[WorkbenchViewModel::class.java].state.value?.pending == false
            }
            scenario.onActivity {
                assertTrue(ViewModelProvider(it)[WorkbenchViewModel::class.java]
                    .submitSpeech("Show battery"))
            }
            await(scenario) {
                binding.snapshot().count { it.optString("command") == "turn_submit" } == 2
            }
        }
        val submissions = binding.snapshot().filter { it.optString("command") == "turn_submit" }
        assertEquals(2, submissions.size)
        assertEquals(listOf("typed", "speech"), submissions.map {
            it.getJSONObject("arguments").getString("source")
        })
        submissions.forEach {
            val args = it.getJSONObject("arguments")
            assertEquals("Show battery", args.getString("text"))
            assertEquals("auto", args.getString("kind"))
            assertEquals("none", args.getString("reference"))
            assertTrue(args.isNull("target_session_id"))
        }
        assertEquals(0, binding.snapshot().count { it.optString("command") == "start" })
    }
    @Test fun replayedFinalSpeechAfterRecreateDoesNotSubmitTwice() {
        val binding = RecordingBinding()
        WorkbenchViewModel.runtimeClientFactory = { app -> RuntimeClient(app, binding) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                ViewModelProvider(it)[WorkbenchViewModel::class.java].state.value?.ready == true
            }
            scenario.onActivity {
                assertTrue(ViewModelProvider(it)[WorkbenchViewModel::class.java]
                    .submitSpeech("Show battery", finalId = 42L))
            }
            await(scenario) {
                binding.snapshot().count { it.optString("command") == "turn_submit" } == 1 &&
                    ViewModelProvider(it)[WorkbenchViewModel::class.java].state.value?.pending == false
            }
            scenario.recreate()
            await(scenario) {
                ViewModelProvider(it)[WorkbenchViewModel::class.java].state.value?.ready == true
            }
            scenario.onActivity {
                val model = ViewModelProvider(it)[WorkbenchViewModel::class.java]
                assertTrue(model.submitSpeech("Show battery", finalId = 42L))
                assertTrue(model.submitSpeech("Show battery", finalId = 43L))
            }
            await(scenario) {
                binding.snapshot().count { it.optString("command") == "turn_submit" } == 2
            }
        }
        assertEquals(2, binding.snapshot().count { it.optString("command") == "turn_submit" })
    }

}
