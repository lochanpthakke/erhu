package com.erhu.erhu.feature.library

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import com.erhu.erhu.core.common.R as CommonR

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

@Composable
internal fun ProviderStatus.localizedLabel(): String = stringResource(labelRes())

internal fun ProviderStatus.localizedLabel(context: Context): String = AppLocaleManager.localizedContext(context).getString(labelRes())

@StringRes
@Suppress("CyclomaticComplexMethod")
private fun ProviderStatus.labelRes(): Int =
    when (this) {
        ProviderStatus.IN_PROGRESS -> CommonR.string.provider_status_in_progress
        ProviderStatus.NO_RESULTS -> CommonR.string.provider_status_no_results
        ProviderStatus.CANDIDATES_FOUND -> CommonR.string.provider_status_candidates_found
        ProviderStatus.LOW_CONFIDENCE -> CommonR.string.provider_status_low_confidence
        ProviderStatus.SUCCESS -> CommonR.string.provider_status_success
        ProviderStatus.SKIPPED -> CommonR.string.provider_status_skipped
        ProviderStatus.NOT_CONFIGURED -> CommonR.string.provider_status_not_configured
        ProviderStatus.AUTH_FAILED -> CommonR.string.provider_status_auth_failed
        ProviderStatus.NETWORK_ERROR -> CommonR.string.provider_status_network_error
        ProviderStatus.TIMED_OUT -> CommonR.string.provider_status_timed_out
        ProviderStatus.RATE_LIMITED -> CommonR.string.provider_status_rate_limited
        ProviderStatus.UNAVAILABLE -> CommonR.string.provider_status_unavailable
        ProviderStatus.FAILED -> CommonR.string.provider_status_failed
    }

@Composable
@Suppress("CyclomaticComplexMethod")
internal fun localizedProviderStatusText(outcome: ProviderOutcome): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val countFormatted = LocaleFormatters.formatInteger(outcome.candidateCount, locale)
    val base =
        when (outcome.status) {
            ProviderStatus.IN_PROGRESS -> stringResource(CommonR.string.provider_status_in_progress)
            ProviderStatus.SUCCESS -> stringResource(CommonR.string.provider_outcome_selected)
            ProviderStatus.CANDIDATES_FOUND ->
                pluralStringResource(CommonR.plurals.provider_status_candidates_count, outcome.candidateCount, countFormatted)
            ProviderStatus.LOW_CONFIDENCE -> stringResource(CommonR.string.provider_status_low_confidence_count, countFormatted)
            ProviderStatus.NO_RESULTS -> stringResource(CommonR.string.provider_status_no_results)
            ProviderStatus.SKIPPED -> stringResource(CommonR.string.provider_status_skipped)
            ProviderStatus.NOT_CONFIGURED -> stringResource(CommonR.string.provider_status_not_configured)
            ProviderStatus.AUTH_FAILED -> stringResource(CommonR.string.provider_status_access_refused)
            ProviderStatus.NETWORK_ERROR -> stringResource(CommonR.string.provider_status_network_error)
            ProviderStatus.TIMED_OUT -> stringResource(CommonR.string.provider_status_timed_out)
            ProviderStatus.RATE_LIMITED -> stringResource(CommonR.string.provider_status_rate_limited)
            ProviderStatus.UNAVAILABLE -> stringResource(CommonR.string.provider_status_unavailable)
            ProviderStatus.FAILED -> stringResource(CommonR.string.error_label)
        }
    val reason = outcome.errorMessage?.takeIf { it.isNotBlank() }
    val withReason =
        outcome.status in
            setOf(
                ProviderStatus.SKIPPED,
                ProviderStatus.NOT_CONFIGURED,
                ProviderStatus.AUTH_FAILED,
                ProviderStatus.NETWORK_ERROR,
                ProviderStatus.RATE_LIMITED,
                ProviderStatus.UNAVAILABLE,
                ProviderStatus.FAILED,
            )
    return if (withReason && reason != null) {
        stringResource(CommonR.string.provider_status_label_with_reason, base, reason)
    } else {
        base
    }
}
