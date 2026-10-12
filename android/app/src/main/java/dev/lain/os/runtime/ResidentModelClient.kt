package dev.lain.os.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import org.json.JSONObject

/**
 * Local-only Binder client. Never starts a foreground service implicitly;
 * explicit Start Model in Workbench owns long-lived background residency.
 */
internal class ResidentModelClient(
    private val context: Context,
    private val onConnection: (Boolean) -> Unit,
) {
    @Volatile private var binder: IBinder? = null
    private var connected = false
    val isConnected: Boolean get() = binder != null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
            onConnection(service != null)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            onConnection(false)
        }
        override fun onBindingDied(name: ComponentName?) {
            binder = null
            onConnection(false)
        }
        override fun onNullBinding(name: ComponentName?) {
            binder = null
            onConnection(false)
        }
    }

    fun connect() {
        if (connected) return
        connected = try {
            context.bindService(Intent(context, ResidentModelService::class.java),
                connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) { false }
        if (!connected) onConnection(false)
    }

    fun disconnect() {
        binder = null
        if (connected) try { context.unbindService(connection) } catch (_: Exception) { }
        connected = false
    }

    private fun transact(target: IBinder?, method: Int, model: String? = null, prompt: String? = null): String {
        if (target == null) return error("PLANNER_MODEL_BUSY")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ResidentModelService.DESCRIPTOR)
            if (method == ResidentModelService.TRANSACTION_CHAT) {
                data.writeString(model)
                data.writeString(prompt)
            }
            check(target.transact(method, data, reply, 0))
            reply.readException()
            val response = reply.readString() ?: error("PLANNER_MODEL_LOAD_FAILED")
            require(response.toByteArray(Charsets.UTF_8).size <= 20_000)
            response
        } catch (_: Exception) { error("PLANNER_MODEL_LOAD_FAILED") }
        finally { data.recycle(); reply.recycle() }
    }

    fun call(digest: String, prompt: String): ChatCall = ChatCall(binder, digest, prompt)

    inner class ChatCall(
        private val target: IBinder?,
        private val digest: String,
        private val prompt: String,
    ) {
        fun execute(): String = transact(target, ResidentModelService.TRANSACTION_CHAT, digest, prompt)
        fun cancel() { transact(target, ResidentModelService.TRANSACTION_CANCEL) }
    }

    fun status(): String = transact(binder, ResidentModelService.TRANSACTION_STATUS)

    companion object {
        private fun error(code: String) = JSONObject().put("ok", false).put("error", code).toString()
    }
}
