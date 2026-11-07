package com.erhu.erhu.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

data class ArtistForSongs(
    @Embedded val artist: ArtistEntity,
    val representativeSongId: Long,
)

@Dao
interface ArtistDao {
    /** Only verified credits from this station, ranked by seed and number of represented tracks. */
    @Query(
        "SELECT a.*, CASE WHEN MAX(CASE WHEN ref.song_id = :seedSongId THEN 1 ELSE 0 END) = 1 " +
            "THEN :seedSongId ELSE MIN(ref.song_id) END AS representativeSongId " +
            "FROM artists a INNER JOIN song_artist_cross_ref ref ON a.id = ref.artist_id " +
            "WHERE ref.song_id IN (:songIds) AND ref.is_unresolved = 0 AND a.is_unresolved = 0 " +
            "GROUP BY a.id ORDER BY MAX(CASE WHEN ref.song_id = :seedSongId AND ref.role = 'PRIMARY' THEN 1 ELSE 0 END) DESC, " +
            "COUNT(DISTINCT ref.song_id) DESC, MIN(ref.position), a.id",
    )
    fun observeArtistsForSongs(
        songIds: List<Long>,
        seedSongId: Long,
    ): Flow<List<ArtistForSongs>>

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC")
    fun getArtistsPaged(): PagingSource<Int, ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: Long): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id = :id LIMIT 1")
    fun observeArtistById(id: Long): Flow<ArtistEntity?>

    /** Copies the stored artwork (artist_artwork, the source of truth) onto every artist row. */
    @Query(
        "UPDATE artists SET artwork_uri = " +
            "(SELECT w.artwork_uri FROM artist_artwork w WHERE w.artist_key = artists.artist_key)",
    )
    suspend fun syncAllArtwork(): Int

    /** [syncAllArtwork] for one artist key. */
    @Query(
        "UPDATE artists SET artwork_uri = " +
            "(SELECT w.artwork_uri FROM artist_artwork w WHERE w.artist_key = artists.artist_key) " +
            "WHERE artist_key = :artistKey",
    )
    suspend fun syncArtwork(artistKey: String): Int

    @Query("DELETE FROM artists")
    suspend fun deleteAll(): Int
}
