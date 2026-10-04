package com.lochanpthakke.empee3.core.model

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    /** User-chosen cover; null shows the default playlist cover. */
    val artworkUri: String? = null,
)
