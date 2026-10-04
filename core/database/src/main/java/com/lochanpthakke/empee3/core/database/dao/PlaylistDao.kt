package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lochanpthakke.empee3.core.database.entity.PlaylistEntity
import com.lochanpthakke.empee3.core.database.entity.PlaylistSongCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val songCount: Int,
    val artworkUri: String? = null,
)

@Dao
@Suppress("TooManyFunctions")
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    /** Counts only songs that still exist in the library. */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.artwork_uri AS artworkUri, COUNT(s.id) AS songCount
        FROM playlists p
        LEFT JOIN playlist_song_cross_ref ref ON ref.playlist_id = p.id
        LEFT JOIN songs s ON s.id = ref.song_id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """,
    )
    fun getPlaylistsWithCounts(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistRow(playlistId: Long): Int

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun deleteAllSongsFromPlaylist(playlistId: Long): Int

    /** Cross-refs have no foreign keys, so remove them explicitly with the playlist. */
    @Transaction
    suspend fun deletePlaylist(playlistId: Long) {
        deleteAllSongsFromPlaylist(playlistId)
        deletePlaylistRow(playlistId)
    }

    @Query("UPDATE playlists SET name = :name, updated_at = :timestamp WHERE id = :playlistId")
    suspend fun renamePlaylist(
        playlistId: Long,
        name: String,
        timestamp: Long,
    ): Int

    @Query("UPDATE playlists SET artwork_uri = :artworkUri, updated_at = :timestamp WHERE id = :playlistId")
    suspend fun setArtwork(
        playlistId: Long,
        artworkUri: String?,
        timestamp: Long,
    ): Int

    @Query("UPDATE playlists SET updated_at = :timestamp WHERE id = :playlistId")
    suspend fun touchPlaylist(
        playlistId: Long,
        timestamp: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRefEntity): Long

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song_cross_ref WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    /** Appends [songId]; a song already in the playlist is left where it is. Returns false if it was already there. */
    @Transaction
    suspend fun appendSongToPlaylist(
        playlistId: Long,
        songId: Long,
        timestamp: Long,
    ): Boolean {
        val nextPosition = getMaxPosition(playlistId) + 1
        val inserted = insertPlaylistSongCrossRef(PlaylistSongCrossRefEntity(playlistId, songId, nextPosition)) != -1L
        if (inserted) touchPlaylist(playlistId, timestamp)
        return inserted
    }

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSongFromPlaylist(
        playlistId: Long,
        songId: Long,
    ): Int

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref ref ON s.id = ref.song_id
        WHERE ref.playlist_id = :playlistId
        ORDER BY ref.position ASC
        """,
    )
    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>
}
