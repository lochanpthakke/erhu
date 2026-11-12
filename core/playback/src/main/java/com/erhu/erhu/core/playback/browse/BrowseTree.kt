@file:Suppress("detekt:All")

package com.erhu.erhu.core.playback.browse

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.playback.toMediaItem
import com.erhu.erhu.core.common.R as CommonR

object BrowseTree {
    fun getRoot(): MediaItem = browsableFolder(id = MediaId.ROOT, title = "erhu")

    fun getCategoryFolder(
        id: String,
        context: Context? = null,
    ): MediaItem = categoryFolder(id, context)

    fun songMediaItem(song: Song): MediaItem = song.toMediaItem()

    fun albumFolder(album: Album): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(album.title)
                .setArtist(album.artist)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        return MediaItem
            .Builder()
            .setMediaId("${MediaId.ALBUM_PREFIX}${album.id}")
            .setMediaMetadata(metadata)
            .build()
    }

    fun artistFolder(artist: Artist): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(artist.name)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        return MediaItem
            .Builder()
            .setMediaId("${MediaId.ARTIST_PREFIX}${artist.id}")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun categoryFolder(
        id: String,
        context: Context? = null,
    ): MediaItem {
        val title =
            when (id) {
                MediaId.SONGS -> context?.getString(CommonR.string.browse_category_songs) ?: "Songs"
                MediaId.ALBUMS -> context?.getString(CommonR.string.browse_category_albums) ?: "Albums"
                MediaId.ARTISTS -> context?.getString(CommonR.string.browse_category_artists) ?: "Artists"
                MediaId.PLAYLISTS -> context?.getString(CommonR.string.browse_category_playlists) ?: "Playlists"
                else -> error("Unknown category id: $id")
            }
        return browsableFolder(id = id, title = title)
    }

    private fun browsableFolder(
        id: String,
        title: String,
    ): MediaItem {
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        return MediaItem
            .Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }
}
