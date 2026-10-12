package dev.lain.os.bootstrap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.lain.os.planner.PlannerProfile
import dev.lain.os.planner.PlannerProfileStore

internal data class BootstrapState(
    val capabilities: List<CapabilityAvailability> = emptyList(),
    val plannerName: String? = null,
    val plannerMode: String? = null,
    val error: Boolean = false,
)

internal class BootstrapViewModel(application: Application) : AndroidViewModel(application) {
    private val store = stateStoreFactory(application)
    private val broker = permissionBrokerFactory(application)
    private val profiles by lazy { PlannerProfileStore(application) }
    private val mutableState = MutableLiveData(BootstrapState())
    val state: LiveData<BootstrapState> = mutableState

    fun isComplete(): Boolean = store.isComplete()

    fun refresh() {
        val selected = runCatching { profiles.active() }.getOrNull()
        mutableState.value = BootstrapState(
            capabilities = broker.snapshot(),
            plannerName = selected?.name,
            plannerMode = selected?.mode,
            error = selected == null,
        )
    }

    fun microphonePermission(): String? = broker.runtimePermissionFor("voice.input")

    fun finish(useDemo: Boolean): Boolean = try {
        if (useDemo) profiles.select(PlannerProfile.DEMO_ID)
        store.complete()
        true
    } catch (_: Exception) {
        mutableState.value = (mutableState.value ?: BootstrapState()).copy(error = true)
        false
    }

    companion object {
        @Volatile
        var permissionBrokerFactory: (Application) -> PermissionBroker = PermissionBroker::android
        @Volatile
        var stateStoreFactory: (Application) -> BootstrapStateStore = { BootstrapStateStore(it) }
    }
}
