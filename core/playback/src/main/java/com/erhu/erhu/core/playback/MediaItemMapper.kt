package com.erhu.erhu.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.RepeatMode
import com.erhu.erhu.core.model.Song

private const val EXTRA_DURATION_MS = "duration_ms"
private const val EXTRA_IS_FAVORITE = "is_favorite"
private const val EXTRA_SONG_URI = "song_uri"
private const val EXTRA_COLLECTION = "collection"

/** Audio URI of the song, used to read its artwork for the notification. */
internal fun Bundle.songUriFromExtras(): String? = getString(EXTRA_SONG_URI)?.takeIf { it.isNotBlank() }

/**
 * [origin]: the album or playlist this entry was started from, and which occurrence it is. It
 * travels with the entry (moves, shuffles and undo keep it); entries added one by one carry none,
 * so they never inherit a context.
 */
internal fun Song.toMediaItem(origin: QueueOrigin? = null): MediaItem {
    val extras =
        Bundle().apply {
            putLong(EXTRA_DURATION_MS, durationMs)
            putBoolean(EXTRA_IS_FAVORITE, isFavorite)
            putString(EXTRA_SONG_URI, uri)
            origin?.let { putString(EXTRA_COLLECTION, it.key) }
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

/** Where this queue entry was started from, or null for an ordinary entry. */
internal fun MediaItem.queueOrigin(): QueueOrigin? =
    mediaId.toLongOrNull()?.let { QueueOrigin.fromKey(mediaMetadata.extras?.getString(EXTRA_COLLECTION), it) }

/** The album or playlist this queue entry was started from, or null for an ordinary entry. */
internal fun MediaItem.collectionRef(): CollectionRef? = queueOrigin()?.collection

/** Duration the library recorded for this entry (0 when unknown). */
internal fun MediaItem.libraryDurationMs(): Long = mediaMetadata.extras?.getLong(EXTRA_DURATION_MS) ?: 0L

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
