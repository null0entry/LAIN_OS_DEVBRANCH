package dev.lain.os.bootstrap

internal enum class AvailabilityState {
    READY,
    NEEDS_PERMISSION,
    NEEDS_SETTINGS_GRANT,
    OPTIONAL_DEPENDENCY_MISSING,
    UNSUPPORTED,
}

internal enum class SettingsGrant {
    ACCESSIBILITY,
}

internal data class CapabilityAvailability(
    val capability: String,
    val state: AvailabilityState,
    val requirement: String? = null,
)

internal data class CapabilityRequirement(
    val capability: String,
    val minApi: Int = 24,
    val runtimePermission: String? = null,
    val permissionMinApi: Int = 23,
    val settingsGrant: SettingsGrant? = null,
    val optionalDependency: String? = null,
)
