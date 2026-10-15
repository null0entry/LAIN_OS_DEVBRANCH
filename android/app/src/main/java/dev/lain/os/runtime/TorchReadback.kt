package dev.lain.os.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** All callbacks and posted work run on the same serial queue. */
internal interface TorchSession {
    fun register(changed: (Boolean) -> Unit, unavailable: () -> Unit)
    fun post(work: () -> Unit): Boolean
    fun set(enabled: Boolean)
    fun unregister()
    fun close()
}

/** Waits only on the caller's background thread, with bounded observation and cancellation. */
internal class TorchReadback(private val initialWaitMs: Long = 300, private val operationWaitMs: Long = 700) {
    fun execute(session: TorchSession, enabled: Boolean): Boolean? {
        val initial = CountDownLatch(1)
        val observed = CountDownLatch(1)
        val requested = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val value = AtomicReference<Boolean?>(null)
        val unavailable = AtomicBoolean(false)
        try {
            session.register(
                changed = { state ->
                    if (requested.get()) {
                        value.set(state)
                        observed.countDown()
                    } else initial.countDown()
                },
                unavailable = {
                    if (requested.get()) {
                        unavailable.set(true)
                        observed.countDown()
                    } else initial.countDown()
                }
            )
            // Registration delivers cached camera state. Drain it before the mutation;
            // if it never arrives, do not classify a delayed initial event as readback.
            val initialized = initial.await(initialWaitMs, TimeUnit.MILLISECONDS)
            val barrier = CountDownLatch(1)
            require(session.post { barrier.countDown() })
            val drained = barrier.await(initialWaitMs, TimeUnit.MILLISECONDS)
            val accepted = CountDownLatch(1)
            val error = AtomicReference<Exception?>(null)
            require(session.post {
                try {
                    if (!cancelled.get()) {
                        requested.set(initialized && drained)
                        session.set(enabled)
                    }
                } catch (failure: Exception) {
                    error.set(failure)
                } finally {
                    accepted.countDown()
                }
            })
            if (!accepted.await(operationWaitMs, TimeUnit.MILLISECONDS)) {
                throw DeviceControlFailure("failure", "operation_rejected")
            }
            error.get()?.let { throw it }
            if (initialized && drained) observed.await(operationWaitMs, TimeUnit.MILLISECONDS)
            if (unavailable.get()) throw DeviceControlFailure("failure", "camera_busy")
            return value.get()
        } finally {
            // A request still queued at timeout/interruption must never execute later.
            cancelled.set(true)
            try {
                session.unregister()
            } finally {
                session.close()
            }
        }
    }
}
