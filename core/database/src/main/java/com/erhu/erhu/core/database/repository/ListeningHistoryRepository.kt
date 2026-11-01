package com.erhu.erhu.core.database.repository

import com.erhu.erhu.core.database.dao.ListeningStatsDao
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.model.MonthlyPlays
import com.erhu.erhu.core.model.TrackFacts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The local data the smart mixes are built from: every song's facts and the counted plays per
 * song and month. Read-only views over existing tables (no schema of its own); each flow emits
 * again only when its content actually changes.
 */
@Singleton
class ListeningHistoryRepository
    @Inject
    constructor(
        private val songDao: SongDao,
        private val listeningStatsDao: ListeningStatsDao,
    ) {
        fun observeTracks(): Flow<List<TrackFacts>> =
            songDao
                .observeTrackFacts()
                .map { rows ->
                    rows.map {
                        TrackFacts(
                            songId = it.id,
                            title = it.title,
                            artistId = it.artistId,
                            artist = it.artist,
                            albumId = it.albumId,
                            album = it.album,
                            genre = it.genre,
                            bpm = it.bpm,
                            durationMs = it.durationMs,
                            isFavorite = it.isFavorite,
                            addedAt = it.dateModified,
                            year = it.year,
                        )
                    }
                }.distinctUntilChanged()

        fun observePlays(): Flow<List<MonthlyPlays>> =
            listeningStatsDao
                .observeMonthlyPlays()
                .map { rows -> rows.map { MonthlyPlays(it.songId, it.monthKey, it.playCount, it.lastPlayedAt) } }
                .distinctUntilChanged()
    }
