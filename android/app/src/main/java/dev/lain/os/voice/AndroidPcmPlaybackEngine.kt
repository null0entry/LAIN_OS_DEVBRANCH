package dev.lain.os.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

class AndroidPcmPlaybackEngine : SpeechPlaybackEngine {
    private val lock = Any()
    @Volatile
    private var track: AudioTrack? = null
    @Volatile
    private var requestedVolume = 1.0f

    override val playing: Boolean
        get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    override fun start(
        audio: SynthesizedAudio,
        onComplete: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val channelMask = when (audio.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException("unsupported playback channel count")
        }
        val frameSize = 2 * audio.channels
        val frameCount = audio.bytes.size / frameSize
        require(frameCount > 0) { "playback audio is empty" }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(audio.sampleRateHz)
            .setChannelMask(channelMask)
            .build()
        val created = AudioTrack(
            attributes,
            format,
            audio.bytes.size,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            throw IllegalStateException("speech playback initialization failed")
        }

        created.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(value: AudioTrack) {
                    val ownsTrack = synchronized(lock) {
                        if (track !== value) {
                            false
                        } else {
                            track = null
                            true
                        }
                    }
                    if (!ownsTrack) return
                    try {
                        value.stop()
                    } catch (_: Exception) {
                    }
                    value.release()
                    onComplete()
                }

                override fun onPeriodicNotification(value: AudioTrack) = Unit
            },
            Handler(Looper.getMainLooper()),
        )
        created.notificationMarkerPosition = frameCount

        try {
            val written = created.write(audio.bytes, 0, audio.bytes.size)
            if (written != audio.bytes.size) {
                throw IllegalStateException("incomplete speech audio write")
            }
            created.setVolume(requestedVolume)
            synchronized(lock) {
                check(track == null) { "speech playback already active" }
                track = created
            }
            created.play()
        } catch (exc: Exception) {
            synchronized(lock) {
                if (track === created) track = null
            }
            try {
                created.stop()
            } catch (_: Exception) {
            }
            created.release()
            throw exc
        }
    }

    override fun setVolume(value: Float) {
        val bounded = value.coerceIn(0.0f, 1.0f)
        requestedVolume = bounded
        track?.setVolume(bounded)
    }

    override fun stop() {
        val current = synchronized(lock) {
            val value = track
            track = null
            value
        } ?: return
        try {
            current.stop()
        } catch (_: Exception) {
        }
        try {
            current.flush()
        } catch (_: Exception) {
        }
        current.release()
    }

    override fun close() = stop()
}
