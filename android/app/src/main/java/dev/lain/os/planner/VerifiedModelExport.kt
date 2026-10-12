package dev.lain.os.planner

import java.io.InputStream
import java.io.OutputStream
import java.io.InterruptedIOException
import java.security.MessageDigest

/** Portable integrity-checked GGUF export for MediaStore and SAF destinations. */
internal object VerifiedModelExport {
    fun copy(
        source: InputStream,
        destination: OutputStream,
        expectedBytes: Long,
        expectedSha256: String,
        isCancelled: () -> Boolean = { false },
    ): Long {
        require(expectedBytes > 24 && expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
            "invalid model export metadata"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            if (isCancelled() || Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("GGUF export cancelled")
            }
            val count = source.read(buffer)
            if (count < 0) break
            require(count > 0) { "model export source stalled" }
            copied += count
            require(copied <= expectedBytes) { "model export exceeds verified size" }
            digest.update(buffer, 0, count)
            destination.write(buffer, 0, count)
        }
        destination.flush()
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("GGUF export cancelled")
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(copied == expectedBytes && actual == expectedSha256) {
            "model export size or SHA-256 mismatch"
        }
        return copied
    }
}
