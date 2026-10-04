package dev.lain.os.voice

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lain.os.MainActivity
import dev.lain.os.R
import dev.lain.os.runtime.RuntimeBinding
import dev.lain.os.runtime.RuntimeClient
import dev.lain.os.runtime.RuntimeProtocol
import dev.lain.os.ui.WorkbenchViewModel
import java.util.Collections
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceSpeechAdapterAndroidTest {
    private class FakeCaptureEngine : AudioCaptureEngine {
        override var recording = false
            private set
        private var chunk: ((ByteArray) -> Unit)? = null

        override fun start(onChunk: (ByteArray) -> Unit, onFailure: () -> Unit) {
            recording = true
            chunk = onChunk
        }

        override fun stop() {
            recording = false
        }

        fun emit(bytes: ByteArray) {
            chunk?.invoke(bytes)
        }
    }

    private class FakeSpeechBackend : OnDeviceSpeechBackend {
        override var transcriptionAvailable = true
        override var synthesisAvailable = true
        override val implementation = "instrumentation-local-speech"
        override val model = "fixture"
        var transcriptions = 0
        var syntheses = 0

        override fun transcribe(
            audio: CapturedAudio,
            callback: (BackendTranscriptionResult) -> Unit,
        ) {
            transcriptions += 1
            callback(BackendTranscriptionResult(text = "Show battery", language = "en-US"))
        }

        override fun synthesize(
            text: String,
            voiceId: String?,
            callback: (BackendSynthesisResult) -> Unit,
        ) {
            syntheses += 1
            callback(
                BackendSynthesisResult(
                    audio = SynthesizedAudio(
                        bytes = ByteArray(3_200),
                        mimeType = "audio/pcm;codec=s16le",
                        sampleRateHz = 16_000,
                        channels = 1,
                        durationMs = 100,
                        providerId = ON_DEVICE_PROVIDER_ID,
                        implementation = implementation,
                        model = model,
                    )
                )
            )
        }

        override fun cancelTranscription() = Unit
        override fun cancelSynthesis() = Unit
        override fun close() = Unit
    }

    private class RecordingRuntimeBinding : RuntimeBinding {
        val requests = Collections.synchronizedList(mutableListOf<JSONObject>())
        private val sessionId = UUID.randomUUID().toString()

        private fun session() = JSONObject()
            .put("session_id", sessionId)
            .put("label", "Custom task")
            .put("status", "complete")
            .put("active", false)
            .put("recovery_required", false)
            .put("stop_requested", false)
            .put("actions", JSONArray())

        private val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(RuntimeProtocol.DESCRIPTOR)
                    return true
                }
                if (code != RuntimeProtocol.REQUEST || reply == null) return false
                data.enforceInterface(RuntimeProtocol.DESCRIPTOR)
                val request = JSONObject(data.readString() ?: error("missing request"))
                requests += request
                val response = when (request.getString("command")) {
                    "sessions" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("sessions", JSONArray())
                    "turn_submit" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("session", session())
                    "inspect" -> JSONObject()
                        .put("version", 1)
                        .put("ok", true)
                        .put("session", session())
                    else -> JSONObject(RuntimeProtocol.failure("APP_REQUEST_INVALID"))
                }
                reply.writeNoException()
                reply.writeString(response.toString())
                return true
            }
        }

        override fun bind(connection: ServiceConnection): Boolean {
            connection.onServiceConnected(ComponentName("dev.lain.os", "SpeechTestRuntime"), binder)
            return true
        }

        override fun unbind(connection: ServiceConnection) = Unit
    }

    private class FakeFocus : AudioFocusController {
        override fun request(onChange: (AudioFocusChange) -> Unit): Boolean = true
        override fun abandon() = Unit
    }

    private class FakePlaybackEngine : SpeechPlaybackEngine {
        override var playing = false
            private set
        var starts = 0

        override fun start(
            audio: SynthesizedAudio,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
        ) {
            starts += 1
            playing = true
        }

        override fun setVolume(value: Float) = Unit
        override fun stop() {
            playing = false
        }
    }

    @After fun resetFactories() {
        VoiceCaptureViewModel.engineFactory = { AndroidAudioCaptureEngine() }
        VoiceCaptureViewModel.permissionChecker = { context ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
        VoiceCaptureViewModel.speechBackendFactory = {
            AndroidOnDeviceSpeechBackend(it, enableSynthesis = false)
        }
        SpeechPlaybackViewModel.engineFactory = { AndroidPcmPlaybackEngine() }
        SpeechPlaybackViewModel.focusFactory = { context -> AndroidAudioFocusController(context) }
        SpeechPlaybackViewModel.speechBackendFactory = { AndroidOnDeviceSpeechBackend(it) }
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application) }
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for speech adapter state")
    }

    @Test fun capturedPcmBecomesExactlyOneSpeechTurnOnApi33Plus() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val capture = FakeCaptureEngine()
        val speech = FakeSpeechBackend()
        val binding = RecordingRuntimeBinding()
        VoiceCaptureViewModel.engineFactory = { capture }
        VoiceCaptureViewModel.permissionChecker = { true }
        VoiceCaptureViewModel.speechBackendFactory = { speech }
        WorkbenchViewModel.runtimeClientFactory = { application ->
            RuntimeClient(application, binding)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                it.findViewById<android.view.View>(R.id.voice_record_button).isEnabled
            }
            scenario.onActivity {
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
                capture.emit(ByteArray(3_200) { 1 })
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline &&
                binding.requests.count { it.optString("command") == "turn_submit" } < 1
            ) {
                SystemClock.sleep(50)
            }
        }

        val turns = binding.requests.filter { it.optString("command") == "turn_submit" }
        assertEquals(1, turns.size)
        val args = turns.single().getJSONObject("arguments")
        assertEquals("Show battery", args.getString("text"))
        assertEquals("speech", args.getString("source"))
        assertEquals("task", args.getString("kind"))
        assertEquals(1, speech.transcriptions)
    }

    @Test fun api24FailsClosedWithoutSubmittingCapturedSpeech() {
        assumeTrue(Build.VERSION.SDK_INT < 33)
        val capture = FakeCaptureEngine()
        val speech = FakeSpeechBackend()
        val binding = RecordingRuntimeBinding()
        VoiceCaptureViewModel.engineFactory = { capture }
        VoiceCaptureViewModel.permissionChecker = { true }
        VoiceCaptureViewModel.speechBackendFactory = { speech }
        WorkbenchViewModel.runtimeClientFactory = { application ->
            RuntimeClient(application, binding)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                it.findViewById<android.view.View>(R.id.voice_record_button).isEnabled
            }
            scenario.onActivity {
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
                capture.emit(ByteArray(3_200) { 1 })
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
            }
            SystemClock.sleep(300)
        }

        assertEquals(0, speech.transcriptions)
        assertFalse(binding.requests.any { it.optString("command") == "turn_submit" })
    }

    @Test fun localSynthesisFeedsTask012Playback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val speech = FakeSpeechBackend()
        val engine = FakePlaybackEngine()
        SpeechPlaybackViewModel.engineFactory = { engine }
        SpeechPlaybackViewModel.focusFactory = { FakeFocus() }
        SpeechPlaybackViewModel.speechBackendFactory = { speech }

        instrumentation.runOnMainSync {
            val model = SpeechPlaybackViewModel(app)
            model.speak("done")
            assertEquals(1, speech.syntheses)
            assertEquals(1, engine.starts)
            assertTrue(engine.playing)
            model.stopTalking()
        }
    }
}
