package com.erhu.erhu.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.model.SoundCapsule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Sound Capsule statistics: plays land in their local calendar month, rankings come
 * from the existing song → album link and resolved artist credits, and recording never
 * touches the library itself.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ListeningStatsTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var now = at(2026, 10, 6, 12, 0)
    private val stats = ListeningStatsRepository(db.listeningStatsDao(), { now }, { ZoneOffset.UTC })

    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    @Before
    fun seed() {
        val sql = db.openHelper.writableDatabase
        listOf(
            album(20, "My Beautiful Dark Twisted Fantasy", "Kanye West", 10),
            album(21, "22, A Million", "Bon Iver", 11),
            album(22, "Her Loss", "Drake & 21 Savage", 12),
            album(23, "Flower Boy", "Tyler, The Creator", 99),
            artist(10, "Kanye West", unresolved = false),
            artist(11, "Bon Iver", unresolved = false),
            artist(12, "Drake", unresolved = false),
            artist(13, "21 Savage", unresolved = false),
            artist(99, "Tyler/ The Creator", unresolved = true),
            song(1, "Lost In The World", "Kanye West/Bon Iver", 20, favorite = true),
            song(2, "Power", "Kanye West", 20),
            song(3, "33 \"GOD\"", "Bon Iver", 21),
            song(4, "Rich Flex", "Drake/21 Savage", 22),
            song(5, "Who Dat Boy", "Tyler/ The Creator", 23),
            song(6, "Alpha", "Kanye West", 20),
            song(7, "Beta", "Kanye West", 20),
            credit(1, 10, "PRIMARY"),
            credit(1, 11, "FEATURED"),
            credit(2, 10, "PRIMARY"),
            // The same artist in two roles still counts once per play.
            credit(2, 10, "FEATURED"),
            credit(3, 11, "PRIMARY"),
            credit(4, 12, "PRIMARY"),
            credit(4, 13, "PRIMARY"),
            credit(5, 99, "PRIMARY", unresolved = true),
            credit(6, 10, "PRIMARY"),
            credit(7, 10, "PRIMARY"),
            "INSERT INTO playlists (id, name, created_at, updated_at, artwork_uri) VALUES (5, 'wack', 1, 2, 'file:///p/wack.jpg')",
            "INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (5, 1, 0)",
            "INSERT INTO artist_artwork (artist_key, artwork_uri, updated_at, source, provider_artist_id, matcher_version) " +
                "VALUES ('kanye west', 'https://img/kanye.jpg', 3, 'AUTO', 230, 2)",
        ).forEach(sql::execSQL)
    }

    @After
    fun close() = db.close()

    private fun capsule(month: YearMonth): SoundCapsule = runBlocking { stats.observeCapsule(month).first() }

    private fun play(
        songId: Long,
        times: Int = 1,
    ) = runBlocking {
        repeat(times) {
            stats.recordPlay(songId)
            now += 1_000L
        }
    }

    @Test
    fun `starting a song increments its monthly play count`() {
        play(1)
        assertEquals(listOf(1L to 1), capsule(october).topSongs.map { it.song.id to it.plays })
        play(1, times = 2)
        assertEquals(listOf(1L to 3), capsule(october).topSongs.map { it.song.id to it.plays })
        assertEquals(3, capsule(october).totalPlays)
    }

    @Test
    fun `a song that is not in the library records nothing`() {
        play(404)
        assertTrue(capsule(october).isEmpty)
        assertEquals(emptyList<YearMonth>(), runBlocking { stats.observeMonths().first() })
    }

    @Test
    fun `a new month uses a new month key and the previous month stays unchanged`() {
        now = at(2026, 10, 30, 12, 0)
        play(2)
        now = at(2026, 10, 31, 23, 58)
        play(1, times = 2)
        val octoberBefore = capsule(october)

        now = at(2026, 11, 1, 0, 1)
        play(3)

        assertEquals(octoberBefore, capsule(october))
        assertEquals(listOf(1L to 2, 2L to 1), capsule(october).topSongs.map { it.song.id to it.plays })
        assertEquals(2, capsule(october).longestStreakDays)
        assertEquals(listOf(3L to 1), capsule(november).topSongs.map { it.song.id to it.plays })
        assertEquals(november, stats.currentMonth())
        assertEquals(listOf(november, october), runBlocking { stats.observeMonths().first() })
    }

    @Test
    fun `the month follows the local calendar, not UTC`() {
        val kolkata = ListeningStatsRepository(db.listeningStatsDao(), { now }, { java.time.ZoneId.of("Asia/Kolkata") })
        now = at(2026, 10, 31, 20, 0) // 1:30 on November 1 in Kolkata
        runBlocking { kolkata.recordPlay(1) }
        assertTrue(capsule(october).isEmpty)
        assertEquals(1, capsule(november).totalPlays)
    }

    @Test
    fun `top five songs are sorted by plays`() {
        play(2, times = 5)
        play(1, times = 6)
        play(3, times = 1)
        play(4, times = 3)
        play(5, times = 4)
        play(6, times = 2)
        assertEquals(
            listOf(1L to 6, 2L to 5, 5L to 4, 4L to 3, 6L to 2),
            capsule(october).topSongs.map { it.song.id to it.plays },
        )
    }

    @Test
    fun `album plays are the plays of its songs`() {
        play(1, times = 2)
        play(2, times = 3)
        play(3)
        play(5, times = 4)
        assertEquals(
            listOf(20L to 5, 23L to 4, 21L to 1),
            capsule(october).topAlbums.map { it.album.id to it.plays },
        )
    }

    @Test
    fun `a play counts once for every resolved credited artist`() {
        play(1, times = 2) // Kanye West + Bon Iver
        play(2, times = 3) // Kanye West, credited twice
        play(3) // Bon Iver
        play(4) // Drake + 21 Savage
        val artists = capsule(october).topArtists.map { it.artist.name to it.plays }
        assertEquals(listOf("Kanye West" to 5, "Bon Iver" to 3), artists.take(2))
        assertEquals(setOf("Drake" to 1, "21 Savage" to 1), artists.drop(2).toSet())
    }

    @Test
    fun `unresolved credits create no artist statistics`() {
        play(5, times = 4)
        val october = capsule(october)
        assertEquals(listOf(5L to 4), october.topSongs.map { it.song.id to it.plays })
        assertEquals(listOf(23L to 4), october.topAlbums.map { it.album.id to it.plays })
        assertEquals(emptyList<String>(), october.topArtists.map { it.artist.name })
    }

    @Test
    fun `ties are ordered by the latest play, then by name`() {
        play(7, times = 2)
        play(6, times = 2) // played last: first among equals
        assertEquals(listOf(6L, 7L), capsule(october).topSongs.map { it.song.id })

        // Same count and same moment: alphabetical, then id.
        val fixed = ListeningStatsRepository(db.listeningStatsDao(), { 5L }, { ZoneOffset.UTC })
        runBlocking {
            fixed.recordPlay(2)
            fixed.recordPlay(3)
        }
        val tied = capsule(YearMonth.of(1970, 1)).topSongs.map { it.song.title }
        assertEquals(listOf("33 \"GOD\"", "Power"), tied)
        assertEquals(capsule(YearMonth.of(1970, 1)).topSongs, capsule(YearMonth.of(1970, 1)).topSongs)
    }

    @Test
    fun `listening time adds up per month`() {
        play(1)
        runBlocking {
            stats.addListeningTime(1, 90_000L)
            stats.addListeningTime(1, 30_000L)
            stats.addListeningTime(1, 0L)
            stats.addListeningTime(404, 10_000L)
        }
        assertEquals(120_000L, capsule(october).listenedMs)
        assertEquals(1, capsule(october).totalPlays)
    }

    @Test
    fun `listening without a counted play does not rank the song`() {
        runBlocking { stats.addListeningTime(2, 60_000L) }
        val october = capsule(october)
        assertTrue(october.isEmpty)
        assertEquals(60_000L, october.listenedMs)
        assertEquals(emptyList<Long>(), october.topSongs.map { it.song.id })
    }

    @Test
    fun `the streak counts consecutive listening days across the month edge`() {
        listOf(at(2026, 9, 28, 9, 0), at(2026, 9, 29, 9, 0), at(2026, 9, 30, 23, 0), at(2026, 10, 1, 8, 0)).forEach {
            now = it
            play(1)
        }
        now = at(2026, 10, 5, 9, 0)
        play(2)
        assertEquals(4, capsule(october).longestStreakDays)
        assertEquals(3, capsule(YearMonth.of(2026, 9)).longestStreakDays) // later days never change a finished month
        assertEquals(0, capsule(november).longestStreakDays)
    }

    @Test
    fun `streak runs`() {
        fun d(day: Int) = LocalDate.of(2026, 10, day)
        assertEquals(0, longestStreakTouching(emptyList(), october))
        assertEquals(1, longestStreakTouching(listOf(d(3)), october))
        assertEquals(3, longestStreakTouching(listOf(d(1), d(2), d(5), d(6), d(7), d(7)), october))
        assertEquals(0, longestStreakTouching(listOf(d(1), d(2)), november))
        val acrossTheEnd = listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2))
        assertEquals(1, longestStreakTouching(acrossTheEnd, october))
        assertEquals(3, longestStreakTouching(acrossTheEnd, november))
    }

    @Test
    fun `recording leaves songs, favorites, playlists, albums, artists and artwork unchanged`() {
        val tables =
            listOf(
                "songs",
                "albums",
                "artists",
                "song_artist_cross_ref",
                "playlists",
                "playlist_song_cross_ref",
                "artist_artwork",
                "recently_played",
            )
        val before = tables.associateWith(::dump)
        play(1, times = 3)
        play(4)
        play(5)
        runBlocking { stats.addListeningTime(1, 42_000L) }
        tables.forEach { assertEquals("table $it", before[it], dump(it)) }
        assertEquals(listOf(listOf("1")), dumpSql("SELECT id FROM songs WHERE is_favorite = 1"))
    }

    private fun dump(table: String): List<List<String?>> = dumpSql("SELECT * FROM $table")

    private fun dumpSql(sql: String): List<List<String?>> =
        db.openHelper.readableDatabase.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }.sortedBy { it.joinToString("|") }

    private companion object {
        fun at(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
        ): Long = LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

        fun album(
            id: Long,
            title: String,
            artist: String,
            artistId: Long,
        ) = "INSERT INTO albums (id, title, artist, artist_id, song_count, year, artwork_uri, is_compilation, " +
            "album_artist_inconsistent, grouping_key) VALUES ($id, '${title.sql()}', '${artist.sql()}', $artistId, 1, 2010, " +
            "'content://art/$id', 0, 0, 'dir:/$id')"

        fun artist(
            id: Long,
            name: String,
            unresolved: Boolean,
        ) = "INSERT INTO artists (id, name, song_count, album_count, artwork_uri, artist_key, is_unresolved) " +
            "VALUES ($id, '${name.sql()}', 1, 1, NULL, '${name.lowercase().sql()}', ${if (unresolved) 1 else 0})"

        fun song(
            id: Long,
            title: String,
            artist: String,
            albumId: Long,
            favorite: Boolean = false,
        ) = "INSERT INTO songs (id, title, artist, album, artist_id, album_id, duration_ms, uri, file_path, track_number, year, " +
            "date_modified, artwork_uri, is_enriched, lyrics, disc_number, composer, genre, is_favorite) " +
            "VALUES ($id, '${title.sql()}', '${artist.sql()}', 'a', 0, $albumId, 200000, 'content://s/$id', '/m/$id', $id, 2010, " +
            "1, NULL, 1, NULL, 1, NULL, NULL, ${if (favorite) 1 else 0})"

        fun credit(
            songId: Long,
            artistId: Long,
            role: String,
            unresolved: Boolean = false,
        ) = "INSERT INTO song_artist_cross_ref (song_id, artist_id, role, position, credit_text, is_unresolved) " +
            "VALUES ($songId, $artistId, '$role', 0, 'x', ${if (unresolved) 1 else 0})"

        fun String.sql() = replace("'", "''")
    }
}
