package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.AlbumArtistCrossRefEntity
import com.erhu.erhu.core.database.entity.ArtistAliasEntity
import com.erhu.erhu.core.database.entity.ArtistArtworkEntity
import com.erhu.erhu.core.database.entity.UnverifiedCreditSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryModelDao {
    @Query("DELETE FROM album_artist_cross_ref")
    suspend fun deleteAlbumArtists(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumArtists(rows: List<AlbumArtistCrossRefEntity>)

    @Query("DELETE FROM unverified_credit_segment")
    suspend fun deleteUnverifiedSegments(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnverifiedSegments(rows: List<UnverifiedCreditSegmentEntity>)

    @Query("SELECT * FROM artist_alias")
    suspend fun getAliases(): List<ArtistAliasEntity>

    @Query("SELECT * FROM artist_artwork")
    suspend fun getArtworkByKey(): List<ArtistArtworkEntity>

    @Upsert
    suspend fun upsertArtwork(artwork: ArtistArtworkEntity)

    @Query("SELECT * FROM artist_artwork WHERE artist_key = :artistKey LIMIT 1")
    suspend fun getArtwork(artistKey: String): ArtistArtworkEntity?

    @Query("SELECT artist_key FROM artist_artwork WHERE source = 'CUSTOM'")
    fun observeCustomArtworkKeys(): Flow<List<String>>

    /** Removes a custom image's row only; automatic rows are never deleted. */
    @Query("DELETE FROM artist_artwork WHERE artist_key = :artistKey AND source = 'CUSTOM'")
    suspend fun deleteCustomArtwork(artistKey: String): Int
}
