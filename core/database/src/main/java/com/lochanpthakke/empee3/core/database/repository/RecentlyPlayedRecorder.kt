package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.database.dao.RecentlyPlayedDao
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.model.Album
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Current time in milliseconds; replaced in tests. */
fun interface TimeSource {
    fun now(): Long
}

/**
 * Recently played albums, recorded from real playback: each time a song starts
 * playing, its album moves to the front. Nothing is recorded or invented otherwise.
 */
@Singleton
class RecentlyPlayedRecorder
    @Inject
    constructor(
        private val recentlyPlayedDao: RecentlyPlayedDao,
        private val timeSource: TimeSource,
    ) {
        suspend fun recordSongPlayed(songId: Long) = recentlyPlayedDao.recordAlbumOfSong(songId, timeSource.now())

        fun observeRecentAlbums(limit: Int): Flow<List<Album>> =
            recentlyPlayedDao.observeRecentAlbums(limit).map { albums -> albums.map { it.toDomainModel() } }
    }
