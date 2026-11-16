package com.erhu.erhu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A connected remote library (one Google account, one Subsonic server). Holds no secret. */
@Entity(tableName = "remote_sources")
data class RemoteSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val provider: String,
    val name: String,
    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,
    val account: String? = null,
    @ColumnInfo(name = "allow_insecure")
    val allowInsecure: Boolean = false,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
    /** Where the user last was in this source's browser (provider-defined text), or null. */
    @ColumnInfo(name = "last_location")
    val lastLocation: String? = null,
)

/**
 * A remote track erhu has handed to the player or the library at least once. It is identified by
 * ([sourceId], [remoteId]); [id] is the local handle used wherever a song id is stored and starts at
 * RemoteIds.FIRST. AUTOINCREMENT, so an id is never reused after its row is deleted.
 *
 * Kept apart from `songs` on purpose: the device scan deletes every `songs` row MediaStore no
 * longer reports.
 */
@Entity(
    tableName = "remote_tracks",
    indices = [Index(value = ["source_id", "remote_id"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = RemoteSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RemoteTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "source_id")
    val sourceId: Long,
    val provider: String,
    @ColumnInfo(name = "remote_id")
    val remoteId: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "track_number")
    val trackNumber: Int,
    @ColumnInfo(name = "disc_number")
    val discNumber: Int,
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,
    @ColumnInfo(name = "artwork_key")
    val artworkKey: String? = null,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    /** The provider reported the track gone. It stays so queues and playlists can show it as such. */
    @ColumnInfo(name = "is_unavailable")
    val isUnavailable: Boolean = false,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
