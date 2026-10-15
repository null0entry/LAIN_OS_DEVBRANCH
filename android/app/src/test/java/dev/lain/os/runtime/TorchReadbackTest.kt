package dev.lain.os.runtime

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TorchReadbackTest {
    private class FakeSession : TorchSession {
        var initial: Boolean? = false
        var observation: Boolean? = null
        var unavailableAfterSet = false
        var rejectSet = false
        var queueMutation = false
        var changed: (Boolean) -> Unit = {}
        var unavailable: () -> Unit = {}
        var queued: (() -> Unit)? = null
        var posts = 0
        var mutations = 0
        var unregistered = false
        var closed = false
        val mutationQueued = CountDownLatch(1)
        override fun register(changed: (Boolean) -> Unit, unavailable: () -> Unit) {
            this.changed = changed
            this.unavailable = unavailable
            initial?.let(changed)
        }
        override fun post(work: () -> Unit): Boolean {
            posts++
            if (queueMutation && posts == 2) {
                queued = work
                mutationQueued.countDown()
            } else work()
            return true
        }
        override fun set(enabled: Boolean) {
            if (rejectSet) throw SecurityException()
            mutations++
            observation?.let(changed)
            if (unavailableAfterSet) unavailable()
        }
        override fun unregister() { unregistered = true }
        override fun close() { closed = true }
    }

    @Test fun acceptedWithoutCallbackIsNullAndInitialCacheCannotVerify() {
        val fake = FakeSession()
        fake.initial = true
        assertNull(TorchReadback(5, 5).execute(fake, true))
        assertEquals(1, fake.mutations)
        assertTrue(fake.unregistered && fake.closed)
    }

    @Test fun realPostRequestMismatchIsPreserved() {
        val fake = FakeSession()
        fake.observation = false
        assertEquals(false, TorchReadback(5, 5).execute(fake, true))
        assertTrue(fake.unregistered && fake.closed)
    }

    @Test fun missingInitialObservationCannotBecomePostRequestEvidence() {
        val fake = FakeSession()
        fake.initial = null
        fake.observation = true
        assertNull(TorchReadback(5, 5).execute(fake, true))
        assertTrue(fake.unregistered && fake.closed)
    }

    @Test fun queuedMutationIsCancelledAfterTimeout() {
        val fake = FakeSession()
        fake.queueMutation = true
        try {
            TorchReadback(5, 5).execute(fake, true)
            fail("Expected bounded timeout")
        } catch (error: DeviceControlFailure) {
            assertEquals("operation_rejected", error.reason)
        }
        fake.queued!!.invoke()
        assertEquals(0, fake.mutations)
        assertTrue(fake.unregistered && fake.closed)
    }

    @Test fun interruptionCancelsQueuedMutationAndUnregisters() {
        val fake = FakeSession()
        fake.queueMutation = true
        val interrupted = CountDownLatch(1)
        val caller = Thread {
            try { TorchReadback(5, 1000).execute(fake, true) }
            catch (_: InterruptedException) { interrupted.countDown() }
        }
        caller.start()
        assertTrue(fake.mutationQueued.await(1, TimeUnit.SECONDS))
        caller.interrupt()
        caller.join(1000)
        assertTrue(interrupted.await(1, TimeUnit.SECONDS))
        fake.queued!!.invoke()
        assertEquals(0, fake.mutations)
        assertTrue(fake.unregistered && fake.closed)
    }

    @Test fun rejectedOrUnavailableCameraStillReleasesObserver() {
        val denied = FakeSession()
        denied.rejectSet = true
        try { TorchReadback(5, 5).execute(denied, true); fail("Expected denied camera") }
        catch (_: SecurityException) { }
        assertTrue(denied.unregistered && denied.closed)
        val busy = FakeSession()
        busy.unavailableAfterSet = true
        try { TorchReadback(5, 5).execute(busy, true); fail("Expected unavailable camera") }
        catch (error: DeviceControlFailure) { assertEquals("camera_busy", error.reason) }
        assertTrue(busy.unregistered && busy.closed)
    }
}
