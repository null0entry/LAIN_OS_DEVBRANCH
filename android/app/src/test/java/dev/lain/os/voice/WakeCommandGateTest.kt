package dev.lain.os.voice

import org.junit.Assert.*
import org.junit.Test

class WakeCommandGateTest {
    private var now = 1_000L
    private val gate = WakeCommandGate(clock = { now })

    @Test fun activationAndFiveSecondWindow() {
        assertEquals(WakeKind.IGNORE, gate.accept(1L, "airplane", 1f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(2L, "please explain LAIN", 1f).kind)
        assertEquals(WakeKind.ARMED, gate.accept(3L, "Hey LAIN", 1f).kind)
        now += 4_900L
        val result = gate.accept(4L, "turn on the flashlight", 1f)
        assertEquals(WakeKind.COMMAND, result.kind)
        assertEquals("turn on the flashlight", result.command)
        assertEquals(WakeKind.IGNORE, gate.accept(5L, "turn off the flashlight", 1f).kind)
    }

    @Test fun expiredAndDuplicateFinalsNeverExecute() {
        assertEquals(WakeKind.ARMED, gate.accept(1L, "LAIN", null).kind)
        now += 5_001L
        assertEquals(WakeKind.IGNORE, gate.accept(2L, "flashlight on", 1f).kind)
        assertEquals(WakeKind.COMMAND, gate.accept(3L, "LAIN, flashlight on", -1f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(3L, "LAIN, flashlight off", 1f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(2L, "LAIN, flashlight off", 1f).kind)
        gate.reset()
        assertEquals(WakeKind.COMMAND, gate.accept(1L, "lane, volume down", 1f).kind)
    }

    @Test fun localStopAndNoVoiceApproval() {
        assertEquals(WakeKind.STOP_TASK, gate.accept(1L, "LAIN stop", null).kind)
        assertEquals(WakeKind.STOP_LISTENING, gate.accept(2L, "LAIN, stop listening", null).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(3L, "LAIN approve", 1f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(4L, "LAIN yes", 1f).kind)
    }

    @Test fun rejectUnreliableOrMalformedTranscripts() {
        assertEquals(WakeKind.IGNORE, gate.accept(1L, "LAIN flashlight on", 0.2f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(2L, "LAIN flashlight on", Float.NaN).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(3L, "LAIN " + "x".repeat(4097), 1f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(4L, "LAIN flashlight\nturn on", 1f).kind)
        assertEquals(WakeKind.IGNORE, gate.accept(5L, "explain LAIN flashlight", 1f).kind)
    }

    @Test fun aliasCanBeDisabledAndLateSessionCallbacksIgnored() {
        val strict = WakeCommandGate(clock = { now }, laneAlias = false)
        assertEquals(WakeKind.IGNORE, strict.accept(1L, "lane flashlight on", 1f).kind)
        assertEquals(WakeKind.COMMAND, strict.accept(2L, "LAIN flashlight on", 1f).kind)
        strict.reset()
        assertEquals(WakeKind.IGNORE, strict.accept(0L, "LAIN flashlight off", 1f).kind)
    }
}
