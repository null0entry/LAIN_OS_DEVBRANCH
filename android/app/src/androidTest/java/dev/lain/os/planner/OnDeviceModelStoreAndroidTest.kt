package dev.lain.os.planner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceModelStoreAndroidTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val root = File(context.cacheDir, "gguf-import-" + UUID.randomUUID())
    @After fun cleanup() { root.deleteRecursively() }

    private fun candidate(): ByteArray =
        ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put("GGUF".toByteArray()).putInt(3).putLong(1).putLong(1).array() +
            ByteArray(256) { it.toByte() }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun privateImportRecordsExactHashAndCanResolveSameBytes() {
        val store = OnDeviceModelStore(context, root)
        val data = candidate()
        val imported = store.importModel(ByteArrayInputStream(data), maxBytes = 2048)
        assertTrue(imported.sha256.matches(Regex("[0-9a-f]{64}")))
        assertArrayEquals(data, store.resolve(imported.sha256).readBytes())
        assertTrue(store.resolve(imported.sha256).canonicalPath.startsWith(root.canonicalPath + "/"))
    }

    @Test fun pinnedImportPublishesOnlyWhenExpectedHashMatches() {
        val store = OnDeviceModelStore(context, root)
        val data = candidate()
        val expected = sha256(data)

        val imported = store.importModel(
            ByteArrayInputStream(data),
            maxBytes = 2048,
            expectedSha256 = expected,
        )

        assertEquals(expected, imported.sha256)
        assertEquals(listOf(expected), store.list().map { it.sha256 })
    }

    @Test fun wrongExpectedHashNeverPublishesModel() {
        val store = OnDeviceModelStore(context, root)
        val data = candidate()
        val wrong = "0".repeat(64)

        assertThrows(IllegalArgumentException::class.java) {
            store.importModel(
                ByteArrayInputStream(data),
                maxBytes = 2048,
                expectedSha256 = wrong,
            )
        }

        assertTrue(store.list().isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test fun cancellationDuringStreamDeletesStagingAndNeverPublishes() {
        val store = OnDeviceModelStore(context, root)
        val data = candidate()
        var cancelled = false
        val source = object : ByteArrayInputStream(data) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count > 0) cancelled = true
                return count
            }
        }

        assertThrows(InterruptedIOException::class.java) {
            store.importModel(
                source,
                maxBytes = 2048,
                expectedSha256 = sha256(data),
                isCancelled = { cancelled },
            )
        }

        assertTrue(store.list().isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test fun insufficientStorageDeletesStagingAndNeverPublishes() {
        val store = OnDeviceModelStore(root, usableSpace = { 0L })

        assertThrows(IllegalArgumentException::class.java) {
            store.importModel(ByteArrayInputStream(candidate()), maxBytes = 2048)
        }

        assertTrue(store.list().isEmpty())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test fun rejectsTruncatedAndOversizedImportsWithoutPublishingFiles() {
        val store = OnDeviceModelStore(context, root)
        assertThrows(IllegalArgumentException::class.java) {
            store.importModel(ByteArrayInputStream(byteArrayOf(1,2,3)), 2048)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.importModel(ByteArrayInputStream(candidate()), 100)
        }
        assertTrue(store.list().isEmpty())
    }

    @Test fun tamperingWithStoredModelInvalidatesItsHash() {
        val store = OnDeviceModelStore(context, root)
        val imported = store.importModel(ByteArrayInputStream(candidate()), 2048)
        val path = store.resolve(imported.sha256)
        path.appendText("tampered")
        assertThrows(IllegalArgumentException::class.java) { store.resolve(imported.sha256) }
        assertThrows(IllegalArgumentException::class.java) { store.resolve("../secrets") }
    }
}
