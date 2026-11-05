package com.erhu.erhu.core.network.provider

/**
 * A provider rejected authorization. Kept for shared failure classification;
 * these failures are never retried automatically.
 */
class ProviderAuthException(
    message: String,
) : Exception(message)
