package dev.lain.os.voice

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lain.os.MainActivity
import dev.lain.os.R
import dev.lain.os.runtime.RuntimeBinding
import dev.lain.os.runtime.RuntimeClient
import dev.lain.os.runtime.RuntimeProtocol
import dev.lain.os.ui.WorkbenchViewModel
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationalTranscriptAndroidTest {
    private class TranscriptBinding : RuntimeBinding {
        private val sessionId = UUID.randomUUID().toString()
        var turnsRequests = 0

        private fun summary() = JSONObject()
            .put("session_id", sessionId)
            .put("label", "Custom task")
            .put("status", "complete")
            .put("updated_at", "fixture")

        private fun session() = summary()
            .put("revision", "revision-3")
            .put("active", false)
            .put("recovery_required", false)
            .put("stop_requested", false)
            .put("actions", JSONArray())
            .put("attempted_actions", 0)
            .put("iterations", 1)
            .put("speech_text", "Finished the task.")
            .put("planner", JSONObject()
                .put("profile_id", "offline-demo")
                .put("mode", "demo")
                .put("model", "offline_demo"))

        private fun conversation() = JSONObject()
            .put("next_turn_id", 4)
            .put("active_task_id", JSONObject.NULL)
            .put("partial_text", JSONObject.NULL)
            .put("turns", JSONArray()
                .put(JSONObject()
                    .put("turn_id", 1)
                    .put("source", "typed")
                    .put("kind", "task")
                    .put("finality", "final")
                    .put("text", "Start typed")
                    .put("route", "start_task")
                    .put("target_session_id", JSONObject.NULL))
                .put(JSONObject()
                    .put("turn_id", 2)
                    .put("source", "speech")
                    .put("kind", "revision")
                    .put("finality", "final")
                    .put("text", "Use fewer words")
                    .put("route", "revision")
                    .put("target_session_id", sessionId))
                .put(JSONObject()
                    .put("turn_id", 3)
                    .put("source", "typed")
                    .put("kind", "revision")
                    .put("finality", "final")
                    .put("text", "Keep the title")
                    .put("route", "revision")
                    .put("target_session_id", sessionId)))

        private val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(RuntimeProtocol.DESCRIPTOR)
                    return true
                }
                if (code != RuntimeProtocol.REQUEST || reply == null) return false
                data.enforceInterface(RuntimeProtocol.DESCRIPTOR)
                val request = JSONObject(data.readString() ?: error("missing request"))
                val response = when (request.getString("command")) {
                    "sessions" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("sessions", JSONArray().put(summary()))
                    "inspect" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("session", session())
                    "turns" -> {
                        turnsRequests += 1
                        JSONObject()
                            .put("version", 1)
                            .put("ok", true)
                            .put("conversation", conversation())
                    }
                    else -> JSONObject(RuntimeProtocol.failure("APP_REQUEST_INVALID"))
                }
                reply.writeNoException()
                reply.writeString(response.toString())
                return true
            }
        }

        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(ComponentName("dev.lain.os", "TranscriptRuntime"), binder)
            return true
        }

        override fun unbind(connection: ServiceConnection) = Unit
    }

    @After fun resetFactory() {
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application) }
    }

    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(texts(view.getChildAt(index)))
        }
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
        throw AssertionError("Timed out waiting for transcript UI")
    }

    @Test fun acceptedTurnsAndAssistantReplyRenderInDeterministicOrderAcrossRecreation() {
        val binding = TranscriptBinding()
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application, binding) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { activity ->
                val screen = texts(activity.findViewById(R.id.workbench_root))
                screen.any { it.contains("Start typed") } &&
                    screen.any { it.contains("Use fewer words") } &&
                    screen.any { it.contains("Keep the title") } &&
                    screen.any { it.contains("Finished the task.") }
            }

            var before = emptyList<String>()
            scenario.onActivity { activity ->
                before = texts(activity.findViewById(R.id.workbench_root))
                    .filter {
                        it.contains("Start typed") ||
                            it.contains("Use fewer words") ||
                            it.contains("Keep the title") ||
                            it.contains("Finished the task.")
                    }
                assertEquals(4, before.size)
                assertTrue(before[0].contains("typed", ignoreCase = true))
                assertTrue(before[1].contains("speech", ignoreCase = true))
                assertTrue(before[1].contains("revision", ignoreCase = true))
                assertTrue(before[2].contains("typed", ignoreCase = true))
                assertTrue(before[3].contains("assistant", ignoreCase = true))
            }

            scenario.recreate()

            await(scenario) { activity ->
                texts(activity.findViewById(R.id.workbench_root))
                    .count { it.contains("Start typed") } == 1
            }
            scenario.onActivity { activity ->
                val after = texts(activity.findViewById(R.id.workbench_root))
                    .filter {
                        it.contains("Start typed") ||
                            it.contains("Use fewer words") ||
                            it.contains("Keep the title") ||
                            it.contains("Finished the task.")
                    }
                assertEquals(before, after)
            }
        }

        assertTrue(binding.turnsRequests >= 2)
    }
}
