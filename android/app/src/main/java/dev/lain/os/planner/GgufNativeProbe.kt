package dev.lain.os.planner

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal enum class GgufNativeProbeFailure {
    INVALID_BOUNDS,
    MODEL_NOT_FOUND,
    NATIVE_LIBRARY_UNAVAILABLE,
    MODEL_LOAD_FAILED,
    MODEL_BUSY,
    CANCELLED,
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

    private external fun nativeProbeBytes(
        modelPath: String,
        contextTokens: Int,
        maxNewTokens: Int,
    ): ByteArray

    private external fun nativeGenerateBytes(
        modelPath: String, promptUtf8: ByteArray,
        contextTokens: Int, maxNewTokens: Int, generationId: Long,
    ): ByteArray

    private external fun nativeCancelGeneration(generationId: Long)

    /** A private JNI seam; planner decisions are still validated elsewhere. */
    fun generate(
        modelPath: String, prompt: String, contextTokens: Int,
        maxNewTokens: Int, generationId: Long,
    ): GgufNativeProbeResult {
        val bytes = prompt.toByteArray(Charsets.UTF_8)
        if (
            generationId <= 0 || modelPath.isBlank() || bytes.isEmpty() ||
            bytes.size > 16_384 || bytes.any { it == 0.toByte() } ||
            contextTokens !in MIN_CONTEXT_TOKENS..MAX_CONTEXT_TOKENS ||
            maxNewTokens !in MIN_NEW_TOKENS..MAX_NEW_TOKENS ||
            maxNewTokens >= contextTokens
        ) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.INVALID_BOUNDS)
        }
        if (!libraryLoaded) return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.NATIVE_LIBRARY_UNAVAILABLE)
        return try {
            decodeNativePayload(nativeGenerateBytes(modelPath, bytes, contextTokens, maxNewTokens, generationId))
        } catch (_: UnsatisfiedLinkError) {
            GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.NATIVE_LIBRARY_UNAVAILABLE)
        } catch (_: RuntimeException) {
            GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.UNKNOWN_NATIVE_ERROR)
        }
    }

    fun cancel(generationId: Long) {
        if (generationId <= 0 || !libraryLoaded) return
        try { nativeCancelGeneration(generationId) }
        catch (_: UnsatisfiedLinkError) { /* missing native library is already unavailable */ }
    }

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
        val payload = try {
            nativeProbeBytes(modelPath, contextTokens, maxNewTokens)
        } catch (_: UnsatisfiedLinkError) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.NATIVE_LIBRARY_UNAVAILABLE)
        } catch (_: RuntimeException) {
            return GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.UNKNOWN_NATIVE_ERROR)
        }
        return decodeNativePayload(payload)
    }

    /** JNI yields raw bytes: never use NewStringUTF on model-generated token bytes. */
    internal fun decodeNativePayload(bytes: ByteArray): GgufNativeProbeResult {
        fun invalid() = GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.OUTPUT_INVALID)
        if (bytes.isEmpty() || bytes.size > MAX_NATIVE_RESPONSE_BYTES + 3 || bytes.any { it == 0.toByte() }) {
            return invalid()
        }
        val response = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return invalid()
        }

        if (response.startsWith("OK:")) {
            val text = response.removePrefix("OK:")
            return if (text.isNotEmpty() && bytes.size - 3 <= MAX_NATIVE_RESPONSE_BYTES) {
                GgufNativeProbeResult.Generated(text)
            } else invalid()
        }
        if (!response.startsWith("ERR:")) return invalid()
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
