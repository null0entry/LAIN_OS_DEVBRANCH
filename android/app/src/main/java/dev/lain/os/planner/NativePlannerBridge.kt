package dev.lain.os.planner

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicReference

/**
 * Small Python-facing boundary over the native bounded planner transport.
 * Raw credentials remain inside [NativePlannerTransport]/[SecretStore].
 */
internal class NativePlannerBridge(
    private val transport: NativePlannerTransport,
    private val modelStore: OnDeviceModelStore? = null,
) {
    constructor(context: Context) : this(
        NativePlannerTransport(AndroidKeystoreSecretStore(context)),
        OnDeviceModelStore(context),
    )

    private val activeCall = AtomicReference<PlannerTransportCall?>(null)
    private val activeNativeCall = AtomicReference<OnDevicePlannerCall?>(null)

    fun execute(bindingJson: String, requestBody: String): String {
        if (
            bindingJson.toByteArray(Charsets.UTF_8).size > MAX_BINDING_BYTES ||
            requestBody.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BYTES
        ) {
            return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED)
        }
        val mode = try {
            val tokener = JSONTokener(bindingJson)
            val raw = tokener.nextValue()
            require(raw is JSONObject && tokener.nextClean() == '\u0000')
            raw.getString("mode")
        } catch (_: Exception) { return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED) }
        if (mode == "on_device") return executeOnDevice(bindingJson, requestBody)
        if (activeNativeCall.get() != null) return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED)
        val binding = try {
            parseBinding(bindingJson)
        } catch (_: Exception) {
            return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED)
        }
        val call = transport.newCall(binding, requestBody)
        if (!activeCall.compareAndSet(null, call)) {
            return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED)
        }
        return try {
            when (val result = call.execute()) {
                is PlannerTransportResult.Success -> JSONObject()
                    .put("ok", true)
                    .put("body", result.body)
                    .toString()
                is PlannerTransportResult.Failure -> failure(result.code)
            }
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    /** This branch cannot reach HTTP transport, even if model load fails. */
    private fun executeOnDevice(bindingJson: String, requestBody: String): String {
        val store = modelStore ?: return failure("PLANNER_MODEL_NOT_FOUND")
        val call = try {
            val tokener = JSONTokener(bindingJson)
            val raw = tokener.nextValue() as? JSONObject
                ?: throw IllegalArgumentException("bad native binding")
            require(tokener.nextClean() == '\u0000' && raw.keys().asSequence().toSet() == BINDING_FIELDS)
            require(strictString(raw, "mode") == "on_device")
            require(strictString(raw, "protocol") == "gguf_native_v1")
            require(strictString(raw, "profile_id").isNotBlank())
            val digest = strictString(raw, "model")
            require(Regex("^[0-9a-f]{64}$").matches(digest))
            require(strictString(raw, "base_url").isEmpty())
            require(raw.get("credential_ref") === JSONObject.NULL)
            require(strictString(raw, "response_mode") == "none")
            require(raw.get("allow_insecure_lan_http") === false)
            val timeout = strictNumber(raw, "timeout_seconds").toDouble()
            require(timeout.isFinite() && timeout in 1.0..180.0)
            val maxResponse = strictInt(raw, "max_response_bytes")
            require(maxResponse in 128..65536)
            OnDevicePlannerCall(store, digest, requestBody, timeout, maxResponse)
        } catch (_: Exception) {
            return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED)
        }
        if (activeCall.get() != null || !activeNativeCall.compareAndSet(null, call)) {
            return failure(NativePlannerTransport.ERROR_TRANSPORT_FAILED)
        }
        return try { call.execute() }
        finally { activeNativeCall.compareAndSet(call, null) }
    }

    fun cancel() {
        activeCall.get()?.cancel()
        activeNativeCall.get()?.cancel()
    }

    private fun failure(code: String): String = JSONObject()
        .put("ok", false)
        .put("error", code)
        .toString()

    private fun parseBinding(payload: String): PlannerBinding {
        val tokener = JSONTokener(payload)
        val raw = tokener.nextValue()
        require(raw is JSONObject && tokener.nextClean() == '\u0000')
        require(raw.keys().asSequence().toSet() == BINDING_FIELDS)

        val credential = raw.get("credential_ref")
        return PlannerBinding(
            profileId = strictString(raw, "profile_id"),
            mode = strictString(raw, "mode"),
            protocol = strictString(raw, "protocol"),
            baseUrl = strictString(raw, "base_url"),
            model = strictString(raw, "model"),
            credentialRef = if (credential === JSONObject.NULL) null else credential as? String
                ?: throw IllegalArgumentException("invalid credential ref"),
            timeoutSeconds = strictNumber(raw, "timeout_seconds").toDouble(),
            maxResponseBytes = strictInt(raw, "max_response_bytes"),
            responseMode = strictString(raw, "response_mode"),
            allowInsecureLanHttp = raw.get("allow_insecure_lan_http") as? Boolean
                ?: throw IllegalArgumentException("invalid planner binding"),
        )
    }

    private fun strictString(raw: JSONObject, key: String): String =
        raw.get(key) as? String ?: throw IllegalArgumentException("invalid planner binding")

    private fun strictNumber(raw: JSONObject, key: String): Number =
        raw.get(key) as? Number ?: throw IllegalArgumentException("invalid planner binding")

    private fun strictInt(raw: JSONObject, key: String): Int {
        val value = strictNumber(raw, key).toDouble()
        require(
            value.isFinite() &&
                value % 1.0 == 0.0 &&
                value >= Int.MIN_VALUE.toDouble() &&
                value <= Int.MAX_VALUE.toDouble()
        )
        return value.toInt()
    }

    companion object {
        private const val MAX_BINDING_BYTES = 8 * 1024
        private const val MAX_REQUEST_BYTES = 1024 * 1024
        private val BINDING_FIELDS = setOf(
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
        )
    }
}
