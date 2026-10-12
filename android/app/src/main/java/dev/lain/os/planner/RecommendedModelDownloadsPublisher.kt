package dev.lain.os.planner

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.InterruptedIOException

/**
 * Publishes a verified private GGUF into user-visible Downloads.
 * Android 10+ uses a pending MediaStore entry so incomplete files are hidden.
 * Android 7-9 exports via ACTION_CREATE_DOCUMENT and the user's chosen location.
 * The private app-owned copy must remain for native GGUF path loading.
 */
internal class RecommendedModelDownloadsPublisher(private val context: Context) {
    @TargetApi(Build.VERSION_CODES.Q)
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
            resolver.openOutputStream(uri, "w")?.use { output ->
                privateFile.inputStream().buffered().use { input ->
                    VerifiedModelExport.copy(input, output, model.expectedBytes,
                        model.expectedSha256, isCancelled)
                }
            } ?: throw IllegalStateException("cannot write public Downloads entry")
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

    /** Target must be the newly created URI returned by ACTION_CREATE_DOCUMENT. */
    fun publishToDocument(
        uri: Uri,
        expectedSha256: String,
        expectedBytes: Long,
        privateFile: File,
        isCancelled: () -> Boolean = { false },
    ) {
        require(uri.scheme == "content") { "invalid system document URI" }
        require(privateFile.isFile && privateFile.length() == expectedBytes) {
            "private GGUF size mismatch"
        }
        val resolver = context.contentResolver
        var complete = false
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                privateFile.inputStream().buffered().use { input ->
                    VerifiedModelExport.copy(input, output, expectedBytes,
                        expectedSha256, isCancelled)
                }
            } ?: throw IllegalStateException("cannot open system document")
            complete = true
        } finally {
            if (!complete) {
                try { DocumentsContract.deleteDocument(resolver, uri) } catch (_: Exception) { }
            }
        }
    }
}
