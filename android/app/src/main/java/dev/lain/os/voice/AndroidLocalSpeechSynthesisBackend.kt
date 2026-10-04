package dev.lain.os.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class AndroidLocalSpeechSynthesisBackend(context: Context) : LocalSpeechSynthesisBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    private var generation = 0L
    private var activeId: String? = null
    private var activeVoice: LocalSpeechVoice? = null
    private var activeSuccess: ((EncodedSpeechAudio) -> Unit)? = null
    private var activeFailure: (() -> Unit)? = null
    private var activeFile: File? = null
    private var pcm = ByteArrayOutputStream()
    private var sampleRateHz = 0
    private var channels = 0
    private var pcm16 = false
    private var overflow = false

    override val providerId: String = "android-on-device-tts"
    override val implementation: String
        get() = engine?.defaultEngine?.takeIf { it.isNotBlank() }
            ?: "android.speech.tts.TextToSpeech"

    init {
        main.post { ensureEngine() }
    }

    fun hasService(): Boolean = try {
        app.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            0,
        ).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    override fun voices(): List<LocalSpeechVoice> {
        val tts = engine ?: return emptyList()
        if (!ready) return emptyList()
        val engineId = tts.defaultEngine?.takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            tts.voices.orEmpty().map { voice ->
                LocalSpeechVoice(
                    id = voice.name,
                    engineId = engineId,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    installed = !voice.features.orEmpty()
                        .contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun synthesize(
        text: String,
        voice: LocalSpeechVoice,
        onSuccess: (EncodedSpeechAudio) -> Unit,
        onFailure: () -> Unit,
    ) {
        val token = synchronized(this) {
            generation += 1
            generation
        }
        main.post {
            if (current(token)) start(token, text, voice, onSuccess, onFailure)
        }
    }

    override fun stop() {
        synchronized(this) { generation += 1 }
        main.post {
            try { engine?.stop() } catch (_: Exception) { }
            clearActive()
        }
    }

    override fun close() {
        synchronized(this) { generation += 1 }
        main.post {
            try { engine?.stop() } catch (_: Exception) { }
            clearActive()
            try { engine?.shutdown() } catch (_: Exception) { }
            engine = null
            ready = false
        }
    }

    private fun ensureEngine() {
        if (engine != null || !hasService()) return
        try {
            engine = TextToSpeech(app) { status ->
                ready = status == TextToSpeech.SUCCESS
            }
        } catch (_: Exception) {
            engine = null
            ready = false
        }
    }

    private fun start(
        token: Long,
        text: String,
        voice: LocalSpeechVoice,
        onSuccess: (EncodedSpeechAudio) -> Unit,
        onFailure: () -> Unit,
    ) {
        ensureEngine()
        val tts = engine
        if (!ready || tts == null) {
            fail(token, onFailure)
            return
        }
        val actual = try {
            tts.voices.orEmpty().firstOrNull {
                it.name == voice.id &&
                    !it.isNetworkConnectionRequired &&
                    !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
        } catch (_: Exception) {
            null
        }
        if (actual == null || tts.setVoice(actual) != TextToSpeech.SUCCESS) {
            fail(token, onFailure)
            return
        }

        val target = try {
            File.createTempFile("lain-tts-", ".wav", app.cacheDir)
        } catch (_: Exception) {
            fail(token, onFailure)
            return
        }
        activeFile = target
        activeId = UUID.randomUUID().toString()
        activeVoice = voice
        activeSuccess = onSuccess
        activeFailure = onFailure
        pcm = ByteArrayOutputStream()
        sampleRateHz = 0
        channels = 0
        pcm16 = false
        overflow = false

        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onBeginSynthesis(
                    utteranceId: String?,
                    sampleRateInHz: Int,
                    audioFormat: Int,
                    channelCount: Int,
                ) {
                    if (utteranceId != activeId || !current(token)) return
                    synchronized(this@AndroidLocalSpeechSynthesisBackend) {
                        this@AndroidLocalSpeechSynthesisBackend.sampleRateHz = sampleRateInHz
                        channels = channelCount
                        pcm16 = audioFormat == AudioFormat.ENCODING_PCM_16BIT &&
                            sampleRateInHz in 1..MAX_PLAYBACK_SAMPLE_RATE_HZ &&
                            channelCount in 1..2
                    }
                }

                override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                    if (utteranceId != activeId || audio == null || !current(token)) return
                    var shouldStop = false
                    synchronized(this@AndroidLocalSpeechSynthesisBackend) {
                        if (!pcm16 || overflow) return
                        if (audio.size > MAX_PLAYBACK_BYTES - pcm.size()) {
                            overflow = true
                            shouldStop = true
                        } else {
                            pcm.write(audio)
                        }
                    }
                    if (shouldStop) main.post { try { tts.stop() } catch (_: Exception) { } }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == activeId) finish(token)
                }

                @Deprecated("Deprecated in API 21")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == activeId) fail(token, onFailure)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == activeId) fail(token, onFailure)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == activeId && current(token)) fail(token, onFailure)
                }
            }
        )

        val queued = try {
            tts.synthesizeToFile(text, Bundle(), target, activeId!!)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (queued != TextToSpeech.SUCCESS) fail(token, onFailure)
    }

    private fun finish(token: Long) {
        val success: ((EncodedSpeechAudio) -> Unit)?
        val failure: (() -> Unit)?
        val encoded: EncodedSpeechAudio?
        synchronized(this) {
            if (token != generation) return
            val voice = activeVoice
            val raw = pcm.toByteArray()
            encoded = if (
                voice != null && pcm16 && !overflow && raw.isNotEmpty() &&
                raw.size <= MAX_PLAYBACK_BYTES && channels in 1..2 &&
                sampleRateHz in 1..MAX_PLAYBACK_SAMPLE_RATE_HZ &&
                raw.size % (channels * 2) == 0
            ) {
                EncodedSpeechAudio(
                    bytes = wavPcm16(raw, sampleRateHz, channels),
                    mimeType = "audio/wav",
                    voice = voice,
                )
            } else null
            success = activeSuccess
            failure = activeFailure
            generation += 1
            clearActive()
        }
        if (encoded != null && success != null) success(encoded) else failure?.invoke()
    }

    private fun fail(token: Long, callback: () -> Unit) {
        synchronized(this) {
            if (token != generation) return
            generation += 1
            clearActive()
        }
        callback()
    }

    @Synchronized
    private fun current(token: Long): Boolean = token == generation

    @Synchronized
    private fun clearActive() {
        try { activeFile?.delete() } catch (_: Exception) { }
        activeFile = null
        activeId = null
        activeVoice = null
        activeSuccess = null
        activeFailure = null
        pcm.reset()
        sampleRateHz = 0
        channels = 0
        pcm16 = false
        overflow = false
    }

    private fun wavPcm16(raw: ByteArray, sampleRate: Int, channelCount: Int): ByteArray {
        val byteRate = sampleRate * channelCount * 2
        val blockAlign = channelCount * 2
        return ByteBuffer.allocate(44 + raw.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(36 + raw.size)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1)
            .putShort(channelCount.toShort())
            .putInt(sampleRate)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(raw.size)
            .put(raw)
            .array()
    }
}
