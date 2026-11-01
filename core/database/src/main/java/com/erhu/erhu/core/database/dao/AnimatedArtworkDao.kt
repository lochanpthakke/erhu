package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.AnimatedArtworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimatedArtworkDao {
    @Query("SELECT * FROM animated_artwork WHERE album_id = :albumId")
    fun observeByAlbumId(albumId: Long): Flow<AnimatedArtworkEntity?>

    @Query("SELECT * FROM animated_artwork WHERE album_id = :albumId")
    suspend fun getByAlbumId(albumId: Long): AnimatedArtworkEntity?

    @Query("SELECT * FROM animated_artwork WHERE normalized_artist = :normalizedArtist AND normalized_album = :normalizedAlbum LIMIT 1")
    suspend fun getByNormalizedKey(
        normalizedArtist: String,
        normalizedAlbum: String,
    ): AnimatedArtworkEntity?

    @Query("SELECT * FROM animated_artwork")
    suspend fun getAll(): List<AnimatedArtworkEntity>

    @Upsert
    suspend fun upsert(entity: AnimatedArtworkEntity)

    @Query("DELETE FROM animated_artwork WHERE album_id = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)

    @Query("DELETE FROM animated_artwork")
    suspend fun deleteAll()

    @Query(
        """UPDATE animated_artwork SET status = CASE
        WHEN square_file_name IS NOT NULL OR tall_file_name IS NOT NULL THEN 'AVAILABLE'
        ELSE 'UNCHECKED' END WHERE status = 'DOWNLOADING'""",
    )
    suspend fun resetDownloading()
}
