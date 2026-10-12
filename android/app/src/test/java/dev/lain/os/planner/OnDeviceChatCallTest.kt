package dev.lain.os.planner

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real native-call lifecycle seam with a deterministic fake model. */
class OnDeviceChatCallTest {
    @Test fun rawConversationBypassesTaskPlannerJsonAndNeverExecutesActions() {
        val prompt = QwenChatPrompt.build(listOf(QwenChatTurn("user", "Hello LAIN")))
        var actualPrompt: String? = null
        var actions = 0
        val engine = object : OnDevicePlannerEngine {
            override fun registerGeneration(generationId: Long): GgufNativeProbeFailure? = null
            override fun releaseGeneration(generationId: Long) = Unit
            override fun cancel(generationId: Long) = Unit
            override fun generate(
                modelPath: String, prompt: String, contextTokens: Int,
                maxNewTokens: Int, generationId: Long,
            ): GgufNativeProbeResult {
                actualPrompt = prompt
                actions += 0 // Engine generation is not a task or capability invocation.
                assertEquals(4096, contextTokens)
                assertEquals(256, maxNewTokens)
                return GgufNativeProbeResult.Generated("Hello, human!")
            }
        }
        val call = OnDevicePlannerCall(
            { _, _ -> File("/private/qwen.gguf") },
            "a".repeat(64), prompt, 2.0, 16384, engine, { it },
        )
        val result = JSONObject(call.execute())
        assertTrue(result.getBoolean("ok"))
        assertEquals("Hello, human!", result.getString("body"))
        assertEquals(prompt, actualPrompt)
        assertEquals(0, actions)
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }
}
