package dev.lain.os.planner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceNativeBridgeAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val missing = "a".repeat(64)
    private val binding = PlannerProfile(
        id = "offline-a", name = "Offline test", mode = "on_device",
        protocol = "gguf_native_v1", baseUrl = "", model = missing,
        credentialRef = null, timeoutSeconds = 120.0,
        maxResponseBytes = 65536, responseMode = "none", allowInsecureLanHttp = false,
    ).toTrustedBindingJson()

    @Test fun absentModelFailsClosedWithoutHttpOrDemoFallback() {
        val request = JSONObject()
            .put("mode", "agent").put("version", "0")
            .put("goal", "show battery").put("context", JSONObject())
            .put("capabilities", org.json.JSONArray())
            .put("constraints", JSONObject()).put("instructions", "return strict JSON")
        val result = JSONObject(NativePlannerBridge(context).execute(binding.toString(), request.toString()))
        assertFalse(result.getBoolean("ok"))
        assertEquals("PLANNER_MODEL_NOT_FOUND", result.getString("error"))
    }

    @Test fun forgedOnDeviceProfileWithEndpointIsRejectedBeforeWork() {
        val forged = JSONObject(binding.toString()).put("base_url", "https://cloud.invalid/v1")
        val result = JSONObject(NativePlannerBridge(context).execute(forged.toString(), "{}"))
        assertFalse(result.getBoolean("ok"))
        assertEquals("PLANNER_TRANSPORT_FAILED", result.getString("error"))
    }
}
