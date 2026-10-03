package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lochanpthakke.empee3.core.database.entity.AlbumArtistCrossRefEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistAliasEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistArtworkEntity
import com.lochanpthakke.empee3.core.database.entity.UnverifiedCreditSegmentEntity

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
}
