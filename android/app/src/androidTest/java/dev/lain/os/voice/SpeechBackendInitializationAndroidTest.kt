package dev.lain.os.voice

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lain.os.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechBackendInitializationAndroidTest {
    private class FakeSpeechBackend : OnDeviceSpeechBackend {
        override val transcriptionAvailable = false
        override val synthesisAvailable = false
        override val implementation = "initialization-test"
        override val model: String? = null

        override fun transcribe(
            audio: CapturedAudio,
            callback: (BackendTranscriptionResult) -> Unit,
        ) = Unit

        override fun synthesize(
            text: String,
            voiceId: String?,
            callback: (BackendSynthesisResult) -> Unit,
        ) = Unit

        override fun cancelTranscription() = Unit
        override fun cancelSynthesis() = Unit
        override fun close() = Unit
    }

    @After
    fun resetFactory() {
        SpeechPlaybackViewModel.speechBackendFactory = {
            AndroidOnDeviceSpeechBackend(it)
        }
    }

    @Test
    fun launchingWorkbenchDoesNotInitializeSpeechBackend() {
        var backendConstructions = 0
        SpeechPlaybackViewModel.speechBackendFactory = {
            backendConstructions += 1
            FakeSpeechBackend()
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(0, backendConstructions)
            }
        }
    }
}
