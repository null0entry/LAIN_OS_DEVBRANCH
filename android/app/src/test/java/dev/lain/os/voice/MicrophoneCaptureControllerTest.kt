package dev.lain.os.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneCaptureControllerTest {
    private class FakeEngine : AudioCaptureEngine {
        override var recording = false
            private set
        var starts = 0
        var stops = 0
        private var onChunk: ((ByteArray) -> Unit)? = null
        private var onFailure: (() -> Unit)? = null

        override fun start(onChunk: (ByteArray) -> Unit, onFailure: () -> Unit) {
            check(!recording)
            recording = true
            starts += 1
            this.onChunk = onChunk
            this.onFailure = onFailure
        }

        override fun stop() {
            if (recording) stops += 1
            recording = false
        }

        fun emit(bytes: ByteArray) = onChunk?.invoke(bytes)
        fun fail() = onFailure?.invoke()
    }

    @Test fun explicitStartRequiresPermissionBeforeOpeningMicrophone() {
        val engine = FakeEngine()
        val controller = MicrophoneCaptureController(engine)

        controller.start(permissionGranted = false)

        assertEquals(MicrophoneStatus.PERMISSION_REQUIRED, controller.state.status)
        assertFalse(engine.recording)
        assertEquals(0, engine.starts)
    }

    @Test fun grantedPermissionStartsAndUserStopHandsOffBoundedAudio() {
        val engine = FakeEngine()
        var captured: CapturedAudio? = null
        val controller = MicrophoneCaptureController(engine, onCaptured = { captured = it })

        controller.start(permissionGranted = true)
        engine.emit(ByteArray(3_200) { 7 })
        controller.stop()

        assertEquals(MicrophoneStatus.IDLE, controller.state.status)
        assertFalse(engine.recording)
        assertEquals(1, engine.stops)
        assertNotNull(captured)
        assertEquals(3_200, captured!!.bytes.size)
        assertEquals(CAPTURE_SAMPLE_RATE_HZ, captured!!.sampleRateHz)
        assertEquals(CAPTURE_CHANNELS, captured!!.channels)
        assertEquals(100L, captured!!.durationMs)
        assertEquals("audio/L16", captured!!.mimeType)
        assertEquals(100L, controller.state.lastCaptureDurationMs)
    }

    @Test fun deniedPermissionIsRecoverableAndNeverStartsEngine() {
        val engine = FakeEngine()
        val controller = MicrophoneCaptureController(engine)

        controller.start(permissionGranted = false)
        controller.onPermissionResult(granted = false)

        assertEquals(MicrophoneStatus.FAILED, controller.state.status)
        assertEquals(MicrophoneFailure.PERMISSION_DENIED, controller.state.failure)
        assertFalse(engine.recording)
    }

    @Test fun permissionRevocationStopsAndDiscardsBufferedAudio() {
        val engine = FakeEngine()
        var captured: CapturedAudio? = null
        val controller = MicrophoneCaptureController(engine, onCaptured = { captured = it })

        controller.start(permissionGranted = true)
        engine.emit(ByteArray(3_200))
        controller.reconcilePermission(granted = false)

        assertEquals(MicrophoneStatus.FAILED, controller.state.status)
        assertEquals(MicrophoneFailure.PERMISSION_REVOKED, controller.state.failure)
        assertFalse(engine.recording)
        assertNull(captured)
    }

    @Test fun rotationKeepsCaptureButBackgroundStopsWithoutHandoff() {
        val engine = FakeEngine()
        var captures = 0
        val controller = MicrophoneCaptureController(engine, onCaptured = { captures += 1 })

        controller.start(permissionGranted = true)
        engine.emit(ByteArray(640))
        controller.onActivityStop(changingConfigurations = true)
        assertTrue(engine.recording)
        assertEquals(MicrophoneStatus.RECORDING, controller.state.status)

        controller.onActivityStop(changingConfigurations = false)

        assertFalse(engine.recording)
        assertEquals(MicrophoneStatus.IDLE, controller.state.status)
        assertEquals(0, captures)
    }

    @Test fun oversizedCaptureFailsClosedAndReleasesMicrophone() {
        val engine = FakeEngine()
        var captured: CapturedAudio? = null
        val controller = MicrophoneCaptureController(engine, onCaptured = { captured = it })

        controller.start(permissionGranted = true)
        engine.emit(ByteArray(MAX_CAPTURE_BYTES))
        engine.emit(byteArrayOf(1))

        assertEquals(MicrophoneStatus.FAILED, controller.state.status)
        assertEquals(MicrophoneFailure.RESOURCE_LIMIT, controller.state.failure)
        assertFalse(engine.recording)
        assertNull(captured)
    }

    @Test fun backendFailureReleasesMicrophoneAndDoesNotDeliverAudio() {
        val engine = FakeEngine()
        var captured: CapturedAudio? = null
        val controller = MicrophoneCaptureController(engine, onCaptured = { captured = it })

        controller.start(permissionGranted = true)
        engine.emit(ByteArray(320))
        engine.fail()

        assertEquals(MicrophoneStatus.FAILED, controller.state.status)
        assertEquals(MicrophoneFailure.CAPTURE_FAILED, controller.state.failure)
        assertFalse(engine.recording)
        assertNull(captured)
    }

    @Test fun closeAlwaysReleasesAndDiscards() {
        val engine = FakeEngine()
        var captured: CapturedAudio? = null
        val controller = MicrophoneCaptureController(engine, onCaptured = { captured = it })

        controller.start(permissionGranted = true)
        engine.emit(ByteArray(320))
        controller.close()

        assertFalse(engine.recording)
        assertEquals(MicrophoneStatus.IDLE, controller.state.status)
        assertNull(captured)
    }
}
