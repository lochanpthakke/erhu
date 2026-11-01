package com.erhu.erhu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.erhu.erhu.core.database.entity.SongArtistCrossRefEntity

@Dao
interface SongArtistCrossRefDao {
    @Upsert
    suspend fun upsertAll(crossRefs: List<SongArtistCrossRefEntity>)

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id IN (:songIds)")
    suspend fun deleteBySongIds(songIds: List<Long>): Int

    @Query("DELETE FROM song_artist_cross_ref")
    suspend fun deleteAll(): Int
}
