package dev.lain.os.runtime

import android.content.Intent
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedPythonRuntimeAndroidTest {
    @get:Rule val service = ServiceTestRule()

    private fun sessions(binder: IBinder): JSONObject {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(RuntimeProtocol.DESCRIPTOR)
            data.writeString(RuntimeProtocol.request("sessions", JSONObject()))
            assertTrue(binder.transact(RuntimeProtocol.REQUEST, data, reply, 0))
            reply.readException()
            return JSONObject(reply.readString()!!)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @Test fun runtimeStartupWaitsForSharedAssetsAndOwnerImportsRemainUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val binder = RandomAccessFile(File(context.filesDir, "lain-python-initialization.lock"), "rw").use { file ->
            file.channel.lock().use {
                val runtime = service.bindService(Intent(context, RuntimeService::class.java))
                // The service is in :runtime. It must not import or delete the owner's
                // extracted Python assets while another process owns this file lock.
                val deadline = SystemClock.elapsedRealtime() + 1_000
                do {
                    assertEquals("APP_STARTING", sessions(runtime).getString("error"))
                    SystemClock.sleep(50)
                } while (SystemClock.elapsedRealtime() < deadline)
                runtime
            }
        }

        val workers = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        try {
            val imports = (0 until 3).map {
                workers.submit<Boolean> {
                    assertTrue(start.await(10, TimeUnit.SECONDS))
                    EmbeddedPythonRuntime.controllerModule(context)
                        .get("create_controller") != null
                }
            }
            start.countDown()
            imports.forEach { assertTrue(it.get(60, TimeUnit.SECONDS)) }
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (SystemClock.elapsedRealtime() < deadline) {
                val response = sessions(binder)
                if (response.optBoolean("ok")) {
                    assertTrue(response.has("sessions"))
                    return
                }
                assertEquals("APP_STARTING", response.getString("error"))
                SystemClock.sleep(50)
            }
            fail("Runtime did not become ready after Python asset lock release")
        } finally {
            start.countDown()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
