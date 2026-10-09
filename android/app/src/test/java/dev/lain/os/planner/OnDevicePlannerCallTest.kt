package dev.lain.os.planner

import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnDevicePlannerCallTest {
    private fun request(goal: String = "show battery") = JSONObject()
        .put("mode", "agent").put("version", "0").put("goal", goal)
        .put("capabilities", JSONArray()).toString()

    private fun call(
        engine: FakeEngine, payload: String = request(), timeout: Double = 2.0,
        resolve: (String, () -> Boolean) -> File = { _, _ -> File("/private/model.gguf") },
    ) = OnDevicePlannerCall(resolve, "a".repeat(64), payload, timeout, 65536, engine)

    @Test fun stopBeforeExecuteDoesNotRegisterOrReadModel() {
        val engine = FakeEngine()
        val call = call(engine, resolve = { _, _ -> error("must not read model") })
        call.cancel()
        assertEquals("PLANNER_CANCELLED", JSONObject(call.execute()).getString("error"))
        assertEquals(0, engine.registrations.get())
    }

    @Test fun stopDuringNativeRegistrationIsReplayedBeforeModelRead() {
        val enteredRegistration = CountDownLatch(1)
        val finishRegistration = CountDownLatch(1)
        val engine = FakeEngine().apply {
            beforeRegistration = {
                enteredRegistration.countDown()
                awaitUninterruptibly(finishRegistration)
            }
        }
        val call = call(engine, resolve = { _, stopped ->
            assertTrue("Stop must survive entry into native registration", stopped())
            throw InterruptedIOException("stopped")
        })
        val response = AtomicReference<String>()
        val caller = Thread { response.set(call.execute()) }.apply { start() }
        try {
            assertTrue(enteredRegistration.await(2, TimeUnit.SECONDS))
            call.cancel()
            caller.join(2000)
            assertFalse(caller.isAlive)
            assertEquals("PLANNER_CANCELLED", JSONObject(response.get()).getString("error"))
        } finally {
            finishRegistration.countDown()
        }
        assertTrue(engine.released.await(2, TimeUnit.SECONDS))
        assertEquals(1, engine.cancelledRegistered.get())
        assertEquals(0, engine.generations.get())
        assertTrue(engine.registered.isEmpty())
    }

    @Test fun stopKeepsRegistrationUntilNativeCleanupFinishes() {
        val enteredGeneration = CountDownLatch(1)
        val finishCleanup = CountDownLatch(1)
        val engine = FakeEngine().apply {
            generation = { id ->
                enteredGeneration.countDown()
                awaitUninterruptibly(finishCleanup)
                assertEquals(true, registered[id])
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.CANCELLED)
            }
        }
        val call = call(engine)
        val response = AtomicReference<String>()
        val caller = Thread { response.set(call.execute()) }.apply { start() }
        try {
            assertTrue(enteredGeneration.await(2, TimeUnit.SECONDS))
            call.cancel()
            caller.join(2000)
            assertFalse(caller.isAlive)
            assertEquals("PLANNER_CANCELLED", JSONObject(response.get()).getString("error"))
            assertEquals(1, engine.registered.size)
            assertEquals(listOf(true), engine.registered.values.toList())
            assertEquals(1L, engine.released.count)
        } finally {
            finishCleanup.countDown()
        }
        assertTrue(engine.released.await(2, TimeUnit.SECONDS))
        assertTrue(engine.registered.isEmpty())
    }

    @Test fun timeoutSignalsStopAndWorkerReleasesRegistration() {
        val finishCleanup = CountDownLatch(1)
        val engine = FakeEngine().apply {
            generation = { id ->
                awaitUninterruptibly(finishCleanup)
                assertEquals(true, registered[id])
                GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.CANCELLED)
            }
        }
        try {
            assertEquals("PLANNER_TIMEOUT", JSONObject(call(engine, timeout = 1.0).execute()).getString("error"))
            assertEquals(1, engine.cancelledRegistered.get())
            assertEquals(1, engine.registered.size)
        } finally {
            finishCleanup.countDown()
        }
        assertTrue(engine.released.await(2, TimeUnit.SECONDS))
        assertTrue(engine.registered.isEmpty())
    }

    @Test fun serializedRequestBudgetRejectsInputBeforeModelWork() {
        val overhead = request("").toByteArray(Charsets.UTF_8).size
        val atLimit = request("a".repeat(OnDevicePlannerCall.MAX_REQUEST_BYTES - overhead))
        assertTrue(OnDevicePlannerCall.promptForQwen(atLimit).toByteArray().size <= 16_384)
        val engine = FakeEngine()
        val overLimit = request("a".repeat(OnDevicePlannerCall.MAX_REQUEST_BYTES - overhead + 1))
        assertEquals("PLANNER_REQUEST_INVALID", JSONObject(call(engine, payload = overLimit).execute()).getString("error"))
        assertEquals("PLANNER_REQUEST_INVALID", JSONObject(call(engine, payload = request() + "{}").execute()).getString("error"))
        assertEquals(0, engine.registrations.get())
    }

    private class FakeEngine : OnDevicePlannerEngine {
        val registered = ConcurrentHashMap<Long, Boolean>()
        val registrations = AtomicInteger()
        val generations = AtomicInteger()
        val cancelledRegistered = AtomicInteger()
        val released = CountDownLatch(1)
        var beforeRegistration: () -> Unit = {}
        var generation: (Long) -> GgufNativeProbeResult = { GgufNativeProbeResult.Generated("{}") }

        override fun registerGeneration(generationId: Long): GgufNativeProbeFailure? {
            beforeRegistration()
            registrations.incrementAndGet()
            registered[generationId] = false
            return null
        }

        override fun cancel(generationId: Long) {
            if (registered.replace(generationId, true) != null) cancelledRegistered.incrementAndGet()
        }

        override fun releaseGeneration(generationId: Long) {
            registered.remove(generationId)
            released.countDown()
        }

        override fun generate(
            modelPath: String, prompt: String, contextTokens: Int,
            maxNewTokens: Int, generationId: Long,
        ): GgufNativeProbeResult {
            generations.incrementAndGet()
            return generation(generationId)
        }
    }

    companion object {
        private fun awaitUninterruptibly(latch: CountDownLatch) {
            var interrupted = false
            while (true) {
                try {
                    check(latch.await(5, TimeUnit.SECONDS)) { "Test worker did not finish" }
                    break
                } catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
