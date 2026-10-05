package dev.lain.os.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

private const val LOCAL_STT_TIMEOUT_MS = 30_000L

/**
 * Android's on-device recognizer only.
 *
 * Captured PCM is accepted only on API 33+, where it can be supplied through
 * EXTRA_AUDIO_SOURCE. This backend never constructs a network recognizer.
 */
class AndroidOnDeviceSpeechBackend(context: Context) : OnDeviceSpeechBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var callback: ((BackendTranscriptionResult) -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null
    private var ownedAudio: ByteArray? = null

    override val transcriptionAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 33 && try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        } catch (_: Exception) {
            false
        }

    override val synthesisAvailable: Boolean = false
    override val implementation: String = "android-platform-on-device-stt"
    override val model: String? = null

    override fun transcribe(
        audio: CapturedAudio,
        callback: (BackendTranscriptionResult) -> Unit,
    ) {
        if (!supportedCapture(audio)) {
            callback(BackendTranscriptionResult(failure = SpeechAdapterFailure.UNSUPPORTED_MEDIA))
            return
        }
        if (Build.VERSION.SDK_INT < 33 || !transcriptionAvailable) {
            callback(BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE))
            return
        }

        val copy = audio.bytes.copyOf()
        val token = synchronized(this) {
            generation += 1
            this.callback = callback
            ownedAudio?.fill(0)
            ownedAudio = copy
            generation
        }
        main.post {
            if (current(token)) startRecognitionApi33(token, audio.copy(bytes = copy))
        }
        main.postDelayed({
            finish(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED),
                cancelFirst = true,
            )
        }, LOCAL_STT_TIMEOUT_MS)
    }

    override fun synthesize(
        text: String,
        voiceId: String?,
        callback: (BackendSynthesisResult) -> Unit,
    ) {
        callback(BackendSynthesisResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE))
    }

    override fun cancelTranscription() {
        synchronized(this) {
            generation += 1
            callback = null
            ownedAudio?.fill(0)
            ownedAudio = null
        }
        main.post { releaseRecognizer(cancelFirst = true) }
    }

    override fun cancelSynthesis() = Unit

    override fun close() {
        cancelTranscription()
        io.shutdownNow()
    }

    @RequiresApi(33)
    private fun startRecognitionApi33(token: Long, audio: CapturedAudio) {
        val local = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        } catch (_: Exception) {
            finish(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelFirst = false,
            )
            return
        }
        recognizer = local
        local.setRecognitionListener(listener(token))

        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (_: Exception) {
            finish(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED),
                cancelFirst = true,
            )
            return
        }
        val readSide = pipe[0]
        val writeSide = pipe[1]
        try {
            local.startListening(recognitionIntent(readSide, audio))
            try { readSide.close() } catch (_: Exception) { }
        } catch (_: Exception) {
            try { readSide.close() } catch (_: Exception) { }
            try { writeSide.close() } catch (_: Exception) { }
            finish(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED),
                cancelFirst = true,
            )
            return
        }

        io.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                    output.write(audio.bytes)
                    output.flush()
                }
            } catch (_: Exception) {
                main.post {
                    finish(
                        token,
                        BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED),
                        cancelFirst = true,
                    )
                }
            } finally {
                audio.bytes.fill(0)
            }
        }
    }

    private fun listener(token: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            finish(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED),
                cancelFirst = true,
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            finish(
                token,
                if (text.isNullOrEmpty()) {
                    BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_FAILED)
                } else {
                    BackendTranscriptionResult(text = text, isFinal = true)
                },
                cancelFirst = false,
            )
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    @RequiresApi(33)
    private fun recognitionIntent(source: ParcelFileDescriptor, audio: CapturedAudio): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, audio.channels)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, audio.sampleRateHz)

    private fun finish(
        token: Long,
        result: BackendTranscriptionResult,
        cancelFirst: Boolean,
    ) {
        val target = synchronized(this) {
            if (token != generation) return
            generation += 1
            ownedAudio?.fill(0)
            ownedAudio = null
            val current = callback
            callback = null
            current
        }
        main.post { releaseRecognizer(cancelFirst) }
        target?.invoke(result)
    }

    @Synchronized
    private fun current(token: Long): Boolean = token == generation

    private fun releaseRecognizer(cancelFirst: Boolean) {
        val local = recognizer
        recognizer = null
        if (local != null) {
            if (cancelFirst) try { local.cancel() } catch (_: Exception) { }
            try { local.destroy() } catch (_: Exception) { }
        }
    }

    private fun supportedCapture(audio: CapturedAudio): Boolean =
        audio.mimeType == "audio/pcm;codec=s16le" &&
            audio.sampleRateHz == CAPTURE_SAMPLE_RATE_HZ &&
            audio.channels == CAPTURE_CHANNELS &&
            audio.durationMs in 1..MAX_CAPTURE_DURATION_MS &&
            audio.bytes.isNotEmpty() &&
            audio.bytes.size <= MAX_CAPTURE_BYTES &&
            audio.bytes.size % (CAPTURE_BYTES_PER_SAMPLE * CAPTURE_CHANNELS) == 0
}
