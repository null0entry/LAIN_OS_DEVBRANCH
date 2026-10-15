package dev.lain.os.bootstrap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer

internal class PermissionBroker(
    private val apiLevel: Int,
    private val hasRuntimePermission: (String) -> Boolean,
    private val hasSettingsGrant: (SettingsGrant) -> Boolean,
    private val hasOptionalDependency: (String) -> Boolean,
    private val rules: List<CapabilityRequirement> = DEFAULT_RULES,
) {
    fun snapshot(): List<CapabilityAvailability> = rules.map { requirement ->
        CapabilityAvailability(
            capability = requirement.capability,
            state = when {
                apiLevel < requirement.minApi -> AvailabilityState.UNSUPPORTED
                requirement.optionalDependency != null &&
                    !hasOptionalDependency(requirement.optionalDependency) ->
                    AvailabilityState.OPTIONAL_DEPENDENCY_MISSING
                requirement.settingsGrant != null &&
                    !hasSettingsGrant(requirement.settingsGrant) ->
                    AvailabilityState.NEEDS_SETTINGS_GRANT
                requirement.runtimePermission != null && apiLevel >= requirement.permissionMinApi &&
                    !hasRuntimePermission(requirement.runtimePermission) ->
                    AvailabilityState.NEEDS_PERMISSION
                else -> AvailabilityState.READY
            },
            requirement = when {
                apiLevel < requirement.minApi -> "Android API ${requirement.minApi}+"
                requirement.optionalDependency != null -> requirement.optionalDependency
                requirement.settingsGrant != null -> requirement.settingsGrant.name.lowercase()
                requirement.runtimePermission != null && apiLevel >= requirement.permissionMinApi -> requirement.runtimePermission
                else -> null
            },
        )
    }

    fun runtimePermissionFor(capability: String): String? =
        rules.firstOrNull { it.capability == capability && apiLevel >= it.permissionMinApi }?.runtimePermission

    fun settingsGrantFor(capability: String): SettingsGrant? =
        rules.firstOrNull { it.capability == capability }?.settingsGrant

    fun settingsIntentFor(capability: String): Intent? = when (settingsGrantFor(capability)) {
        SettingsGrant.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        null -> null
    }

    companion object {
        internal val DEFAULT_RULES = listOf(
            CapabilityRequirement(
                capability = "voice.input",
                runtimePermission = Manifest.permission.RECORD_AUDIO,
            ),
            CapabilityRequirement(
                capability = "voice.on_device_stt",
                minApi = 33,
                runtimePermission = Manifest.permission.RECORD_AUDIO,
                optionalDependency = "android.on_device_recognizer",
            ),
            CapabilityRequirement(capability = "android.battery_status"),
            CapabilityRequirement(capability = "android.vibrate"),
            CapabilityRequirement(capability = "android.toast"),
            CapabilityRequirement(capability = "android.clipboard_set"),
            CapabilityRequirement(capability = "android.open_uri"),
            CapabilityRequirement(capability = "android.notify",
                runtimePermission = Manifest.permission.POST_NOTIFICATIONS, permissionMinApi = 33),
            CapabilityRequirement(capability = "android.share_text"),
            CapabilityRequirement(capability = "android.sms.compose"),
        )

        fun android(context: Context): PermissionBroker {
            val app = context.applicationContext
            return PermissionBroker(
                apiLevel = Build.VERSION.SDK_INT,
                hasRuntimePermission = { permission ->
                    app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                },
                hasSettingsGrant = { false },
                hasOptionalDependency = { dependency ->
                    dependency == "android.on_device_recognizer" &&
                        Build.VERSION.SDK_INT >= 33 && try {
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
                    } catch (_: Exception) {
                        false
                    }
                },
            )
        }
    }
}
