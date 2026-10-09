package dev.lain.os.planner

import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** One bounded native GGUF generation; model bytes never become transport URLs. */
internal class OnDevicePlannerCall(
    private val resolveModel: (String, () -> Boolean) -> File,
    private val modelSha256: String,
    private val request: String,
    private val timeoutSeconds: Double,
    private val maxResponseBytes: Int,
    private val engine: OnDevicePlannerEngine = GgufNativeProbe,
) {
    constructor(
        store: OnDeviceModelStore, modelSha256: String, request: String,
        timeoutSeconds: Double, maxResponseBytes: Int,
    ) : this(store::resolve, modelSha256, request, timeoutSeconds, maxResponseBytes)

    private val cancelled = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val generationId = NEXT_GENERATION.incrementAndGet()
    @Volatile private var active: FutureTask<GgufNativeProbeResult>? = null

    fun cancel() {
        cancelled.set(true)
        engine.cancel(generationId)
        active?.cancel(true)
    }

    fun execute(): String {
        if (cancelled.get()) return failure("PLANNER_CANCELLED")
        if (!started.compareAndSet(false, true)) return failure("PLANNER_MODEL_BUSY")
        val prompt = try { promptForQwen(request) } catch (_: Exception) {
            return failure("PLANNER_REQUEST_INVALID")
        }
        val task = FutureTask<GgufNativeProbeResult> {
            val registrationFailure = engine.registerGeneration(generationId)
            if (registrationFailure != null) {
                return@FutureTask GgufNativeProbeResult.Rejected(registrationFailure)
            }
            try {
                // Replay Stop if it arrived before native registration completed.
                if (cancelled.get()) engine.cancel(generationId)
                // Verify exact private model hash, checking Stop during each read.
                val file = try { resolveModel(modelSha256) { cancelled.get() } } catch (_: Exception) {
                    return@FutureTask GgufNativeProbeResult.Rejected(
                        if (cancelled.get() || Thread.currentThread().isInterrupted) {
                            GgufNativeProbeFailure.CANCELLED
                        } else GgufNativeProbeFailure.MODEL_NOT_FOUND
                    )
                }
                if (cancelled.get() || Thread.currentThread().isInterrupted) {
                    return@FutureTask GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.CANCELLED)
                }
                engine.generate(file.absolutePath, prompt, 4096, 256, generationId)
            } finally {
                // FutureTask.cancel returns before JNI finishes; the worker owns release.
                engine.releaseGeneration(generationId)
            }
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
        // Matches the serialized request budget in lain.app.planner_bridge.
        const val MAX_REQUEST_BYTES = 12_288
        private val NEXT_GENERATION = AtomicLong(1000L)
        private fun failure(code: String) = JSONObject().put("ok", false).put("error", code).toString()

        /** Qwen2.5 chat format for the first supported offline GGUF family. */
        internal fun promptForQwen(payload: String): String {
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_REQUEST_BYTES)
            val tokener = JSONTokener(payload)
            val request = tokener.nextValue()
            require(request is JSONObject && tokener.nextClean() == '\u0000')
            require(request.get("mode") == "agent")
            require(request.get("version") == "0")
            require(request.get("goal") is String && request.get("capabilities") is JSONArray)
            val system = "You are LAIN's bounded task planner. Only propose capability names supplied in the JSON. " +
                "Do not claim action results or grant approvals. Respond with one JSON object only: " +
                "{\"status\":\"continue|complete|blocked\",\"reason\":\"brief reason\",\"actions\":[{\"type\":\"capability\",\"arguments\":{}}]}. " +
                "Use an empty actions array when complete or blocked. Never add markdown."
            val prompt = "<|im_start|>system\n" + system +
                "<|im_end|>\n<|im_start|>user\n" + payload +
                "<|im_end|>\n<|im_start|>assistant\n"
            require(prompt.toByteArray(Charsets.UTF_8).size <= 16_384)
            return prompt
        }
    }
}
