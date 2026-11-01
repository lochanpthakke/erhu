package com.erhu.erhu.core.database.mapper

import com.erhu.erhu.core.database.entity.ArtistEntity
import com.erhu.erhu.core.model.Artist

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
