package com.lochanpthakke.empee3.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song

private const val EXTRA_DURATION_MS = "duration_ms"

internal fun Song.toMediaItem(): MediaItem {
    val extras =
        Bundle().apply {
            putLong(EXTRA_DURATION_MS, durationMs)
        }
    val metadataBuilder =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(extras)

    artworkData?.let { data ->
        metadataBuilder.setArtworkData(data, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
    }

    return MediaItem
        .Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

internal fun MediaItem.toSong(): Song {
    val metadata = mediaMetadata
    return Song(
        id = mediaId.toLong(),
        title = metadata.title?.toString().orEmpty(),
        artist = metadata.artist?.toString().orEmpty(),
        album = metadata.albumTitle?.toString().orEmpty(),
        durationMs = metadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
        uri = localConfiguration?.uri?.toString().orEmpty(),
        artworkData = metadata.artworkData,
    )
}

internal fun RepeatMode.toPlayerRepeatMode(): Int =
    when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

internal fun Int.toDomainRepeatMode(): RepeatMode =
    when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
