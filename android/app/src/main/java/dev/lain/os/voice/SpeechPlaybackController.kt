package dev.lain.os.voice

const val MAX_PLAYBACK_BYTES = 16 * 1024 * 1024
const val MAX_PLAYBACK_DURATION_MS = 120_000L
const val MAX_PLAYBACK_SAMPLE_RATE_HZ = 192_000
private const val PCM16_LE_MIME = "audio/pcm;codec=s16le"
private const val FULL_VOLUME = 1.0f
private const val DUCK_VOLUME = 0.25f

enum class PlaybackStatus {
    IDLE,
    PLAYING,
    DUCKED,
    FAILED,
}

enum class PlaybackFailure {
    AUDIO_FOCUS_DENIED,
    UNSUPPORTED_MEDIA,
    PLAYBACK_FAILED,
}

enum class PlaybackStopReason {
    USER_CANCELLED,
    COMPLETED,
    FOCUS_LOSS,
    LIFECYCLE,
}

enum class AudioFocusChange {
    GAIN,
    LOSS,
    LOSS_TRANSIENT,
    DUCK,
}

data class SynthesizedAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val sampleRateHz: Int,
    val channels: Int,
    val durationMs: Long,
    val providerId: String,
    val implementation: String,
    val model: String?,
)

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val failure: PlaybackFailure? = null,
    val lastStopReason: PlaybackStopReason? = null,
    val providerId: String? = null,
)

interface AudioFocusController {
    fun request(onChange: (AudioFocusChange) -> Unit): Boolean
    fun abandon()
}

interface SpeechPlaybackEngine {
    val playing: Boolean
    fun start(
        audio: SynthesizedAudio,
        onComplete: () -> Unit,
        onFailure: () -> Unit,
    )
    fun setVolume(value: Float)
    fun stop()
    fun close() = stop()
}

/**
 * Owns speech playback only. It has no reference to the task runtime, planner,
 * approval state, microphone, or command routing, so "Stop talking" cannot
 * become "Stop task" through this boundary.
 */
