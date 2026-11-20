package com.erhu.erhu.core.database.repository

import com.erhu.erhu.core.database.dao.ListeningEventDao
import com.erhu.erhu.core.database.dao.ListeningStatsDao
import com.erhu.erhu.core.database.entity.ListeningEventEntity
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.model.RankedAlbum
import com.erhu.erhu.core.model.RankedArtist
import com.erhu.erhu.core.model.RankedSong
import com.erhu.erhu.core.model.SoundCapsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

enum class StatsPeriod {
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    THIS_YEAR,
}

data class PeriodStats(
    val period: StatsPeriod,
    val topSongs: List<RankedSong> = emptyList(),
    val topAlbums: List<RankedAlbum> = emptyList(),
    val topArtists: List<RankedArtist> = emptyList(),
    val totalPlays: Int = 0,
    val totalListenedMs: Long = 0L,
    val uniqueTracks: Int = 0,
) {
    val isEmpty: Boolean get() = totalPlays == 0 && topSongs.isEmpty() && topArtists.isEmpty()
}

/** The device's time zone; replaced in tests. */
fun interface ZoneSource {
    fun zone(): ZoneId
}

/**
 * Monthly listening statistics (Sound Capsule) and period recap events. Playback reports
 * listening events here; each lands in the local calendar month it happened in (`YYYY-MM`),
 * while granular timestamped events power flexible recap cards.
 */
@Singleton
class ListeningStatsRepository
    @Inject
    constructor(
        private val dao: ListeningStatsDao,
        private val eventDao: ListeningEventDao,
        private val timeSource: TimeSource,
        private val zoneSource: ZoneSource,
    ) {
        constructor(
            dao: ListeningStatsDao,
            timeSource: TimeSource,
            zoneSource: ZoneSource,
        ) : this(
            dao = dao,
            eventDao = NoOpListeningEventDao,
            timeSource = timeSource,
            zoneSource = zoneSource,
        )

        init {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    if (eventDao.getCount() == 0) {
                        eventDao.backfillFromListeningStats()
                    }
                } catch (_: Exception) {
                }
            }
        }

        /** A song started playing as a listening event. */
        suspend fun recordPlay(songId: Long) {
            val now = timeSource.now()
            val date = localDate(now)
            dao.recordPlay(YearMonth.from(date).toString(), date.toString(), songId, now)
            eventDao.insert(
                ListeningEventEntity(
                    songId = songId,
                    timestamp = now,
                    durationMs = 0L,
                    isCountedPlay = true,
                ),
            )
        }

        /** [durationMs] of actual playing time of [songId]. */
        suspend fun addListeningTime(
            songId: Long,
            durationMs: Long,
        ) {
            if (durationMs <= 0L) return
            val now = timeSource.now()
            dao.addListened(YearMonth.from(localDate(now)).toString(), songId, durationMs, now)
            eventDao.insert(
                ListeningEventEntity(
                    songId = songId,
                    timestamp = now,
                    durationMs = durationMs,
                    isCountedPlay = false,
                ),
            )
        }

        fun currentMonth(): YearMonth = YearMonth.from(localDate(timeSource.now()))

        /** Months with at least one play, newest first. */
        fun observeMonths(): Flow<List<YearMonth>> = dao.observeMonthKeys().map { keys -> keys.mapNotNull(::parseMonth) }

        fun observeCapsule(
            month: YearMonth,
            limit: Int = TOP_LIMIT,
        ): Flow<SoundCapsule> {
            val key = month.toString()
            val rankings =
                combine(
                    dao.observeTopSongs(key, limit),
                    dao.observeTopAlbums(key, limit),
                    dao.observeTopArtists(key, limit),
                ) { songs, albums, artists ->
                    Triple(
                        songs.map { RankedSong(it.song.toDomainModel(), it.plays) },
                        albums.map { RankedAlbum(it.album.toDomainModel(), it.plays) },
                        artists.map { RankedArtist(it.artist.toDomainModel(), it.plays) },
                    )
                }
            return combine(
                rankings,
                dao.observeTotals(key),
                dao.observeListeningDays(),
            ) { (songs, albums, artists), totals, days ->
                SoundCapsule(
                    month = month,
                    totalPlays = totals.plays,
                    listenedMs = totals.listenedMs,
                    longestStreakDays = longestStreakTouching(days.mapNotNull(::parseDay), month),
                    topSongs = songs,
                    topAlbums = albums,
                    topArtists = artists,
                    uniqueTracks = totals.uniqueTracks,
                )
            }
        }

        fun observePeriodStats(
            period: StatsPeriod,
            limit: Int = TOP_LIMIT,
        ): Flow<PeriodStats> {
            val now = timeSource.now()
            val startTime =
                when (period) {
                    StatsPeriod.LAST_7_DAYS -> now - 7L * 24 * 60 * 60 * 1000
                    StatsPeriod.LAST_30_DAYS -> now - 30L * 24 * 60 * 60 * 1000
                    StatsPeriod.THIS_MONTH -> {
                        val firstOfMonth = localDate(now).withDayOfMonth(1)
                        firstOfMonth.atStartOfDay(zoneSource.zone()).toInstant().toEpochMilli()
                    }
                    StatsPeriod.THIS_YEAR -> {
                        val firstOfYear = localDate(now).withDayOfYear(1)
                        firstOfYear.atStartOfDay(zoneSource.zone()).toInstant().toEpochMilli()
                    }
                }
            val endTime = now
            val currentMonthKey = currentMonth().toString()

            val eventRankings =
                combine(
                    eventDao.observeTopSongsBetween(startTime, endTime, limit),
                    eventDao.observeTopAlbumsBetween(startTime, endTime, limit),
                    eventDao.observeTopArtistsBetween(startTime, endTime, limit),
                ) { songs, albums, artists ->
                    Triple(
                        songs.map { RankedSong(it.song.toDomainModel(), it.plays) },
                        albums.map { RankedAlbum(it.album.toDomainModel(), it.plays) },
                        artists.map { RankedArtist(it.artist.toDomainModel(), it.plays) },
                    )
                }

            val fallbackRankings =
                combine(
                    dao.observeTopSongs(currentMonthKey, limit),
                    dao.observeTopAlbums(currentMonthKey, limit),
                    dao.observeTopArtists(currentMonthKey, limit),
                ) { songs, albums, artists ->
                    Triple(
                        songs.map { RankedSong(it.song.toDomainModel(), it.plays) },
                        albums.map { RankedAlbum(it.album.toDomainModel(), it.plays) },
                        artists.map { RankedArtist(it.artist.toDomainModel(), it.plays) },
                    )
                }

            // The monthly aggregates only stand in for "this month", whose range they cover exactly:
            // a rolling 7 or 30 days without listening events has no plays, not the month's.
            val rankings =
                combine(eventRankings, fallbackRankings) { eventData, fallbackData ->
                    if (eventData.first.isNotEmpty() || eventData.second.isNotEmpty() || eventData.third.isNotEmpty()) {
                        eventData
                    } else if (period == StatsPeriod.THIS_MONTH) {
                        fallbackData
                    } else {
                        eventData
                    }
                }

            val totalsFlow =
                combine(eventDao.observePeriodTotals(startTime, endTime), dao.observeTotals(currentMonthKey)) { eventTotals, monthTotals ->
                    if (eventTotals.plays > 0) {
                        eventTotals
                    } else if (period == StatsPeriod.THIS_MONTH) {
                        com.erhu.erhu.core.database.dao.PeriodTotals(
                            plays = monthTotals.plays,
                            listenedMs = monthTotals.listenedMs,
                            uniqueTracks = monthTotals.uniqueTracks,
                        )
                    } else {
                        eventTotals
                    }
                }

            return combine(rankings, totalsFlow) { (songs, albums, artists), totals ->
                PeriodStats(
                    period = period,
                    topSongs = songs,
                    topAlbums = albums,
                    topArtists = artists,
                    totalPlays = totals.plays,
                    totalListenedMs = totals.listenedMs,
                    uniqueTracks = totals.uniqueTracks,
                )
            }
        }

        private fun localDate(epochMs: Long): LocalDate =
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zoneSource.zone()).toLocalDate()

        companion object {
            const val TOP_LIMIT = 5
        }
    }

