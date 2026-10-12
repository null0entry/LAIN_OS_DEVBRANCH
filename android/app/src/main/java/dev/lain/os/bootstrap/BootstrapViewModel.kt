package dev.lain.os.bootstrap

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.lain.os.planner.PlannerProfile
import dev.lain.os.planner.PlannerProfileStore
import java.util.concurrent.Executors

internal data class BootstrapState(
    val capabilities: List<CapabilityAvailability> = emptyList(),
    val plannerName: String? = null,
    val plannerMode: String? = null,
    val plannerLoading: Boolean = true,
    val error: Boolean = false,
    val finishing: Boolean = false,
    val complete: Boolean = false,
)

internal class BootstrapViewModel(application: Application) : AndroidViewModel(application) {
    private val store = stateStoreFactory(application)
    private val broker = permissionBrokerFactory(application)
    private val profiles by lazy { PlannerProfileStore(application) }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val mutableState = MutableLiveData(BootstrapState())
    val state: LiveData<BootstrapState> = mutableState

    fun isComplete(): Boolean = store.isComplete()

    fun refresh() {
        mutableState.value = current().copy(capabilities = broker.snapshot())
        io.execute {
            val selected = runCatching { profiles.active() }.getOrNull()
            main.post {
                if (!closed) mutableState.value = current().copy(
                    plannerName = selected?.name,
                    plannerMode = selected?.mode,
                    plannerLoading = false,
                    error = selected == null,
                )
            }
        }
    }

    fun microphonePermission(): String? = broker.runtimePermissionFor("voice.input")

    fun finish(useDemo: Boolean) {
        if (current().finishing || current().complete) return
        mutableState.value = current().copy(finishing = true, error = false)
        io.execute {
            val saved = runCatching {
                if (useDemo) profiles.select(PlannerProfile.DEMO_ID)
                store.complete()
            }.isSuccess
            main.post {
                if (!closed) mutableState.value = current().copy(
                    finishing = false,
                    complete = saved,
                    error = !saved,
                )
            }
        }
    }

    private fun current(): BootstrapState = mutableState.value ?: BootstrapState()

    override fun onCleared() {
        closed = true
        io.shutdownNow()
        super.onCleared()
    }

    companion object {
        @Volatile
        var permissionBrokerFactory: (Application) -> PermissionBroker = PermissionBroker::android
        @Volatile
        var stateStoreFactory: (Application) -> BootstrapStateStore = { BootstrapStateStore(it) }
    }
}
