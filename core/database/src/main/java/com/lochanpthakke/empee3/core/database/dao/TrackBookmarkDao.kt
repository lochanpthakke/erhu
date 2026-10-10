package com.lochanpthakke.empee3.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lochanpthakke.empee3.core.database.entity.TrackBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackBookmarkDao {
    @Insert
    suspend fun insert(bookmark: TrackBookmarkEntity): Long

    /** One track's bookmarks in time order (then creation order for equal times). */
    @Query("SELECT * FROM track_bookmarks WHERE song_id = :songId ORDER BY position_ms ASC, id ASC")
    fun observeForSong(songId: Long): Flow<List<TrackBookmarkEntity>>

    @Query("UPDATE track_bookmarks SET label = :label WHERE id = :id")
    suspend fun rename(
        id: Long,
        label: String,
    ): Int

    @Query("DELETE FROM track_bookmarks WHERE id = :id")
    suspend fun delete(id: Long): Int
}
