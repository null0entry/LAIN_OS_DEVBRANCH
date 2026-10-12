package dev.lain.os.planner

import java.io.ByteArrayInputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class OnDeviceModelStoreTest {
    private val root = Files.createTempDirectory("lain-gguf-cancel-").toFile()
    @After fun cleanup() { root.deleteRecursively() }

    private fun candidate(): ByteArray = ByteBuffer.allocate(24)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put("GGUF".toByteArray()).putInt(3).putLong(1).putLong(1).array() +
        ByteArray(512 * 1024)

    @Test fun stopDuringHashVerificationPreservesPublishedModel() {
        val store = OnDeviceModelStore(root)
        val data = candidate()
        val imported = store.importModel(ByteArrayInputStream(data), maxBytes = 1024 * 1024)
        var checkpoints = 0
        assertThrows(InterruptedIOException::class.java) {
            store.resolve(imported.sha256) { ++checkpoints >= 3 }
        }
        assertEquals(3, checkpoints)
        assertArrayEquals(data, store.resolve(imported.sha256).readBytes())
    }

    @Test fun verificationCacheAvoidsFullRehashButStillHonorsStopAndMutation() {
        val store = OnDeviceModelStore(root)
        val imported = store.importModel(ByteArrayInputStream(candidate()), maxBytes = 1024 * 1024)
        val model = store.resolve(imported.sha256)
        var checkpoints = 0
        assertTrue(store.resolve(imported.sha256) { ++checkpoints >= 3 }.isFile)
        assertEquals(2, checkpoints)
        assertThrows(InterruptedIOException::class.java) {
            store.resolve(imported.sha256) { true }
        }
        java.io.RandomAccessFile(model, "rw").use {
            it.seek(model.length() - 1)
            it.write(42)
        }
        assertTrue(model.setLastModified(model.lastModified() + 3000))
        assertThrows(IllegalArgumentException::class.java) {
            store.resolve(imported.sha256)
        }
    }

    @Test fun interruptedWorkerRejectsVerificationAndRetainsInterrupt() {
        val store = OnDeviceModelStore(root)
        val imported = store.importModel(ByteArrayInputStream(candidate()), maxBytes = 1024 * 1024)
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedIOException::class.java) { store.resolve(imported.sha256) }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
        assertTrue(store.resolve(imported.sha256).isFile)
    }

    @Test fun interruptedImportClosesInputAndDeletesStagingFile() {
        val store = OnDeviceModelStore(root)
        var closed = false
        val input = object : ByteArrayInputStream(candidate()) {
            override fun close() { closed = true; super.close() }
        }
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedIOException::class.java) {
                store.importModel(input, maxBytes = 1024 * 1024)
            }
        } finally { Thread.interrupted() }
        assertTrue(closed)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }
}
