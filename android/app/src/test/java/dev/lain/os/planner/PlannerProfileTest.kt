package dev.lain.os.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject

class PlannerProfileTest {

    @Test fun onDeviceProfilePinsShaAndForbidsEndpointAndCredential() {
        val profile = PlannerProfile(
            id = "offline-qwen", name = "Qwen Offline", mode = "on_device",
            protocol = "gguf_native_v1", baseUrl = "", model = "a".repeat(64),
            credentialRef = null, timeoutSeconds = 120.0,
            maxResponseBytes = 65536, responseMode = "none", allowInsecureLanHttp = false
        )
        assertEquals("on_device", profile.toTrustedBindingJson().getString("mode"))
        assertEquals("a".repeat(64), profile.toTrustedBindingJson().getString("model"))
        val invalid = listOf(
            { profile.copy(baseUrl = "https://api.example.invalid/v1") },
            { profile.copy(credentialRef = "cred_0123456789abcdef0123456789abcdef") },
            { profile.copy(model = "../evil.gguf") },
            { profile.copy(model = "b".repeat(63)) },
            { profile.copy(responseMode = "json_schema") },
            { profile.copy(allowInsecureLanHttp = true) }
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test fun demoProfileProducesTrustedOfflineBinding() {
        val profile = PlannerProfile.offlineDemo()

        assertEquals("offline-demo", profile.id)
        assertEquals("demo", profile.mode)
        assertEquals("offline_demo", profile.model)
        assertEquals(
            setOf(
                "profile_id",
                "mode",
                "protocol",
                "base_url",
                "model",
                "credential_ref",
                "timeout_seconds",
                "max_response_bytes",
                "response_mode",
                "allow_insecure_lan_http",
            ),
            profile.toTrustedBindingJson().keys().asSequence().toSet(),
        )
        assertFalse(profile.toTrustedBindingJson().toString().contains("sk-raw-secret"))
    }

    @Test fun cloudAndLocalProfilesShareOneStrictSchema() {
        val cloud = PlannerProfile(
            id = "cloud-primary",
            name = "Cloud primary",
            mode = "cloud",
            protocol = "openai_compatible_v1",
            baseUrl = "https://api.example.invalid/v1",
            model = "model-a",
            credentialRef = "cred_0123456789abcdef0123456789abcdef",
            timeoutSeconds = 30.0,
            maxResponseBytes = 1_048_576,
            responseMode = "json_schema",
            allowInsecureLanHttp = false,
        )
        val local = cloud.copy(
            id = "local-primary",
            name = "Local primary",
            mode = "local",
            baseUrl = "https://192.168.1.50:8443/v1",
            credentialRef = null,
        )

        assertEquals("cloud-primary", cloud.toTrustedBindingJson().getString("profile_id"))
        assertEquals("local", local.toTrustedBindingJson().getString("mode"))
    }

    @Test fun diagnosticProbeIsBoundedAndDoesNotRequireStructuredGeneration() {
        val profile = PlannerProfile(
            id = "cloud-primary",
            name = "Cloud primary",
            mode = "cloud",
            protocol = "openai_compatible_v1",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "openai/gpt-oss-120b",
            credentialRef = "cred_0123456789abcdef0123456789abcdef",
            timeoutSeconds = 30.0,
            maxResponseBytes = 1_048_576,
            responseMode = "json_schema",
            allowInsecureLanHttp = false,
        )
        val request = JSONObject(buildPlannerDiagnosticRequest(profile))
        assertEquals(64, request.getInt("max_completion_tokens"))
        assertFalse(request.has("response_format"))
    }

    @Test fun plannerConnectionStatusPreservesProviderFailureClass() {
        assertEquals(
            PlannerConnectionStatus.REQUEST_REJECTED,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_HTTP_REJECTED),
        )
        assertEquals(
            PlannerConnectionStatus.RATE_LIMITED,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_RATE_LIMITED),
        )
        assertEquals(
            PlannerConnectionStatus.SERVER_ERROR,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_SERVER),
        )
        assertEquals(
            PlannerConnectionStatus.AUTHENTICATION_REJECTED,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_AUTH_REJECTED),
        )
        assertEquals(
            PlannerConnectionStatus.MODEL_UNAVAILABLE,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_MODEL_NOT_FOUND),
        )
        assertEquals(
            PlannerConnectionStatus.TIMED_OUT,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_TIMEOUT),
        )
        assertEquals(
            PlannerConnectionStatus.TLS_FAILURE,
            plannerConnectionStatusFor(NativePlannerTransport.ERROR_TLS),
        )
    }

    @Test fun invalidEndpointsModelsAndCredentialValuesFailClosed() {
        val base = PlannerProfile(
            id = "cloud-primary",
            name = "Cloud primary",
            mode = "cloud",
            protocol = "openai_compatible_v1",
            baseUrl = "https://api.example.invalid/v1",
            model = "model-a",
            credentialRef = "cred_0123456789abcdef0123456789abcdef",
            timeoutSeconds = 30.0,
            maxResponseBytes = 1_048_576,
            responseMode = "json_schema",
            allowInsecureLanHttp = false,
        )

        val invalid = listOf(
            { base.copy(baseUrl = "http://api.example.invalid/v1") },
            { base.copy(baseUrl = "https://user:secret@api.example.invalid/v1") },
            { base.copy(baseUrl = "https://api.example.invalid/v1?token=secret") },
            { base.copy(model = " ") },
            { base.copy(credentialRef = "sk-raw-secret") },
            { base.copy(timeoutSeconds = Double.NaN) },
            { base.copy(maxResponseBytes = 0) },
            { base.copy(responseMode = "none") },
            { base.copy(mode = "local", allowInsecureLanHttp = true) },
        )

        invalid.forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { candidate() }
        }
    }
}
