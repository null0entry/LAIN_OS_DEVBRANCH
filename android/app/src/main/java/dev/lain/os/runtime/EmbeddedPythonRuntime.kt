package dev.lain.os.runtime

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import java.io.RandomAccessFile

/** Coordinates Chaquopy's shared asset extraction across the owner and runtime processes. */
internal object EmbeddedPythonRuntime {
    private var controllerModule: PyObject? = null

    @Synchronized
    fun controllerModule(context: Context): PyObject {
        val application = context.applicationContext
        val lockFile = File(application.filesDir, "lain-python-initialization.lock")
        return RandomAccessFile(lockFile, "rw").use { file ->
            file.channel.lock().use {
                controllerModule?.let { return it }
                // RuntimeService lives in :runtime. Its first imports and the owner's
                // first imports can otherwise delete/extract the same Chaquopy assets.
                if (!Python.isStarted()) Python.start(AndroidPlatform(application))
                val module = Python.getInstance().getModule("lain.app.control")

                // Chaquopy records extracted asset hashes with apply(). Flush those
                // writes before another process constructs its AndroidPlatform and
                // reads the shared hashes; stale hashes would trigger another delete.
                check(application.getSharedPreferences("chaquopy", Context.MODE_PRIVATE)
                    .edit().commit()) { "Could not persist Python asset state" }
                controllerModule = module
                module
            }
        }
    }
}
