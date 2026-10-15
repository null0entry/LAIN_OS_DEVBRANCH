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

    @Test fun constrainedGenerationUsesThePackagedJniEntryPoint() {
        val file = java.io.File.createTempFile("lain-grammar-jni-", ".gguf", context.cacheDir)
        file.writeBytes(ByteArray(32))
        val id = 9001L
        try {
            assertEquals(null, GgufNativeProbe.registerGeneration(id))
            val result = GgufNativeProbe.generate(file.absolutePath, "prompt", 4096, 16,
                id, OnDeviceJsonGrammar.routing())
            // A readable invalid model must reach native loading, rather than
            // failing with a missing symbol or silently using an unconstrained path.
            assertEquals(GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_LOAD_FAILED), result)
        } finally {
            GgufNativeProbe.releaseGeneration(id)
            file.delete()
        }
    }

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
