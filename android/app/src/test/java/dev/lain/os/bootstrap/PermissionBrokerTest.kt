package dev.lain.os.bootstrap

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionBrokerTest {
    private fun broker(
        apiLevel: Int = 35,
        runtimeGranted: Set<String> = emptySet(),
        settingsGranted: Set<SettingsGrant> = emptySet(),
        dependencies: Set<String> = emptySet(),
        rules: List<CapabilityRequirement> = PermissionBroker.DEFAULT_RULES,
    ) = PermissionBroker(
        apiLevel = apiLevel,
        hasRuntimePermission = runtimeGranted::contains,
        hasSettingsGrant = settingsGranted::contains,
        hasOptionalDependency = dependencies::contains,
        rules = rules,
    )

    @Test fun notificationsRequireRuntimePermissionOnlyOnAndroid13AndLater() {
        assertEquals(AvailabilityState.READY, broker(apiLevel = 24).snapshot()
            .first { it.capability == "android.notify" }.state)
        assertNull(broker(apiLevel = 24).runtimePermissionFor("android.notify"))
        assertEquals(AvailabilityState.NEEDS_PERMISSION, broker().snapshot()
            .first { it.capability == "android.notify" }.state)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, broker().runtimePermissionFor("android.notify"))
        assertEquals(AvailabilityState.READY,
            broker(runtimeGranted = setOf(Manifest.permission.POST_NOTIFICATIONS)).snapshot()
                .first { it.capability == "android.notify" }.state)
    }

    @Test fun microphonePermissionDoesNotDisableUnrelatedCapabilities() {
        val state = broker().snapshot().associateBy { it.capability }

        assertEquals(AvailabilityState.NEEDS_PERMISSION, state.getValue("voice.input").state)
        assertEquals(Manifest.permission.RECORD_AUDIO, state.getValue("voice.input").requirement)
        assertEquals(AvailabilityState.READY, state.getValue("android.battery_status").state)
        assertEquals(AvailabilityState.READY, state.getValue("android.share_text").state)
    }

    @Test fun grantedRuntimePermissionMakesVoiceReady() {
        val state = broker(runtimeGranted = setOf(Manifest.permission.RECORD_AUDIO))
            .snapshot().associateBy { it.capability }

        assertEquals(AvailabilityState.READY, state.getValue("voice.input").state)
        assertEquals(
            Manifest.permission.RECORD_AUDIO,
            broker().runtimePermissionFor("voice.input"),
        )
        assertNull(broker().runtimePermissionFor("android.battery_status"))
    }

    @Test fun specialGrantNeverMasqueradesAsRuntimePermission() {
        val rule = CapabilityRequirement(
            capability = "integration.accessibility",
            settingsGrant = SettingsGrant.ACCESSIBILITY,
        )
        val denied = broker(rules = listOf(rule)).snapshot().single()
        assertEquals(AvailabilityState.NEEDS_SETTINGS_GRANT, denied.state)
        assertEquals(
            SettingsGrant.ACCESSIBILITY,
            broker(rules = listOf(rule)).settingsGrantFor("integration.accessibility"),
        )
        assertNull(broker(rules = listOf(rule)).runtimePermissionFor("integration.accessibility"))

        val granted = broker(
            settingsGranted = setOf(SettingsGrant.ACCESSIBILITY),
            rules = listOf(rule),
        ).snapshot().single()
        assertEquals(AvailabilityState.READY, granted.state)
    }

    @Test fun missingOptionalDependencyIsTruthfulAndIsolated() {
        val rules = listOf(
            CapabilityRequirement(
                capability = "integration.shizuku",
                optionalDependency = "shizuku",
            ),
            CapabilityRequirement(capability = "android.battery_status"),
        )
        val missing = broker(rules = rules).snapshot().associateBy { it.capability }

        assertEquals(
            AvailabilityState.OPTIONAL_DEPENDENCY_MISSING,
            missing.getValue("integration.shizuku").state,
        )
        assertEquals(AvailabilityState.READY, missing.getValue("android.battery_status").state)

        val available = broker(
            dependencies = setOf("shizuku"),
            rules = rules,
        ).snapshot().associateBy { it.capability }
        assertEquals(AvailabilityState.READY, available.getValue("integration.shizuku").state)
    }

    @Test fun unsupportedApiFailsClosedWithoutAffectingOtherCapabilities() {
        val state = broker(apiLevel = 24).snapshot().associateBy { it.capability }

        assertEquals(
            AvailabilityState.UNSUPPORTED,
            state.getValue("voice.on_device_stt").state,
        )
        assertEquals(AvailabilityState.READY, state.getValue("android.battery_status").state)
    }

    @Test fun missingOnDeviceRecognizerNeverReportsTranscriptionReady() {
        val state = broker(runtimeGranted = setOf(Manifest.permission.RECORD_AUDIO))
            .snapshot().associateBy { it.capability }
        assertEquals(AvailabilityState.READY, state.getValue("voice.input").state)
        assertEquals(
            AvailabilityState.OPTIONAL_DEPENDENCY_MISSING,
            state.getValue("voice.on_device_stt").state,
        )
        val installed = broker(
            runtimeGranted = setOf(Manifest.permission.RECORD_AUDIO),
            dependencies = setOf("android.on_device_recognizer"),
        ).snapshot().associateBy { it.capability }
        assertEquals(AvailabilityState.READY, installed.getValue("voice.on_device_stt").state)
    }
}
