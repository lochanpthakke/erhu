package com.lochanpthakke.empee3.core.model

enum class ProviderStatus(val label: String) {
    NO_RESULTS("No results found"),
    CANDIDATES_FOUND("Candidates found"),
    LOW_CONFIDENCE("Low confidence candidates"),
    FAILED("Search request failed"),
    TIMED_OUT("Request timed out"),
    SUCCESS("Selected candidate contributed"),
    SKIPPED("Provider not queried"),
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
