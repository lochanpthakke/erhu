package com.lochanpthakke.empee3.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kept across scans: an artist's profile artwork by canonical artist key. See ArtistArtworkCache.
 *
 * [source] AUTO is the automatic Deezer lookup ([artworkUri] a photo url or a "NONE…" not-found
 * marker, [providerArtistId] the Deezer artist it came from, [matcherVersion] the matcher that
 * decided). CUSTOM is a user-chosen image file and is never touched by automatic lookups or scans.
 */
@Entity(tableName = "artist_artwork")
data class ArtistArtworkEntity(
    @PrimaryKey
    @ColumnInfo(name = "artist_key")
    val artistKey: String,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "source", defaultValue = "'AUTO'")
    val source: String = SOURCE_AUTO,
    @ColumnInfo(name = "provider_artist_id")
    val providerArtistId: Long? = null,
    @ColumnInfo(name = "matcher_version", defaultValue = "0")
    val matcherVersion: Int = 0,
) {
    companion object {
        const val SOURCE_AUTO = "AUTO"
        const val SOURCE_CUSTOM = "CUSTOM"
    }
}
