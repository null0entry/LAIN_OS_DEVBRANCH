package dev.lain.os.planner

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class VerifiedModelExportTest {
    private val sample = ByteArray(128 * 1024) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(sample)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun verifiedCopyWritesExactBytes() {
        val output = ByteArrayOutputStream()
        assertEquals(sample.size.toLong(), VerifiedModelExport.copy(
            ByteArrayInputStream(sample), output, sample.size.toLong(), sha
        ))
        assertArrayEquals(sample, output.toByteArray())
    }

    @Test fun cancelledExportStopsDuringStream() {
        val output = ByteArrayOutputStream()
        var calls = 0
        assertThrows(InterruptedIOException::class.java) {
            VerifiedModelExport.copy(ByteArrayInputStream(sample), output, sample.size.toLong(), sha) {
                ++calls >= 2
            }
        }
        assertTrue(output.size() < sample.size)
    }

    @Test fun rejectWrongDigestOrTruncatedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedModelExport.copy(ByteArrayInputStream(sample),
                ByteArrayOutputStream(), sample.size.toLong(), "0".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedModelExport.copy(ByteArrayInputStream(sample),
                ByteArrayOutputStream(), sample.size.toLong() + 1L, sha)
        }
    }

    @Test fun invalidMetadataFailsBeforeCopy() {
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedModelExport.copy(ByteArrayInputStream(sample),
                ByteArrayOutputStream(), 0L, sha)
        }
    }
}
