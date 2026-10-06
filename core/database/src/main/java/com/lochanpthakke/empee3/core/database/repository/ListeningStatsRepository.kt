package com.lochanpthakke.empee3.core.database.repository

import com.lochanpthakke.empee3.core.database.dao.ListeningStatsDao
import com.lochanpthakke.empee3.core.database.mapper.toDomainModel
import com.lochanpthakke.empee3.core.model.RankedAlbum
import com.lochanpthakke.empee3.core.model.RankedArtist
import com.lochanpthakke.empee3.core.model.RankedSong
import com.lochanpthakke.empee3.core.model.SoundCapsule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The device's time zone; replaced in tests. */
fun interface ZoneSource {
    fun zone(): ZoneId
}

/**
 * Monthly listening statistics (Sound Capsule). Playback reports listening events here;
 * each lands in the local calendar month it happened in (`YYYY-MM`), so a new month
 * starts from zero by itself and earlier months stay as they were.
 */
@Singleton
class ListeningStatsRepository
    @Inject
    constructor(
        private val dao: ListeningStatsDao,
        private val timeSource: TimeSource,
        private val zoneSource: ZoneSource,
    ) {
        /** A song started playing as a listening event. */
        suspend fun recordPlay(songId: Long) {
            val now = timeSource.now()
            val date = localDate(now)
            dao.recordPlay(YearMonth.from(date).toString(), date.toString(), songId, now)
        }

        /** [durationMs] of actual playing time of [songId]. */
        suspend fun addListeningTime(
            songId: Long,
            durationMs: Long,
        ) {
            if (durationMs <= 0L) return
            val now = timeSource.now()
            dao.addListened(YearMonth.from(localDate(now)).toString(), songId, durationMs, now)
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
            return combine(rankings, dao.observeTotals(key), dao.observeListeningDays()) { (songs, albums, artists), totals, days ->
                SoundCapsule(
                    month = month,
                    totalPlays = totals.plays,
                    listenedMs = totals.listenedMs,
                    longestStreakDays = longestStreakTouching(days.mapNotNull(::parseDay), month),
                    topSongs = songs,
                    topAlbums = albums,
                    topArtists = artists,
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
    var previous: LocalDate? = null
    val lastDay = month.atEndOfMonth()
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
