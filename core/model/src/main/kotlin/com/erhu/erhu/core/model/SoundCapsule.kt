package com.erhu.erhu.core.model

import java.time.YearMonth

data class RankedSong(
    val song: Song,
    val plays: Int,
)

data class RankedAlbum(
    val album: Album,
    val plays: Int,
)

data class RankedArtist(
    val artist: Artist,
    val plays: Int,
)

/** One calendar month of listening: totals and the top songs, albums and artists. */
data class SoundCapsule(
    val month: YearMonth,
    val totalPlays: Int,
    /** Time music was actually playing this month. */
    val listenedMs: Long,
    /** Longest run of consecutive listening days that includes a day of [month]. */
    val longestStreakDays: Int,
    val topSongs: List<RankedSong>,
    val topAlbums: List<RankedAlbum>,
    val topArtists: List<RankedArtist>,
) {
    val isEmpty: Boolean get() = totalPlays == 0
}
