package dev.lain.os

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dev.lain.os.planner.PlannerProfile
import dev.lain.os.planner.PlannerProfileStore
import dev.lain.os.runtime.NativeCapabilities
import dev.lain.os.runtime.RuntimeBinding
import dev.lain.os.runtime.RuntimeClient
import dev.lain.os.runtime.RuntimeProtocol
import dev.lain.os.ui.WorkbenchViewModel
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerRuntimeGuiAcceptanceTest {
    private class FakePlannerBridge {
        val modes = mutableListOf<String>()
        private var calls = 0

        fun execute(bindingJson: String, requestBody: String): String {
            val binding = JSONObject(bindingJson)
            val request = JSONObject(requestBody)
            modes += binding.getString("mode")
            assertEquals("model-a", request.getString("model"))
            calls += 1
            val decision = if (calls % 2 == 1) {
                JSONObject()
                    .put("status", "continue")
                    .put("reason", "read battery")
                    .put(
                        "actions",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "android.battery_status")
                                .put("arguments", JSONObject()),
                        ),
                    )
            } else {
                JSONObject()
                    .put("status", "complete")
                    .put("reason", "finished")
                    .put("actions", JSONArray())
            }
            val upstream = JSONObject().put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("message", JSONObject().put("content", decision.toString()))
                        .put("finish_reason", "stop"),
                ),
            )
            return JSONObject().put("ok", true).put("body", upstream.toString()).toString()
        }

        fun cancel() = Unit
    }

    private class InProcessRuntimeBinding(
        private val controller: PyObject,
    ) : RuntimeBinding {
        private val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(RuntimeProtocol.DESCRIPTOR)
                    return true
                }
                if (code != RuntimeProtocol.REQUEST || reply == null) return false
                data.enforceInterface(RuntimeProtocol.DESCRIPTOR)
                val payload = data.readString() ?: error("missing payload")
                val command = JSONObject(payload).getString("command")
                val response = controller.callAttr("dispatch", payload).toString()
                if (command in setOf("start", "approve", "resume")) {
                    while (controller.callAttr("advance").toBoolean()) {
                        // Drain the same bounded controller pump used by RuntimeService.
                    }
                }
                reply.writeNoException()
                reply.writeString(response)
                return true
            }
        }

        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(ComponentName("dev.lain.os", "InProcessRuntime"), binder)
            return true
        }

        override fun unbind(connection: ServiceConnection) = Unit
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for planner runtime GUI acceptance state")
    }

    @Test fun cloudAndLocalProfilesCompleteTrustedActionThroughInstalledWorkbench() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        val store = PlannerProfileStore(context)

        for (mode in listOf("cloud", "local")) {
            val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
            val profileId = "gui-$mode-$suffix"
            val profile = PlannerProfile(
                id = profileId,
                name = "GUI $mode $suffix",
                mode = mode,
                protocol = "openai_compatible_v1",
                baseUrl = "https://planner.example.invalid/v1",
                model = "model-a",
                credentialRef = if (mode == "cloud") {
                    "cred_0123456789abcdef0123456789abcdef"
                } else {
                    null
                },
                timeoutSeconds = 30.0,
                maxResponseBytes = 1_048_576,
                responseMode = "json_schema",
                allowInsecureLanHttp = false,
            )
            store.upsert(profile)
            store.select(profileId)

            val bridge = FakePlannerBridge()
            val root = File(context.filesDir, "lain-gui-acceptance/$profileId")
            val controller = Python.getInstance().getModule("lain.app.control").callAttr(
                "create_controller",
                root.absolutePath,
                NativeCapabilities(context),
                store,
                bridge,
            )
            val binding = InProcessRuntimeBinding(controller)
            WorkbenchViewModel.runtimeClientFactory = { application ->
                RuntimeClient(application, binding)
            }

            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    await(scenario) {
                        it.findViewById<View>(R.id.run_button).isEnabled &&
                            it.findViewById<TextView>(R.id.active_planner).text.toString()
                                .contains(profile.name)
                    }
                    scenario.onActivity {
                        it.findViewById<EditText>(R.id.command_input).setText("Read battery")
                        assertTrue(it.findViewById<View>(R.id.run_button).performClick())
                    }
                    await(scenario) {
                        it.findViewById<TextView>(R.id.task_status).text.toString() == "COMPLETE"
                    }
                    scenario.onActivity {
                        val results = it.findViewById<TextView>(R.id.task_status).text.toString()
                        assertEquals("COMPLETE", results)
                    }
                }
                assertEquals(listOf(mode, mode), bridge.modes)
            } finally {
                WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application) }
                store.select(PlannerProfile.DEMO_ID)
                store.remove(profileId)
                root.deleteRecursively()
            }
        }
    }
}
