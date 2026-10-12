package dev.lain.os.runtime

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.chaquo.python.PyObject
import dev.lain.os.planner.NativePlannerBridge
import dev.lain.os.planner.PlannerProfileStore
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Only the owner application's UID can reach this non-exported Binder. */
class RuntimeService : Service() {
    private val worker = ScheduledThreadPoolExecutor(1)
    private val pump = RuntimePump(worker) {
        if (!bound) controller?.callAttr("stop_active")
        controller?.callAttr("advance")?.toBoolean() ?: false
    }
    @Volatile private var controller: PyObject? = null
    @Volatile private var plannerBridge: NativePlannerBridge? = null
    @Volatile private var startupFailed = false
    @Volatile private var bound = false

    override fun onCreate() {
        super.onCreate()
        worker.removeOnCancelPolicy = true
        worker.execute {
            try {
                val module = EmbeddedPythonRuntime.controllerModule(this)
                val bridge = NativePlannerBridge(this)
                plannerBridge = bridge
                controller = module.callAttr(
                    "create_controller",
                    File(filesDir, "lain").absolutePath,
                    NativeCapabilities(this),
                    PlannerProfileStore(this),
                    bridge,
                )
            } catch (_: Exception) {
                // Fail closed without Python tracebacks or raw private output in logcat.
                controller = null
                plannerBridge = null
                startupFailed = true
            }
        }
    }

    private val controlBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            RuntimeProtocol.enforceCaller()
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(RuntimeProtocol.DESCRIPTOR)
                return true
            }
            if (code != RuntimeProtocol.REQUEST || reply == null) return false
            data.enforceInterface(RuntimeProtocol.DESCRIPTOR)
            val response = try {
                require(data.dataAvail() <= RuntimeProtocol.MAX_BYTES * 4)
                val payload = data.readString() ?: throw IllegalArgumentException()
                require(data.dataAvail() == 0)
                require(payload.toByteArray(Charsets.UTF_8).size <= RuntimeProtocol.MAX_BYTES)
                process(payload)
            } catch (_: Exception) { RuntimeProtocol.failure("APP_REQUEST_FAILED") }
            reply.writeNoException()
            reply.writeString(response)
            return true
        }
    }

    private fun process(payload: String): String {
        val python = controller ?: return RuntimeProtocol.failure(
            if (startupFailed) "APP_START_FAILED" else "APP_STARTING")
        val command = JSONObject(payload).optString("command")
        if (command in RuntimeProtocol.readCommands) {
            if (command == "stop") plannerBridge?.cancel()
            val result = python.callAttr("dispatch", payload).toString()
            if (command == "stop") kick()
            return bounded(result)
        }
        if (command !in RuntimeProtocol.writeCommands || !bound) {
            return RuntimeProtocol.failure("APP_REQUEST_INVALID")
        }
        if (worker.queue.size >= 32) return RuntimeProtocol.failure("APP_BUSY")
        val pending = worker.submit<String> {
            if (!bound) RuntimeProtocol.failure("APP_NOT_VISIBLE")
            else {
                val result = bounded(python.callAttr("dispatch", payload).toString())
                // Unbinding may race a mutation already running on this worker.
                if (!bound) python.callAttr("stop_active")
                result
            }
        }
        return try {
            val result = pending.get(5, TimeUnit.SECONDS)
            if (RuntimeProtocol.shouldAdvance(command, result)) kick()
            result
        } catch (_: Exception) {
            pending.cancel(false)
            // A started command may have settled. Never auto-retry ambiguous writes.
            // New turn submissions deliberately do not wake unrelated active work
            // when their outcome is uncertain; inspection/recovery remains explicit.
            if (RuntimeProtocol.shouldAdvanceAfterUncertainOutcome(command)) kick()
            RuntimeProtocol.failure("APP_OUTCOME_UNCERTAIN")
        }
    }

    private fun bounded(value: String): String =
        if (value.toByteArray(Charsets.UTF_8).size <= RuntimeProtocol.MAX_BYTES) value
        else RuntimeProtocol.failure("APP_RESPONSE_TOO_LARGE")

    private fun kick() = pump.kick()

    override fun onBind(intent: Intent?): IBinder {
        bound = true
        return controlBinder
    }

    override fun onRebind(intent: Intent?) {
        bound = true
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        requestStop()
        return true
    }

    private fun requestStop() {
        plannerBridge?.cancel()
        try { controller?.callAttr("stop_active") } catch (_: Exception) { }
        kick()
    }

    override fun onDestroy() {
        bound = false
        requestStop()
        worker.shutdown()
        super.onDestroy()
    }
}
