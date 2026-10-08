package dev.lain.os.planner

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A header candidate is NOT a validated GGUF file, installed model, or runnable inference backend. */
internal data class GgufHeaderCandidate(
    val version: Int,
    val tensorCount: Long,
    val metadataCount: Long,
)

internal enum class GgufHeaderRejection {
    INVALID_BUDGET,
    INVALID_SIZE,
    OVER_BUDGET,
    TRUNCATED_HEADER,
    BAD_MAGIC,
    UNSUPPORTED_VERSION,
    INVALID_TENSOR_COUNT,
    INVALID_METADATA_COUNT,
    STALLED_INPUT,
    READ_FAILED,
}

internal sealed interface GgufHeaderPreflightResult {
    /** Header is plausible only; caller must still validate every metadata entry, tensor, and hash. */
    data class Candidate(val header: GgufHeaderCandidate) : GgufHeaderPreflightResult
    data class Rejected(val reason: GgufHeaderRejection) : GgufHeaderPreflightResult
}

/**
 * Bounded, network-free preflight of the fixed GGUF v3 header. Does not retain or trust
 * an InputStream, validate model payloads, or approve file import. A caller must supply
 * a trustworthy stat size and enforce its own storage budget separately.
 */
internal object GgufHeaderPreflight {
    private const val HEADER_BYTES = 24
    // Conservative early rejection thresholds, not authoritative GGUF tensor limits.
    private const val MAX_DECLARED_TENSORS = 1_000_000L
    private const val MAX_DECLARED_METADATA_ENTRIES = 100_000L

    fun inspect(
        source: InputStream,
        declaredSizeBytes: Long,
        maxSizeBytes: Long,
    ): GgufHeaderPreflightResult {
        fun reject(reason: GgufHeaderRejection) = GgufHeaderPreflightResult.Rejected(reason)
        if (maxSizeBytes <= 0L) return reject(GgufHeaderRejection.INVALID_BUDGET)
        if (declaredSizeBytes <= HEADER_BYTES.toLong()) return reject(GgufHeaderRejection.INVALID_SIZE)
        if (declaredSizeBytes > maxSizeBytes) return reject(GgufHeaderRejection.OVER_BUDGET)

        val bytes = ByteArray(HEADER_BYTES)
        var offset = 0
        try {
            while (offset < HEADER_BYTES) {
                val read = source.read(bytes, offset, HEADER_BYTES - offset)
                if (read < 0) return reject(GgufHeaderRejection.TRUNCATED_HEADER)
                if (read == 0) return reject(GgufHeaderRejection.STALLED_INPUT)
                offset += read
            }
        } catch (_: IOException) {
            return reject(GgufHeaderRejection.READ_FAILED)
        }
        if (
            bytes[0] != 'G'.code.toByte() || bytes[1] != 'G'.code.toByte() ||
            bytes[2] != 'U'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return reject(GgufHeaderRejection.BAD_MAGIC)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.getInt(4)
        if (version != 3) return reject(GgufHeaderRejection.UNSUPPORTED_VERSION)
        val tensors = buffer.getLong(8)
        if (tensors <= 0 || tensors > MAX_DECLARED_TENSORS) {
            return reject(GgufHeaderRejection.INVALID_TENSOR_COUNT)
        }
        val metadata = buffer.getLong(16)
        if (metadata < 0 || metadata > MAX_DECLARED_METADATA_ENTRIES) {
            return reject(GgufHeaderRejection.INVALID_METADATA_COUNT)
        }
        return GgufHeaderPreflightResult.Candidate(
            GgufHeaderCandidate(version = version, tensorCount = tensors, metadataCount = metadata),
        )
    }
}
