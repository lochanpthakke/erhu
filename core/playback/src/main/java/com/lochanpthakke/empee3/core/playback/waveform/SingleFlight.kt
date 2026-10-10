package com.lochanpthakke.empee3.core.playback.waveform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * At most one running computation per key: concurrent callers for the same key share it. The
 * computation runs in [scope]; when every caller waiting for it has gone (cancelled), it is
 * cancelled too, so switching tracks quickly does not leave decodes running for nobody.
 */
internal class SingleFlight<K : Any, V>(
    private val scope: CoroutineScope,
) {
    private class Flight<V>(
        val result: Deferred<V>,
        var waiters: Int = 0,
    )

    private val mutex = Mutex()
    private val flights = HashMap<K, Flight<V>>()

    suspend fun run(
        key: K,
        compute: suspend () -> V,
    ): V {
        val flight =
            mutex.withLock {
                flights.getOrPut(key) { Flight(scope.async { compute() }) }.also { it.waiters++ }
            }
        try {
            return flight.result.await()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    flight.waiters--
                    if (flight.waiters == 0) {
                        if (flights[key] === flight) flights.remove(key)
                        if (!flight.result.isCompleted) flight.result.cancel()
                    }
                }
            }
        }
    }

    /** Computations currently running (for tests). */
    suspend fun inFlight(): Int = mutex.withLock { flights.size }
}
