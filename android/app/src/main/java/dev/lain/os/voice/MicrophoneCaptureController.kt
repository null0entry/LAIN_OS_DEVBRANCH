package dev.lain.os.voice

import java.io.ByteArrayOutputStream

const val CAPTURE_SAMPLE_RATE_HZ = 16_000
const val CAPTURE_CHANNELS = 1
const val CAPTURE_BYTES_PER_SAMPLE = 2
const val MAX_CAPTURE_DURATION_MS = 120_000L
const val MAX_CAPTURE_BYTES =
    CAPTURE_SAMPLE_RATE_HZ * CAPTURE_CHANNELS * CAPTURE_BYTES_PER_SAMPLE *
        (MAX_CAPTURE_DURATION_MS / 1_000L).toInt()

enum class MicrophoneStatus {
    IDLE,
    PERMISSION_REQUIRED,
    RECORDING,
    FAILED,
}

enum class MicrophoneFailure {
    PERMISSION_DENIED,
    PERMISSION_REVOKED,
    CAPTURE_FAILED,
    RESOURCE_LIMIT,
}

data class MicrophoneState(
    val status: MicrophoneStatus = MicrophoneStatus.IDLE,
    val failure: MicrophoneFailure? = null,
    val lastCaptureDurationMs: Long? = null,
)

data class CapturedAudio(
    val bytes: ByteArray,
    val mimeType: String = "audio/pcm;codec=s16le",
    val sampleRateHz: Int = CAPTURE_SAMPLE_RATE_HZ,
    val channels: Int = CAPTURE_CHANNELS,
    val durationMs: Long,
)

interface AudioCaptureEngine {
    val recording: Boolean
    fun start(onChunk: (ByteArray) -> Unit, onFailure: () -> Unit)
    fun stop()
    fun close() = stop()
}

/**
 * Owns one explicit user-started microphone capture.
 *
 * Raw PCM exists only in [buffer] while recording and in the synchronous [onCaptured]
 * handoff after an explicit user stop. Backgrounding, permission loss, failure, and
 * lifecycle teardown discard the buffer instead of producing audio.
 */
class MicrophoneCaptureController(
    private val engine: AudioCaptureEngine,
    private val onState: (MicrophoneState) -> Unit = {},
    private val onCaptured: (CapturedAudio) -> Unit = {},
) {
    private val buffer = ByteArrayOutputStream()
    @Volatile
    var state = MicrophoneState()
        private set

    @Synchronized
    fun start(permissionGranted: Boolean) {
        if (state.status == MicrophoneStatus.RECORDING) return
        discardBuffer()
        if (!permissionGranted) {
            publish(MicrophoneState(MicrophoneStatus.PERMISSION_REQUIRED))
            return
        }
        try {
            // Publish before opening the engine so an implementation which emits
            // its first frame synchronously cannot lose that frame as "not recording".
            publish(MicrophoneState(MicrophoneStatus.RECORDING))
            engine.start(::acceptChunk, ::backendFailed)
        } catch (_: Exception) {
            try { engine.stop() } catch (_: Exception) { }
            discardBuffer()
            publish(
                MicrophoneState(
                    status = MicrophoneStatus.FAILED,
                    failure = MicrophoneFailure.CAPTURE_FAILED,
                )
            )
        }
    }

    @Synchronized
    fun onPermissionResult(granted: Boolean) {
        if (state.status != MicrophoneStatus.PERMISSION_REQUIRED) return
        if (granted) {
            start(permissionGranted = true)
        } else {
            discardBuffer()
            publish(
                MicrophoneState(
                    status = MicrophoneStatus.FAILED,
                    failure = MicrophoneFailure.PERMISSION_DENIED,
                )
            )
        }
    }

    @Synchronized
    fun stop() {
        if (state.status != MicrophoneStatus.RECORDING) {
            if (state.status == MicrophoneStatus.PERMISSION_REQUIRED) {
                discardBuffer()
                publish(MicrophoneState())
            }
            return
        }
        try { engine.stop() } catch (_: Exception) { }
        val raw = buffer.toByteArray()
        discardBuffer()
        val durationMs = durationMs(raw.size)
        publish(MicrophoneState(lastCaptureDurationMs = durationMs.takeIf { raw.isNotEmpty() }))
        if (raw.isNotEmpty()) {
            onCaptured(CapturedAudio(bytes = raw, durationMs = durationMs))
        }
    }

    @Synchronized
    fun reconcilePermission(granted: Boolean) {
        if (granted || state.status != MicrophoneStatus.RECORDING) return
        permissionRevoked()
    }

    @Synchronized
    fun permissionRevoked() {
        if (state.status == MicrophoneStatus.RECORDING) {
            try { engine.stop() } catch (_: Exception) { }
        }
        discardBuffer()
        publish(
            MicrophoneState(
                status = MicrophoneStatus.FAILED,
                failure = MicrophoneFailure.PERMISSION_REVOKED,
            )
        )
    }

    @Synchronized
    fun onActivityStop(changingConfigurations: Boolean) {
        if (changingConfigurations) return
        if (state.status == MicrophoneStatus.RECORDING) {
            try { engine.stop() } catch (_: Exception) { }
        }
        discardBuffer()
        publish(MicrophoneState())
    }

    @Synchronized
    fun close() {
        try { engine.close() } catch (_: Exception) { }
        discardBuffer()
        publish(MicrophoneState())
    }

    @Synchronized
    private fun acceptChunk(chunk: ByteArray) {
        if (state.status != MicrophoneStatus.RECORDING || chunk.isEmpty()) return
        if (chunk.size > MAX_CAPTURE_BYTES - buffer.size()) {
            failAndDiscard(MicrophoneFailure.RESOURCE_LIMIT)
            return
        }
        buffer.write(chunk)
    }

    @Synchronized
    private fun backendFailed() {
        if (state.status == MicrophoneStatus.RECORDING) {
            failAndDiscard(MicrophoneFailure.CAPTURE_FAILED)
        }
    }

    private fun failAndDiscard(failure: MicrophoneFailure) {
        try { engine.stop() } catch (_: Exception) { }
        discardBuffer()
        publish(MicrophoneState(status = MicrophoneStatus.FAILED, failure = failure))
    }

    private fun discardBuffer() {
        buffer.reset()
    }

    private fun durationMs(byteCount: Int): Long {
        val bytesPerSecond = CAPTURE_SAMPLE_RATE_HZ * CAPTURE_CHANNELS * CAPTURE_BYTES_PER_SAMPLE
        return byteCount.toLong() * 1_000L / bytesPerSecond
    }

    private fun publish(next: MicrophoneState) {
        state = next
        onState(next)
    }
}
