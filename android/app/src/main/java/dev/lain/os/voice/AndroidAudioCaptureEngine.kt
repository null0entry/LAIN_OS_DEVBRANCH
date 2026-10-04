package dev.lain.os.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AndroidAudioCaptureEngine : AudioCaptureEngine {
    private val worker = Executors.newSingleThreadExecutor()
    private val active = AtomicBoolean(false)
    @Volatile
    private var recorder: AudioRecord? = null

    override val recording: Boolean
        get() = active.get()

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(onChunk: (ByteArray) -> Unit, onFailure: () -> Unit) {
        check(!active.get()) { "microphone capture already active" }
        val minimum = AudioRecord.getMinBufferSize(
            CAPTURE_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minimum > 0) { "microphone buffer size unavailable" }
        val size = max(minimum, 4_096)
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            CAPTURE_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            size,
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            throw IllegalStateException("microphone initialization failed")
        }

        try {
            audio.startRecording()
        } catch (exc: Exception) {
            audio.release()
            throw exc
        }

        recorder = audio
        active.set(true)
        worker.execute {
            val buffer = ByteArray(size)
            try {
                while (active.get()) {
                    val read = audio.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        onChunk(buffer.copyOf(read))
                    } else if (active.get() && read < 0) {
                        onFailure()
                        break
                    }
                }
            } catch (_: Exception) {
                if (active.get()) onFailure()
            } finally {
                synchronized(this) {
                    if (recorder === audio) {
                        active.set(false)
                        recorder = null
                        try { audio.stop() } catch (_: Exception) { }
                        audio.release()
                    }
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!active.getAndSet(false)) return
        val audio = recorder
        recorder = null
        if (audio != null) {
            try { audio.stop() } catch (_: Exception) { }
            audio.release()
        }
    }

    override fun close() {
        stop()
        worker.shutdownNow()
    }
}
