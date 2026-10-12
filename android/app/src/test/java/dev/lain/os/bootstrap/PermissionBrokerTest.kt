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
    ) = PermissionBroker(
        apiLevel = apiLevel,
        hasRuntimePermission = runtimeGranted::contains,
        hasSettingsGrant = settingsGranted::contains,
        hasOptionalDependency = dependencies::contains,
    )

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
        val denied = broker().snapshot().associateBy { it.capability }
        assertEquals(
            AvailabilityState.NEEDS_SETTINGS_GRANT,
            denied.getValue("integration.accessibility").state,
        )
        assertEquals(
            SettingsGrant.ACCESSIBILITY,
            broker().settingsGrantFor("integration.accessibility"),
        )
        assertNull(broker().runtimePermissionFor("integration.accessibility"))

        val granted = broker(settingsGranted = setOf(SettingsGrant.ACCESSIBILITY))
            .snapshot().associateBy { it.capability }
        assertEquals(
            AvailabilityState.READY,
            granted.getValue("integration.accessibility").state,
        )
    }

    @Test fun missingOptionalDependencyIsTruthfulAndIsolated() {
        val missing = broker().snapshot().associateBy { it.capability }

        assertEquals(
            AvailabilityState.OPTIONAL_DEPENDENCY_MISSING,
            missing.getValue("integration.shizuku").state,
        )
        assertEquals(AvailabilityState.READY, missing.getValue("android.battery_status").state)

        val available = broker(dependencies = setOf("shizuku"))
            .snapshot().associateBy { it.capability }
        assertEquals(AvailabilityState.READY, available.getValue("integration.shizuku").state)
    }

    @Test fun unsupportedApiFailsClosedWithoutAffectingOtherCapabilities() {
        val state = broker(apiLevel = 24).snapshot().associateBy { it.capability }

        assertEquals(
            AvailabilityState.UNSUPPORTED,
            state.getValue("android.notification_runtime").state,
        )
        assertEquals(AvailabilityState.READY, state.getValue("android.battery_status").state)
    }
}
