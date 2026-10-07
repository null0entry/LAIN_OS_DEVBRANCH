package dev.lain.os.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressNarrationPolicyTest {
    private fun snapshot(
        status: String = "running",
        revision: String = "rev-1",
        active: Boolean = true,
        stopRequested: Boolean = false,
        recoveryRequired: Boolean = false,
        completedActions: Int = 0,
    ) = TrustedProgressSnapshot(
        sessionId = "session-1",
        revision = revision,
        status = status,
        active = active,
        stopRequested = stopRequested,
        recoveryRequired = recoveryRequired,
        completedActions = completedActions,
    )

    @Test fun trustedRuntimeStateFormatsOnlyFixedProgressPhrases() {
        val policy = ProgressNarrationPolicy(minimumIntervalMs = 5_000)

        val narration = policy.next(snapshot(status = "running"), nowMs = 10_000)

        assertEquals("session-1:rev-1:running", narration?.key)
        assertEquals("Working on your task.", narration?.text)
    }

    @Test fun repeatedProgressIsCoalescedWithoutAnotherUtterance() {
        val policy = ProgressNarrationPolicy(minimumIntervalMs = 5_000)
        val current = snapshot(status = "running")

        assertEquals("Working on your task.", policy.next(current, nowMs = 10_000)?.text)
        assertNull(policy.next(current, nowMs = 20_000))
    }

    @Test fun changedProgressWaitsForTheBoundedIntervalThenSpeaks() {
        val policy = ProgressNarrationPolicy(minimumIntervalMs = 5_000)
        assertEquals(
            "Planning your task.",
            policy.next(snapshot(status = "planning"), nowMs = 10_000)?.text,
        )

        assertNull(policy.next(snapshot(status = "running"), nowMs = 12_000))
        assertEquals(
            "Working on your task.",
            policy.next(snapshot(status = "running"), nowMs = 15_000)?.text,
        )
    }

    @Test fun interruptionStateIsPresentationOnlyAndDoesNotClaimCompletion() {
        val policy = ProgressNarrationPolicy(minimumIntervalMs = 0)

        val narration = policy.next(
            snapshot(status = "running", stopRequested = true),
            nowMs = 10_000,
        )

        assertEquals("Stopping after the current action settles.", narration?.text)
        assertEquals("running", snapshot(status = "running").status)
    }

    @Test fun terminalUnknownAndUntrustedSnapshotsNeverNarrate() {
        val policy = ProgressNarrationPolicy(minimumIntervalMs = 0)

        assertNull(policy.next(snapshot(status = "complete", active = false), nowMs = 1))
        assertNull(policy.next(snapshot(status = "provider says complete"), nowMs = 2))
        assertNull(policy.next(snapshot(revision = ""), nowMs = 3))
        assertNull(policy.next(snapshot(status = "running", active = false), nowMs = 4))
    }

    @Test fun approvalAndRecoveryUseBoundedAuthoritativeFallbackMessages() {
        val approvalPolicy = ProgressNarrationPolicy(minimumIntervalMs = 0)
        assertEquals(
            "Your approval is needed.",
            approvalPolicy.next(snapshot(status = "paused_confirmation"), nowMs = 1)?.text,
        )

        val recoveryPolicy = ProgressNarrationPolicy(minimumIntervalMs = 0)
        assertEquals(
            "Task recovery is required.",
            recoveryPolicy.next(snapshot(recoveryRequired = true), nowMs = 1)?.text,
        )
    }
}
