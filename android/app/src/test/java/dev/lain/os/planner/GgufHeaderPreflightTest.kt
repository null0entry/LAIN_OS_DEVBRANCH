package dev.lain.os.planner

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufHeaderPreflightTest {
    private fun header(
        version: Int = 3,
        tensors: Long = 4,
        metadata: Long = 5,
    ): ByteArray = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        .put(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        .putInt(version)
        .putLong(tensors)
        .putLong(metadata)
        .array()

    private fun result(bytes: ByteArray, claimedSize: Long = 1024, limit: Long = 2048) =
        GgufHeaderPreflight.inspect(ByteArrayInputStream(bytes), claimedSize, limit)

    private fun rejected(result: GgufHeaderPreflightResult, reason: GgufHeaderRejection) {
        assertEquals(GgufHeaderPreflightResult.Rejected(reason), result)
    }

    @Test fun readsOnlyTheHeaderAndReportsCandidateNotVerifiedModel() {
        val payload = header() + ByteArray(80) { 0x35 }
        val stream = ByteArrayInputStream(payload)
        val result = GgufHeaderPreflight.inspect(stream, payload.size.toLong(), 2048)
        assertEquals(
            GgufHeaderPreflightResult.Candidate(
                GgufHeaderCandidate(version = 3, tensorCount = 4, metadataCount = 5)
            ),
            result,
        )
        assertEquals(80, stream.available())
        assertTrue(result is GgufHeaderPreflightResult.Candidate)
    }

    @Test fun rejectsInvalidMagicAndVersion() {
        val badMagic = header().also { it[0] = 0 }
        rejected(result(badMagic), GgufHeaderRejection.BAD_MAGIC)
        rejected(result(header(version = 1)), GgufHeaderRejection.UNSUPPORTED_VERSION)
        rejected(result(header(version = 99)), GgufHeaderRejection.UNSUPPORTED_VERSION)
    }

    @Test fun rejectsTruncatedHeaderAndHeaderOnlyFile() {
        rejected(result(header().copyOf(23)), GgufHeaderRejection.TRUNCATED_HEADER)
        rejected(result(header(), claimedSize = 24), GgufHeaderRejection.INVALID_SIZE)
    }

    @Test fun refusesUnboundedOrInconsistentSizeBeforeReading() {
        val failOnRead = object : InputStream() {
            override fun read(): Int = error("header stream must not be opened")
        }
        rejected(GgufHeaderPreflight.inspect(failOnRead, -1, 1024), GgufHeaderRejection.INVALID_SIZE)
        rejected(GgufHeaderPreflight.inspect(failOnRead, 25, 0), GgufHeaderRejection.INVALID_BUDGET)
        rejected(GgufHeaderPreflight.inspect(failOnRead, 1025, 1024), GgufHeaderRejection.OVER_BUDGET)
    }

    @Test fun rejectsNegativeZeroAndAbsurdCounts() {
        rejected(result(header(tensors = 0)), GgufHeaderRejection.INVALID_TENSOR_COUNT)
        rejected(result(header(tensors = -1)), GgufHeaderRejection.INVALID_TENSOR_COUNT)
        rejected(result(header(tensors = Long.MAX_VALUE)), GgufHeaderRejection.INVALID_TENSOR_COUNT)
        rejected(result(header(metadata = -1)), GgufHeaderRejection.INVALID_METADATA_COUNT)
        rejected(result(header(metadata = Long.MAX_VALUE)), GgufHeaderRejection.INVALID_METADATA_COUNT)
    }

    @Test fun acceptsShortReadsWithoutReadingPastHeader() {
        val payload = header() + ByteArray(64)
        val stream = object : ByteArrayInputStream(payload) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 1))
        }
        assertTrue(GgufHeaderPreflight.inspect(stream, payload.size.toLong(), 1000) is GgufHeaderPreflightResult.Candidate)
        assertEquals(64, stream.available())
    }

    @Test fun stalledAndThrowingStreamsFailClosed() {
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        rejected(GgufHeaderPreflight.inspect(stalled, 100, 1000), GgufHeaderRejection.STALLED_INPUT)
        val throwing = object : InputStream() {
            override fun read(): Int = throw IOException("private details")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("private details")
        }
        rejected(GgufHeaderPreflight.inspect(throwing, 100, 1000), GgufHeaderRejection.READ_FAILED)
    }
}
