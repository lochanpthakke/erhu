package com.lochanpthakke.empee3.feature.library.mix

import com.lochanpthakke.empee3.core.model.MonthlyPlays
import com.lochanpthakke.empee3.core.model.TrackFacts

internal const val DAY = 86_400_000L

/** 2026-10-15 12:00 UTC. */
internal const val NOW = 1_792_065_600_000L

internal fun track(
    id: Long,
    artist: Long = id,
    album: Long = artist * 100,
    genre: String? = null,
    bpm: Int? = null,
    favorite: Boolean = false,
    addedAt: Long = id,
) = TrackFacts(
    songId = id,
    title = "Song $id",
    artistId = artist,
    artist = "Artist $artist",
    albumId = album,
    album = "Album $album",
    genre = genre,
    bpm = bpm,
    durationMs = 200_000L,
    isFavorite = favorite,
    addedAt = addedAt,
)

/** [count] plays of [songId], the latest [daysAgo] days before [NOW]. */
internal fun played(
    songId: Long,
    count: Int,
    daysAgo: Int,
) = MonthlyPlays(songId, "m${daysAgo / 30}", count, NOW - daysAgo * DAY)
