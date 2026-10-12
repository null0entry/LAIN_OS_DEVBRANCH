package dev.lain.os.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeTimeoutPolicyTest {
    @Test fun automaticFinalTurnsReceiveBoundedModelWindow() {
        assertEquals(190L, RuntimeTimeoutPolicy.seconds("turn_submit", "auto"))
    }

    @Test fun nonModelMutationsKeepFiveSecondBound() {
        for ((command, kind) in listOf(
            "start" to null,
            "approve" to null,
            "resume" to null,
            "turn_partial" to null,
            "turn_submit" to "task",
            "turn_submit" to "revision",
            "turn_submit" to "chat",
        )) {
            assertEquals("$command/$kind", 5L, RuntimeTimeoutPolicy.seconds(command, kind))
        }
    }

    @Test fun readsAndUnknownRequestsNeverAcquireLongWindow() {
        for (command in listOf("inspect", "sessions", "turns", "stop", "unknown")) {
            assertEquals(command, 5L, RuntimeTimeoutPolicy.seconds(command, "auto"))
        }
    }
}
