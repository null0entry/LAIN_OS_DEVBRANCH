package dev.lain.os.planner

import org.json.JSONObject
import java.net.URI

/**
 * Non-secret planner identity and transport configuration.
 *
 * Raw credentials never belong in this model. [credentialRef] must be an opaque reference
 * previously issued by [SecretStore].
 */
internal data class PlannerProfile(
    val id: String,
    val name: String,
    val mode: String,
    val protocol: String,
    val baseUrl: String,
    val model: String,
    val credentialRef: String?,
    val timeoutSeconds: Double,
    val maxResponseBytes: Int,
    val responseMode: String,
    val allowInsecureLanHttp: Boolean,
) {
    init {
        require(PROFILE_ID.matches(id)) { "invalid planner profile id" }
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) {
            "invalid planner profile name"
        }
        require(name.none { it.isISOControl() }) { "invalid planner profile name" }
        require(mode in setOf("demo", "cloud", "local", "on_device")) { "invalid planner profile mode" }
        require(credentialRef == null || CREDENTIAL_REF.matches(credentialRef)) {
            "invalid planner credential reference"
        }

        if (mode == "demo") {
            require(id == DEMO_ID) { "demo planner id is reserved" }
            require(protocol == "demo_v1")
            require(baseUrl.isEmpty())
            require(model == "offline_demo")
            require(credentialRef == null)
            require(timeoutSeconds == 0.0)
            require(maxResponseBytes == 0)
            require(responseMode == "none")
            require(!allowInsecureLanHttp)
        } else if (mode == "on_device") {
            require(id != DEMO_ID)
            require(protocol == "gguf_native_v1")
            require(baseUrl.isEmpty() && credentialRef == null)
            require(GGUF_SHA.matches(model)) { "model must be a verified GGUF SHA-256" }
            require(timeoutSeconds.isFinite() && timeoutSeconds in 1.0..180.0)
            require(maxResponseBytes in 128..65536)
            require(responseMode == "none")
            require(!allowInsecureLanHttp)
        } else {
            require(id != DEMO_ID) { "offline demo profile id is reserved" }
            require(protocol == "openai_compatible_v1")
            require(baseUrl.isNotBlank() && baseUrl.toByteArray(Charsets.UTF_8).size <= MAX_URL_BYTES)
            require(model.isNotBlank() && model.toByteArray(Charsets.UTF_8).size <= MAX_MODEL_BYTES)
            require(model.none { it.isISOControl() }) { "invalid planner model" }
            require(timeoutSeconds.isFinite() && timeoutSeconds > 0.0)
            require(maxResponseBytes > 0)
            require(responseMode == "json_schema" || responseMode == "json_object")
            // TASK-003 established HTTPS-only native endpoints for 1.0.
            require(!allowInsecureLanHttp) { "Android planner profiles require HTTPS" }
            validateHttpsEndpoint(baseUrl)
        }
    }

    fun toTrustedBindingJson(): JSONObject = JSONObject()
        .put("profile_id", id)
        .put("mode", mode)
        .put("protocol", protocol)
        .put("base_url", baseUrl)
        .put("model", model)
        .put("credential_ref", credentialRef ?: JSONObject.NULL)
        .put("timeout_seconds", timeoutSeconds)
        .put("max_response_bytes", maxResponseBytes)
        .put("response_mode", responseMode)
        .put("allow_insecure_lan_http", allowInsecureLanHttp)

    internal fun toStorageJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("mode", mode)
        .put("protocol", protocol)
        .put("base_url", baseUrl)
        .put("model", model)
        .put("credential_ref", credentialRef ?: JSONObject.NULL)
        .put("timeout_seconds", timeoutSeconds)
        .put("max_response_bytes", maxResponseBytes)
        .put("response_mode", responseMode)
        .put("allow_insecure_lan_http", allowInsecureLanHttp)

    companion object {
        const val DEMO_ID = "offline-demo"

        private const val MAX_NAME_BYTES = 128
        private const val MAX_MODEL_BYTES = 512
        private const val MAX_URL_BYTES = 4096

        private val GGUF_SHA = Regex("^[0-9a-f]{64}$")
        private val PROFILE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
        private val CREDENTIAL_REF = Regex("^cred_[0-9a-f]{32}$")
        private val STORAGE_FIELDS = setOf(
            "id",
            "name",
            "mode",
            "protocol",
            "base_url",
            "model",
            "credential_ref",
            "timeout_seconds",
            "max_response_bytes",
            "response_mode",
            "allow_insecure_lan_http",
        )

        fun offlineDemo() = PlannerProfile(
            id = DEMO_ID,
            name = "Offline Demo",
            mode = "demo",
            protocol = "demo_v1",
            baseUrl = "",
            model = "offline_demo",
            credentialRef = null,
            timeoutSeconds = 0.0,
            maxResponseBytes = 0,
            responseMode = "none",
            allowInsecureLanHttp = false,
        )

        internal fun fromStorageJson(raw: JSONObject): PlannerProfile {
            require(raw.keys().asSequence().toSet() == STORAGE_FIELDS) {
                "invalid planner profile fields"
            }
            return PlannerProfile(
                id = requireString(raw, "id"),
                name = requireString(raw, "name"),
                mode = requireString(raw, "mode"),
                protocol = requireString(raw, "protocol"),
                baseUrl = requireString(raw, "base_url"),
                model = requireString(raw, "model"),
                credentialRef = requireNullableString(raw, "credential_ref"),
                timeoutSeconds = requireDouble(raw, "timeout_seconds"),
                maxResponseBytes = requireInt(raw, "max_response_bytes"),
                responseMode = requireString(raw, "response_mode"),
                allowInsecureLanHttp = requireBoolean(raw, "allow_insecure_lan_http"),
            )
        }

        private fun validateHttpsEndpoint(value: String) {
            val uri = try {
                URI(value)
            } catch (exc: Exception) {
                throw IllegalArgumentException("invalid planner endpoint", exc)
            }
            require(
                uri.scheme?.lowercase() == "https" &&
                    !uri.host.isNullOrBlank() &&
                    uri.userInfo == null &&
                    uri.query == null &&
                    uri.fragment == null
            ) { "invalid planner endpoint" }
        }

        private fun requireString(raw: JSONObject, key: String): String {
            val value = raw.get(key)
            require(value is String) { "invalid planner profile field: $key" }
            return value
        }

        private fun requireNullableString(raw: JSONObject, key: String): String? {
            val value = raw.get(key)
            if (value === JSONObject.NULL) return null
            require(value is String) { "invalid planner profile field: $key" }
            return value
        }

        private fun requireDouble(raw: JSONObject, key: String): Double {
            val value = raw.get(key)
            require(value is Number) { "invalid planner profile field: $key" }
            return value.toDouble()
        }

        private fun requireInt(raw: JSONObject, key: String): Int {
            val value = raw.get(key)
            require(value is Number) { "invalid planner profile field: $key" }
            val number = value.toDouble()
            require(
                number.isFinite() &&
                    number % 1.0 == 0.0 &&
                    number >= Int.MIN_VALUE.toDouble() &&
                    number <= Int.MAX_VALUE.toDouble()
            ) { "invalid planner profile field: $key" }
            return number.toInt()
        }

        private fun requireBoolean(raw: JSONObject, key: String): Boolean {
            val value = raw.get(key)
            require(value is Boolean) { "invalid planner profile field: $key" }
            return value
        }
    }
}
