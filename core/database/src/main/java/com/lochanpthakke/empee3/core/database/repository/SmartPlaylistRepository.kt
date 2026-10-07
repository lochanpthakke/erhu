package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.database.dao.ListeningStatsDao
import com.lochanpthakke.empee3.core.database.dao.SongDao
import com.lochanpthakke.empee3.core.database.entity.SongEntity
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.model.SmartPlaylist
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The songs of each [SmartPlaylist], computed by Room from current data and kept
 * up to date as it changes: play history from the listening statistics (a play
 * counts once half of the song was actually heard), favorites from the songs, and
 * "added" from the song file's MediaStore modification time. Nothing is stored.
 */
@Singleton
class SmartPlaylistRepository
    @Inject
    constructor(
        private val listeningStatsDao: ListeningStatsDao,
        private val songDao: SongDao,
    ) {
        fun observe(playlist: SmartPlaylist): Flow<List<Song>> =
            entities(playlist, ALL).map { songs -> songs.map(SongEntity::toDomainModel) }

        /**
         * The songs whose artwork makes [playlist]'s cover: in the playlist's own order,
         * one per album (the album's artwork), at most [COVER_ALBUMS]. Reads only the first
         * [COVER_SCAN] songs of the playlist.
         */
        fun observeCover(playlist: SmartPlaylist): Flow<List<Song>> =
            entities(playlist, COVER_SCAN).map { songs ->
                songs.distinctBy { it.albumId }.take(COVER_ALBUMS).map(SongEntity::toDomainModel)
            }

        private fun entities(
            playlist: SmartPlaylist,
            limit: Int,
        ): Flow<List<SongEntity>> =
            when (playlist) {
                SmartPlaylist.MOST_PLAYED -> listeningStatsDao.observeMostPlayedSongs(limit)
                SmartPlaylist.RECENTLY_ADDED -> songDao.observeRecentlyAddedSongs(limit)
                SmartPlaylist.RECENTLY_PLAYED -> listeningStatsDao.observeRecentlyPlayedSongs(limit)
                SmartPlaylist.FAVORITES -> songDao.observeFavoriteSongs(limit)
                SmartPlaylist.NEVER_PLAYED -> listeningStatsDao.observeNeverPlayedSongs(limit)
            }

        private companion object {
            /** SQLite: no limit. */
            const val ALL = -1
            const val COVER_SCAN = 40
            const val COVER_ALBUMS = 4
        }
    }
