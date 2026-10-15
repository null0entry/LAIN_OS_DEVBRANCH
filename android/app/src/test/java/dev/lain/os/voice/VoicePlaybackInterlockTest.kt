package dev.lain.os.voice

import org.junit.Assert.*
import org.junit.Test

class VoicePlaybackInterlockTest {
    @Test fun stopAndStaleCompletionDoNotReleaseCurrentPlayback() {
        val lock = VoicePlaybackInterlock()
        assertTrue(lock.queue("one"))
        assertFalse(lock.queue("two"))
        assertFalse(lock.complete("one"))
        assertFalse(lock.complete("one"))
        assertTrue(lock.complete("two"))
        assertFalse(lock.complete("ghost"))
    }

    @Test fun flushReplacesOldQueueWithoutStaleResume() {
        val lock = VoicePlaybackInterlock()
        assertTrue(lock.queue("old"))
        assertFalse(lock.queue("replacement", flush = true))
        assertFalse(lock.complete("old"))
        assertTrue(lock.complete("replacement"))
        assertTrue(lock.queue("fresh"))
        assertTrue(lock.cancel())
        assertFalse(lock.complete("fresh"))
    }
}
