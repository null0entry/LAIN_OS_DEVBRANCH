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

internal sealed interface RecommendedModelDownloadState {
    data object Idle : RecommendedModelDownloadState
    data class Downloading(
        val model: RecommendedModel,
        val downloadedBytes: Long,
        val expectedBytes: Long,
    ) : RecommendedModelDownloadState
    data class Downloaded(
        val model: RecommendedModel,
        val imported: ImportedGgufModel,
    ) : RecommendedModelDownloadState
    data class Failed(
        val model: RecommendedModel,
        val message: String,
    ) : RecommendedModelDownloadState
    data class Cancelled(val model: RecommendedModel) : RecommendedModelDownloadState
}

/** Retains one bounded recommended-model download across Activity recreation. */
internal class RecommendedModelDownloadViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        @Volatile
        internal var downloadActionFactory:
            (Application) -> (RecommendedModel, (Long, Long) -> Unit, () -> Boolean) -> ImportedGgufModel =
            { app ->
                val downloader = RecommendedModelDownloader(OnDeviceModelStore(app))
                val action:
                    (RecommendedModel, (Long, Long) -> Unit, () -> Boolean) -> ImportedGgufModel =
                    { model, progress, cancelled ->
                        downloader.download(model, progress, cancelled)
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

    fun start(model: RecommendedModel = RecommendedModelCatalog.default()): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (cleared || isDownloading) return false

        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        mutable.value = RecommendedModelDownloadState.Downloading(model, 0L, model.expectedBytes)
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
                                    )
                                }
                            }
                        }
                    },
                    cancelled::get,
                )
                if (cancelled.get()) {
                    RecommendedModelDownloadState.Cancelled(model)
                } else {
                    RecommendedModelDownloadState.Downloaded(model, imported)
                }
            } catch (_: InterruptedIOException) {
                RecommendedModelDownloadState.Cancelled(model)
            } catch (exc: Exception) {
                if (cancelled.get()) {
                    RecommendedModelDownloadState.Cancelled(model)
                } else {
                    RecommendedModelDownloadState.Failed(
                        model,
                        exc.message?.takeIf { it.isNotBlank() } ?: exc.javaClass.simpleName,
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
