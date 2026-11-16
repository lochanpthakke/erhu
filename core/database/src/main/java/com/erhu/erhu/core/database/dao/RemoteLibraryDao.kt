package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.erhu.erhu.core.database.entity.RemoteSourceEntity
import com.erhu.erhu.core.database.entity.RemoteTrackEntity
import com.erhu.erhu.core.model.RemoteIds
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteLibraryDao {
    @Query("SELECT * FROM remote_sources ORDER BY added_at")
    fun observeSources(): Flow<List<RemoteSourceEntity>>

    @Query("SELECT * FROM remote_sources WHERE id = :id")
    suspend fun getSource(id: Long): RemoteSourceEntity?

    @Insert
    suspend fun insertSource(source: RemoteSourceEntity): Long

    @Update
    suspend fun updateSource(source: RemoteSourceEntity)

    /** Its tracks go with it (foreign key cascade). */
    @Query("DELETE FROM remote_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT * FROM remote_tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<RemoteTrackEntity>

    @Query("SELECT * FROM remote_tracks WHERE source_id = :sourceId AND remote_id = :remoteId")
    suspend fun findTrack(
        sourceId: Long,
        remoteId: String,
    ): RemoteTrackEntity?

    @Insert
    suspend fun insertTrack(track: RemoteTrackEntity): Long

    @Update
    suspend fun updateTrack(track: RemoteTrackEntity)

    @Query("SELECT last_location FROM remote_sources WHERE id = :id")
    suspend fun getLastLocation(id: Long): String?

    @Query("UPDATE remote_sources SET last_location = :location WHERE id = :id")
    suspend fun setLastLocation(
        id: Long,
        location: String?,
    )

    /** Tags read from the file itself; they outlive later listings that only know the file name. */
    @Query("UPDATE remote_tracks SET title = :title, artist = :artist, album = :album, duration_ms = :durationMs WHERE id = :id")
    suspend fun setTags(
        id: Long,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    )

    @Query("SELECT is_favorite FROM remote_tracks WHERE id = :id")
    fun observeFavorite(id: Long): Flow<Boolean?>

    @Query("UPDATE remote_tracks SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    )

    @Query("UPDATE remote_tracks SET is_unavailable = :unavailable WHERE id = :id")
    suspend fun setUnavailable(
        id: Long,
        unavailable: Boolean,
    )

    /**
     * Stores what the provider says about each track and returns the rows in the same order. A
     * track seen before keeps its local id; a new one gets the next id in the remote range.
     */
    @Transaction
    suspend fun remember(tracks: List<RemoteTrackEntity>): List<RemoteTrackEntity> =
        tracks.map { fresh ->
            val known = findTrack(fresh.sourceId, fresh.remoteId)
            if (known != null) {
                // A listing without a duration only knows the file name (Drive): keep tags already read from the file.
                val merged =
                    if (fresh.durationMs == 0L && known.durationMs > 0L) {
                        fresh.copy(id = known.id, title = known.title, artist = known.artist, album = known.album, durationMs = known.durationMs)
                    } else {
                        fresh.copy(id = known.id)
                    }
                merged.also { updateTrack(it) }
            } else {
                val id = insertTrack(fresh.copy(id = 0L))
                // The id sequence is seeded when the table is created; anything lower could collide with a device file.
                check(RemoteIds.isRemote(id)) { "remote_tracks id sequence was not seeded" }
                fresh.copy(id = id)
            }
        }
}
