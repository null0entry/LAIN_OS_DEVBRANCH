package dev.lain.os.planner

import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** One bounded native GGUF generation; model bytes never become transport URLs. */
internal class OnDevicePlannerCall(
    private val store: OnDeviceModelStore,
    private val modelSha256: String,
    private val request: String,
    private val timeoutSeconds: Double,
    private val maxResponseBytes: Int,
) {
    private val cancelled = AtomicBoolean(false)
    private val generationId = NEXT_GENERATION.incrementAndGet()
    @Volatile private var active: FutureTask<GgufNativeProbeResult>? = null

    fun cancel() {
        cancelled.set(true)
        GgufNativeProbe.cancel(generationId)
        active?.cancel(true)
    }

    fun execute(): String {
        if (cancelled.get()) return failure("PLANNER_CANCELLED")
        val task = FutureTask<GgufNativeProbeResult> {
            // Verify exact private model hash on each request, not an untrusted path.
            val file = try { store.resolve(modelSha256) } catch (_: Exception) {
                return@FutureTask GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_NOT_FOUND)
            }
            if (cancelled.get()) {
                return@FutureTask GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.CANCELLED)
            }
            val prompt = try { promptForQwen(request) } catch (_: Exception) {
                return@FutureTask GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.INVALID_BOUNDS)
            }
            GgufNativeProbe.generate(file.absolutePath, prompt, 4096, 256, generationId)
        }
        active = task
        if (cancelled.get()) task.cancel(true)
        Thread(task, "lain-on-device-planner").apply { isDaemon = true; start() }
        return try {
            val result = task.get((timeoutSeconds * 1000.0).toLong(), TimeUnit.MILLISECONDS)
            if (cancelled.get()) failure("PLANNER_CANCELLED")
            else when (result) {
                is GgufNativeProbeResult.Generated -> {
                    if (result.text.toByteArray(Charsets.UTF_8).size > maxResponseBytes) {
                        failure("PLANNER_OUTPUT_INVALID")
                    } else JSONObject().put("ok", true).put("body", result.text).toString()
                }
                is GgufNativeProbeResult.Rejected -> failure(when (result.reason) {
                    GgufNativeProbeFailure.MODEL_NOT_FOUND -> "PLANNER_MODEL_NOT_FOUND"
                    GgufNativeProbeFailure.MODEL_BUSY -> "PLANNER_MODEL_BUSY"
                    GgufNativeProbeFailure.CANCELLED -> "PLANNER_CANCELLED"
                    GgufNativeProbeFailure.MODEL_LOAD_FAILED,
                    GgufNativeProbeFailure.CONTEXT_FAILED -> "PLANNER_MODEL_LOAD_FAILED"
                    GgufNativeProbeFailure.INVALID_BOUNDS,
                    GgufNativeProbeFailure.OUTPUT_INVALID,
                    GgufNativeProbeFailure.TOKENIZE_FAILED,
                    GgufNativeProbeFailure.DECODE_FAILED -> "PLANNER_OUTPUT_INVALID"
                    else -> "PLANNER_MODEL_LOAD_FAILED"
                })
            }
        } catch (_: TimeoutException) {
            cancel()
            failure("PLANNER_TIMEOUT")
        } catch (_: CancellationException) {
            failure("PLANNER_CANCELLED")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            cancel()
            failure("PLANNER_CANCELLED")
        } catch (_: ExecutionException) {
            failure("PLANNER_MODEL_LOAD_FAILED")
        } finally {
            active = null
        }
    }

    companion object {
        private val NEXT_GENERATION = AtomicLong(1000L)
        private fun failure(code: String) = JSONObject().put("ok", false).put("error", code).toString()

        /** Qwen2.5 chat format for the first supported offline GGUF family. */
        private fun promptForQwen(payload: String): String {
            require(payload.toByteArray(Charsets.UTF_8).size <= 12_288)
            val request = JSONObject(payload)
            require(request.optString("mode") == "agent")
            require(request.optString("version") == "0")
            require(request.has("goal") && request.has("capabilities"))
            val system = "You are LAIN's bounded task planner. Only propose capability names supplied in the JSON. " +
                "Do not claim action results or grant approvals. Respond with one JSON object only: " +
                "{\"status\":\"continue|complete|blocked\",\"reason\":\"brief reason\",\"actions\":[{\"type\":\"capability\",\"arguments\":{}}]}. " +
                "Use an empty actions array when complete or blocked. Never add markdown."
            val prompt = "<|im_start|>system\\n" + system +
                "<|im_end|>\\n<|im_start|>user\\n" + payload +
                "<|im_end|>\\n<|im_start|>assistant\\n"
            require(prompt.toByteArray(Charsets.UTF_8).size <= 16_384)
            return prompt
        }
    }
}
