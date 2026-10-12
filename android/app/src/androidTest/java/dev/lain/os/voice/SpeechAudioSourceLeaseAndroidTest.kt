package dev.lain.os.voice

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Regression for the real-device on-device STT failure: SpeechRecognizer
 * queues startListening asynchronously. The app must keep the pipe reader
 * alive until the recognizer lifecycle completes, not just method return.
 */
@RunWith(AndroidJUnit4::class)
class SpeechAudioSourceLeaseAndroidTest {
    @Test fun readerRemainsOpenAcrossAsynchronousRecognitionStart() {
        val (reader, writer) = ParcelFileDescriptor.createPipe()
        val lease = SpeechAudioSourceLease()
        try {
            lease.retain(reader)
            // startListening returns before the service processes the Intent.
            assertTrue(reader.fileDescriptor.valid())
            assertTrue(lease.isRetained())
            lease.release()
            assertFalse(reader.fileDescriptor.valid())
            assertFalse(lease.isRetained())
        } finally {
            lease.release()
            try { writer.close() } catch (_: Exception) {}
        }
    }

    @Test fun cancelAndRebindReleaseOldAudioSource() {
        val (firstRead, firstWrite) = ParcelFileDescriptor.createPipe()
        val (secondRead, secondWrite) = ParcelFileDescriptor.createPipe()
        val lease = SpeechAudioSourceLease()
        try {
            lease.retain(firstRead)
            lease.retain(secondRead)
            assertFalse(firstRead.fileDescriptor.valid())
            assertTrue(secondRead.fileDescriptor.valid())
            lease.release()
            assertFalse(secondRead.fileDescriptor.valid())
        } finally {
            lease.release()
            try { firstWrite.close() } catch (_: Exception) {}
            try { secondWrite.close() } catch (_: Exception) {}
        }
    }
}
