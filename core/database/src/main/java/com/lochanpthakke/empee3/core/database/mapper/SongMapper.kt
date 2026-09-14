package com.lochanpthakke.empee3.core.database.mapper

import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.model.Song

fun SongEntity.toDomainModel(): Song =
    Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        artworkData = null,
    )
