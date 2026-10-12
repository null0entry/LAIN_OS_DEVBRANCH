package dev.lain.os.planner

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InterruptedIOException
import java.security.MessageDigest

/**
 * Publishes a verified private GGUF into user-visible Downloads.
 * Android 10+ uses a pending MediaStore entry: incomplete files never become public.
 * Older Android versions require a user-selected SAF destination (handled by the caller).
 */
internal class RecommendedModelDownloadsPublisher(private val context: Context) {
    fun publish(
        model: RecommendedModel,
        privateFile: File,
        isCancelled: () -> Boolean = { false },
    ): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "public Downloads requires Android 10+; use the system document picker on older devices"
        }
        require(model.id.matches(Regex("^[A-Za-z0-9._-]+\\.gguf$"))) { "invalid model filename" }
        require(privateFile.isFile && privateFile.length() == model.expectedBytes) {
            "model size mismatch before export"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, model.id)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("cannot create public Downloads entry")
        var published = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            resolver.openOutputStream(uri, "w")?.use { output ->
                privateFile.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isCancelled() || Thread.currentThread().isInterrupted) {
                            throw InterruptedIOException("GGUF export cancelled")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        require(copied <= model.expectedBytes) { "model export exceeded pinned size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            } ?: throw IllegalStateException("cannot write public Downloads entry")
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(copied == model.expectedBytes && actual == model.expectedSha256) {
                "exported GGUF checksum or size mismatch"
            }
            if (isCancelled() || Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("GGUF export cancelled")
            }
            val update = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            require(resolver.update(uri, update, null, null) == 1) {
                "failed to publish Downloads entry"
            }
            published = true
            return uri
        } finally {
            if (!published) resolver.delete(uri, null, null)
        }
    }
}
