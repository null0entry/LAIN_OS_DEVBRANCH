package dev.lain.os.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnRuntimeProtocolTest {
    @Test fun partialAndRevisionTurnsCannotAdvanceRuntimePump() {
        assertFalse(
            RuntimeProtocol.shouldAdvance(
                "turn_partial",
                """{"version":1,"ok":true,"conversation":{"next_turn_id":1,"turns":[]}}""",
            )
        )
        assertFalse(
            RuntimeProtocol.shouldAdvance(
                "turn_submit",
                """{"version":1,"ok":true,"turn":{"route":"revision"},"revision_intent":{"target_session_id":"x"}}""",
            )
        )
        assertFalse(
            RuntimeProtocol.shouldAdvance(
                "turn_submit",
                """{"version":1,"ok":true,"turn":{"route":"clarification_required"},"clarification_required":true}""",
            )
        )
    }

    @Test fun taskStartingTurnsAndTrustedExistingMutationsAdvance() {
        assertTrue(
            RuntimeProtocol.shouldAdvance(
                "turn_submit",
                """{"version":1,"ok":true,"turn":{"route":"start_task"},"session":{"session_id":"x"}}""",
            )
        )
        assertTrue(RuntimeProtocol.shouldAdvance("start", """{"version":1,"ok":true}"""))
        assertTrue(RuntimeProtocol.shouldAdvance("approve", """{"version":1,"ok":true}"""))
        assertTrue(RuntimeProtocol.shouldAdvance("resume", """{"version":1,"ok":true}"""))
    }
}
