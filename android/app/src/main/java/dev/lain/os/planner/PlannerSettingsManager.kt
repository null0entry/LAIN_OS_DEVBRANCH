package dev.lain.os.planner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

internal data class PlannerProfileDraft(
    val profileId: String? = null,
    val name: String,
    val mode: String,
    val baseUrl: String,
    val model: String,
    val timeoutSeconds: Double = 30.0,
    val maxResponseBytes: Int = 1_048_576,
    val responseMode: String = "json_schema",
    val credential: String? = null,
)

internal data class PlannerProfileSummary(
    val id: String,
    val name: String,
    val mode: String,
    val baseUrl: String,
    val model: String,
    val timeoutSeconds: Double,
    val maxResponseBytes: Int,
    val responseMode: String,
    val credentialSaved: Boolean,
)

internal data class PlannerSettingsSnapshot(
    val activeProfileId: String,
    val profiles: List<PlannerProfileSummary>,
)

internal enum class PlannerConnectionStatus {
    OFFLINE_DEMO,
    MODEL_READY,
    CONNECTED,
    AUTHENTICATION_REJECTED,
    MODEL_UNAVAILABLE,
    ENDPOINT_UNREACHABLE,
    TLS_FAILURE,
    TIMED_OUT,
    REQUEST_REJECTED,
    RATE_LIMITED,
    SERVER_ERROR,
    RESPONSE_UNSUPPORTED,
    MISSING_CREDENTIAL,
    UNAVAILABLE,
}

internal class PlannerSettingsManager(
    private val profiles: PlannerProfileStore,
    private val secrets: SecretStore,
    private val diagnostic: (PlannerProfile) -> PlannerConnectionStatus,
    private val modelStore: OnDeviceModelStore? = null,
) {
    // Preserve the existing trailing-lambda diagnostic constructor used by callers/tests.
    constructor(
        profiles: PlannerProfileStore,
        secrets: SecretStore,
        diagnostic: (PlannerProfile) -> PlannerConnectionStatus,
    ) : this(profiles, secrets, diagnostic, null)

    constructor(context: Context) : this(
        PlannerProfileStore(context),
        AndroidKeystoreSecretStore(context),
        PlannerConnectionDiagnostic(context)::test,
        OnDeviceModelStore(context),
    )

    fun snapshot(): PlannerSettingsSnapshot {
        val active = profiles.active().id
        return PlannerSettingsSnapshot(
            activeProfileId = active,
            profiles = profiles.list().map { profile ->
                PlannerProfileSummary(
                    id = profile.id,
                    name = profile.name,
                    mode = profile.mode,
                    baseUrl = profile.baseUrl,
                    model = profile.model,
                    timeoutSeconds = profile.timeoutSeconds,
                    maxResponseBytes = profile.maxResponseBytes,
                    responseMode = profile.responseMode,
                    credentialSaved = profile.credentialRef?.let { ref ->
                        try {
                            secrets.contains(ref)
                        } catch (_: SecretStoreException) {
                            false
                        }
                    } ?: false,
                )
            },
        )
    }

    fun save(draft: PlannerProfileDraft): String {
        require(draft.mode in setOf("cloud", "local", "on_device")) {
            "unknown planner profile mode"
        }
        val existing = draft.profileId?.let { id ->
            require(id != PlannerProfile.DEMO_ID) { "offline demo profile is built in" }
            profiles.get(id) ?: throw IllegalArgumentException("planner profile does not exist")
        }
        require(existing == null || existing.mode == draft.mode) {
            "planner profile mode is immutable; create a new profile"
        }
        if (draft.mode == "on_device") {
            require(draft.baseUrl.isEmpty() && draft.credential.isNullOrBlank())
            val store = modelStore ?: throw IllegalArgumentException("local model store unavailable")
            store.resolve(draft.model) // Hash+path proof before making a profile selectable.
        }
        val id = existing?.id ?: newProfileId()
        val replacement = draft.credential?.takeIf { it.isNotBlank() }
        var createdRef: String? = null
        val credentialRef = when {
            replacement == null -> existing?.credentialRef
            existing?.credentialRef != null -> existing.credentialRef
            else -> secrets.create(replacement).also { createdRef = it }
        }

        val profile = try {
            PlannerProfile(
                id = id,
                name = draft.name,
                mode = draft.mode,
                protocol = if (draft.mode == "on_device") "gguf_native_v1" else "openai_compatible_v1",
                baseUrl = draft.baseUrl,
                model = draft.model,
                credentialRef = credentialRef,
                timeoutSeconds = if (draft.mode == "on_device") 120.0 else draft.timeoutSeconds,
                maxResponseBytes = if (draft.mode == "on_device") 65536 else draft.maxResponseBytes,
                responseMode = if (draft.mode == "on_device") "none" else draft.responseMode,
                allowInsecureLanHttp = false,
            )
        } catch (exc: Exception) {
            createdRef?.let { safeRemoveSecret(it) }
            throw exc
        }

        try {
            if (replacement != null && existing?.credentialRef != null) {
                secrets.replace(existing.credentialRef, replacement)
            }
            profiles.upsert(profile)
        } catch (exc: Exception) {
            createdRef?.let { safeRemoveSecret(it) }
            throw exc
        }
        return id
    }

    fun importOnDeviceModel(source: InputStream): ImportedGgufModel =
        (modelStore ?: throw IllegalArgumentException("private GGUF store unavailable")).importModel(source)

    fun select(profileId: String) {
        profiles.select(profileId)
    }

    fun removeCredential(profileId: String) {
        val profile = requireEditable(profileId)
        val ref = profile.credentialRef ?: return
        profiles.upsert(profile.copy(credentialRef = null))
        safeRemoveSecret(ref)
    }

    fun delete(profileId: String): Boolean {
        if (profileId == PlannerProfile.DEMO_ID) return false
        val profile = profiles.get(profileId) ?: return false
        val removed = profiles.remove(profileId)
        if (removed) profile.credentialRef?.let { safeRemoveSecret(it) }
        return removed
    }

    fun testConnection(profileId: String): PlannerConnectionStatus {
        val profile = profiles.get(profileId)
            ?: throw IllegalArgumentException("planner profile does not exist")
        if (profile.mode == "demo") return PlannerConnectionStatus.OFFLINE_DEMO
        if (profile.mode == "on_device") {
            return try {
                modelStore?.resolve(profile.model)
                    ?: return PlannerConnectionStatus.MODEL_UNAVAILABLE
                PlannerConnectionStatus.MODEL_READY
            } catch (_: Exception) {
                PlannerConnectionStatus.MODEL_UNAVAILABLE
            }
        }
        if (profile.mode == "cloud" && profile.credentialRef == null) {
            return PlannerConnectionStatus.MISSING_CREDENTIAL
        }
        return diagnostic(profile)
    }

    private fun requireEditable(profileId: String): PlannerProfile {
        require(profileId != PlannerProfile.DEMO_ID) { "offline demo profile is built in" }
        return profiles.get(profileId)
            ?: throw IllegalArgumentException("planner profile does not exist")
    }

    private fun safeRemoveSecret(ref: String) {
        try {
            secrets.remove(ref)
        } catch (_: SecretStoreException) {
            // An unreferenced encrypted record is safer than re-exposing a credential.
        }
    }

    private fun newProfileId(): String =
        "profile-" + UUID.randomUUID().toString().replace("-", "")
}

