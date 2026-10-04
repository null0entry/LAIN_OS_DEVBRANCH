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
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

private const val SPEECH_TIMEOUT_MS = 30_000L

class AndroidOnDeviceSpeechBackend(
    context: Context,
    enableSynthesis: Boolean = true,
) : OnDeviceSpeechBackend {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val localTts = if (enableSynthesis) AndroidLocalSpeechSynthesisBackend(app) else null
    private val localSynthesis = localTts?.let {
        OnDeviceSpeechSynthesisController(it, ::onLocalSynthesisState)
    }

    private var transcriptionToken = 0L
    private var transcriptionCallback: ((BackendTranscriptionResult) -> Unit)? = null
    private var recognizer: SpeechRecognizer? = null
    private var ownedAudio: ByteArray? = null

    private var synthesisToken = 0L
    private var synthesisCallback: ((BackendSynthesisResult) -> Unit)? = null

    override val transcriptionAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 33 && try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        } catch (_: Exception) {
            false
        }

    override val synthesisAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 24 && localTts?.hasService() == true

    override val implementation: String = "android-platform-local-speech"
    override val model: String? = null

    override fun transcribe(
        audio: CapturedAudio,
        callback: (BackendTranscriptionResult) -> Unit,
    ) {
        if (!supportedCapture(audio)) {
            callback(BackendTranscriptionResult(failure = SpeechAdapterFailure.UNSUPPORTED_MEDIA))
            return
        }
        if (!transcriptionAvailable) {
            callback(BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE))
            return
        }
        val copy = audio.bytes.copyOf()
        val token = synchronized(this) {
            transcriptionToken += 1
            transcriptionCallback = callback
            ownedAudio?.fill(0)
            ownedAudio = copy
            transcriptionToken
        }
        main.post {
            if (currentTranscription(token)) startRecognitionApi33(token, audio.copy(bytes = copy))
        }
        main.postDelayed({
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.TIMEOUT),
                cancelRecognizer = true,
            )
        }, SPEECH_TIMEOUT_MS)
    }

    override fun synthesize(
        text: String,
        voiceId: String?,
        callback: (BackendSynthesisResult) -> Unit,
    ) {
        val controller = localSynthesis
        if (!synthesisAvailable || controller == null || voiceId != null) {
            callback(BackendSynthesisResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE))
            return
        }
        val token = synchronized(this) {
            synthesisToken += 1
            synthesisCallback = callback
            synthesisToken
        }
        controller.synthesize(text)
        main.postDelayed({
            val active = synchronized(this) {
                token == synthesisToken && synthesisCallback != null
            }
            if (active) {
                try { controller.cancel() } catch (_: Exception) { }
                finishSynthesis(token, BackendSynthesisResult(failure = SpeechAdapterFailure.TIMEOUT))
            }
        }, SPEECH_TIMEOUT_MS)
    }

    override fun cancelTranscription() {
        synchronized(this) {
            transcriptionToken += 1
            transcriptionCallback = null
            ownedAudio?.fill(0)
            ownedAudio = null
        }
        main.post { releaseRecognizer(cancelFirst = true) }
    }

    override fun cancelSynthesis() {
        synchronized(this) {
            synthesisToken += 1
            synthesisCallback = null
        }
        try { localSynthesis?.cancel() } catch (_: Exception) { }
    }

    override fun close() {
        cancelTranscription()
        cancelSynthesis()
        try { localSynthesis?.close() } catch (_: Exception) { }
        io.shutdownNow()
    }

    @RequiresApi(33)
    private fun startRecognitionApi33(token: Long, audio: CapturedAudio) {
        val local = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        } catch (_: Exception) {
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelRecognizer = false,
            )
            return
        }
        recognizer = local
        local.setRecognitionListener(listener(token))

        val probe = try { ParcelFileDescriptor.createPipe() } catch (_: Exception) {
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelRecognizer = true,
            )
            return
        }
        try {
            probe[1].close()
            local.checkRecognitionSupport(
                recognitionIntent(probe[0], audio),
                app.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        try { probe[0].close() } catch (_: Exception) { }
                        if (currentTranscription(token)) beginListeningApi33(token, local, audio)
                    }

                    override fun onError(error: Int) {
                        try { probe[0].close() } catch (_: Exception) { }
                        finishTranscription(
                            token,
                            BackendTranscriptionResult(
                                failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE,
                            ),
                            cancelRecognizer = true,
                        )
                    }
                },
            )
        } catch (_: Exception) {
            try { probe[0].close() } catch (_: Exception) { }
            try { probe[1].close() } catch (_: Exception) { }
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelRecognizer = true,
            )
        }
    }

    @RequiresApi(33)
    private fun beginListeningApi33(
        token: Long,
        local: SpeechRecognizer,
        audio: CapturedAudio,
    ) {
        if (!currentTranscription(token) || recognizer !== local) return
        val pipe = try { ParcelFileDescriptor.createPipe() } catch (_: Exception) {
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelRecognizer = true,
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
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelRecognizer = true,
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
                    finishTranscription(
                        token,
                        BackendTranscriptionResult(
                            failure = SpeechAdapterFailure.MALFORMED_RESPONSE,
                        ),
                        cancelRecognizer = true,
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
            finishTranscription(
                token,
                BackendTranscriptionResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE),
                cancelRecognizer = true,
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            finishTranscription(
                token,
                if (text.isNullOrEmpty()) {
                    BackendTranscriptionResult(failure = SpeechAdapterFailure.MALFORMED_RESPONSE)
                } else {
                    BackendTranscriptionResult(text = text)
                },
                cancelRecognizer = false,
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

    private fun onLocalSynthesisState(state: LocalSynthesisState) {
        when (state.status) {
            LocalSynthesisStatus.READY -> {
                val token = synchronized(this) { synthesisToken }
                finishSynthesis(
                    token,
                    state.audio?.let { BackendSynthesisResult(audio = it) }
                        ?: BackendSynthesisResult(failure = SpeechAdapterFailure.MALFORMED_RESPONSE),
                )
            }
            LocalSynthesisStatus.FAILED -> {
                val failure = when (state.failure) {
                    LocalSynthesisFailure.PROVIDER_UNAVAILABLE ->
                        SpeechAdapterFailure.PROVIDER_UNAVAILABLE
                    LocalSynthesisFailure.CANCELLED -> SpeechAdapterFailure.CANCELLED
                    LocalSynthesisFailure.UNSUPPORTED_AUDIO ->
                        SpeechAdapterFailure.UNSUPPORTED_MEDIA
                    LocalSynthesisFailure.RESOURCE_LIMIT -> SpeechAdapterFailure.RESOURCE_LIMIT
                    else -> SpeechAdapterFailure.MALFORMED_RESPONSE
                }
                val token = synchronized(this) { synthesisToken }
                finishSynthesis(token, BackendSynthesisResult(failure = failure))
            }
            LocalSynthesisStatus.IDLE,
            LocalSynthesisStatus.SYNTHESIZING -> Unit
        }
    }

    private fun finishSynthesis(token: Long, result: BackendSynthesisResult) {
        val callback = synchronized(this) {
            if (token != synthesisToken) return
            synthesisToken += 1
            val current = synthesisCallback
            synthesisCallback = null
            current
        }
        callback?.invoke(result)
    }

    private fun finishTranscription(
        token: Long,
        result: BackendTranscriptionResult,
        cancelRecognizer: Boolean,
    ) {
        val callback = synchronized(this) {
            if (token != transcriptionToken) return
            transcriptionToken += 1
            ownedAudio?.fill(0)
            ownedAudio = null
            val current = transcriptionCallback
            transcriptionCallback = null
            current
        }
        main.post { releaseRecognizer(cancelFirst = cancelRecognizer) }
        callback?.invoke(result)
    }

    @Synchronized
    private fun currentTranscription(token: Long): Boolean = token == transcriptionToken

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
