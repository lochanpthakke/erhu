package com.erhu.erhu.core.database.mapper

import com.erhu.erhu.core.database.entity.AlbumEntity
import com.erhu.erhu.core.model.Album

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
