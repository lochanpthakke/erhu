package com.lochanpthakke.empee3.core.network.provider

/**
 * A provider answered, but not with usable results: an API error, a rejected key, or results
 * withheld in this country. Thrown instead of returning an empty list, so the failure is
 * reported as FAILED (with [message]) and never mistaken for "no such track".
 */
class ProviderUnavailableException(
    message: String,
) : Exception(message)
