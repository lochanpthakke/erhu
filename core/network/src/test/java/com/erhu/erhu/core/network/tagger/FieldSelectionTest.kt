package com.erhu.erhu.core.network.tagger

import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.MetadataField
import com.erhu.erhu.core.network.repository.MatchDecision
import com.erhu.erhu.core.network.repository.TrackIdentity
import com.erhu.erhu.core.network.repository.decide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The merged field selection (Lochan's multi-select) on top of the field-by-field planner. */
class FieldSelectionTest {
    @Test
    fun storedSelections_parse_includingTheOldSingleActionNames() {
        assertEquals(MetadataField.all, MetadataField.fromStored(""))
        assertEquals(MetadataField.all, MetadataField.fromStored("ALL_FIELDS"))
        assertEquals(setOf(MetadataField.GENRE), MetadataField.fromStored("GENRE_ONLY"))
        assertEquals(setOf(MetadataField.YEAR), MetadataField.fromStored("YEAR_ONLY"))
        assertEquals(setOf(MetadataField.TITLE, MetadataField.FILENAME), MetadataField.fromStored("TITLE,FILENAME"))
        assertEquals(setOf(MetadataField.GENRE, MetadataField.BPM), MetadataField.fromStored("GENRE,BPM"))
        assertEquals(setOf(MetadataField.BPM), MetadataField.fromStored("BPM_ONLY"))
        assertEquals(MetadataField.all, MetadataField.fromStored("NONSENSE"))
        val stored = MetadataField.toStored(setOf(MetadataField.FILENAME, MetadataField.ALBUM))
        assertEquals(setOf(MetadataField.ALBUM, MetadataField.FILENAME), MetadataField.fromStored(stored))
        assertTrue(MetadataField.BPM in MetadataField.supported)
    }

    @Test
    fun everyTagField_isAllFields_fewerReplaceExactlyThose() {
        val all = fieldSelection(MetadataField.all)
        assertTrue(all.fillOnly)
        assertTrue(all.rename)
        assertEquals(FixField.entries.filter { it != FixField.ARTWORK }.toSet(), all.fields)

        val tagsOnly = fieldSelection(MetadataField.supportedTags)
        assertTrue("all tag fields, no rename", tagsOnly.fillOnly && !tagsOnly.rename)

        // BPM on or off never changes whether the other fields are filled or replaced, so a
        // selection saved before BPM was offered keeps its meaning.
        val savedBeforeBpm = MetadataField.fromStored("TITLE,ARTIST,ALBUM,YEAR,GENRE,ARTWORK,FILENAME")
        assertTrue(fieldSelection(savedBeforeBpm).fillOnly)
        assertFalse(FixField.BPM in fieldSelection(savedBeforeBpm).fields)
        val bpmOnly = fieldSelection(setOf(MetadataField.BPM))
        assertEquals(setOf(FixField.BPM), bpmOnly.fields)
        assertFalse(bpmOnly.fillOnly)

        val two = fieldSelection(setOf(MetadataField.GENRE, MetadataField.YEAR))
        assertFalse(two.fillOnly)
        assertEquals(setOf(FixField.GENRE, FixField.YEAR), two.fields)

        val renameOnly = fieldSelection(setOf(MetadataField.FILENAME))
        assertTrue(renameOnly.rename)
        assertTrue(renameOnly.fields.isEmpty())
    }

    @Test
    fun aPartialSelection_changesOnlyItsFields() {
        val local = LocalTrackTags("old title", "Unknown Artist", "Unknown Album", year = "1970")
        val match = MetadataCandidate("1", "Runaway", "Kanye West", "My Beautiful Dark Twisted Fantasy", sourceName = "MusicBrainz")
        val selection = fieldSelection(setOf(MetadataField.ALBUM, MetadataField.GENRE))
        val request =
            planFieldFix("content://1", "/a.mp3", selection.fields, selection.fillOnly, local, match, "hip hop", byteArrayOf(1), null).request!!
        assertEquals("My Beautiful Dark Twisted Fantasy", request.album)
        assertEquals("hip hop", request.genre)
        assertNull(request.title)
        assertNull(request.artist)
        assertNull(request.artworkBytes)
    }

    @Test
    fun theFilesFeaturingNote_isKept_whenTheOnlineTitleHasNone() {
        assertEquals(
            "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)",
            withLocalFeaturing("911 / Mr. Lonely", "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)"),
        )
        assertEquals("Runaway (feat. Pusha T)", withLocalFeaturing("Runaway (feat. Pusha T)", "Runaway (ft. Someone)"))
        assertEquals("Runaway", withLocalFeaturing("Runaway", "Runaway"))

        // So a fix doesn't drop the note from the title.
        val local = LocalTrackTags("911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)", "Tyler", "Flower Boy")
        val match = MetadataCandidate("1", "911 / Mr. Lonely", "Tyler", "Flower Boy", sourceName = "MusicBrainz")
        val plan = planFieldFix("content://1", "/a.mp3", setOf(FixField.TITLE), false, local, match, null, null, null)
        assertNull("the title already matches", plan.request)
    }

    @Test
    fun theUsersThreshold_decidesWhatIsWritten() {
        val identity = TrackIdentity("Song", "Artist", null, null)
        val ranked = listOf(MetadataCandidate("1", "Song", "Artist", "", confidenceScore = 0.80f))
        assertTrue(decide(ranked, identity) is MatchDecision.LowConfidence)
        assertTrue(decide(ranked, identity, acceptAt = 0.75f) is MatchDecision.Confident)
        assertTrue("never below the floor", decide(listOf(ranked[0].copy(confidenceScore = 0.4f)), identity, 0.5f) is MatchDecision.NoMatch)
    }
}
