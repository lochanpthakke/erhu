package com.erhu.erhu.core.model

/**
 * One entry of a playlist: the song and its position. Positions are unique within a playlist and
 * never change once the entry exists (removals leave gaps, additions append), so two copies of the
 * same song stay distinguishable and keep their own transition settings.
 */
data class PlaylistEntry(
    val song: Song,
    val position: Int,
)
