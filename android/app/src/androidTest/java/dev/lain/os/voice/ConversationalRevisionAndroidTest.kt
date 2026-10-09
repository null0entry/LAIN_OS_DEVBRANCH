package dev.lain.os.voice

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lain.os.MainActivity
import dev.lain.os.R
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

@RunWith(AndroidJUnit4::class)
class ConversationalRevisionAndroidTest {
    private class ActiveSessionBinding : RuntimeBinding {
        val requests = Collections.synchronizedList(mutableListOf<JSONObject>())
        // Synchronized collection operations do not synchronize iteration:
        // snapshot under the same monitor before any any/count/filter in test threads.
        fun requestsSnapshot(): List<JSONObject> = synchronized(requests) { requests.toList() }
        private val sessionId = UUID.randomUUID().toString()
        private var revision = "revision-1"

        private fun summary() = JSONObject()
            .put("session_id", sessionId)
            .put("label", "Custom task")
            .put("status", "planning")
            .put("updated_at", "fixture")

        private fun session() = summary()
            .put("revision", revision)
            .put("active", true)
            .put("recovery_required", false)
            .put("stop_requested", false)
            .put("actions", JSONArray())
            .put("attempted_actions", 0)
            .put("iterations", 0)
            .put("speech_text", JSONObject.NULL)
            .put("planner", JSONObject()
                .put("profile_id", "offline-demo")
                .put("mode", "demo")
                .put("model", "offline_demo"))

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
                    "sessions" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("sessions", JSONArray().put(summary()))
                    "inspect" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("session", session())
                    "turn_submit" -> {
                        revision = "revision-2"
                        JSONObject()
                            .put("version", 1)
                            .put("ok", true)
                            .put("turn", JSONObject().put("route", "revision"))
                            .put("revision_applied", JSONObject().put("turn_id", 2))
                            .put("session", session())
                    }
                    "stop" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("stop_requested", true)
                        .put("session_id", sessionId)
                    else -> JSONObject(RuntimeProtocol.failure("APP_REQUEST_INVALID"))
                }
                reply.writeNoException()
                reply.writeString(response.toString())
                return true
            }
        }

        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(ComponentName("dev.lain.os", "RevisionTestRuntime"), binder)
            return true
        }

        override fun unbind(connection: ServiceConnection) = Unit
    }

    @After fun resetFactory() {
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application) }
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
        throw AssertionError("Timed out waiting for active revision UI")
    }

    @Test fun activeSessionAcceptsTypedAndFinalSpeechRevisionThroughSameTurnRoute() {
        val binding = ActiveSessionBinding()
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application, binding) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                val model = ViewModelProvider(it)[WorkbenchViewModel::class.java]
                model.state.value?.ready == true &&
                    model.state.value?.session?.optBoolean("active") == true
            }

            scenario.onActivity {
                assertTrue(it.findViewById<android.view.View>(R.id.run_button).isEnabled)
                assertTrue(it.findViewById<android.view.View>(R.id.command_input).isEnabled)
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).isEnabled)

                val model = ViewModelProvider(it)[WorkbenchViewModel::class.java]
                model.run("Make it shorter")
            }
            await(scenario) {
                binding.requestsSnapshot().any { request ->
                    request.optString("command") == "turn_submit" &&
                        request.getJSONObject("arguments").optString("source") == "typed"
                }
            }

            scenario.onActivity {
                val model = ViewModelProvider(it)[WorkbenchViewModel::class.java]
                assertTrue(model.submitSpeech("Use fewer words"))
            }
            await(scenario) {
                binding.requestsSnapshot().count { it.optString("command") == "turn_submit" } >= 2
            }
        }

        val turns = binding.requestsSnapshot().filter { it.optString("command") == "turn_submit" }
        assertEquals(2, turns.size)
        assertEquals(listOf("typed", "speech"), turns.map {
            it.getJSONObject("arguments").getString("source")
        })
        turns.forEach {
            val arguments = it.getJSONObject("arguments")
            assertEquals("revision", arguments.getString("kind"))
            assertEquals("active", arguments.getString("reference"))
            assertTrue(arguments.isNull("target_session_id"))
        }
    }
}
