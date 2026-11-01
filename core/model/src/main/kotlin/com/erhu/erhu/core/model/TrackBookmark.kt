package com.erhu.erhu.core.model

/** A saved position in one track. Tied to the track's library id, never to its title or metadata. */
data class TrackBookmark(
    val id: Long,
    val songId: Long,
    val positionMs: Long,
    /** Short user-given name; blank shows as the timestamp only. */
    val label: String,
    val createdAt: Long,
)
