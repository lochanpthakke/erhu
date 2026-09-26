package com.lochanpthakke.empee3.core.database.mapper

import android.media.MediaMetadataRetriever
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.model.Song
import java.io.File

fun SongEntity.toDomainModel(): Song {
    val artworkBytes =
        if (filePath.isNotBlank() && File(filePath).exists()) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(filePath)
                val pic = mmr.embeddedPicture
                mmr.release()
                pic
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        artworkData = artworkBytes,
        isFavorite = isFavorite,
    )
}
