package dev.lain.os.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GgufNativeFeasibilityAndroidTest {
    @Test
    fun nativeLibraryLoadsAndRejectsMissingModel() {
        val result = GgufNativeProbe.probe(
            modelPath = "/data/user/0/dev.lain.os/files/models/does-not-exist.gguf",
            contextTokens = 128,
            maxNewTokens = 8,
        )

        assertEquals(
            GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_NOT_FOUND),
            result,
        )
    }
}
