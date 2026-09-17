package com.lochanpthakke.empee3.core.model

data class MetadataCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int = 0,
    val genre: String? = null,
    val trackNumber: Int = 0,
    val coverArtUrl: String? = null,
    val confidenceScore: Float = 0f,
    val sourceName: String = "",
)
