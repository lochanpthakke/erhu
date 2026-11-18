package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.OnConflictStrategy
import com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity
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

    /** The track's Library row (when its folder is in the Library) shows the same favourite. */
    @Query("UPDATE songs SET is_favorite = :favorite WHERE id = :id")
    suspend fun setLibraryFavorite(
        id: Long,
        favorite: Boolean,
    )

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

    @Query("SELECT * FROM remote_library_folders ORDER BY name")
    fun observeLibraryFolders(): Flow<List<RemoteLibraryFolderEntity>>

    @Query("SELECT * FROM remote_library_folders ORDER BY name")
    suspend fun getLibraryFolders(): List<RemoteLibraryFolderEntity>

    /** -1 when the folder is already in the Library. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLibraryFolder(folder: RemoteLibraryFolderEntity): Long

    @Query("SELECT * FROM remote_library_folders WHERE source_id = :sourceId AND folder_id = :folderId")
    suspend fun findLibraryFolder(
        sourceId: Long,
        folderId: String,
    ): RemoteLibraryFolderEntity?

    @Query("UPDATE remote_library_folders SET synced_at = :at WHERE id = :id")
    suspend fun setLibraryFolderSynced(
        id: Long,
        at: Long,
    )

    @Query("DELETE FROM remote_library_folders WHERE id = :id")
    suspend fun deleteLibraryFolderRow(id: Long)

    @Query("UPDATE remote_tracks SET library_folder_id = NULL WHERE library_folder_id = :folderId")
    suspend fun clearLibraryFolder(folderId: Long)

    /** The folder leaves the Library; its tracks stay known (queues, playlists) but are no longer listed. */
    @Transaction
    suspend fun deleteLibraryFolder(id: Long) {
        clearLibraryFolder(id)
        deleteLibraryFolderRow(id)
    }

    @Query("UPDATE remote_tracks SET library_folder_id = :folderId, parent_folder = :parent WHERE id = :id")
    suspend fun putInLibraryFolder(
        id: Long,
        folderId: Long,
        parent: String,
    )

    @Query("SELECT id FROM remote_tracks WHERE library_folder_id = :folderId")
    suspend fun getLibraryFolderTrackIds(folderId: Long): List<Long>

    @Query("UPDATE remote_tracks SET library_folder_id = NULL WHERE id IN (:ids)")
    suspend fun removeFromLibrary(ids: List<Long>)

    /** Every track a synced folder puts in the Library, for the library scan. */
    @Query("SELECT * FROM remote_tracks WHERE library_folder_id IS NOT NULL AND is_unavailable = 0")
    suspend fun getLibraryTracks(): List<RemoteTrackEntity>

    /**
     * Makes [tracks] (track id to the folder directly holding it) the whole content of [folderId]:
     * tracks no longer found there leave the Library.
     */
    @Transaction
    suspend fun replaceLibraryFolderTracks(
        folderId: Long,
        tracks: Map<Long, String>,
    ) {
        val gone = getLibraryFolderTrackIds(folderId).filterNot { it in tracks }
        gone.chunked(LIBRARY_CHUNK).forEach { removeFromLibrary(it) }
        tracks.forEach { (id, parent) -> putInLibraryFolder(id, folderId, parent) }
    }

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
                merged.copy(libraryFolderId = known.libraryFolderId, parentFolder = known.parentFolder).also { updateTrack(it) }
            } else {
                val id = insertTrack(fresh.copy(id = 0L))
                // The id sequence is seeded when the table is created; anything lower could collide with a device file.
                check(RemoteIds.isRemote(id)) { "remote_tracks id sequence was not seeded" }
                fresh.copy(id = id)
            }
        }
}

/** Stay well below SQLite's 999 bound-variable limit. */
private const val LIBRARY_CHUNK = 500
