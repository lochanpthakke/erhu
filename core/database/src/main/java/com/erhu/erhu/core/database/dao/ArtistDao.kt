package com.erhu.erhu.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
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
