package com.lochanpthakke.empee3.core.playback.browse

/**
 * Media ID constants for the Android Auto / media browser tree.
 */
object MediaId {
    const val ROOT = "root"

    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"

    const val ALBUM_PREFIX = "album_"
    const val ARTIST_PREFIX = "artist_"

    /** All top-level browsable category IDs, in the order they should be displayed. */
    val ROOT_CHILDREN = listOf(SONGS, ALBUMS, ARTISTS, PLAYLISTS)
}
