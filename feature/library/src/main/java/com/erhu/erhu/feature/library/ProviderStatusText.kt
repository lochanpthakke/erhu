package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus

/**
 * One provider's line in the repair diagnostics. Whenever no answer came back, the line says why:
 * not selected, not configured, credentials rejected, network, rate limit or another error; never a
 * bare "not queried" or "no results".
 */
@Suppress("CyclomaticComplexMethod") // one branch per status
fun providerStatusText(outcome: ProviderOutcome): String {
    fun withReason(label: String) = outcome.errorMessage?.takeIf { it.isNotBlank() }?.let { "$label: $it" } ?: label
    return when (outcome.status) {
        ProviderStatus.IN_PROGRESS -> "Searching…"
        ProviderStatus.SUCCESS -> "✓ Selected candidate"
        ProviderStatus.CANDIDATES_FOUND -> "${outcome.candidateCount} candidate(s)"
        ProviderStatus.LOW_CONFIDENCE -> "${outcome.candidateCount} low confidence"
        ProviderStatus.NO_RESULTS -> "No results found"
        ProviderStatus.SKIPPED -> withReason("Not queried")
        ProviderStatus.NOT_CONFIGURED -> withReason("Not configured")
        ProviderStatus.AUTH_FAILED -> withReason("Access refused")
        ProviderStatus.NETWORK_ERROR -> withReason("Network unavailable")
        ProviderStatus.TIMED_OUT -> "Request timed out"
        ProviderStatus.RATE_LIMITED -> withReason("Rate limited")
        ProviderStatus.UNAVAILABLE -> withReason("Not available here")
        ProviderStatus.FAILED -> withReason("Error")
    }
}