internal fun buildPlannerDiagnosticRequest(profile: PlannerProfile): String = JSONObject()
    .put("model", profile.model)
    .put("max_completion_tokens", 64)
    .put(
        "messages",
        JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", "Reply OK."),
        ),
    )
    .toString()

internal fun plannerConnectionStatusFor(code: String): PlannerConnectionStatus = when (code) {
    NativePlannerTransport.ERROR_AUTH_REJECTED ->
        PlannerConnectionStatus.AUTHENTICATION_REJECTED
    NativePlannerTransport.ERROR_MODEL_NOT_FOUND ->
        PlannerConnectionStatus.MODEL_UNAVAILABLE
    NativePlannerTransport.ERROR_DNS_UNREACHABLE,
    NativePlannerTransport.ERROR_CONNECTION_REFUSED,
    NativePlannerTransport.ERROR_ENDPOINT_UNREACHABLE,
    NativePlannerTransport.ERROR_CLEARTEXT_BLOCKED ->
        PlannerConnectionStatus.ENDPOINT_UNREACHABLE
    NativePlannerTransport.ERROR_TLS ->
        PlannerConnectionStatus.TLS_FAILURE
    NativePlannerTransport.ERROR_TIMEOUT ->
        PlannerConnectionStatus.TIMED_OUT
    NativePlannerTransport.ERROR_HTTP_REJECTED ->
        PlannerConnectionStatus.REQUEST_REJECTED
    NativePlannerTransport.ERROR_RATE_LIMITED ->
        PlannerConnectionStatus.RATE_LIMITED
    NativePlannerTransport.ERROR_SERVER ->
        PlannerConnectionStatus.SERVER_ERROR
    NativePlannerTransport.ERROR_RESPONSE_TOO_LARGE,
    NativePlannerTransport.ERROR_RESPONSE_MALFORMED,
    NativePlannerTransport.ERROR_RESPONSE_UNSUPPORTED ->
        PlannerConnectionStatus.RESPONSE_UNSUPPORTED
    AndroidKeystoreSecretStore.ERROR_CREDENTIAL_MISSING ->
        PlannerConnectionStatus.MISSING_CREDENTIAL
    else -> PlannerConnectionStatus.UNAVAILABLE
}

private class PlannerConnectionDiagnostic(context: Context) {
    private val transport = NativePlannerTransport(AndroidKeystoreSecretStore(context))

    fun test(profile: PlannerProfile): PlannerConnectionStatus {
        val binding = PlannerBinding(
            profileId = profile.id,
            mode = profile.mode,
            protocol = profile.protocol,
            baseUrl = profile.baseUrl,
            model = profile.model,
            credentialRef = profile.credentialRef,
            timeoutSeconds = profile.timeoutSeconds,
            maxResponseBytes = profile.maxResponseBytes,
            responseMode = profile.responseMode,
            allowInsecureLanHttp = profile.allowInsecureLanHttp,
        )
        val request = buildPlannerDiagnosticRequest(profile)

        return when (val result = transport.newCall(binding, request).execute()) {
            is PlannerTransportResult.Success -> PlannerConnectionStatus.CONNECTED
            is PlannerTransportResult.Failure -> plannerConnectionStatusFor(result.code)
        }
    }

}
