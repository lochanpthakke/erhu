package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataFixAction
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Which tags a metadata fix may write, per action, and that nothing valid is overwritten with less. */
class FieldFixPlanTest {
    private val match =
        MetadataCandidate(
            id = "mb_1",
            title = "Runaway",
            artist = "Kanye West",
            album = "My Beautiful Dark Twisted Fantasy",
            sourceName = "MusicBrainz",
        )
    private val messy = LocalTrackTags(title = "runaway_spotidownloader.com", artist = "Unknown Artist", album = "Unknown Album")
    private val cover = byteArrayOf(1, 2, 3)
    private val today = LocalDate.of(2026, 10, 13)

    private fun date(raw: String) = requireNotNull(ReleaseDate.parse(raw, today))

    private fun plan(
        action: MetadataFixAction,
        local: LocalTrackTags = messy,
        candidate: MetadataCandidate = match,
        genre: String? = "hip hop",
        artwork: ByteArray? = cover,
        released: ReleaseDate? = date("2010-11-19"),
    ) = planFieldFix("content://media/1", "/music/a.mp3", action, local, candidate, genre, artwork, released)

    @Test
    fun allFields_fillsEverythingThatIsMissing() {
        val request = plan(MetadataFixAction.ALL_FIELDS).request!!
        assertEquals("Runaway", request.title)
        assertEquals("Kanye West", request.artist)
        assertEquals("My Beautiful Dark Twisted Fantasy", request.album)
        assertEquals("hip hop", request.genre)
        assertArrayEquals(cover, request.artworkBytes)
        assertEquals("2010-11-19", request.releaseDate)
        assertNull("BPM is never written", request.bpm)
        assertNull(request.year)
        assertNull(request.lyrics)
    }

    @Test
    fun singleFieldActions_changeOnlyTheirField() {
        val expected =
            mapOf<MetadataFixAction, (TagWriteRequest) -> Any?>(
                MetadataFixAction.TITLE_ONLY to { it.title },
                MetadataFixAction.ARTIST_ONLY to { it.artist },
                MetadataFixAction.ALBUM_ONLY to { it.album },
                MetadataFixAction.YEAR_ONLY to { it.releaseDate },
                MetadataFixAction.GENRE_ONLY to { it.genre },
                MetadataFixAction.ARTWORK_ONLY to { it.artworkBytes },
            )
        for ((action, field) in expected) {
            val request = plan(action).request!!
            val set =
                listOfNotNull(
                    request.title,
                    request.artist,
                    request.album,
                    request.releaseDate,
                    request.genre,
                    request.artworkBytes,
                    request.bpm,
                    request.year,
                    request.lyrics,
                )
            assertEquals("$action should set exactly one field", 1, set.size)
            assertTrue("$action set the wrong field", field(request) != null)
        }
    }

    @Test
    fun allFields_keepsAValidLocalAlbumGenreAndCover() {
        val tidy =
            LocalTrackTags(
                title = "Runaway",
                artist = "Kanye West",
                album = "MBDTF (Deluxe)",
                genre = "Hip-Hop",
                hasArtwork = true,
                year = "2010",
            )
        val result = plan(MetadataFixAction.ALL_FIELDS, local = tidy)
        assertNull("nothing to change", result.request)
        assertTrue(result.notes.any { it.contains("MBDTF (Deluxe)") })
        assertTrue(result.notes.contains("Kept the existing artwork"))
    }

    @Test
    fun explicitActions_replaceValidValues() {
        val tidy =
            LocalTrackTags(title = "Runaway", artist = "Kanye West", album = "MBDTF (Deluxe)", genre = "Rap", hasArtwork = true, year = "2008")
        assertEquals("My Beautiful Dark Twisted Fantasy", plan(MetadataFixAction.ALBUM_ONLY, local = tidy).request?.album)
        assertEquals("2010-11-19", plan(MetadataFixAction.YEAR_ONLY, local = tidy).request?.releaseDate)
        assertEquals("hip hop", plan(MetadataFixAction.GENRE_ONLY, local = tidy).request?.genre)
        assertArrayEquals(cover, plan(MetadataFixAction.ARTWORK_ONLY, local = tidy).request?.artworkBytes)
    }

    @Test
    fun missingOrPlaceholderProviderValues_areNeverWritten() {
        val empty = match.copy(title = " ", artist = "Unknown Artist", album = "")
        val result = plan(MetadataFixAction.ALL_FIELDS, candidate = empty, genre = null, artwork = null, released = null)
        assertNull(result.request)
        assertTrue(result.notes.contains("No title found online"))
        assertTrue(result.notes.contains("No artist found online"))
        assertTrue(result.notes.contains("No album found online"))
        assertTrue(result.notes.contains("No genre found online"))
        assertTrue(result.notes.contains("No artwork found online"))
        assertTrue(result.notes.contains("No release year found online"))
    }

    @Test
    fun allFields_keepsAValidLocalArtist_evenWhenTheMatchCreditsMore() {
        // MusicBrainz credits Runaway to "Kanye West feat. Pusha T": not a reason to change the artist.
        val local = messy.copy(artist = "Kanye West")
        val fuller = match.copy(artist = "Kanye West feat. Pusha T")
        assertNull(plan(MetadataFixAction.ALL_FIELDS, local = local, candidate = fuller).request?.artist)
        assertEquals("Kanye West feat. Pusha T", plan(MetadataFixAction.ARTIST_ONLY, local = local, candidate = fuller).request?.artist)
    }

    @Test
    fun aFullerArtistCredit_withFeaturedArtists_isKept() {
        val local = messy.copy(artist = "Kanye West feat. Pusha T")
        assertNull(plan(MetadataFixAction.ARTIST_ONLY, local = local).request)
        assertNull(plan(MetadataFixAction.ALL_FIELDS, local = local).request?.artist)
    }

