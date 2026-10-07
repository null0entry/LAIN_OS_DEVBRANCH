package dev.lain.os.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceTalkBackAndroidTest {
    private class FakeCaptureEngine(
        private val events: MutableList<String>? = null,
    ) : AudioCaptureEngine {
        override var recording = false
            private set
        private var chunk: ((ByteArray) -> Unit)? = null

        override fun start(onChunk: (ByteArray) -> Unit, onFailure: () -> Unit) {
            events?.add("capture-start")
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
        override val transcriptionAvailable = true
        override val synthesisAvailable = false
        override val implementation = "fixture-on-device-stt"
        override val model: String? = null

        override fun transcribe(
            audio: CapturedAudio,
            callback: (BackendTranscriptionResult) -> Unit,
        ) {
            callback(
                BackendTranscriptionResult(
                    text = "Show battery",
                    language = "en-US",
                    isFinal = true,
                )
            )
        }

        override fun synthesize(
            text: String,
            voiceId: String?,
            callback: (BackendSynthesisResult) -> Unit,
        ) {
            callback(BackendSynthesisResult(failure = SpeechAdapterFailure.PROVIDER_UNAVAILABLE))
        }

        override fun cancelTranscription() = Unit
        override fun cancelSynthesis() = Unit
        override fun close() = Unit
    }

    private class FakeTalkBackBackend(
        private val events: MutableList<String>? = null,
        private val acceptSpeak: Boolean = true,
    ) : LocalSpeechSynthesisBackend {
        override val providerId = "fixture-local-tts"
        override val implementation = "fixture-local-tts"
        val spoken = Collections.synchronizedList(mutableListOf<String>())
        var stopCalls = 0
            private set

        override fun prepareVoices(onReady: (List<LocalSpeechVoice>) -> Unit) {
            onReady(
                listOf(
                    LocalSpeechVoice(
                        id = "offline",
                        engineId = "fixture.engine",
                        requiresNetwork = false,
                        installed = true,
                    )
                )
            )
        }

        override fun speak(text: String, voice: LocalSpeechVoice): Boolean {
            spoken += text
            return acceptSpeak
        }

        override fun stop() {
            stopCalls += 1
            events?.add("tts-stop")
        }
    }

    private class RecordingRuntimeBinding(
        private val initialSession: Boolean = false,
        private val sessionStatus: String = "complete",
        private val sessionActive: Boolean = false,
        private val speechText: String = "Battery is at fifty percent.",
    ) : RuntimeBinding {
        val requests = Collections.synchronizedList(mutableListOf<JSONObject>())
        private val sessionId = UUID.randomUUID().toString()

        private fun session() = JSONObject()
            .put("session_id", sessionId)
            .put("label", "Custom task")
            .put("status", sessionStatus)
            .put("updated_at", "fixture")
            .put("revision", "speech-reply-1")
            .put("active", sessionActive)
            .put("recovery_required", false)
            .put("stop_requested", false)
            .put("actions", JSONArray())
            .put("speech_text", speechText)

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
                        .put(
                            "sessions",
                            if (initialSession) {
                                JSONArray().put(
                                    JSONObject()
                                        .put("session_id", sessionId)
                                        .put("label", "Custom task")
                                        .put("status", sessionStatus)
                                )
                            } else {
                                JSONArray()
                            },
                        )
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

    @After fun resetFactories() {
        VoiceCaptureViewModel.engineFactory = { AndroidAudioCaptureEngine() }
        VoiceCaptureViewModel.permissionChecker = { context ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
        VoiceCaptureViewModel.speechBackendFactory = { AndroidOnDeviceSpeechBackend(it) }
        SpeechOutputViewModel.backendFactory = { AndroidLocalSpeechSynthesisBackend(it) }
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application) }
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for voice talk-back")
    }


    @Test fun progressSpeechFailureLeavesTrustedTaskStateRunning() {
        val tts = FakeTalkBackBackend(acceptSpeak = false)
        val binding = RecordingRuntimeBinding(
            initialSession = true,
            sessionStatus = "running",
            sessionActive = true,
            speechText = "",
        )
        SpeechOutputViewModel.backendFactory = { tts }
        WorkbenchViewModel.runtimeClientFactory = { application ->
            RuntimeClient(application, binding)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { tts.spoken.contains("Working on your task.") }
            scenario.onActivity {
                assertEquals(
                    "RUNNING",
                    it.findViewById<android.widget.TextView>(R.id.task_status).text.toString(),
                )
                assertTrue(binding.requests.none {
                    it.optString("command") in
                        setOf("stop", "approve", "resume", "start", "turn_submit")
                })
            }
        }
    }

    @Test fun startingVoiceCaptureStopsProgressNarrationBeforeMicrophoneWithoutStoppingTask() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val capture = FakeCaptureEngine(events)
        val stt = FakeSpeechBackend()
        val tts = FakeTalkBackBackend(events)
        val binding = RecordingRuntimeBinding()
        VoiceCaptureViewModel.engineFactory = { capture }
        VoiceCaptureViewModel.permissionChecker = { true }
        VoiceCaptureViewModel.speechBackendFactory = { stt }
        SpeechOutputViewModel.backendFactory = { tts }
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application, binding) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                it.findViewById<android.view.View>(R.id.voice_record_button).isEnabled
            }
            scenario.onActivity {
                val output = ViewModelProvider(it)[SpeechOutputViewModel::class.java]
                output.narrateProgress(
                    TrustedProgressSnapshot(
                        sessionId = "barge-in-test",
                        revision = "rev-1",
                        status = "running",
                        active = true,
                        stopRequested = false,
                        recoveryRequired = false,
                    )
                )
                assertEquals(listOf("Working on your task."), tts.spoken.toList())
                events.clear()

                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
                assertEquals(listOf("tts-stop", "capture-start"), events.take(2))
                assertEquals(1, tts.stopCalls)
                assertTrue(capture.recording)
            }
        }

        assertTrue(binding.requests.none { it.optString("command") == "stop" })
    }

    @Test fun capturedPcmBecomesOneSpeechTurnAndFinalReplyTalksBack() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val capture = FakeCaptureEngine()
        val stt = FakeSpeechBackend()
        val tts = FakeTalkBackBackend()
        val binding = RecordingRuntimeBinding()
        VoiceCaptureViewModel.engineFactory = { capture }
        VoiceCaptureViewModel.permissionChecker = { true }
        VoiceCaptureViewModel.speechBackendFactory = { stt }
        SpeechOutputViewModel.backendFactory = { tts }
        WorkbenchViewModel.runtimeClientFactory = { application -> RuntimeClient(application, binding) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                it.findViewById<android.view.View>(R.id.voice_record_button).isEnabled
            }
            scenario.onActivity {
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
                capture.emit(ByteArray(3_200) { 1 })
                assertTrue(it.findViewById<android.view.View>(R.id.voice_record_button).performClick())
            }
            await(scenario) { tts.spoken.size == 1 }
        }

        val turns = binding.requests.filter { it.optString("command") == "turn_submit" }
        assertEquals(1, turns.size)
        val args = turns.single().getJSONObject("arguments")
        assertEquals("Show battery", args.getString("text"))
        assertEquals("speech", args.getString("source"))
        assertEquals("task", args.getString("kind"))
        assertEquals(listOf("Battery is at fifty percent."), tts.spoken.toList())
    }
}
