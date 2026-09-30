package com.lochanpthakke.empee3.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song

private const val EXTRA_DURATION_MS = "duration_ms"
private const val EXTRA_IS_FAVORITE = "is_favorite"
private const val EXTRA_SONG_URI = "song_uri"

/** Audio URI of the song, used to read its artwork for the notification. */
internal fun Bundle.songUriFromExtras(): String? = getString(EXTRA_SONG_URI)?.takeIf { it.isNotBlank() }

internal fun Song.toMediaItem(): MediaItem {
    val extras =
        Bundle().apply {
            putLong(EXTRA_DURATION_MS, durationMs)
            putBoolean(EXTRA_IS_FAVORITE, isFavorite)
            putString(EXTRA_SONG_URI, uri)
        }
    val metadataBuilder =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)

    // Lets the media notification / lock screen load album art on demand.
    artworkUri?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }

    return MediaItem
        .Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

/**
 * Maps a queue item back to a [Song]. Returns null for items that were not
 * created by [toMediaItem] (non-numeric media IDs) instead of crashing.
 */
internal fun MediaItem.toSongOrNull(): Song? {
    val id = mediaId.toLongOrNull() ?: return null
    val metadata = mediaMetadata
    return Song(
        id = id,
        title = metadata.title?.toString().orEmpty(),
        artist = metadata.artist?.toString().orEmpty(),
        album = metadata.albumTitle?.toString().orEmpty(),
        durationMs = metadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
        uri = localConfiguration?.uri?.toString().orEmpty(),
        artworkUri = metadata.artworkUri?.toString(),
        isFavorite = metadata.extras?.getBoolean(EXTRA_IS_FAVORITE) ?: false,
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
