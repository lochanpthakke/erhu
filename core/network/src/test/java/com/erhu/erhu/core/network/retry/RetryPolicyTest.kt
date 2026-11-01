package com.erhu.erhu.core.network.retry

import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.provider.ProviderAuthException
import com.erhu.erhu.core.network.provider.ProviderRateLimitedException
import com.erhu.erhu.core.network.provider.ProviderUnavailableException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class RetryPolicyTest {
    private fun http(
        code: Int,
        vararg headers: String,
    ) = HttpException(Response.error<Any>("".toResponseBody(), okhttp3.Response.Builder()
        .request(okhttp3.Request.Builder().url("https://example.org/").build())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(code)
        .message("x")
        .headers(headersOf(*headers))
        .body("".toResponseBody())
        .build()))

    // ---- classification

    @Test
    fun failures_areSortedIntoTheirRealCategory() {
        val dns = classifyProviderFailure(UnknownHostException("Unable to resolve host \"musicbrainz.org\": No address associated with hostname"))
        assertEquals(ProviderStatus.NETWORK_ERROR, dns.status)
        assertTrue(dns.retryable)
        assertEquals(ProviderStatus.TIMED_OUT, classifyProviderFailure(SocketTimeoutException()).status)
        assertEquals(ProviderStatus.NETWORK_ERROR, classifyProviderFailure(java.net.ConnectException("refused")).status)

        val limited = classifyProviderFailure(http(429, "Retry-After", "7"))
        assertEquals(ProviderStatus.RATE_LIMITED, limited.status)
        assertEquals(7_000L, limited.retryAfterMs)
        assertEquals("MusicBrainz throttles with 503", ProviderStatus.RATE_LIMITED, classifyProviderFailure(http(503)).status)

        assertEquals(ProviderStatus.AUTH_FAILED, classifyProviderFailure(http(403)).status)
        assertFalse(classifyProviderFailure(http(403)).retryable)
        assertEquals(ProviderStatus.AUTH_FAILED, classifyProviderFailure(ProviderAuthException("Last.fm error 10")).status)

        val server = classifyProviderFailure(http(500))
        assertEquals(ProviderStatus.FAILED, server.status)
        assertTrue(server.retryable)
        val withheld = classifyProviderFailure(ProviderUnavailableException("not offered here"))
        assertEquals(ProviderStatus.FAILED, withheld.status)
        assertFalse(withheld.retryable)
        assertEquals(ProviderStatus.RATE_LIMITED, classifyProviderFailure(ProviderRateLimitedException("Last.fm error 29")).status)
        assertEquals(ProviderStatus.FAILED, classifyProviderFailure(IllegalStateException("bad json")).status)
    }

    @Test
    fun retryAfter_readsSecondsAndHttpDates() {
        assertEquals(120_000L, parseRetryAfterMs("120", nowMs = 0))
        val now = java.time.ZonedDateTime.parse("Wed, 14 Oct 2026 10:00:00 GMT", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        assertEquals(30_000L, parseRetryAfterMs("Wed, 14 Oct 2026 10:00:30 GMT", now.toInstant().toEpochMilli()))
        assertNull(parseRetryAfterMs("soon", nowMs = 0))
    }

    // ---- retries

    @Test
    fun aTransientFailure_isRetried_withBackoff_andRecovers() =
        runTest {
            var calls = 0
            val policy = RetryPolicy(baseDelayMs = 1_000, jitter = 0.0)
            val result =
                policy.run {
                    calls++
                    if (calls < 3) throw UnknownHostException("dns") else "ok"
                }
            assertEquals("ok", result)
            assertEquals(3, calls)
            assertEquals("1 s then 2 s", 3_000L, currentTime)
        }

    @Test
    fun retriesStopAfterTheBudget_andTheLastFailureIsReported() =
        runTest {
            var calls = 0
            try {
                RetryPolicy(maxAttempts = 3, jitter = 0.0).run<Unit> {
                    calls++
                    throw SocketTimeoutException("slow")
                }
                fail("expected the failure")
            } catch (_: SocketTimeoutException) {
                assertEquals(3, calls)
            }
        }

    @Test
    fun permanentFailures_areNeverRetried() =
        runTest {
            for (error in listOf(ProviderAuthException("bad key"), ProviderUnavailableException("withheld"), http(404))) {
                var calls = 0
                try {
                    RetryPolicy().run<Unit> {
                        calls++
                        throw error
                    }
                } catch (_: Exception) {
                }
                assertEquals("$error", 1, calls)
            }
            assertEquals(0L, currentTime)
        }

    @Test
    fun retryAfter_isRespected_andTooLongAWaitIsNotSat() =
        runTest {
            var calls = 0
            RetryPolicy(maxRetryAfterMs = 30_000).run {
                calls++
                if (calls == 1) throw http(429, "Retry-After", "12") else Unit
            }
            assertEquals(12_000L, currentTime)

            val policy = RetryPolicy(maxRetryAfterMs = 30_000)
            assertNull(policy.delayBeforeRetry(1, ProviderFailure(ProviderStatus.RATE_LIMITED, "x", retryAfterMs = 3_600_000, retryable = true)))
        }

    @Test
    fun backoffIsCapped_andJitterStaysInBounds() {
        val policy = RetryPolicy(maxAttempts = 10, baseDelayMs = 1_000, maxDelayMs = 8_000, jitter = 0.25, random = Random(42))
        val transient = ProviderFailure(ProviderStatus.NETWORK_ERROR, "x", retryable = true)
        val first = policy.delayBeforeRetry(1, transient)!!
        assertTrue("$first", first in 750..1_250)
        val late = policy.delayBeforeRetry(8, transient)!!
        assertTrue("$late", late in 6_000..8_000)
    }
}
