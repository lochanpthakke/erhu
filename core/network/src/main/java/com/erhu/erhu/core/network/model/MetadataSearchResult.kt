package com.erhu.erhu.core.network.model

import com.erhu.erhu.core.model.MetadataCandidate

sealed class MetadataSearchResult {
    data class Success(
        val candidates: List<MetadataCandidate>,
    ) : MetadataSearchResult()

    object NoMatch : MetadataSearchResult()

    data class NetworkError(
        val message: String? = null,
    ) : MetadataSearchResult()
}
