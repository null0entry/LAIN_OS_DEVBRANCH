package dev.lain.os.voice

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import dev.lain.os.MainActivity
import dev.lain.os.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechPlaybackLifecycleAndroidTest {
    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 60_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for playback/task state")
    }

    private class FakeFocus : AudioFocusController {
        var granted = true
        var abandons = 0
        private var listener: ((AudioFocusChange) -> Unit)? = null

        override fun request(onChange: (AudioFocusChange) -> Unit): Boolean {
            listener = onChange
            return granted
        }

        override fun abandon() {
            abandons += 1
            listener = null
        }

        fun change(value: AudioFocusChange) = listener?.invoke(value)
    }

    private class FakeEngine : SpeechPlaybackEngine {
        override var playing = false
            private set
        var stops = 0
        var currentVolume = 1.0f
        private var complete: (() -> Unit)? = null

        override fun start(
            audio: SynthesizedAudio,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
        ) {
            playing = true
            complete = onComplete
        }

        override fun setVolume(value: Float) {
            currentVolume = value
        }

        override fun stop() {
            if (playing) stops += 1
            playing = false
        }
    }

    private fun audio() = SynthesizedAudio(
        bytes = ByteArray(3_200),
        mimeType = "audio/pcm;codec=s16le",
        sampleRateHz = 16_000,
        channels = 1,
        durationMs = 100,
        providerId = "instrumentation",
        implementation = "fake",
        model = null,
    )

    @After fun resetFactories() {
        SpeechPlaybackViewModel.engineFactory = { AndroidPcmPlaybackEngine() }
        SpeechPlaybackViewModel.focusFactory = { context -> AndroidAudioFocusController(context) }
    }

    @Test fun stopTalkingButtonStopsPlaybackWithoutTouchingTaskStopButton() {
        val engine = FakeEngine()
        val focus = FakeFocus()
        SpeechPlaybackViewModel.engineFactory = { engine }
        SpeechPlaybackViewModel.focusFactory = { focus }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val playback = ViewModelProvider(it)[SpeechPlaybackViewModel::class.java]
                playback.play(audio())
                assertTrue(engine.playing)
                assertTrue(it.findViewById<android.view.View>(R.id.stop_talking_button).performClick())
            }
            scenario.onActivity {
                assertFalse(engine.playing)
                assertEquals(
                    "Stop task",
                    it.findViewById<android.widget.Button>(R.id.stop_button).text.toString(),
                )
                assertTrue(
                    it.findViewById<android.widget.TextView>(R.id.playback_status).text.toString()
                        .contains("stopped", ignoreCase = true)
                )
            }
        }
    }

    @Test fun stopTalkingLeavesPausedTaskAndApprovalUntouched() {
        val engine = FakeEngine()
        val focus = FakeFocus()
        SpeechPlaybackViewModel.engineFactory = { engine }
        SpeechPlaybackViewModel.focusFactory = { focus }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) {
                it.findViewById<android.view.View>(R.id.run_button).isEnabled
            }
            onView(withId(R.id.command_input)).perform(
                replaceText("Share demo text"),
                closeSoftKeyboard(),
            )
            onView(withId(R.id.run_button)).perform(click())
            await(scenario) {
                it.findViewById<android.widget.TextView>(R.id.task_status).text.toString() ==
                    "PAUSED CONFIRMATION" &&
                    it.findViewById<android.widget.TextView>(R.id.approval_text).text.toString()
                        .contains("android.share_text")
            }

            scenario.onActivity {
                ViewModelProvider(it)[SpeechPlaybackViewModel::class.java].play(audio())
                assertTrue(engine.playing)
            }
            onView(withId(R.id.stop_talking_button)).perform(click())

            scenario.onActivity {
                assertFalse(engine.playing)
                assertEquals(
                    "PAUSED CONFIRMATION",
                    it.findViewById<android.widget.TextView>(R.id.task_status).text.toString(),
                )
                assertTrue(it.findViewById<android.view.View>(R.id.approve_button).isEnabled)
                assertTrue(
                    it.findViewById<android.widget.TextView>(R.id.approval_text).text.toString()
                        .contains("android.share_text")
                )
            }
        }
    }

    @Test fun playbackStateSurvivesRotationButBackgroundReleasesFocus() {
        val engine = FakeEngine()
        val focus = FakeFocus()
        SpeechPlaybackViewModel.engineFactory = { engine }
        SpeechPlaybackViewModel.focusFactory = { focus }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                ViewModelProvider(it)[SpeechPlaybackViewModel::class.java].play(audio())
                assertTrue(engine.playing)
            }

            scenario.recreate()

            scenario.onActivity {
                assertTrue(engine.playing)
                assertTrue(
                    it.findViewById<android.widget.TextView>(R.id.playback_status).text.toString()
                        .contains("Speaking", ignoreCase = true)
                )
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(engine.playing)
            assertTrue(focus.abandons >= 1)
        }
    }

    @Test fun focusDuckAndLossAreSurfacedWithoutCreatingRuntimeCommand() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val engine = FakeEngine()
        val focus = FakeFocus()
        SpeechPlaybackViewModel.engineFactory = { engine }
        SpeechPlaybackViewModel.focusFactory = { focus }

        instrumentation.runOnMainSync {
            val model = SpeechPlaybackViewModel(app)
            model.play(audio())
            focus.change(AudioFocusChange.DUCK)
            assertEquals(PlaybackStatus.DUCKED, model.state.value!!.status)
            assertEquals(0.25f, engine.currentVolume)

            focus.change(AudioFocusChange.LOSS)
            assertEquals(PlaybackStatus.IDLE, model.state.value!!.status)
            assertEquals(PlaybackStopReason.FOCUS_LOSS, model.state.value!!.lastStopReason)
            assertFalse(engine.playing)
        }
    }
}
