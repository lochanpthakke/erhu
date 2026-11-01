package com.erhu.erhu.feature.library.mix

import com.erhu.erhu.core.database.repository.TimeSource
import com.erhu.erhu.core.model.MonthlyPlays
import com.erhu.erhu.core.model.TrackFacts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioEngineTest {
    /** 60 songs over 6 artists (10 each, two albums per artist); artists 1-3 Hip-Hop, 4-5 Ambient, 6 untagged. */
    private val library =
        (1L..60L).map { id ->
            val artist = (id - 1) / 10 + 1
            val genre =
                when (artist) {
                    in 1L..3L -> "Hip-Hop"
                    in 4L..5L -> "Ambient"
                    else -> null
                }
            track(id, artist = artist, album = artist * 100 + (id % 2), genre = genre)
        }

    private fun profile(
        tracks: List<TrackFacts> = library,
        plays: List<MonthlyPlays> = emptyList(),
        hidden: Set<Long> = emptySet(),
    ) = SmartMixEngine.profile(tracks, plays, NOW, hidden)

    private fun radio(
        seed: RadioSeed,
        profile: ListeningProfile = profile(),
        similarity: Map<Long, Float>? = null,
        exclude: Set<Long> = emptySet(),
    ) = RadioEngine.build(profile, seed, similarity, exclude)!!

    private fun artistOf(id: Long) = (id - 1) / 10 + 1

    @Test
    fun aSongRadio_startsWithTheSeed_andNeverRepeatsIt() {
        val plan = radio(RadioSeed.Song(1))
        assertEquals(1L, plan.startSongId)
        assertFalse(1L in plan.songIds)
        assertEquals(plan.songIds.size, plan.songIds.toSet().size)
        assertEquals(RadioEngine.LENGTH, plan.songIds.size)
    }

    @Test
    fun withoutVybe_sharedGenresLead_andNoSoundMatchIsClaimed() {
        val plan = radio(RadioSeed.Song(1))
        // The first picks are Hip-Hop (artists 1-3), not Ambient or untagged songs.
        assertTrue(plan.songIds.take(6).all { artistOf(it) in 1L..3L })
        assertTrue(RadioSignal.GENRE in plan.basedOn)
        assertFalse(RadioSignal.SOUND in plan.basedOn)
    }

    @Test
    fun vybeSimilarity_outranksGenreAlone_whenThereIsAnIndex() {
        // VYBE says the Ambient songs of artist 4 sound closest to song 1, against its genre tag.
        val similarity = library.associate { it.songId to if (artistOf(it.songId) == 4L) 0.9f else 0.1f }
        val plan = radio(RadioSeed.Song(1), similarity = similarity)
        // Among songs by other artists, VYBE's closest come before the genre-only Hip-Hop matches.
        val others = plan.songIds.map(::artistOf).filter { it != 1L }
        assertEquals(4L, others.first())
        assertTrue(RadioSignal.SOUND in plan.basedOn)
    }

    /** 120 songs by 20 artists (three per album): room for the strict caps to fill a whole radio. */
    private val wide =
        (1L..120L).map { id ->
            val artist = (id - 1) / 6 + 1
            track(id, artist = artist, album = artist * 100 + (id % 2), genre = if (artist <= 10) "Hip-Hop" else "Jazz")
        }

    @Test
    fun picksAreSpreadOverArtistsAndAlbums() {
        val plan = radio(RadioSeed.Song(1), profile(tracks = wide))
        assertEquals(RadioEngine.LENGTH, plan.songIds.size)
        val artists = plan.songIds.map { (it - 1) / 6 + 1 }
        // Never the same artist twice in a row while another song fits; at most three per artist.
        assertTrue(artists.zipWithNext().none { (a, b) -> a == b })
        assertTrue(artists.groupingBy { it }.eachCount().values.all { it <= 3 })
        val albums = plan.songIds.map { id -> wide.first { it.songId == id }.albumId }
        assertTrue(albums.groupingBy { it }.eachCount().values.all { it <= 2 })
    }

    @Test
    fun aLibraryOfFewArtists_stillFillsTheRadio_relaxingTheCaps() {
        // Six artists: strict caps allow 18 songs; the rest come under looser caps, still alternating.
        val plan = radio(RadioSeed.Song(1))
        assertEquals(RadioEngine.LENGTH, plan.songIds.size)
        assertTrue(plan.songIds.map(::artistOf).zipWithNext().none { (a, b) -> a == b })
    }

    @Test
    fun anArtistRadio_isMostlyThatArtist_andStartsWithTheirMostPlayedSong() {
        val p = profile(plays = listOf(played(17, 9, daysAgo = 3), played(12, 2, daysAgo = 3)))
        val plan = radio(RadioSeed.Artist(2), p)
        assertEquals(17L, plan.startSongId)
        val ownSongs = plan.songIds.count { artistOf(it) == 2L }
        // More than the usual three per artist, but within a third of the radio.
        assertTrue(ownSongs in 4..RadioEngine.LENGTH / 3)
        assertTrue(RadioSignal.SAME_ARTIST in plan.basedOn)
    }

    @Test
    fun anAlbumRadio_reachesBeyondTheAlbum() {
        val plan = radio(RadioSeed.Album(100 + 1))
        val albumSongs = library.filter { it.albumId == 101L }.map { it.songId }.toSet()
        assertTrue(plan.startSongId in albumSongs)
        assertTrue(plan.songIds.count { it in albumSongs } <= 2)
    }

    @Test
    fun hiddenSongs_areNeverPicked() {
        val hidden = (2L..10L).toSet()
        val plan = radio(RadioSeed.Song(1), profile(hidden = hidden))
        assertTrue(plan.songIds.none { it in hidden })
    }

    @Test
    fun refreshing_avoidsEarlierPicks_whileEnoughOtherSongsRemain() {
        val first = radio(RadioSeed.Song(1))
        val second = radio(RadioSeed.Song(1), exclude = first.songIds.toSet())
        assertTrue(second.songIds.none { it in first.songIds })
        assertTrue(second.songIds.isNotEmpty())
    }

    @Test
    fun refreshingASmallLibrary_fallsBackToEarlierPicks() {
        val small = library.take(8)
        val p = profile(tracks = small)
        val first = radio(RadioSeed.Song(1), p)
        val second = radio(RadioSeed.Song(1), p, exclude = first.songIds.toSet())
        assertEquals(first.songIds.toSet(), second.songIds.toSet())
    }

    @Test
    fun aTinyLibrary_stillGivesARadio() {
        val p = profile(tracks = library.take(3))
        val plan = radio(RadioSeed.Song(1), p)
        assertEquals(listOf(2L, 3L), plan.songIds.sorted())
    }

    @Test
    fun songsWithoutAnyMetadata_stillPlay_butNoSignalIsInvented() {
        val bare = (1L..6L).map { track(it, artist = it) }
        val plan = radio(RadioSeed.Song(1), profile(tracks = bare))
        assertEquals(5, plan.songIds.size)
        assertTrue(plan.basedOn.isEmpty())
    }

    @Test
    fun outOfRangeBpm_isNotTrustedAsTempo() {
        // Seed at 140 BPM; others tagged 280 and 35 (outside the trusted 70-175 range) and 138.
        val tracks =
            listOf(
                track(1, artist = 1, bpm = 140),
                track(2, artist = 2, bpm = 280),
                track(3, artist = 3, bpm = 138),
                track(4, artist = 4, bpm = 35),
            )
        val plan = radio(RadioSeed.Song(1), profile(tracks = tracks))
        assertEquals(3L, plan.songIds.first())
        assertEquals(listOf(RadioSignal.TEMPO), plan.basedOn)
        // The implausible tags score nothing for tempo; they are ordered only by the fixed tie-break.
        assertTrue(2L in plan.songIds && 4L in plan.songIds)
    }

    @Test
    fun familiarSongs_outrankUnheardOnes_whenOtherwiseEqual() {
        val tracks = (1L..4L).map { track(it, artist = it, genre = "Jazz") }
        val plan = radio(RadioSeed.Song(1), profile(tracks = tracks, plays = listOf(played(3, 5, daysAgo = 10))))
        assertEquals(3L, plan.songIds.first())
        assertTrue(RadioSignal.LISTENING in plan.basedOn)
    }

    @Test
    fun theSameInputs_giveTheSameRadio() {
        assertEquals(radio(RadioSeed.Song(5)), radio(RadioSeed.Song(5)))
    }

    @Test
    fun anUnknownSeed_givesNoRadio() {
        assertNull(RadioEngine.build(profile(), RadioSeed.Song(999), null))
        assertNull(RadioEngine.build(profile(), RadioSeed.Artist(999), null))
    }

    @Test
    fun theSource_asksVybeAboutTheSeedSongs_andCopesWhenItFails() =
        runBlocking {
            var asked: Map<Long, Float>? = null
            val source =
                RadioSource(
                    tracks = { library },
                    plays = { emptyList() },
                    hidden = { emptySet() },
                    clock = TimeSource { NOW },
                    taste =
                        TasteNeighbours { seeds, _ ->
                            asked = seeds
                            error("index unreadable")
                        },
                )
            val plan = source.build(RadioSeed.Album(201))
            assertNotNull(plan)
            assertEquals(library.filter { it.albumId == 201L }.map { it.songId }.toSet(), asked?.keys)
            assertFalse(RadioSignal.SOUND in plan!!.basedOn)
        }
}
