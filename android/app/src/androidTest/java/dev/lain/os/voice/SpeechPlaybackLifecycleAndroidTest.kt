package dev.lain.os.voice

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lain.os.MainActivity
import dev.lain.os.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechOutputLifecycleAndroidTest {
    private class FakeTalkBackBackend : LocalSpeechSynthesisBackend {
        override val providerId = "fixture-local-tts"
        override val implementation = "fixture-local-tts"
        var speakCalls = 0
        var stopCalls = 0
        var lastText: String? = null

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
            speakCalls += 1
            lastText = text
            return true
        }

        override fun stop() {
            stopCalls += 1
        }
    }

    @After fun resetFactory() {
        SpeechOutputViewModel.backendFactory = { AndroidLocalSpeechSynthesisBackend(it) }
    }

    @Test fun directTalkBackHasNoPlaybackUiAndTaskStopRemainsSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        assertEquals(
            0,
            instrumentation.targetContext.resources.getIdentifier(
                "stop_talking_button",
                "id",
                packageName,
            ),
        )
        assertEquals(
            0,
            instrumentation.targetContext.resources.getIdentifier(
                "playback_status",
                "id",
                packageName,
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(
                    "Stop task",
                    it.findViewById<android.widget.Button>(R.id.stop_button).text.toString(),
                )
            }
        }
    }

    @Test fun activityFinishClosesDirectTalkBackBackend() {
        val backend = FakeTalkBackBackend()
        SpeechOutputViewModel.backendFactory = { backend }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity {
                val output = ViewModelProvider(it)[SpeechOutputViewModel::class.java]
                val baselineSpeakCalls = backend.speakCalls
                output.speakOnce("reply-1", "hello")
                assertEquals(baselineSpeakCalls + 1, backend.speakCalls)
                assertEquals("hello", backend.lastText)
            }
        } finally {
            scenario.close()
        }

        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (backend.stopCalls == 0 && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(25)
        }
        assertTrue("closing the Activity must release local TTS", backend.stopCalls >= 1)
    }
}
