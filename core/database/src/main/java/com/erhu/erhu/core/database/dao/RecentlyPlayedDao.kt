package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.AlbumEntity
import com.erhu.erhu.core.database.entity.RecentlyPlayedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {
    @Upsert
    suspend fun upsert(entry: RecentlyPlayedEntity)

    /** Records the album of [songId] as played at [playedAt]; no-op for an unknown song. */
    @Query(
        """
        INSERT OR REPLACE INTO recently_played (kind, item_id, played_at)
        SELECT 'ALBUM', album_id, :playedAt FROM songs WHERE id = :songId
        """,
    )
    suspend fun recordAlbumOfSong(
        songId: Long,
        playedAt: Long,
    )

    @Query(
        """
        SELECT a.* FROM albums a
        INNER JOIN recently_played r ON r.kind = 'ALBUM' AND r.item_id = a.id
        ORDER BY r.played_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecentAlbums(limit: Int): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.artwork_uri AS artworkUri, COUNT(s.id) AS songCount
        FROM recently_played r
        INNER JOIN playlists p ON r.kind = 'PLAYLIST' AND r.item_id = p.id
        LEFT JOIN playlist_song_cross_ref ref ON ref.playlist_id = p.id
        LEFT JOIN songs s ON s.id = ref.song_id
        GROUP BY p.id
        ORDER BY MAX(r.played_at) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentPlaylists(limit: Int): Flow<List<PlaylistWithCount>>

    @Query("DELETE FROM recently_played WHERE kind = :kind AND item_id = :itemId")
    suspend fun delete(
        kind: String,
        itemId: Long,
    )
}