class SpeechPlaybackController(
    private val engine: SpeechPlaybackEngine,
    private val focus: AudioFocusController,
    private val onState: (PlaybackState) -> Unit = {},
) {
    @Volatile
    var state = PlaybackState()
        private set

    private var focusHeld = false
    private var generation = 0L

    @Synchronized
    fun play(audio: SynthesizedAudio) {
        val replacingActivePlayback = active()
        if (replacingActivePlayback) {
            // Revoke the old callback generation before teardown. Engines are
            // allowed to report completion while stop() is in flight; that
            // completion belongs to the replaced playback, not the new slot.
            generation += 1
            stopEngine()
            releaseFocus()
        }

        if (!supported(audio)) {
            publish(
                PlaybackState(
                    status = PlaybackStatus.FAILED,
                    failure = PlaybackFailure.UNSUPPORTED_MEDIA,
                    providerId = audio.providerId.takeIf { it.isNotBlank() },
                )
            )
            return
        }

        if (!replacingActivePlayback) {
            generation += 1
        }
        val token = generation
        val granted = try {
            focus.request { change -> onFocusChange(token, change) }
        } catch (_: Exception) {
            false
        }
        if (!granted) {
            publish(
                PlaybackState(
                    status = PlaybackStatus.FAILED,
                    failure = PlaybackFailure.AUDIO_FOCUS_DENIED,
                    providerId = audio.providerId,
                )
            )
            return
        }
        focusHeld = true

        try {
            // Publish first so engines which complete synchronously cannot have
            // their completion state overwritten by a late PLAYING publication.
            publish(PlaybackState(status = PlaybackStatus.PLAYING, providerId = audio.providerId))
            engine.setVolume(FULL_VOLUME)
            engine.start(
                audio,
                onComplete = { completed(token) },
                onFailure = { failed(token) },
            )
        } catch (_: Exception) {
            generation += 1
            stopEngine()
            releaseFocus()
            publish(
                PlaybackState(
                    status = PlaybackStatus.FAILED,
                    failure = PlaybackFailure.PLAYBACK_FAILED,
                    providerId = audio.providerId,
                )
            )
        }
    }

    @Synchronized
    fun stopTalking() {
        if (!active()) return
        generation += 1
        stopEngine()
        releaseFocus()
        publish(
            PlaybackState(
                status = PlaybackStatus.IDLE,
                lastStopReason = PlaybackStopReason.USER_CANCELLED,
            )
        )
    }

    @Synchronized
    fun onActivityStop(changingConfigurations: Boolean) {
        if (changingConfigurations || !active()) return
        generation += 1
        stopEngine()
        releaseFocus()
        publish(
            PlaybackState(
                status = PlaybackStatus.IDLE,
                lastStopReason = PlaybackStopReason.LIFECYCLE,
            )
        )
    }

    @Synchronized
    fun close() {
        generation += 1
        try {
            engine.close()
        } catch (_: Exception) {
        }
        releaseFocus()
        publish(PlaybackState())
    }

    @Synchronized
    private fun completed(token: Long) {
        if (token != generation || !active()) return
        generation += 1
        releaseFocus()
        publish(
            PlaybackState(
                status = PlaybackStatus.IDLE,
                lastStopReason = PlaybackStopReason.COMPLETED,
            )
        )
    }

    @Synchronized
    private fun failed(token: Long) {
        if (token != generation || !active()) return
        generation += 1
        stopEngine()
        releaseFocus()
        publish(
            PlaybackState(
                status = PlaybackStatus.FAILED,
                failure = PlaybackFailure.PLAYBACK_FAILED,
                providerId = state.providerId,
            )
        )
    }

    @Synchronized
    private fun onFocusChange(token: Long, change: AudioFocusChange) {
        if (token != generation || !active()) return
        when (change) {
            AudioFocusChange.GAIN -> {
                try {
                    engine.setVolume(FULL_VOLUME)
                    publish(
                        PlaybackState(
                            status = PlaybackStatus.PLAYING,
                            providerId = state.providerId,
                        )
                    )
                } catch (_: Exception) {
                    failed(token)
                }
            }
            AudioFocusChange.DUCK -> {
                try {
                    engine.setVolume(DUCK_VOLUME)
                    publish(
                        PlaybackState(
                            status = PlaybackStatus.DUCKED,
                            providerId = state.providerId,
                        )
                    )
                } catch (_: Exception) {
                    failed(token)
                }
            }
            AudioFocusChange.LOSS,
            AudioFocusChange.LOSS_TRANSIENT -> {
                generation += 1
                stopEngine()
                releaseFocus()
                publish(
                    PlaybackState(
                        status = PlaybackStatus.IDLE,
                        lastStopReason = PlaybackStopReason.FOCUS_LOSS,
                    )
                )
            }
        }
    }

    private fun active(): Boolean =
        state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.DUCKED

    private fun supported(audio: SynthesizedAudio): Boolean {
        if (audio.mimeType != PCM16_LE_MIME) return false
        if (audio.bytes.isEmpty() || audio.bytes.size > MAX_PLAYBACK_BYTES) return false
        if (audio.bytes.size % 2 != 0) return false
        if (audio.sampleRateHz !in 1..MAX_PLAYBACK_SAMPLE_RATE_HZ) return false
        if (audio.channels !in 1..2) return false
        if (audio.bytes.size % (2 * audio.channels) != 0) return false
        if (audio.durationMs !in 1..MAX_PLAYBACK_DURATION_MS) return false
        if (audio.providerId.isBlank() || audio.implementation.isBlank()) return false
        return true
    }

    private fun stopEngine() {
        try {
            engine.stop()
        } catch (_: Exception) {
        }
    }

    private fun releaseFocus() {
        if (!focusHeld) return
        focusHeld = false
        try {
            focus.abandon()
        } catch (_: Exception) {
        }
    }

    private fun publish(next: PlaybackState) {
        state = next
        onState(next)
    }
}
