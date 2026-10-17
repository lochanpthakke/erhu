package com.lochanpthakke.empee3.core.model

/**
 * What happened when a provider was (or was not) asked about a track. [SKIPPED] and
 * [NOT_CONFIGURED] mean no request was sent; every failure status means one was sent and failed,
 * and none of them is ever reported as [NO_RESULTS].
 */
enum class ProviderStatus(
    val label: String,
) {
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
    val hadOriginalArtwork: Boolean = false,
    val originalArtworkBytes: ByteArray? = null,
    val hadEmbeddedTitle: Boolean = true,
    val hadEmbeddedArtist: Boolean = true,
    val hadEmbeddedAlbum: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SuccessDetails) return false
        if (originalTitle != other.originalTitle) return false
        if (originalArtist != other.originalArtist) return false
        if (originalAlbum != other.originalAlbum) return false
        if (originalYear != other.originalYear) return false
        if (originalGenre != other.originalGenre) return false
        if (updatedTitle != other.updatedTitle) return false
        if (updatedArtist != other.updatedArtist) return false
        if (updatedAlbum != other.updatedAlbum) return false
        if (updatedYear != other.updatedYear) return false
        if (updatedGenre != other.updatedGenre) return false
        if (changes != other.changes) return false
        if (isManualSelection != other.isManualSelection) return false
        if (isRenamed != other.isRenamed) return false
        if (oldFilename != other.oldFilename) return false
        if (newFilename != other.newFilename) return false
        if (renameError != other.renameError) return false
        if (hadOriginalArtwork != other.hadOriginalArtwork) return false
        if (originalArtworkBytes != null) {
            if (other.originalArtworkBytes == null) return false
            if (!originalArtworkBytes.contentEquals(other.originalArtworkBytes)) return false
        } else if (other.originalArtworkBytes != null) return false
        if (hadEmbeddedTitle != other.hadEmbeddedTitle) return false
        if (hadEmbeddedArtist != other.hadEmbeddedArtist) return false
        if (hadEmbeddedAlbum != other.hadEmbeddedAlbum) return false
        return true
    }

    override fun hashCode(): Int {
        var result = originalTitle.hashCode()
        result = 31 * result + originalArtist.hashCode()
        result = 31 * result + originalAlbum.hashCode()
        result = 31 * result + (originalYear?.hashCode() ?: 0)
        result = 31 * result + (originalGenre?.hashCode() ?: 0)
        result = 31 * result + updatedTitle.hashCode()
        result = 31 * result + updatedArtist.hashCode()
        result = 31 * result + updatedAlbum.hashCode()
        result = 31 * result + (updatedYear?.hashCode() ?: 0)
        result = 31 * result + (updatedGenre?.hashCode() ?: 0)
        result = 31 * result + changes.hashCode()
        result = 31 * result + isManualSelection.hashCode()
        result = 31 * result + isRenamed.hashCode()
        result = 31 * result + (oldFilename?.hashCode() ?: 0)
        result = 31 * result + (newFilename?.hashCode() ?: 0)
        result = 31 * result + (renameError?.hashCode() ?: 0)
        result = 31 * result + hadOriginalArtwork.hashCode()
        result = 31 * result + (originalArtworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + hadEmbeddedTitle.hashCode()
        result = 31 * result + hadEmbeddedArtist.hashCode()
        result = 31 * result + hadEmbeddedAlbum.hashCode()
        return result
    }
}
