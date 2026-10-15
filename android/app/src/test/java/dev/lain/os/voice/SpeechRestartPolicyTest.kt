package dev.lain.os.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechRestartPolicyTest {
    @Test fun noMatchUsesBoundedBackoffNotRapidMicCycling() {
        val policy = SpeechRestartPolicy()
        assertEquals(2_000L, policy.afterNoMatch(5_000L).delayMs)
        assertEquals(4_000L, policy.afterNoMatch(5_000L).delayMs)
        assertEquals(8_000L, policy.afterNoMatch(5_000L).delayMs)
        assertEquals(12_000L, policy.afterNoMatch(5_000L).delayMs)
        assertEquals(12_000L, policy.afterNoMatch(5_000L).delayMs)
    }

    @Test fun rapidRecognizerTerminationStopsInsteadOfChimingForever() {
        val policy = SpeechRestartPolicy()
        assertEquals(2_000L, policy.afterNoMatch(300L).delayMs)
        assertEquals(4_000L, policy.afterNoMatch(700L).delayMs)
        val stopped = policy.afterNoMatch(200L)
        assertNull(stopped.delayMs)
        assertEquals("recognizer_ended_too_quickly", stopped.reason)
    }

    @Test fun validRecognitionResetsTheFastStopCounterAndBackoff() {
        val policy = SpeechRestartPolicy()
        policy.afterNoMatch(400L)
        policy.afterNoMatch(600L)
        policy.afterFinalRecognition()
        assertEquals(2_000L, policy.afterNoMatch(600L).delayMs)
        assertEquals(4_000L, policy.afterNoMatch(600L).delayMs)
        assertNotNull(policy.afterNoMatch(5_000L).delayMs)
    }

    @Test fun ignoredAmbientFinalUsesCooldownToAvoidAudioChirping() {
        val policy = SpeechRestartPolicy()
        assertEquals(2_000L, policy.afterIgnoredFinal().delayMs)
        assertEquals(4_000L, policy.afterIgnoredFinal().delayMs)
        assertEquals(8_000L, policy.afterIgnoredFinal().delayMs)
        policy.afterFinalRecognition()
        assertEquals(2_000L, policy.afterIgnoredFinal().delayMs)
    }
}
