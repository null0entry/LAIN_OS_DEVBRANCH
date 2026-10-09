package dev.lain.os.planner

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GgufNativeFeasibilityAndroidTest {
    @Test
    fun nativePlannerGenerationRejectsMissingModelWithoutNetworkFallback() {
        assertNull(GgufNativeProbe.registerGeneration(777L))
        val result = try {
            GgufNativeProbe.generate(
                modelPath = "/data/user/0/dev.lain.os/files/models/absent.gguf",
                prompt = "Return only JSON.",
                contextTokens = 2048,
                maxNewTokens = 128,
                generationId = 777L,
            )
        } finally { GgufNativeProbe.releaseGeneration(777L) }
        assertEquals(
            GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_NOT_FOUND),
            result,
        )
    }

    @Test fun registeredStopBeforeJniEntryIsRememberedAndReleased() {
        val id = 778L
        assertNull(GgufNativeProbe.registerGeneration(id))
        try {
            GgufNativeProbe.cancel(id)
            assertEquals(
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.CANCELLED),
                GgufNativeProbe.generate("/missing.gguf", "Return JSON.", 512, 32, id),
            )
        } finally { GgufNativeProbe.releaseGeneration(id) }
        assertNull(GgufNativeProbe.registerGeneration(id))
        try {
            assertEquals(
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_NOT_FOUND),
                GgufNativeProbe.generate("/missing.gguf", "Return JSON.", 512, 32, id),
            )
        } finally { GgufNativeProbe.releaseGeneration(id) }
    }

    @Test fun nativeCancellationRegistryIsBoundedAndIsolatesGenerations() {
        val ids = (800L..807L).toList()
        try {
            ids.forEach { assertNull(GgufNativeProbe.registerGeneration(it)) }
            assertEquals(GgufNativeProbeFailure.MODEL_BUSY, GgufNativeProbe.registerGeneration(808L))
            GgufNativeProbe.cancel(ids.first())
            assertEquals(
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_NOT_FOUND),
                GgufNativeProbe.generate("/missing.gguf", "Return JSON.", 512, 32, ids.last()),
            )
            assertEquals(
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.CANCELLED),
                GgufNativeProbe.generate("/missing.gguf", "Return JSON.", 512, 32, ids.first()),
            )
        } finally { ids.forEach(GgufNativeProbe::releaseGeneration) }
        assertNull(GgufNativeProbe.registerGeneration(808L))
        GgufNativeProbe.releaseGeneration(808L)
    }

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
