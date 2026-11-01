package com.erhu.erhu.core.network.provider

/**
 * The provider rejected the app's credentials (e.g. Last.fm error 10 "Invalid API key"). Never
 * retried: the same key gets the same answer.
 */
class ProviderAuthException(
    message: String,
) : Exception(message)
