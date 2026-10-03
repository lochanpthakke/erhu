package com.lochanpthakke.empee3.core.database.mapper

import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import com.lochanpthakke.empee3.core.model.Artist

fun ArtistEntity.toDomainModel(): Artist =
    Artist(
        id = id,
        name = name,
        songCount = songCount,
        albumCount = albumCount,
        artworkUri = artworkUri,
        artistKey = artistKey,
        isUnresolved = isUnresolved,
    )
