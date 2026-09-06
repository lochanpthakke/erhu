package com.lochanpthakke.empee3.core.playback.browse

/**
 * Media ID constants for the Android Auto / media browser tree.
 *
 * These IDs are opaque strings from the client's perspective (Android Auto,
 * Wear OS, etc.) but must be stable and unique within this app's session,
 * since [onGetChildren]/[onGetItem] match against them.
 *
 * Structural-only for Deliverable #9: category nodes exist and are wired
 * correctly, but return empty children until Phase 3 (Room + MediaStore
 * scanning) provides real data via SongLookup.
 */
object MediaId {
    const val ROOT = "root"

    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"

    /** All top-level browsable category IDs, in the order they should be displayed. */
    val ROOT_CHILDREN = listOf(SONGS, ALBUMS, ARTISTS, PLAYLISTS)
}
