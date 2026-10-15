package dev.lain.os.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeDeviceControlsTest {
    private class Fake : DeviceControlPlatform {
        var calls = mutableListOf<String>()
        var cameraGranted = true
        var settingsGranted = true
        var fixed = false
        var state: VolumeReading? = VolumeReading(4, 2, 12, false)
        var after: VolumeReading? = state
        var torchFailure: DeviceControlFailure? = null
        var settingAccepted = true
        var audioDenied = false
        var torch: Boolean? = true
        var brightness: BrightnessReading? = BrightnessReading(77, "manual")
        var navigationReason = "background_launch_restricted"
        var apps = emptyList<LaunchableApp>()
        override fun cameraPermission() = cameraGranted
        override fun writeSettingsPermission() = settingsGranted
        override fun setTorch(enabled: Boolean): Boolean? { calls += "torch:$enabled"; torchFailure?.let { throw it }; return torch }
        override fun fixedVolume() = fixed
        override fun readVolume(stream: String): VolumeReading? { calls += "read:$stream"; return if (calls.any { it.startsWith("set:") || it.startsWith("adjust:") }) after else state }
        override fun setVolume(stream: String, index: Int) { calls += "set:$stream:$index"; if (audioDenied) throw SecurityException() }
        override fun adjustVolume(stream: String, direction: String) { calls += "adjust:$stream:$direction" }
        override fun writeSetting(key: String, value: Int): Boolean { calls += "$key:$value"; return settingAccepted }
        override fun readBrightness() = brightness
        override fun readSetting(key: String): Int? = when (key) { "accelerometer_rotation" -> 0; else -> 45000 }
        override fun navigate(page: String?, packageName: String?): String? { calls += "navigate:${page ?: packageName}"; return navigationReason }
        override fun launchableApps(): List<LaunchableApp> { calls += "apps"; return apps }
    }
    private fun run(fake: Fake, name: String, json: String) = NativeDeviceControls(fake).executeRaw("android.$name", json)!!
    private fun reason(result: JSONObject) = result.getJSONObject("details").getString("reason")

    @Test fun strictArgumentsRejectBeforeAnyPlatformInteraction() {
        for (json in listOf("{\"enabled\":1}", "{\"enabled\":true,\"extra\":0}", "{\"enabled\":true,\"enabled\":false}", "{enabled:true}", "{\"enabled\":true} trailing", "{\"enabled\":true,}", "{\"enabled\":\"\\uD800\"}", "{\"enabled\":null}")) {
            val fake = Fake()
            assertEquals(json, "operation_rejected", reason(run(fake, "flashlight_set", json)))
            assertTrue(json, fake.calls.isEmpty())
        }
        for (json in listOf("{\"percentage\":40.0,\"stream\":\"media\"}", "{\"percentage\":101,\"stream\":\"media\"}", "{\"percentage\":40,\"stream\":\"bad\"}")) {
            val fake = Fake(); assertEquals("operation_rejected", reason(run(fake,"volume_set",json))); assertTrue(fake.calls.isEmpty())
        }
    }
    @Test fun mappingUsesActualMinAndRoundsHalfUpWithoutOverflow() {
        val fake = Fake()
        val result = run(fake, "volume_set", "{\"percentage\":25,\"stream\":\"voice_call\"}")
        assertTrue(fake.calls.contains("set:voice_call:5"))
        assertEquals(2, result.getJSONObject("details").getJSONObject("observed").getInt("min_index"))
        assertEquals(2147483647, NativeDeviceControls.volumeIndex(0, Int.MAX_VALUE, 100))
        for (stream in listOf("media", "ring", "alarm", "notification", "system", "voice_call")) {
            run(fake,"volume_get","{\"stream\":\"$stream\"}")
            assertEquals("read:$stream",fake.calls.last())
        }
    }
    @Test fun mismatchAndUnavailableEvidenceRemainHonest() {
        val fake = Fake(); fake.after = VolumeReading(3,2,12,false)
        assertEquals(3,run(fake,"volume_set","{\"percentage\":100,\"stream\":\"media\"}").getJSONObject("details").getJSONObject("observed").getInt("index"))
        fake.after = null
        assertTrue(run(fake,"volume_set","{\"percentage\":100,\"stream\":\"media\"}").getJSONObject("details").isNull("observed"))
        fake.torch = false
        assertFalse(run(fake,"flashlight_set","{\"enabled\":true}").getJSONObject("details").getJSONObject("observed").getBoolean("enabled"))
    }
    @Test fun deniedGrantsAndFixedVolumeNeverMutate() {
        val fake = Fake(); fake.cameraGranted = false; fake.settingsGranted = false
        assertEquals("permission_denied", reason(run(fake,"flashlight_set","{\"enabled\":true}")))
        for ((name,json) in listOf("brightness_set" to "{\"percentage\":30}", "rotation_set" to "{\"enabled\":false}", "screen_timeout_set" to "{\"milliseconds\":30000}")) assertEquals("special_access_required",reason(run(fake,name,json)))
        assertTrue(fake.calls.isEmpty())
        fake.fixed = true
        assertEquals("fixed_volume",reason(run(fake,"volume_set","{\"percentage\":30,\"stream\":\"media\"}")))
        assertFalse(fake.calls.any { it.startsWith("set:") })
        fake.fixed=false; fake.state=VolumeReading(2,2,2,false)
        assertEquals("success",run(fake,"volume_get","{\"stream\":\"media\"}").getString("status"))
        assertEquals("fixed_volume",reason(run(fake,"volume_adjust","{\"direction\":\"up\",\"stream\":\"media\"}")))
    }
    @Test fun settingsWriteManualFirstAndReturnActualReadback() {
        val fake=Fake()
        val result=run(fake,"brightness_set","{\"percentage\":30}")
        assertEquals(listOf("screen_brightness_mode:0","screen_brightness:77"),fake.calls)
        assertEquals("manual",result.getJSONObject("details").getJSONObject("observed").getString("mode"))
        assertEquals(45000,run(fake,"screen_timeout_set","{\"milliseconds\":30000}").getJSONObject("details").getJSONObject("observed").getInt("milliseconds"))
    }
    @Test fun adjustmentsPreserveBeforeAndRequestedDirection() {
        val fake=Fake(); fake.after=VolumeReading(5,2,12,false)
        val details=run(fake,"volume_adjust","{\"direction\":\"up\",\"stream\":\"alarm\"}").getJSONObject("details")
        assertEquals(4,details.getJSONObject("before").getInt("index")); assertEquals(5,details.getJSONObject("observed").getInt("index"))
        assertTrue(fake.calls.contains("set:alarm:5"))
    }
    @Test fun navigationReportsTapRestrictionAndRejectsUntrustedDestinations() {
        val fake=Fake()
        assertEquals("background_launch_restricted",reason(run(fake,"settings_open","{\"page\":\"wifi\"}")))
        assertEquals("background_launch_restricted",reason(run(fake,"app_open","{\"package\":\"com.example.app\"}")))
        fake.calls.clear()
        for ((name,json) in listOf("settings_open" to "{\"page\":\"intent://evil\"}","app_open" to "{\"package\":\"com.x/Activity\"}")) assertEquals("operation_rejected",reason(run(fake,name,json)))
        assertTrue(fake.calls.isEmpty())
        fake.navigationReason="handler_unavailable"
        assertEquals("handler_unavailable",reason(run(fake,"app_open","{\"package\":\"com.missing.app\"}")))
    }
    @Test fun inventoryFiltersDeduplicatesSortsAndBoundsUnicodeAndBytes() {
        val fake=Fake(); fake.apps=(0..180).reversed().map { LaunchableApp("com.example.a$it", "😀".repeat(300)) } + LaunchableApp("com.example.a0", "duplicate") + LaunchableApp("bad", "bad")
        val details=run(fake,"app_list","{\"query\":\"ExAmPlE\"}").getJSONObject("details")
        assertEquals("ExAmPlE",details.getString("query")); assertTrue(details.getBoolean("truncated")); assertTrue(details.toString().toByteArray(Charsets.UTF_8).size<=32768)
        val apps=details.getJSONArray("apps"); assertTrue(apps.length()<=128)
        val packages=(0 until apps.length()).map { apps.getJSONObject(it).getString("package") }
        assertEquals(packages.sorted().distinct(),packages)
        for(i in 0 until apps.length()) { val label=apps.getJSONObject(i).getString("label"); assertTrue(label.codePointCount(0,label.length)<=256) }
    }

    @Test fun platformFailuresNeverBecomeNullSuccessAndReasonsStayClosed() {
        val fake = Fake()
        for ((status, reason) in listOf("unsupported" to "hardware_unavailable", "failure" to "camera_busy")) {
            fake.torchFailure = DeviceControlFailure(status, reason)
            val result = run(fake, "flashlight_set", "{\"enabled\":true}")
            assertEquals(status, result.getString("status"))
            assertEquals(reason, reason(result))
            assertEquals(setOf("reason"), result.getJSONObject("details").keys().asSequence().toSet())
        }
        fake.settingAccepted = false
        fake.calls.clear()
        assertEquals("policy_rejected", reason(run(fake, "brightness_set", "{\"percentage\":30}")))
        assertEquals(listOf("screen_brightness_mode:0"), fake.calls)
        fake.audioDenied = true
        assertEquals("special_access_required", reason(run(fake, "volume_set", "{\"percentage\":30,\"stream\":\"ring\"}")))
    }

    @Test fun invalidRangesTypesAndTextNeverReachThePlatform() {
        for ((name, args) in listOf(
            "screen_timeout_set" to "{\"milliseconds\":14999}",
            "screen_timeout_set" to "{\"milliseconds\":1800001}",
            "rotation_set" to "{\"enabled\":0}",
            "app_list" to "{\"query\":\"\\u0000\"}",
            "app_list" to JSONObject().put("query", "😀".repeat(65)).toString(),
            "volume_get" to "{}"
        )) {
            val fake = Fake()
            assertEquals("operation_rejected", reason(run(fake, name, args)))
            assertTrue(fake.calls.isEmpty())
        }
    }

    @Test fun clampAndMuteHaveSeparateEvidenceAndEmptyLabelsFallBack() {
        val fake = Fake()
        fake.state = VolumeReading(12, 2, 12, false)
        fake.after = VolumeReading(12, 2, 12, true)
        run(fake, "volume_adjust", "{\"direction\":\"up\",\"stream\":\"alarm\"}")
        assertTrue(fake.calls.contains("set:alarm:12"))
        fake.calls.clear()
        val muted = run(fake, "volume_adjust", "{\"direction\":\"mute\",\"stream\":\"alarm\"}").getJSONObject("details")
        assertFalse(muted.getJSONObject("before").getBoolean("muted"))
        assertTrue(muted.getJSONObject("observed").getBoolean("muted"))
        assertTrue(fake.calls.contains("adjust:alarm:mute"))
        fake.apps = listOf(LaunchableApp("com.example.app", ""))
        assertEquals("com.example.app", run(fake, "app_list", "{\"query\":\"\"}").getJSONObject("details").getJSONArray("apps").getJSONObject(0).getString("label"))
    }

    @Test fun recordCountBoundIsIndependentOfByteBound() {
        val fake = Fake()
        fake.apps = (0..128).map { LaunchableApp("com.example.a$it", "Short") }
        val details = run(fake, "app_list", "{\"query\":\"\"}").getJSONObject("details")
        assertEquals(128, details.getJSONArray("apps").length())
        assertTrue(details.getBoolean("truncated"))
        fake.apps = listOf(LaunchableApp("com.example.app", "Camera"))
        val byLabel = run(fake, "app_list", "{\"query\":\"CaM\"}").getJSONObject("details")
        assertEquals(1, byLabel.getJSONArray("apps").length())
        assertFalse(byLabel.getBoolean("truncated"))
    }

    @Test fun unrelatedNamesRemainUnowned() { assertNull(NativeDeviceControls(Fake()).executeRaw("android.notify","{}")) }
}
