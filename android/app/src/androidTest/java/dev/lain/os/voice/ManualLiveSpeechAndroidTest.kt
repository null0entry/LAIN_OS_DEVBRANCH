package dev.lain.os.voice

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualLiveSpeechAndroidTest {
    private class FakeLive : LiveSpeechInput {
        override val available = true
        var listening = false
        var stopped = false
        var cancelled = false
        var callback: ((BackendTranscriptionResult) -> Unit)? = null
        override fun start(onFinal: (BackendTranscriptionResult) -> Unit): Boolean {
            listening = true
            callback = onFinal
            return true
        }
        override fun stop() { stopped = true }
        override fun cancel() { cancelled = true; listening = false }
        override fun close() { cancel() }
        fun finish(text: String) {
            listening = false
            callback?.invoke(BackendTranscriptionResult(text = text))
        }
    }

    @After fun reset() {
        VoiceCaptureViewModel.liveSpeechFactory = { AndroidLiveSpeechInput(it) }
        VoiceCaptureViewModel.permissionChecker = { context ->
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @Test fun manualRecordAndStopDoNotInjectCapturedPcm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val fake = FakeLive()
        VoiceCaptureViewModel.liveSpeechFactory = { fake }
        VoiceCaptureViewModel.permissionChecker = { true }
        instrumentation.runOnMainSync {
            val vm = VoiceCaptureViewModel(app)
            assertTrue(vm.start())
            assertEquals(MicrophoneStatus.RECORDING, vm.state.value!!.status)
            vm.stop()
            assertTrue(fake.stopped)
            assertEquals(SpeechInputStatus.TRANSCRIBING, vm.speechState.value!!.status)
            fake.finish("Show battery")
            assertEquals(SpeechInputStatus.READY, vm.speechState.value!!.status)
            assertEquals("Show battery", vm.speechState.value!!.result!!.text)
            assertEquals(1L, vm.speechState.value!!.finalId)
            vm.closeForTest()
        }
    }

    @Test fun recognitionAutoFinalWaitsForExplicitOwnerStopBeforeTask() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val fake = FakeLive()
        VoiceCaptureViewModel.liveSpeechFactory = { fake }
        VoiceCaptureViewModel.permissionChecker = { true }
        instrumentation.runOnMainSync {
            val vm = VoiceCaptureViewModel(app)
            assertTrue(vm.start())
            fake.finish("Delete all files")
            assertEquals(SpeechInputStatus.IDLE, vm.speechState.value!!.status)
            assertEquals(MicrophoneStatus.RECORDING, vm.state.value!!.status)
            vm.stop()
            assertEquals(SpeechInputStatus.READY, vm.speechState.value!!.status)
            assertEquals("Delete all files", vm.speechState.value!!.result!!.text)
            vm.closeForTest()
        }
    }

    @Test fun backgroundCancelsAndNeverAcceptsLateFinal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val fake = FakeLive()
        VoiceCaptureViewModel.liveSpeechFactory = { fake }
        VoiceCaptureViewModel.permissionChecker = { true }
        instrumentation.runOnMainSync {
            val vm = VoiceCaptureViewModel(app)
            assertTrue(vm.start())
            vm.onActivityStop(false)
            assertTrue(fake.cancelled)
            fake.finish("Delete all files")
            assertEquals(SpeechInputStatus.IDLE, vm.speechState.value!!.status)
            vm.closeForTest()
        }
    }
}
