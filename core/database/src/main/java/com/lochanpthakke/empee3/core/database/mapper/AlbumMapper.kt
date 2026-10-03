package com.lochanpthakke.empee3.core.database.mapper

import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.model.Album

fun AlbumEntity.toDomainModel(): Album =
    Album(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        songCount = songCount,
        year = year,
        artworkUri = artworkUri,
        isCompilation = isCompilation,
    )
