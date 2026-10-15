package com.lochanpthakke.empee3.core.database.mapper

import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.model.Song

/**
 * Pure field mapping — no file or MediaStore I/O. Artwork is referenced by URI
 * and loaded lazily by the UI; decoding embedded pictures here made every
 * library query (paging, search, browse) do disk I/O per row.
 */
fun SongEntity.toDomainModel(): Song =
    Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        artworkUri = artworkUri,
        isFavorite = isFavorite,
        trackNumber = trackNumber,
        discNumber = discNumber,
        filePath = filePath,
        bpm = bpm,
    )
