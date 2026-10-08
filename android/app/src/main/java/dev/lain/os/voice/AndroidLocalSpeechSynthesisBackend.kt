package dev.lain.os.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.UUID

/**
 * Thin Android TextToSpeech backend.
 *
 * It exposes installed voice metadata and speaks directly through the platform
 * engine. It never synthesizes an intermediate file/PCM buffer.
 */
class AndroidLocalSpeechSynthesisBackend(context: Context) : LocalSpeechSynthesisBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var closed = false
    private val pending = mutableListOf<(List<LocalSpeechVoice>) -> Unit>()

    override val providerId: String = "android-on-device-tts"
    override val supportsProgressiveSpeech: Boolean = true

    override val implementation: String
        get() = engine?.defaultEngine?.takeIf { it.isNotBlank() }
            ?: "android.speech.tts.TextToSpeech"

    override fun prepareVoices(onReady: (List<LocalSpeechVoice>) -> Unit) {
        main.post {
            if (closed) {
                onReady(emptyList())
                return@post
            }
            if (ready && engine != null) {
                onReady(voices())
                return@post
            }
            pending += onReady
            ensureEngine()
        }
    }

    override fun speak(text: String, voice: LocalSpeechVoice): Boolean {
        if (closed || Looper.myLooper() != Looper.getMainLooper()) return false
        val tts = engine ?: return false
        if (!ready) return false
        val actual = try {
            tts.voices.orEmpty().firstOrNull {
                it.name == voice.id &&
                    !it.isNetworkConnectionRequired &&
                    !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
        } catch (_: Exception) {
            null
        } ?: return false
        if (try { tts.setVoice(actual) } catch (_: Exception) { TextToSpeech.ERROR } != TextToSpeech.SUCCESS) {
            return false
        }
        val queued = try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        return queued == TextToSpeech.SUCCESS
    }

    override fun speakSegments(segments: List<String>, voice: LocalSpeechVoice): Boolean {
        if (closed || Looper.myLooper() != Looper.getMainLooper() || !ready) return false
        if (segments.isEmpty() || segments.size > MAX_PROGRESSIVE_SEGMENTS ||
            segments.any { it.isBlank() || it.toByteArray(Charsets.UTF_8).size > MAX_PROGRESSIVE_SEGMENT_BYTES }
        ) return false
        val tts = engine ?: return false
        if (tts.defaultEngine != voice.engineId) return false
        val actual = try {
            tts.voices.orEmpty().firstOrNull {
                it.name == voice.id &&
                    !it.isNetworkConnectionRequired &&
                    !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
        } catch (_: Exception) {
            null
        } ?: return false
        if (try { tts.setVoice(actual) } catch (_: Exception) { TextToSpeech.ERROR } != TextToSpeech.SUCCESS) {
            return false
        }

        // Android TTS owns the queue: the first utterance replaces superseded
        // speech, subsequent utterances append in the original source order.
        // No PCM handoff, provider change, or executor/task callback exists.
        for ((index, segment) in segments.withIndex()) {
            if (closed) {
                try { tts.stop() } catch (_: Exception) { }
                return false
            }
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val queued = try {
                tts.speak(segment, mode, null, UUID.randomUUID().toString())
            } catch (_: Exception) {
                TextToSpeech.ERROR
            }
            if (queued != TextToSpeech.SUCCESS) {
                try { tts.stop() } catch (_: Exception) { }
                return false
            }
        }
        return true
    }

    override fun stop() {
        main.post {
            try { engine?.stop() } catch (_: Exception) { }
        }
    }

    override fun close() {
        closed = true
        main.post {
            val callbacks = pending.toList()
            pending.clear()
            callbacks.forEach { callback ->
                try { callback(emptyList()) } catch (_: Exception) { }
            }
            try { engine?.stop() } catch (_: Exception) { }
            try { engine?.shutdown() } catch (_: Exception) { }
            engine = null
            ready = false
        }
    }

    private fun ensureEngine() {
        if (closed || engine != null) return
        try {
            engine = TextToSpeech(app) { status ->
                main.post {
                    ready = !closed && status == TextToSpeech.SUCCESS && engine != null
                    if (!ready) {
                        try { engine?.shutdown() } catch (_: Exception) { }
                        engine = null
                    }
                    val callbacks = pending.toList()
                    pending.clear()
                    val available = if (ready) voices() else emptyList()
                    callbacks.forEach { callback ->
                        try { callback(available) } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) {
            engine = null
            ready = false
            val callbacks = pending.toList()
            pending.clear()
            callbacks.forEach { callback ->
                try { callback(emptyList()) } catch (_: Exception) { }
            }
        }
    }

    private fun voices(): List<LocalSpeechVoice> {
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
}
