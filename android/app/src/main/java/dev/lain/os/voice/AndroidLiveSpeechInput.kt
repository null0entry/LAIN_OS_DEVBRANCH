package dev.lain.os.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Live, local-only manual dictation. Unlike EXTRA_AUDIO_SOURCE captured-PCM
 * injection, on-device SpeechRecognizer consumes microphone audio directly.
 * It is started by Record Voice and finalizes once (on end of speech or Stop).
 */
interface LiveSpeechInput {
    val available: Boolean
    fun start(onFinal: (BackendTranscriptionResult) -> Unit): Boolean
    fun stop()
    fun cancel()
    fun close()
}

class AndroidLiveSpeechInput(context: Context) : LiveSpeechInput {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var callback: ((BackendTranscriptionResult) -> Unit)? = null
    private var generation = 0L
    private var timeout: Runnable? = null

    override val available: Boolean
        get() = Build.VERSION.SDK_INT >= 31 && try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        } catch (_: Exception) { false }

    override fun start(onFinal: (BackendTranscriptionResult) -> Unit): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        cancel()
        // Guard the API31 class entry point itself, not just the availability getter.
        if (Build.VERSION.SDK_INT < 31 || !available) return false
        val token = ++generation
        callback = onFinal
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()
                    val confidence = firstRecognizerConfidence(
                        results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES))
                    finish(token, if (text.isNullOrEmpty()) {
                        BackendTranscriptionResult(
                            failure = SpeechAdapterFailure.PROVIDER_FAILED,
                            diagnostic = "Offline recognizer returned no speech")
                    } else BackendTranscriptionResult(
                        text = text,
                        confidence = confidence,
                        language = Locale.getDefault().toLanguageTag(),
                        isFinal = true,
                    ))
                }

                override fun onError(error: Int) {
                    finish(token, BackendTranscriptionResult(
                        failure = SpeechAdapterFailure.PROVIDER_FAILED,
                        diagnostic = recognizerErrorDiagnostic(error, supportCheck = false)))
                }
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            speech.startListening(request)
            timeout = Runnable {
                finish(token, BackendTranscriptionResult(
                    failure = SpeechAdapterFailure.PROVIDER_FAILED,
                    diagnostic = "On-device speech recognizer timed out"))
            }.also { main.postDelayed(it, 60_000L) }
            return true
        } catch (_: Exception) {
            finish(token, BackendTranscriptionResult(
                failure = SpeechAdapterFailure.PROVIDER_FAILED,
                diagnostic = "Unable to start the on-device speech recognizer"))
            return false
        }
    }

    override fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        val speech = recognizer ?: return
        try {
            speech.stopListening()
            // The service returns final text asynchronously after Stop.
            timeout?.let(main::removeCallbacks)
            val token = generation
            timeout = Runnable {
                finish(token, BackendTranscriptionResult(
                    failure = SpeechAdapterFailure.PROVIDER_FAILED,
                    diagnostic = "Speech provider did not return a final transcript after Stop"))
            }.also { main.postDelayed(it, 15_000L) }
        } catch (_: Exception) {
            finish(generation, BackendTranscriptionResult(
                failure = SpeechAdapterFailure.PROVIDER_FAILED,
                diagnostic = "Speech provider failed when stopping recognition"))
        }
    }

    override fun cancel() {
        generation += 1
        callback = null
        timeout?.let(main::removeCallbacks)
        timeout = null
        val old = recognizer
        recognizer = null
        if (old != null) main.post {
            try { old.cancel() } catch (_: Exception) { }
            try { old.destroy() } catch (_: Exception) { }
        }
    }

    override fun close() = cancel()

    private fun finish(token: Long, result: BackendTranscriptionResult) {
        if (token != generation) return
        val done = callback ?: return
        cancel()
        done(result)
    }
}
