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

    /** The provider doesn't serve this request where the phone is (Deezer track search in India). */
    UNAVAILABLE("Not available here"),

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

/** One query sent for a track: its separate parts, where the title came from, and each provider's answer. */
data class QueryAttempt(
    val title: String,
    val artist: String?,
    val album: String?,
    val source: String,
    val outcomes: List<ProviderOutcome>,
)

/**
 * What a repair lookup saw and did, kept as separate fields so the screen never has to piece
 * text back together. [searchTitle] / [searchArtist] are the first query's; [queries] has all of them.
 */
data class RepairDiagnostics(
    val originalFilename: String,
    val searchTitle: String,
    val searchArtist: String?,
    val targetDurationMs: Long? = null,
    val providerOutcomes: List<ProviderOutcome> = emptyList(),
    val topCandidates: List<MetadataCandidate> = emptyList(),
    val finalReason: String? = null,
    val embeddedTitle: String? = null,
    val embeddedArtist: String? = null,
    val embeddedAlbum: String? = null,
    val fileTitles: List<String> = emptyList(),
    val queries: List<QueryAttempt> = emptyList(),
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
    val originalYear: String? = null,
    val originalGenre: String? = null,
    val updatedTitle: String,
    val updatedArtist: String,
    val updatedAlbum: String,
    val updatedYear: String? = null,
    val updatedGenre: String? = null,
    val changes: List<TagChange> = emptyList(),
    val isManualSelection: Boolean = false,
    val isRenamed: Boolean = false,
    val oldFilename: String? = null,
    val newFilename: String? = null,
    val renameError: String? = null,
)
