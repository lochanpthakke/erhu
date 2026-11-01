package com.erhu.erhu.core.network.apple

import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AppleRequestGateTest {
    private fun tooManyRequests(retryAfterSeconds: String? = null): HttpException {
        val raw =
            okhttp3.Response
                .Builder()
                .request(Request.Builder().url("https://itunes.apple.com/search").build())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .apply { if (retryAfterSeconds != null) header("Retry-After", retryAfterSeconds) }
                .build()
        return HttpException(Response.error<Any>("".toResponseBody(), raw))
    }

    private fun limitOf(block: suspend () -> Unit): RateLimitException =
        runBlocking {
            try {
                block()
                fail("expected RateLimitException")
                throw IllegalStateException()
            } catch (e: RateLimitException) {
                e
            }
        }

    @Test
    fun `a 429 pauses Apple requests and is reported as a rate limit`() {
        val gate = AppleRequestGate()
        val before = System.currentTimeMillis()
        val limit = limitOf { gate.runItunes { throw tooManyRequests() } }
        val pause = limit.untilMs - before
        assertTrue("default pause ~5 min, was $pause", pause in 4 * 60_000L..6 * 60_000L)

        // While paused nothing reaches Apple.
        var called = false
        limitOf { gate.runAppleMusic { called = true } }
        assertEquals(false, called)
    }

    @Test
    fun `Retry-After decides the pause, within the cap`() {
        val gate = AppleRequestGate()
        val before = System.currentTimeMillis()
        val limit = limitOf { gate.runAppleMusic { throw tooManyRequests(retryAfterSeconds = "120") } }
        assertTrue(limit.untilMs - before in 110_000L..130_000L)

        val capped = AppleRequestGate()
        val start = System.currentTimeMillis()
        val long = limitOf { capped.runItunes { throw tooManyRequests(retryAfterSeconds = "86400") } }
        assertTrue(long.untilMs - start <= AnimatedArtworkConfig.BREAKER_COOLDOWN_MS + 1_000L)
    }

    @Test
    fun `other errors pass through unchanged`() {
        val gate = AppleRequestGate()
        val error =
            runBlocking {
                try {
                    gate.runItunes { throw IllegalStateException("boom") }
                } catch (e: Exception) {
                    e
                }
            }
        assertTrue(error is IllegalStateException)
        assertEquals("ok", runBlocking { gate.runAppleMusic { "ok" } })
    }

    @Test
    fun `minutes left are rounded up for the message`() {
        val now = 1_000_000L
        assertEquals(1, RateLimitException(now + 10_000L).minutesLeft(now))
        assertEquals(5, RateLimitException(now + 4 * 60_000L + 1L).minutesLeft(now))
    }
}
