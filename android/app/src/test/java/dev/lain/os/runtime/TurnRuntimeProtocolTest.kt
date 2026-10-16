package dev.lain.os.runtime

import org.junit.Assert.assertEquals
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
    @Test fun workflowCommandsArePrivateAndDoNotKickLegacyAgentPump() {
        assertTrue("workflow_inspect" in RuntimeProtocol.readCommands)
        for (command in listOf("workflow_create", "workflow_run", "workflow_advance", "workflow_approve")) {
            assertTrue(command in RuntimeProtocol.writeCommands)
            assertFalse(RuntimeProtocol.shouldAdvance(command, """{"ok":true}"""))
            assertFalse(RuntimeProtocol.shouldAdvanceAfterUncertainOutcome(command))
        }
        assertEquals(60L, RuntimeTimeoutPolicy.seconds("workflow_run", null))
        assertEquals(30L, RuntimeTimeoutPolicy.seconds("workflow_approve", null))
        assertEquals(5L, RuntimeTimeoutPolicy.seconds("workflow_advance", null))
    }

    @Test fun hostedWorkflowGoalUsesPrivateWriteChannelAndModelBudget() {
        assertTrue("workflow_goal" in RuntimeProtocol.writeCommands)
        assertEquals(190L, RuntimeTimeoutPolicy.seconds("workflow_goal", null))
        assertFalse(RuntimeProtocol.shouldAdvance("workflow_goal", """{"ok":true}"""))
        assertFalse(RuntimeProtocol.shouldAdvanceAfterUncertainOutcome("workflow_goal"))
    }

}
