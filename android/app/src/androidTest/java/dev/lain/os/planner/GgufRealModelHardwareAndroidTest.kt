package dev.lain.os.planner

import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit hardware gate, never CI proof by omission. Needs an owner-supplied
 * app-private GGUF at files/models/<sha256>.gguf and the instrumentation arg
 * lain_model_sha256. Without that argument this test is SKIPPED, not PASSED.
 */
@RunWith(AndroidJUnit4::class)
class GgufRealModelHardwareAndroidTest {
    @Test
    fun importedModelGeneratesTokensInAirplaneMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val hash = InstrumentationRegistry.getArguments().getString("lain_model_sha256")
        assumeTrue("NO_REAL_MODEL_SUPPLIED: physical GGUF acceptance unverified", hash != null)
        assertTrue("Expected 64 lowercase SHA-256 characters", Regex("[0-9a-f]{64}").matches(hash!!))

        val context = instrumentation.targetContext
        val privateModels = File(context.filesDir, "models").canonicalFile
        val model = File(privateModels, "$hash.gguf").canonicalFile
        assertEquals("Model must stay under the private models directory", privateModels.path, model.parentFile?.canonicalPath)
        assertTrue("Missing app-private GGUF model", model.isFile)
        val sha = MessageDigest.getInstance("SHA-256")
        model.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                sha.update(buffer, 0, size)
            }
        }
        val actualHash = sha.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("Model content does not match pinned expected SHA-256", hash, actualHash)
        assertEquals(
            "Airplane mode must be enabled for the offline hardware gate",
            1,
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        )

        val start = SystemClock.elapsedRealtime()
        val result = GgufNativeProbe.probe(model.absolutePath, contextTokens = 512, maxNewTokens = 32)
        val durationMs = SystemClock.elapsedRealtime() - start
        Log.i("LAIN_GGUF_HARDWARE", "model=$hash generationMs=$durationMs result=${result.javaClass.simpleName}")
        assertTrue("Real GGUF generation failed: $result", result is GgufNativeProbeResult.Generated)
        assertTrue("Model produced empty text", (result as GgufNativeProbeResult.Generated).text.isNotBlank())
    }
}
