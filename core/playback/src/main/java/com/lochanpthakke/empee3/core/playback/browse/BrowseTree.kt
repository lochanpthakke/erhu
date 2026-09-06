package com.lochanpthakke.empee3.core.playback.browse

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Builds the media browse tree for Android Auto / media browser clients.
 *
 * Structural-only for Deliverable #9: the tree shape and node wiring are
 * correct and stable, but category nodes return empty children until
 * Phase 3 (Room + MediaStore scanning) implements SongLookup. Only
 * [getChildren]'s body should need to change when that data exists —
 * node IDs and hierarchy shape are meant to survive that transition.
 */
object BrowseTree {
    fun getRoot(): MediaItem = browsableFolder(id = MediaId.ROOT, title = "empee3")

    fun getChildren(parentId: String): List<MediaItem>? =
        when (parentId) {
            MediaId.ROOT -> MediaId.ROOT_CHILDREN.map { categoryFolder(it) }
            MediaId.SONGS, MediaId.ALBUMS, MediaId.ARTISTS, MediaId.PLAYLISTS -> emptyList()
            else -> null
        }

    fun getItem(mediaId: String): MediaItem? =
        when (mediaId) {
            MediaId.ROOT -> getRoot()
            MediaId.SONGS, MediaId.ALBUMS, MediaId.ARTISTS, MediaId.PLAYLISTS -> categoryFolder(mediaId)
            else -> null
        }

    private fun categoryFolder(id: String): MediaItem {
        val title =
            when (id) {
                MediaId.SONGS -> "Songs"
                MediaId.ALBUMS -> "Albums"
                MediaId.ARTISTS -> "Artists"
                MediaId.PLAYLISTS -> "Playlists"
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
