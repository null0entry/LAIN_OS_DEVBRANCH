package dev.lain.os.planner

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecommendedModelDownloaderTest {
    private val root = Files.createTempDirectory("lain-qwen-download-").toFile()
    @After fun cleanup() { root.deleteRecursively() }

    private fun candidate(): ByteArray =
        ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put("GGUF".toByteArray()).putInt(3).putLong(1).putLong(1).array() +
            ByteArray(256) { it.toByte() }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun model(bytes: ByteArray = candidate(), url: String = "https://example.test/model.gguf") =
        RecommendedModel(
            id = "test-model",
            displayName = "Test Q4_K_M",
            url = url,
            expectedSha256 = sha256(bytes),
            expectedBytes = bytes.size.toLong(),
        )

    @Test fun defaultCatalogPinsApprovedQwenArtifact() {
        val model = RecommendedModelCatalog.default()
        assertEquals("Qwen3-1.7B-Q4_K_M.gguf", model.id)
        assertEquals("Qwen3 1.7B Q4_K_M", model.displayName)
        assertEquals(
            "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            model.url,
        )
        assertEquals(
            "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
            model.expectedSha256,
        )
        assertEquals(1_282_439_264L, model.expectedBytes)
    }

    @Test fun rejectsNonHttpsBeforeOpeningConnection() {
        var opened = false
        val downloader = RecommendedModelDownloader(
            modelStore = OnDeviceModelStore(root),
            connectionFactory = {
                opened = true
                FakeConnection(it, 200, candidate())
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            downloader.download(model(url = "http://example.test/model.gguf"))
        }
        assertFalse(opened)
        assertTrue(OnDeviceModelStore(root).list().isEmpty())
    }

    @Test fun successfulDownloadReportsProgressAndPublishesExactModel() {
        val bytes = candidate()
        val progress = mutableListOf<Pair<Long, Long>>()
        val downloader = RecommendedModelDownloader(
            modelStore = OnDeviceModelStore(root),
            connectionFactory = { FakeConnection(it, 200, bytes) },
        )

        val imported = downloader.download(model(bytes), onProgress = { done, total ->
            progress += done to total
        })

        assertEquals(sha256(bytes), imported.sha256)
        assertEquals(bytes.size.toLong(), imported.bytes)
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), progress.last())
        assertEquals(listOf(imported.sha256), OnDeviceModelStore(root).list().map { it.sha256 })
    }

    @Test fun httpFailureDoesNotPublish() {
        val downloader = RecommendedModelDownloader(
            modelStore = OnDeviceModelStore(root),
            connectionFactory = { FakeConnection(it, 503, byteArrayOf()) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            downloader.download(model())
        }
        assertTrue(OnDeviceModelStore(root).list().isEmpty())
    }

    @Test fun advertisedOversizeFailsBeforeReadingBody() {
        val bytes = candidate()
        var bodyOpened = false
        val downloader = RecommendedModelDownloader(
            modelStore = OnDeviceModelStore(root),
            connectionFactory = {
                FakeConnection(it, 200, bytes, advertisedBytes = bytes.size.toLong() + 1L) {
                    bodyOpened = true
                }
            },
        )

        assertThrows(IllegalArgumentException::class.java) {
            downloader.download(model(bytes))
        }
        assertFalse(bodyOpened)
        assertTrue(OnDeviceModelStore(root).list().isEmpty())
    }

    @Test fun cancellationNeverPublishes() {
        val bytes = candidate()
        val downloader = RecommendedModelDownloader(
            modelStore = OnDeviceModelStore(root),
            connectionFactory = { FakeConnection(it, 200, bytes) },
        )

        assertThrows(InterruptedIOException::class.java) {
            downloader.download(model(bytes), isCancelled = { true })
        }
        assertTrue(OnDeviceModelStore(root).list().isEmpty())
    }

    @Test fun wrongBodyHashNeverPublishes() {
        val expected = candidate()
        val wrong = expected.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val downloader = RecommendedModelDownloader(
            modelStore = OnDeviceModelStore(root),
            connectionFactory = { FakeConnection(it, 200, wrong, expected.size.toLong()) },
        )

        assertThrows(IllegalArgumentException::class.java) {
            downloader.download(model(expected))
        }
        assertTrue(OnDeviceModelStore(root).list().isEmpty())
    }

    private class FakeConnection(
        url: URL,
        private val code: Int,
        private val body: ByteArray,
        private val advertisedBytes: Long = body.size.toLong(),
        private val onBodyOpen: () -> Unit = {},
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = advertisedBytes
        override fun getInputStream(): ByteArrayInputStream {
            onBodyOpen()
            return ByteArrayInputStream(body)
        }
    }
}