/**
 * The longest run of consecutive listening days that includes at least one day of
 * [month]. A run may start before the month (Sep 28 – Oct 1 is 4 days for October), but
 * days after the month's end are not counted, so a finished month never changes.
 * 0 when [month] has no listening day.
 */
internal fun longestStreakTouching(
    days: List<LocalDate>,
    month: YearMonth,
): Int {
    var best = 0
    var runLength = 0
    var runTouches = false
    val lastDay = month.atEndOfMonth()
    var previous: LocalDate? = null
    for (day in days.distinct().sorted().filter { it <= lastDay }) {
        if (previous == null || day != previous.plusDays(1)) {
            runLength = 0
            runTouches = false
        }
        runLength++
        runTouches = runTouches || YearMonth.from(day) == month
        if (runTouches) best = maxOf(best, runLength)
        previous = day
    }
    return best
}

private fun parseMonth(key: String): YearMonth? = runCatching { YearMonth.parse(key) }.getOrNull()

private fun parseDay(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

private object NoOpListeningEventDao : ListeningEventDao {
    override suspend fun insert(event: ListeningEventEntity): Long = 0L

    override suspend fun getCount(): Int = 0

    override suspend fun backfillFromListeningStats() {}

    override fun observeTopSongsBetween(
        startTime: Long,
        endTime: Long,
        limit: Int,
    ) = kotlinx.coroutines.flow.flowOf(emptyList<com.erhu.erhu.core.database.dao.SongPlays>())

    override fun observeTopAlbumsBetween(
        startTime: Long,
        endTime: Long,
        limit: Int,
    ) = kotlinx.coroutines.flow.flowOf(emptyList<com.erhu.erhu.core.database.dao.AlbumPlays>())

    override fun observeTopArtistsBetween(
        startTime: Long,
        endTime: Long,
        limit: Int,
    ) = kotlinx.coroutines.flow.flowOf(emptyList<com.erhu.erhu.core.database.dao.ArtistPlays>())

    override fun observePeriodTotals(
        startTime: Long,
        endTime: Long,
    ) = kotlinx.coroutines.flow.flowOf(
        com.erhu.erhu.core.database.dao
            .PeriodTotals(0, 0L, 0),
    )
}
