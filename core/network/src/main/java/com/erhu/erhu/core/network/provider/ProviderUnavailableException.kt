package com.erhu.erhu.core.network.provider

/**
 * A provider answered, but not with usable results: an API error, a rejected key, or results
 * withheld in this country. Thrown instead of returning an empty list, so the failure is
 * reported as FAILED (with [message]) and never mistaken for "no such track".
 */
class ProviderUnavailableException(
    message: String,
    /** True when every request this session will get the same answer (a country restriction): not asked again. */
    val sessionWide: Boolean = false,
) : Exception(message)
