package com.erhu.erhu.core.network.retry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

/**
 * Bounded retries for transient provider failures: at most [maxAttempts] tries, exponential
 * backoff from [baseDelayMs] capped at [maxDelayMs] with ±[jitter] randomisation, and a provider's
 * Retry-After respected when it is no longer than [maxRetryAfterMs] (longer waits fail at once
 * rather than stall the batch). Failures that aren't [ProviderFailure.retryable] are not retried.
 */
class RetryPolicy(
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 8_000,
    private val maxRetryAfterMs: Long = 30_000,
    private val jitter: Double = 0.25,
    private val random: Random = Random.Default,
    private val classify: (Throwable) -> ProviderFailure = { classifyProviderFailure(it) },
) {
    /** Runs [block], retrying eligible failures; the last failure is rethrown. [onRetry] sees each wait. */
    suspend fun <T> run(
        onRetry: (attempt: Int, failure: ProviderFailure, delayMs: Long) -> Unit = { _, _, _ -> },
        block: suspend () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                val failure = classify(e)
                val wait = delayBeforeRetry(attempt, failure) ?: throw e
                onRetry(attempt, failure, wait)
                delay(wait)
                attempt++
            }
        }
    }

    /** The wait before attempt [attempt] + 1, or null when [failure] must not be retried. */
    @Suppress("ReturnCount")
    fun delayBeforeRetry(
        attempt: Int,
        failure: ProviderFailure,
    ): Long? {
        if (!failure.retryable || attempt >= maxAttempts) return null
        failure.retryAfterMs?.let { asked -> return asked.takeIf { it <= maxRetryAfterMs } }
        val exponential = min(maxDelayMs, baseDelayMs shl (attempt - 1))
        val spread = (exponential * jitter).toLong()
        return (exponential + random.nextLong(-spread, spread + 1)).coerceIn(0, maxDelayMs)
    }
}
