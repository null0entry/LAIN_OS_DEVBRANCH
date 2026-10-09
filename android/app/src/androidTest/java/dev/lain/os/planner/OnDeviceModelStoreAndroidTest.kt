package dev.lain.os.planner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    @Test fun privateImportRecordsExactHashAndCanResolveSameBytes() {
        val store = OnDeviceModelStore(context, root)
        val data = candidate()
        val imported = store.importModel(ByteArrayInputStream(data), maxBytes = 2048)
        assertTrue(imported.sha256.matches(Regex("[0-9a-f]{64}")))
        assertArrayEquals(data, store.resolve(imported.sha256).readBytes())
        assertTrue(store.resolve(imported.sha256).canonicalPath.startsWith(root.canonicalPath + "/"))
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
