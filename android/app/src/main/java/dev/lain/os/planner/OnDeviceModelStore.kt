package dev.lain.os.planner

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.UUID

internal data class ImportedGgufModel(val sha256: String, val bytes: Long)

/**
 * User-chosen model bytes are streamed into app-private storage, never decoded into RAM.
 * The SHA-256 filename is the model identity pinned by trusted planner profiles.
 * This is an import/integrity check, NOT an assertion that all GGUF tensor layouts are valid.
 */
internal class OnDeviceModelStore(
    private val root: File,
    private val usableSpace: () -> Long = { root.usableSpace },
) {
    constructor(
        context: Context,
        root: File = File(context.applicationContext.filesDir, "models"),
    ) : this(root, { root.usableSpace })

    companion object {
        private val SHA = Regex("^[0-9a-f]{64}$")
        private const val DEFAULT_MAX_BYTES = 2_000_000_000L
        private const val FREE_SPACE_FLOOR_BYTES = 96L * 1024 * 1024
        @Volatile private var lastVerified: Verified? = null
    }

    init { require(root.isDirectory || root.mkdirs()) { "private model storage unavailable" } }

    private data class Verified(val path: String, val size: Long, val modified: Long)

    fun requireStagingCapacity(expectedBytes: Long) {
        require(expectedBytes > 24 && expectedBytes <= DEFAULT_MAX_BYTES) {
            "invalid GGUF staging budget"
        }
        require(usableSpace() >= expectedBytes + FREE_SPACE_FLOOR_BYTES) {
            "insufficient private model storage"
        }
    }

    @Synchronized
    fun importModel(
        source: InputStream,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        expectedSha256: String? = null,
        isCancelled: () -> Boolean = { false },
    ): ImportedGgufModel {
        require(maxBytes > 24 && maxBytes <= DEFAULT_MAX_BYTES) { "invalid GGUF import budget" }
        require(expectedSha256 == null || SHA.matches(expectedSha256)) {
            "invalid expected GGUF digest"
        }
        val stage = File(root, ".import-${UUID.randomUUID()}.part")
        val sha = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        try {
            source.use { input ->
                checkCancellation(isCancelled)
                stage.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancellation(isCancelled)
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(n > 0) { "GGUF import stream stalled" }
                        checkCancellation(isCancelled)
                        bytes += n
                        require(bytes <= maxBytes) { "GGUF exceeds configured size" }
                        require(usableSpace() > FREE_SPACE_FLOOR_BYTES) {
                            "insufficient private model storage"
                        }
                        sha.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            checkCancellation(isCancelled)
            require(bytes > 24) { "invalid GGUF size" }
            require(
                stage.inputStream().use {
                    GgufHeaderPreflight.inspect(it, bytes, maxBytes) is GgufHeaderPreflightResult.Candidate
                }
            ) { "unsupported GGUF fixed header" }

            val hash = sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(expectedSha256 == null || hash == expectedSha256) {
                "GGUF digest did not match pinned model"
            }
            checkCancellation(isCancelled)
            val destination = File(root, "$hash.gguf")
            if (destination.exists()) {
                resolve(hash, isCancelled)
            } else {
                require(stage.renameTo(destination)) { "atomic GGUF import failed" }
            }
            return ImportedGgufModel(hash, bytes)
        } finally {
            stage.delete()
        }
    }

    @Synchronized
    fun resolve(hash: String, isCancelled: () -> Boolean = { false }): File {
        checkCancellation(isCancelled)
        require(SHA.matches(hash)) { "invalid GGUF model identity" }
        val parent = root.canonicalFile
        val file = File(parent, "$hash.gguf")
        require(file.canonicalFile.parentFile == parent) { "model escaped private storage" }
        require(file.isFile && file.length() > 24 && file.length() <= DEFAULT_MAX_BYTES) {
            "model file not found or outside resource bounds"
        }
        // This is app-private storage. A matching fingerprint avoids repeatedly
        // hashing gigabytes for every turn after the initial verification.
        val signature = Verified(file.canonicalPath, file.length(), file.lastModified())
        if (lastVerified == signature) {
            checkCancellation(isCancelled)
            return file
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancellation(isCancelled)
                val n = input.read(buffer)
                if (n < 0) break
                require(n > 0) { "GGUF hash stream stalled" }
                digest.update(buffer, 0, n)
            }
        }
        checkCancellation(isCancelled)
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == actual) { "GGUF digest mismatch" }
        lastVerified = signature
        return file
    }

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || isCancelled()) {
            throw InterruptedIOException("GGUF verification cancelled")
        }
    }

    @Synchronized
    fun list(): List<ImportedGgufModel> = root.listFiles().orEmpty()
        .asSequence()
        .filter { it.isFile && it.name.matches(Regex("^[0-9a-f]{64}\\.gguf$")) }
        .map { ImportedGgufModel(it.name.removeSuffix(".gguf"), it.length()) }
        .sortedBy { it.sha256 }
        .toList()
}
