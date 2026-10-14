package com.lochanpthakke.empee3.core.network.provider

/** The provider said "too many requests" in its response body rather than with HTTP 429. */
class ProviderRateLimitedException(
    message: String,
    val retryAfterMs: Long? = null,
) : Exception(message)