    @Test
    fun unreadableTags_neverGetGenreOrCoverFilledByAllFields() {
        val unknown = messy.copy(tagsReadable = false)
        val request = plan(MetadataFixAction.ALL_FIELDS, local = unknown).request!!
        assertNull(request.genre)
        assertNull(request.artworkBytes)
        assertNull(request.releaseDate)
        assertEquals("Runaway", request.title)
    }

    @Test
    fun unchangedValues_produceNoRequest() {
        val same =
            LocalTrackTags("Runaway", "Kanye West", "My Beautiful Dark Twisted Fantasy", genre = "hip hop", hasArtwork = true, year = "2010-11-19")
        assertNull(plan(MetadataFixAction.ALL_FIELDS, local = same).request)
        assertNull(plan(MetadataFixAction.TITLE_ONLY, local = same).request)
    }

    // ---- year

    @Test
    fun allFields_fillsAnEmptyYear_withTheFullDate() {
        for (empty in listOf(null, "", "  ", "0")) {
            assertEquals("'$empty'", "2010-11-19", plan(MetadataFixAction.ALL_FIELDS, local = messy.copy(year = empty)).request?.releaseDate)
        }
    }

    @Test
    fun allFields_replacesThe1970Placeholder() {
        val result = plan(MetadataFixAction.ALL_FIELDS, local = messy.copy(year = "1970"))
        assertEquals("2010-11-19", result.request?.releaseDate)
        assertEquals(FieldFixPlan.Change(FixField.YEAR, "1970", "2010-11-19"), result.changes.single { it.field == FixField.YEAR })
    }

    @Test
    fun allFields_keepsAnyOtherValidYear() {
        val result = plan(MetadataFixAction.ALL_FIELDS, local = messy.copy(year = "2008"))
        assertNull(result.request?.releaseDate)
        assertTrue(result.notes.contains("Kept the existing year \"2008\""))
        // The same year, less precise, is kept silently.
        assertNull(plan(MetadataFixAction.ALL_FIELDS, local = messy.copy(year = "2010")).request?.releaseDate)
    }

    @Test
    fun yearOnly_replacesAValidYear_andTouchesNothingElse() {
        val local = messy.copy(year = "2008", genre = "Rap", hasArtwork = false)
        val request = plan(MetadataFixAction.YEAR_ONLY, local = local).request!!
        assertEquals("2010-11-19", request.releaseDate)
        assertEquals(TagWriteRequest("content://media/1", "/music/a.mp3", releaseDate = "2010-11-19"), request)
    }

    @Test
    fun yearOnly_withoutAReliableDate_writesNothing() {
        val result = plan(MetadataFixAction.YEAR_ONLY, local = messy.copy(year = "1970"), released = null)
        assertNull(result.request)
        assertEquals(listOf("No release year found online"), result.notes)
    }

    @Test
    fun aMonthWithoutADay_isSavedAsTheYear_whenTheTagFormatCantHoldIt() {
        val id3v23 = messy.copy(year = "1970", canStoreYearMonth = false)
        val result = plan(MetadataFixAction.YEAR_ONLY, local = id3v23, released = date("2010-11"))
        assertEquals("2010", result.request?.releaseDate)
        assertTrue(result.notes.single().startsWith("Saved the year only"))
        // A full date fits ID3v2.3 (TYER + TDAT), and other formats keep the month.
        assertEquals("2010-11-19", plan(MetadataFixAction.YEAR_ONLY, local = id3v23).request?.releaseDate)
        assertEquals("2010-11", plan(MetadataFixAction.YEAR_ONLY, local = messy, released = date("2010-11")).request?.releaseDate)
    }

    @Test
    fun aMorePreciseExistingDate_isNeverReplacedByAVaguerOne() {
        val result = plan(MetadataFixAction.YEAR_ONLY, local = messy.copy(year = "2010-11-19"), released = date("2010"))
        assertNull(result.request)
        assertTrue(result.notes.single().contains("more precise"))
    }

    @Test
    fun unsupportedActions_writeNothing_andAreNotOffered() {
        assertNull(plan(MetadataFixAction.BPM_ONLY, candidate = match.copy(bpm = 120)).request)
        assertNull(plan(MetadataFixAction.LYRICS_ONLY).request)
        assertFalse(MetadataFixAction.BPM_ONLY in MetadataFixAction.supported)
        assertFalse(MetadataFixAction.LYRICS_ONLY in MetadataFixAction.supported)
    }

    @Test
    fun storedActions_fallBackToAllFields_whenUnknownOrUnsupported() {
        assertEquals(MetadataFixAction.GENRE_ONLY, MetadataFixAction.fromStored("GENRE_ONLY"))
        assertEquals(MetadataFixAction.YEAR_ONLY, MetadataFixAction.fromStored("YEAR_ONLY"))
        assertTrue(MetadataFixAction.YEAR_ONLY in MetadataFixAction.supported)
        assertEquals(MetadataFixAction.ALL_FIELDS, MetadataFixAction.fromStored("BPM_ONLY"))
        assertEquals(MetadataFixAction.ALL_FIELDS, MetadataFixAction.fromStored(""))
        assertEquals(MetadataFixAction.ALL_FIELDS, MetadataFixAction.fromStored(null))
    }

    @Test
    fun changes_listOldAndNewValues() {
        val result = plan(MetadataFixAction.ALL_FIELDS)
        val title = result.changes.single { it.field == FixField.TITLE }
        assertEquals("runaway_spotidownloader.com", title.oldValue)
        assertEquals("Runaway", title.newValue)
        assertNull("a placeholder is shown as empty", result.changes.single { it.field == FixField.ARTIST }.oldValue)
    }
}
