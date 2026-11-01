package com.erhu.erhu.core.network.retry

import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.network.provider.ProviderAuthException
import com.erhu.erhu.core.network.provider.ProviderRateLimitedException
import com.erhu.erhu.core.network.provider.ProviderUnavailableException
import retrofit2.HttpException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why a provider request failed, in terms the batch and the UI can act on. */
data class ProviderFailure(
    val status: ProviderStatus,
    val message: String,
    /** For [ProviderStatus.RATE_LIMITED]: what the provider asked us to wait (Retry-After), if it said. */
    val retryAfterMs: Long? = null,
    /** Worth retrying the same request after a pause (connection or provider load, never the request itself). */
    val retryable: Boolean = false,
)

/**
 * Sorts a provider exception. A network exception is never "no results": DNS failures (which is
 * also what an app sees when Android blocks its network, e.g. in the background with Battery
 * Saver on: "No address associated with hostname"), refused or reset connections and TLS
 * failures are [ProviderStatus.NETWORK_ERROR]; HTTP 429 and MusicBrainz's 503 are
 * [ProviderStatus.RATE_LIMITED]; 401 / 403 and API key errors are [ProviderStatus.AUTH_FAILED].
 */
fun classifyProviderFailure(
    error: Throwable,
    nowMs: Long = System.currentTimeMillis(),
): ProviderFailure =
    when (error) {
        is ProviderAuthException -> ProviderFailure(ProviderStatus.AUTH_FAILED, error.message.orEmpty())
        is ProviderUnavailableException ->
            ProviderFailure(if (error.sessionWide) ProviderStatus.UNAVAILABLE else ProviderStatus.FAILED, error.message.orEmpty())
        is ProviderRateLimitedException ->
            ProviderFailure(ProviderStatus.RATE_LIMITED, error.message.orEmpty(), error.retryAfterMs, retryable = true)
        is UnknownHostException ->
            ProviderFailure(ProviderStatus.NETWORK_ERROR, "Couldn't resolve the provider's address: ${error.message}", retryable = true)
        is SocketTimeoutException -> ProviderFailure(ProviderStatus.TIMED_OUT, "Request timed out", retryable = true)
        is ConnectException, is NoRouteToHostException, is SocketException, is SSLException ->
            ProviderFailure(ProviderStatus.NETWORK_ERROR, "Connection failed: ${error.message}", retryable = true)
        is InterruptedIOException -> ProviderFailure(ProviderStatus.TIMED_OUT, "Request timed out", retryable = true)
        is HttpException -> classifyHttp(error, nowMs)
        // Any other I/O failure on a request is the connection's (OkHttp reports most as IOException).
        is java.io.IOException -> ProviderFailure(ProviderStatus.NETWORK_ERROR, "Connection failed: ${error.message}", retryable = true)
        else -> ProviderFailure(ProviderStatus.FAILED, error.message ?: error.javaClass.simpleName)
    }

private fun classifyHttp(
    error: HttpException,
    nowMs: Long,
): ProviderFailure {
    val code = error.code()
    val retryAfter =
        error
            .response()
            ?.headers()
            ?.get("Retry-After")
            ?.let { parseRetryAfterMs(it, nowMs) }
    return when {
        code == HTTP_TOO_MANY_REQUESTS || code == HTTP_SERVICE_UNAVAILABLE ->
            ProviderFailure(ProviderStatus.RATE_LIMITED, "Rate limited (HTTP $code)", retryAfter, retryable = true)
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ->
            ProviderFailure(ProviderStatus.AUTH_FAILED, "Access refused (HTTP $code)")
        code >= HTTP_SERVER_ERROR -> ProviderFailure(ProviderStatus.FAILED, "Server error (HTTP $code)", retryable = true)
        else -> ProviderFailure(ProviderStatus.FAILED, "HTTP $code")
    }
}

/** Retry-After as seconds ("120") or an HTTP date; null when unreadable. */
fun parseRetryAfterMs(
    value: String,
    nowMs: Long,
): Long? {
    value.trim().toLongOrNull()?.let { seconds -> return (seconds * MS_PER_SECOND).coerceAtLeast(0) }
    return try {
        val at = java.time.ZonedDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        (at.toInstant().toEpochMilli() - nowMs).coerceAtLeast(0)
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_SERVICE_UNAVAILABLE = 503
private const val MS_PER_SECOND = 1000L
