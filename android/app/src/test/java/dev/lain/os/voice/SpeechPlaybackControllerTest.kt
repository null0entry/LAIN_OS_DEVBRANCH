package dev.lain.os.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPlaybackControllerTest {
    private class FakeFocus : AudioFocusController {
        var granted = true
        var requests = 0
        var abandons = 0
        private var listener: ((AudioFocusChange) -> Unit)? = null

        override fun request(onChange: (AudioFocusChange) -> Unit): Boolean {
            requests += 1
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
        var starts = 0
        var stops = 0
        var currentVolume = 1.0f
        var lastAudio: SynthesizedAudio? = null
        private var onComplete: (() -> Unit)? = null
        private var onFailure: (() -> Unit)? = null

        override fun start(
            audio: SynthesizedAudio,
            onComplete: () -> Unit,
            onFailure: () -> Unit,
        ) {
            check(!playing)
            playing = true
            starts += 1
            lastAudio = audio
            this.onComplete = onComplete
            this.onFailure = onFailure
        }

        override fun setVolume(value: Float) {
            currentVolume = value
        }

        override fun stop() {
            if (playing) stops += 1
            playing = false
        }

        fun complete() {
            playing = false
            onComplete?.invoke()
        }

        fun fail() {
            playing = false
            onFailure?.invoke()
        }
    }

    private fun audio(
        bytes: Int = 3_200,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        mime: String = "audio/pcm;codec=s16le",
    ) = SynthesizedAudio(
        bytes = ByteArray(bytes),
        mimeType = mime,
        sampleRateHz = sampleRate,
        channels = channels,
        durationMs = 100,
        providerId = "test-provider",
        implementation = "test-adapter",
        model = "voice-1",
    )

    @Test fun playbackRequiresAudioFocusBeforeStartingEngine() {
        val focus = FakeFocus().apply { granted = false }
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)

        controller.play(audio())

        assertEquals(PlaybackStatus.FAILED, controller.state.status)
        assertEquals(PlaybackFailure.AUDIO_FOCUS_DENIED, controller.state.failure)
        assertEquals(1, focus.requests)
        assertEquals(0, engine.starts)
    }

    @Test fun explicitSynthesizedAudioStartsPlaybackAndSurfacesState() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)

        controller.play(audio())

        assertEquals(PlaybackStatus.PLAYING, controller.state.status)
        assertTrue(engine.playing)
        assertEquals(1, focus.requests)
        assertEquals(1, engine.starts)
        assertEquals("test-provider", controller.state.providerId)
    }

    @Test fun stopTalkingStopsPlaybackAndReleasesFocusWithoutTaskCallback() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())

        controller.stopTalking()

        assertEquals(PlaybackStatus.IDLE, controller.state.status)
        assertEquals(PlaybackStopReason.USER_CANCELLED, controller.state.lastStopReason)
        assertFalse(engine.playing)
        assertEquals(1, engine.stops)
        assertEquals(1, focus.abandons)
    }

    @Test fun completionReleasesFocusAndReportsCompleted() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())

        engine.complete()

        assertEquals(PlaybackStatus.IDLE, controller.state.status)
        assertEquals(PlaybackStopReason.COMPLETED, controller.state.lastStopReason)
        assertEquals(1, focus.abandons)
    }

    @Test fun duckAndGainChangeVolumeAndTruthfulState() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())

        focus.change(AudioFocusChange.DUCK)
        assertEquals(PlaybackStatus.DUCKED, controller.state.status)
        assertEquals(0.25f, engine.currentVolume)

        focus.change(AudioFocusChange.GAIN)
        assertEquals(PlaybackStatus.PLAYING, controller.state.status)
        assertEquals(1.0f, engine.currentVolume)
    }

    @Test fun permanentOrTransientFocusLossStopsAndReleases() {
        for (loss in listOf(AudioFocusChange.LOSS, AudioFocusChange.LOSS_TRANSIENT)) {
            val focus = FakeFocus()
            val engine = FakeEngine()
            val controller = SpeechPlaybackController(engine, focus)
            controller.play(audio())

            focus.change(loss)

            assertEquals(PlaybackStatus.IDLE, controller.state.status)
            assertEquals(PlaybackStopReason.FOCUS_LOSS, controller.state.lastStopReason)
            assertFalse(engine.playing)
            assertEquals(1, engine.stops)
            assertEquals(1, focus.abandons)
        }
    }

    @Test fun playbackFailureReleasesFocusWithoutChangingTaskState() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())

        engine.fail()

        assertEquals(PlaybackStatus.FAILED, controller.state.status)
        assertEquals(PlaybackFailure.PLAYBACK_FAILED, controller.state.failure)
        assertEquals(1, focus.abandons)
    }

    @Test fun backgroundTeardownStopsButRotationKeepsPlayback() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())

        controller.onActivityStop(changingConfigurations = true)
        assertTrue(engine.playing)
        assertEquals(PlaybackStatus.PLAYING, controller.state.status)

        controller.onActivityStop(changingConfigurations = false)
        assertFalse(engine.playing)
        assertEquals(PlaybackStopReason.LIFECYCLE, controller.state.lastStopReason)
        assertEquals(1, focus.abandons)
    }

    @Test fun invalidReplacementStopsExistingPlaybackAndReleasesFocus() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())
        assertTrue(engine.playing)

        controller.play(audio(mime = "audio/mpeg"))

        assertEquals(PlaybackStatus.FAILED, controller.state.status)
        assertEquals(PlaybackFailure.UNSUPPORTED_MEDIA, controller.state.failure)
        assertFalse(engine.playing)
        assertEquals(1, engine.stops)
        assertEquals(1, focus.abandons)
    }

    @Test fun unsupportedOrMalformedPcmNeverAcquiresFocus() {
        val invalid = listOf(
            audio(mime = "audio/mpeg"),
            audio(channels = 3),
            audio(sampleRate = 0),
            audio(bytes = 3),
        )
        for (item in invalid) {
            val focus = FakeFocus()
            val engine = FakeEngine()
            val controller = SpeechPlaybackController(engine, focus)

            controller.play(item)

            assertEquals(PlaybackStatus.FAILED, controller.state.status)
            assertEquals(PlaybackFailure.UNSUPPORTED_MEDIA, controller.state.failure)
            assertEquals(0, focus.requests)
            assertEquals(0, engine.starts)
        }
    }

    @Test fun synthesizedAudioCarriesNoUserCommandOrCredentialAuthority() {
        val names = SynthesizedAudio::class.java.declaredFields.map { it.name.lowercase() }
        val forbidden = listOf(
            "text",
            "command",
            "turn",
            "approval",
            "capability",
            "credential",
            "token",
            "policy",
            "action",
        )
        for (word in forbidden) {
            assertTrue("forbidden field: $word", names.none { it.contains(word) })
        }
    }

    @Test fun closeAlwaysReleasesPlaybackResources() {
        val focus = FakeFocus()
        val engine = FakeEngine()
        val controller = SpeechPlaybackController(engine, focus)
        controller.play(audio())

        controller.close()

        assertEquals(PlaybackStatus.IDLE, controller.state.status)
        assertFalse(engine.playing)
        assertEquals(1, focus.abandons)
        assertNull(controller.state.failure)
    }
}
