package dev.lain.os.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class AndroidAudioFocusController(context: Context) : AudioFocusController {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var listener: AudioManager.OnAudioFocusChangeListener? = null
    private var request: AudioFocusRequest? = null
    private var held = false

    @Synchronized
    override fun request(onChange: (AudioFocusChange) -> Unit): Boolean {
        abandon()
        val mapped = AudioManager.OnAudioFocusChangeListener { value ->
            when (value) {
                AudioManager.AUDIOFOCUS_GAIN ->
                    onChange(AudioFocusChange.GAIN)
                AudioManager.AUDIOFOCUS_LOSS ->
                    onChange(AudioFocusChange.LOSS)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                    onChange(AudioFocusChange.LOSS_TRANSIENT)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                    onChange(AudioFocusChange.DUCK)
            }
        }
        listener = mapped
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(mapped)
                .build()
            request = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                mapped,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!held) {
            listener = null
            request = null
        }
        return held
    }

    @Synchronized
    override fun abandon() {
        if (!held) {
            listener = null
            request = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            request?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            listener?.let { audioManager.abandonAudioFocus(it) }
        }
        held = false
        listener = null
        request = null
    }
}
