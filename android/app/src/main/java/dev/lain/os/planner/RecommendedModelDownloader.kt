package dev.lain.os.planner

import java.io.FilterInputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

internal class RecommendedModelDownloader(
    private val modelStore: OnDeviceModelStore,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    fun download(
        model: RecommendedModel,
        onProgress: (downloadedBytes: Long, expectedBytes: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): ImportedGgufModel {
        require(model.expectedBytes > 24L) { "invalid recommended model size" }
        require(model.expectedSha256.matches(Regex("^[0-9a-f]{64}$"))) {
            "invalid recommended model digest"
        }
        val url = URL(model.url)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "recommended model download requires HTTPS"
        }
        checkCancellation(isCancelled)
        modelStore.requireStagingCapacity(model.expectedBytes)

        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.useCaches = false

            val code = connection.responseCode
            require(code in 200..299) { "recommended model download failed: HTTP $code" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "recommended model redirect left HTTPS"
            }

            val advertised = connection.contentLengthLong
            require(advertised < 0L || advertised <= model.expectedBytes) {
                "recommended model exceeds pinned size"
            }

            val progressSource = ProgressInputStream(
                source = connection.inputStream,
                expectedBytes = model.expectedBytes,
                onProgress = onProgress,
                isCancelled = isCancelled,
            )
            val imported = modelStore.importModel(
                source = progressSource,
                maxBytes = model.expectedBytes,
                expectedSha256 = model.expectedSha256,
                isCancelled = isCancelled,
            )
            require(imported.bytes == model.expectedBytes) {
                "recommended model size did not match pinned artifact"
            }
            onProgress(imported.bytes, model.expectedBytes)
            return imported
        } finally {
            connection.disconnect()
        }
    }

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || isCancelled()) {
            throw InterruptedIOException("recommended model download cancelled")
        }
    }

    private class ProgressInputStream(
        source: InputStream,
        private val expectedBytes: Long,
        private val onProgress: (Long, Long) -> Unit,
        private val isCancelled: () -> Boolean,
    ) : FilterInputStream(source) {
        private var downloaded = 0L

        override fun read(): Int {
            checkCancelled()
            val value = super.read()
            if (value >= 0) report(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkCancelled()
            val count = super.read(buffer, offset, length)
            if (count > 0) report(count)
            return count
        }

        private fun report(count: Int) {
            downloaded += count
            require(downloaded <= expectedBytes) { "recommended model exceeds pinned size" }
            onProgress(downloaded, expectedBytes)
        }

        private fun checkCancelled() {
            if (Thread.currentThread().isInterrupted || isCancelled()) {
                throw InterruptedIOException("recommended model download cancelled")
            }
        }
    }
}
