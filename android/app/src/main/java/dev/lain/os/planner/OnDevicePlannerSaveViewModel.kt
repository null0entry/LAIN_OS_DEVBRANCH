package dev.lain.os.planner

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

internal sealed interface OnDevicePlannerSaveState {
    data object Idle : OnDevicePlannerSaveState
    data class Saving(val draft: PlannerProfileDraft) : OnDevicePlannerSaveState
    data class Saved(val profileId: String) : OnDevicePlannerSaveState
    data class Failed(val draft: PlannerProfileDraft, val message: String) : OnDevicePlannerSaveState
}

/** Retain one model verification across recreation without retaining an Activity. */
internal class OnDevicePlannerSaveViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        internal var saveActionFactory: (Application) -> (PlannerProfileDraft) -> String = { app ->
            val settings = PlannerSettingsManager(app)
            val save: (PlannerProfileDraft) -> String = { draft ->
                settings.save(draft).also(settings::select)
            }
            save
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "lain-gguf-profile-save").apply { isDaemon = true }
    }
    private val saveAction = saveActionFactory(application)
    private val mutable = MutableLiveData<OnDevicePlannerSaveState>(OnDevicePlannerSaveState.Idle)
    val state: LiveData<OnDevicePlannerSaveState> = mutable
    val isSaving: Boolean get() = mutable.value is OnDevicePlannerSaveState.Saving
    @Volatile private var cleared = false

    fun save(draft: PlannerProfileDraft): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (cleared || mutable.value != OnDevicePlannerSaveState.Idle) return false
        require(draft.mode == "on_device" && draft.credential == null)
        mutable.value = OnDevicePlannerSaveState.Saving(draft)
        worker.execute {
            val result = try {
                OnDevicePlannerSaveState.Saved(saveAction(draft))
            } catch (exc: Exception) {
                OnDevicePlannerSaveState.Failed(
                    draft, exc.message?.takeIf { it.isNotBlank() } ?: exc.javaClass.simpleName,
                )
            }
            main.post { if (!cleared) mutable.value = result }
        }
        return true
    }

    fun consumeResult() {
        if (mutable.value is OnDevicePlannerSaveState.Saved || mutable.value is OnDevicePlannerSaveState.Failed) {
            mutable.value = OnDevicePlannerSaveState.Idle
        }
    }

    override fun onCleared() {
        cleared = true
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
