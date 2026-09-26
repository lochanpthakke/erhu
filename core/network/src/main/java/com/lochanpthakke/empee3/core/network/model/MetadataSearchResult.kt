package com.lochanpthakke.empee3.core.network.model

import com.lochanpthakke.empee3.core.model.MetadataCandidate

sealed class MetadataSearchResult {
    data class Success(
        val candidates: List<MetadataCandidate>,
    ) : MetadataSearchResult()

    object NoMatch : MetadataSearchResult()

    data class NetworkError(
        val message: String? = null,
    ) : MetadataSearchResult()
}
