package com.erhu.erhu.core.playback.waveform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `concurrent requests for one key share a single computation`() =
        runBlocking {
            val flights = SingleFlight<String, Int>(scope)
            val runs = AtomicInteger()
            val gate = CompletableDeferred<Unit>()
            val results =
                List(5) {
                    async(Dispatchers.Default) {
                        flights.run("song") {
                            runs.incrementAndGet()
                            gate.await()
                            42
                        }
                    }
                }
            withTimeout(5_000) { while (runs.get() == 0) kotlinx.coroutines.delay(5) }
            gate.complete(Unit)
            assertEquals(List(5) { 42 }, results.map { it.await() })
            assertEquals(1, runs.get())
            assertEquals(0, flights.inFlight())
        }

    @Test
    fun `the computation is cancelled when its last waiter leaves`() =
        runBlocking {
            val flights = SingleFlight<String, Int>(scope)
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val waiter =
                launch(Dispatchers.Default) {
                    flights.run("song") {
                        try {
                            started.complete(Unit)
                            awaitCancellation()
                        } finally {
                            cancelled.complete(Unit)
                        }
                    }
                }
            started.await()
            waiter.cancelAndJoin()
            withTimeout(5_000) { cancelled.await() }
            assertEquals(0, flights.inFlight())

            // A later request starts afresh.
            assertEquals(7, flights.run("song") { 7 })
        }

    @Test
    fun `a failure reaches every waiter and is not cached`() =
        runBlocking {
            val flights = SingleFlight<String, Int>(scope)
            val failure = runCatching { flights.run("bad") { error("corrupt") } }
            assertTrue(failure.exceptionOrNull() is IllegalStateException)
            assertEquals(1, flights.run("bad") { 1 })
        }
}
