package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.result.AppError
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

class RateLimitException(val untilMs: Long) : Exception("Rate limited until $untilMs")

@Singleton
class AppleRequestGate @Inject constructor() {
    private val mutex = Mutex()
    private var lastItunesCallMs = 0L
    private var circuitBreakerUntilMs = 0L

    suspend fun <T> runItunes(block: suspend () -> T): T {
        mutex.withLock {
            checkCircuitBreaker()
            val now = System.currentTimeMillis()
            val elapsed = now - lastItunesCallMs
            if (elapsed < AnimatedArtworkConfig.ITUNES_MIN_INTERVAL_MS) {
                delay(AnimatedArtworkConfig.ITUNES_MIN_INTERVAL_MS - elapsed)
            }
            lastItunesCallMs = System.currentTimeMillis()
        }
        return try {
            block()
        } catch (e: Exception) {
            handleError(e)
            throw e
        }
    }

    suspend fun <T> runAppleMusic(block: suspend () -> T): T {
        mutex.withLock {
            checkCircuitBreaker()
        }
        return try {
            block()
        } catch (e: Exception) {
            handleError(e)
            throw e
        }
    }

    private fun checkCircuitBreaker() {
        val now = System.currentTimeMillis()
        if (now < circuitBreakerUntilMs) {
            throw RateLimitException(circuitBreakerUntilMs)
        }
    }

    fun tripBreaker() {
        circuitBreakerUntilMs = System.currentTimeMillis() + AnimatedArtworkConfig.BREAKER_COOLDOWN_MS
    }

    private fun handleError(e: Exception) {
        // Interceptors or callers should call tripBreaker explicitly if 429
    }
}
