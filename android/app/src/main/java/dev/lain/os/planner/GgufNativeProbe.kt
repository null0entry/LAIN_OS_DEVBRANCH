package dev.lain.os.planner

internal enum class GgufNativeProbeFailure {
    INVALID_BOUNDS,
    MODEL_NOT_FOUND,
    NATIVE_LIBRARY_UNAVAILABLE,
    MODEL_LOAD_FAILED,
    TOKENIZE_FAILED,
    CONTEXT_FAILED,
    DECODE_FAILED,
    OUTPUT_INVALID,
    UNKNOWN_NATIVE_ERROR,
}

internal sealed interface GgufNativeProbeResult {
    data class Generated(val text: String) : GgufNativeProbeResult
    data class Rejected(val reason: GgufNativeProbeFailure) : GgufNativeProbeResult
}

/**
 * Private feasibility seam only. This is not a planner and grants no execution authority.
 * Model paths must come from app-private storage; later model-store work owns that admission.
 */
internal object GgufNativeProbe {
    private const val MIN_CONTEXT_TOKENS = 64
    private const val MAX_CONTEXT_TOKENS = 4096
    private const val MIN_NEW_TOKENS = 1
    private const val MAX_NEW_TOKENS = 256
    private const val MAX_NATIVE_RESPONSE_BYTES = 65_536

    private val libraryLoaded: Boolean by lazy {
        try {
            System.loadLibrary("lain_gguf_probe")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private external fun nativeProbe(
        modelPath: String,
        contextTokens: Int,
        maxNewTokens: Int,
    ): String

    fun probe(
        modelPath: String,
        contextTokens: Int,
        maxNewTokens: Int,
    ): GgufNativeProbeResult {
        if (
            modelPath.isBlank() ||
            contextTokens !in MIN_CONTEXT_TOKENS..MAX_CONTEXT_TOKENS ||
            maxNewTokens !in MIN_NEW_TOKENS..MAX_NEW_TOKENS ||
            maxNewTokens >= contextTokens
        ) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.INVALID_BOUNDS)
        }
        if (!libraryLoaded) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.NATIVE_LIBRARY_UNAVAILABLE)
        }
        val response = try {
            nativeProbe(modelPath, contextTokens, maxNewTokens)
        } catch (_: UnsatisfiedLinkError) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.NATIVE_LIBRARY_UNAVAILABLE)
        } catch (_: RuntimeException) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.UNKNOWN_NATIVE_ERROR)
        }
        if (response.toByteArray(Charsets.UTF_8).size > MAX_NATIVE_RESPONSE_BYTES) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.OUTPUT_INVALID)
        }
        if (response.startsWith("OK:")) {
            val text = response.removePrefix("OK:")
            return if (text.isNotEmpty()) {
                GgufNativeProbeResult.Generated(text)
            } else {
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.OUTPUT_INVALID)
            }
        }
        val failure = when (response.removePrefix("ERR:")) {
            "INVALID_BOUNDS" -> GgufNativeProbeFailure.INVALID_BOUNDS
            "MODEL_NOT_FOUND" -> GgufNativeProbeFailure.MODEL_NOT_FOUND
            "MODEL_LOAD_FAILED" -> GgufNativeProbeFailure.MODEL_LOAD_FAILED
            "TOKENIZE_FAILED" -> GgufNativeProbeFailure.TOKENIZE_FAILED
            "CONTEXT_FAILED" -> GgufNativeProbeFailure.CONTEXT_FAILED
            "DECODE_FAILED" -> GgufNativeProbeFailure.DECODE_FAILED
            "OUTPUT_INVALID" -> GgufNativeProbeFailure.OUTPUT_INVALID
            else -> GgufNativeProbeFailure.UNKNOWN_NATIVE_ERROR
        }
        return GgufNativeProbeResult.Rejected(failure)
    }
}
