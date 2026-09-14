package com.lochanpthakke.empee3.core.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val songCount: Int,
    val year: Int,
    val artworkUri: String? = null,
)
