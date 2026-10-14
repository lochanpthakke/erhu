package com.lochanpthakke.empee3.core.model

/**
 * What happened when a provider was (or was not) asked about a track. [SKIPPED] and
 * [NOT_CONFIGURED] mean no request was sent; every failure status means one was sent and failed,
 * and none of them is ever reported as [NO_RESULTS].
 */
enum class ProviderStatus(val label: String) {
    IN_PROGRESS("Searching…"),
    NO_RESULTS("No results found"),
    CANDIDATES_FOUND("Candidates found"),
    LOW_CONFIDENCE("Low confidence candidates"),
    SUCCESS("Selected candidate contributed"),

    /** Not queried because the user's provider choice excludes it. */
    SKIPPED("Not queried"),

    /** Not queried: no credentials are configured for it. */
    NOT_CONFIGURED("Not configured"),

    /** Queried, and the provider rejected the app's credentials. */
    AUTH_FAILED("Credentials rejected"),

    /** The request never reached the provider: no connection, DNS failure or network access blocked. */
    NETWORK_ERROR("Network unavailable"),
    TIMED_OUT("Request timed out"),
    RATE_LIMITED("Rate limited"),

    /** Any other provider error (HTTP error, unusable response, service not offered here). */
    FAILED("Provider error"),
    ;

    /** A failure worth retrying later: the connection or the provider's load, not the request itself. */
    val isTransient: Boolean get() = this == NETWORK_ERROR || this == TIMED_OUT || this == RATE_LIMITED
}

data class ProviderOutcome(
    val providerName: String,
    val candidateCount: Int = 0,
    val status: ProviderStatus = ProviderStatus.NO_RESULTS,
    val errorMessage: String? = null,
)

data class RepairDiagnostics(
    val originalFilename: String,
    val searchTitle: String,
    val searchArtist: String?,
    val targetDurationMs: Long? = null,
    val providerOutcomes: List<ProviderOutcome> = emptyList(),
    val topCandidates: List<MetadataCandidate> = emptyList(),
    val finalReason: String? = null,
)

data class TagChange(
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
)

data class SuccessDetails(
    val originalTitle: String,
    val originalArtist: String,
    val originalAlbum: String,
    val updatedTitle: String,
    val updatedArtist: String,
    val updatedAlbum: String,
    val changes: List<TagChange> = emptyList(),
    val isManualSelection: Boolean = false,
    val isRenamed: Boolean = false,
    val oldFilename: String? = null,
    val newFilename: String? = null,
    val renameError: String? = null,
)
