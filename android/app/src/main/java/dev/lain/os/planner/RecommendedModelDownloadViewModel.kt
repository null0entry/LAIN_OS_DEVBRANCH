package dev.lain.os.planner

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class RecommendedModelEditorSnapshot(
    val profileId: String?,
    val name: String,
    val model: String,
)

internal sealed interface RecommendedModelDownloadState {
    val editor: RecommendedModelEditorSnapshot?

    data object Idle : RecommendedModelDownloadState {
        override val editor: RecommendedModelEditorSnapshot? = null
    }

    data class Downloading(
        val model: RecommendedModel,
        val downloadedBytes: Long,
        val expectedBytes: Long,
        override val editor: RecommendedModelEditorSnapshot? = null,
    ) : RecommendedModelDownloadState

    data class Downloaded(
        val model: RecommendedModel,
        val imported: ImportedGgufModel,
        override val editor: RecommendedModelEditorSnapshot? = null,
    ) : RecommendedModelDownloadState

    data class Failed(
        val model: RecommendedModel,
        val message: String,
        override val editor: RecommendedModelEditorSnapshot? = null,
    ) : RecommendedModelDownloadState

    data class Cancelled(
        val model: RecommendedModel,
        override val editor: RecommendedModelEditorSnapshot? = null,
    ) : RecommendedModelDownloadState
}

/** Retains one bounded recommended-model download across Activity recreation. */
internal class RecommendedModelDownloadViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        internal var downloadActionFactory:
            (Application) -> (RecommendedModel, (Long, Long) -> Unit, () -> Boolean) -> ImportedGgufModel =
            { app ->
                val store = OnDeviceModelStore(app)
                val downloader = RecommendedModelDownloader(store)
                val publisher = RecommendedModelDownloadsPublisher(app)
                val action:
                    (RecommendedModel, (Long, Long) -> Unit, () -> Boolean) -> ImportedGgufModel =
                    { model, progress, cancelled ->
                        val imported = downloader.download(model, progress, cancelled)
                        // On API 24-28 the user chooses the export destination
                        // with ACTION_CREATE_DOCUMENT; retain the verified private model
                        // instead of reporting the successful download as failed.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            publisher.publish(model, store.resolve(imported.sha256, cancelled), cancelled)
                        }
                        imported
                    }
                action
            }
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "lain-recommended-model-download").apply { isDaemon = true }
    }
    private val downloadAction = downloadActionFactory(application)
    private val mutable = MutableLiveData<RecommendedModelDownloadState>(RecommendedModelDownloadState.Idle)
    val state: LiveData<RecommendedModelDownloadState> = mutable
    val isDownloading: Boolean get() = mutable.value is RecommendedModelDownloadState.Downloading

    @Volatile private var cleared = false
    @Volatile private var cancellation = AtomicBoolean(false)
    @Volatile private var activeThread: Thread? = null

    fun start(
        model: RecommendedModel = RecommendedModelCatalog.default(),
        editor: RecommendedModelEditorSnapshot? = null,
    ): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (cleared || isDownloading) return false

        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        mutable.value = RecommendedModelDownloadState.Downloading(
            model,
            0L,
            model.expectedBytes,
            editor,
        )
        worker.execute {
            activeThread = Thread.currentThread()
            var lastReported = 0L
            val result = try {
                val imported = downloadAction(
                    model,
                    { downloaded, expected ->
                        if (downloaded == expected || downloaded - lastReported >= 8L * 1024 * 1024) {
                            lastReported = downloaded
                            main.post {
                                if (!cleared && !cancelled.get() &&
                                    mutable.value is RecommendedModelDownloadState.Downloading
                                ) {
                                    mutable.value = RecommendedModelDownloadState.Downloading(
                                        model,
                                        downloaded.coerceAtMost(expected),
                                        expected,
                                        editor,
                                    )
                                }
                            }
                        }
                    },
                    cancelled::get,
                )
                // The imported model is always verified; public export on API 24-28 is a separate user action.
                // A late Cancel cannot truthfully roll that completed publication back.
                RecommendedModelDownloadState.Downloaded(model, imported, editor)
            } catch (_: InterruptedIOException) {
                RecommendedModelDownloadState.Cancelled(model, editor)
            } catch (exc: Exception) {
                if (cancelled.get()) {
                    RecommendedModelDownloadState.Cancelled(model, editor)
                } else {
                    RecommendedModelDownloadState.Failed(
                        model,
                        exc.message?.takeIf { it.isNotBlank() } ?: exc.javaClass.simpleName,
                        editor,
                    )
                }
            }
            activeThread = null
            main.post { if (!cleared) mutable.value = result }
        }
        return true
    }

    fun cancel(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!isDownloading) return false
        cancellation.set(true)
        activeThread?.interrupt()
        return true
    }

    override fun onCleared() {
        cleared = true
        cancellation.set(true)
        activeThread?.interrupt()
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onCleared()
    }
}
