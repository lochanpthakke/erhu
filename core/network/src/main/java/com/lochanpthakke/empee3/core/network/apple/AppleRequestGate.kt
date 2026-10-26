package com.lochanpthakke.empee3.core.network.apple

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Apple is limiting requests (HTTP 429); none are sent before [untilMs]. */
class RateLimitException(
    val untilMs: Long,
) : Exception("Rate limited until $untilMs") {
    /** Whole minutes until requests resume, rounded up (at least 1). */
    fun minutesLeft(nowMs: Long = System.currentTimeMillis()): Int = ((untilMs - nowMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
}

@Singleton
class AppleRequestGate
    @Inject
    constructor() {
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

        /**
         * HTTP 429: pause every Apple request for Retry-After (or RATE_LIMIT_PAUSE_MS), capped at
         * BREAKER_COOLDOWN_MS, and report it as a [RateLimitException]. Retrying straight away only
         * extends Apple's block.
         */
        private fun handleError(e: Exception) {
            if ((e as? HttpException)?.code() != HTTP_TOO_MANY_REQUESTS) return
            val retryAfterMs =
                e
                    .response()
                    ?.headers()
                    ?.get("Retry-After")
                    ?.trim()
                    ?.toLongOrNull()
                    ?.times(1000L)
            val pause = (retryAfterMs ?: AnimatedArtworkConfig.RATE_LIMIT_PAUSE_MS).coerceIn(0L, AnimatedArtworkConfig.BREAKER_COOLDOWN_MS)
            circuitBreakerUntilMs = maxOf(circuitBreakerUntilMs, System.currentTimeMillis() + pause)
            throw RateLimitException(circuitBreakerUntilMs)
        }

        private companion object {
            const val HTTP_TOO_MANY_REQUESTS = 429
        }
    }
