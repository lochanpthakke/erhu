package com.erhu.erhu.core.database.scanner

import com.erhu.erhu.core.database.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncPlanTest {
    private fun entity(
        id: Long,
        dateModified: Long = 100L,
        filePath: String = "/music/$id.mp3",
        title: String = "Song $id",
    ) = SongEntity(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        artistId = 1,
        albumId = 1,
        durationMs = 180_000,
        uri = "content://media/external/audio/media/$id",
        filePath = filePath,
        trackNumber = 1,
        year = 2020,
        dateModified = dateModified,
    )

    @Test
    fun rescan_preservesFavorites() {
        val existing = listOf(entity(1).copy(isFavorite = true), entity(2))
        val plan = planLibrarySync(scanned = listOf(entity(1), entity(2)), existing = existing)
        assertTrue(plan.upserts.first { it.id == 1L }.isFavorite)
        assertFalse(plan.upserts.first { it.id == 2L }.isFavorite)
    }

    @Test
    fun rescan_unchangedFile_keepsEnrichment() {
        val enriched = entity(1).copy(isEnriched = true, lyrics = "la la", genre = "Pop; Synth-pop", discNumber = 2, bpm = 78)
        val plan = planLibrarySync(scanned = listOf(entity(1)), existing = listOf(enriched))
        val result = plan.upserts.single()
        assertTrue(result.isEnriched)
        assertEquals("la la", result.lyrics)
        assertEquals("Pop; Synth-pop", result.genre)
        assertEquals(78, result.bpm)
        assertEquals(2, result.discNumber)
    }

    @Test
    fun rescan_modifiedFile_isReEnrichedButKeepsFavorite() {
        val old = entity(1, dateModified = 100).copy(isEnriched = true, lyrics = "old", isFavorite = true, bpm = 156)
        val plan = planLibrarySync(scanned = listOf(entity(1, dateModified = 200, title = "New title")), existing = listOf(old))
        val result = plan.upserts.single()
        assertFalse(result.isEnriched)
        assertNull(result.lyrics)
        assertNull("re-read from the file's tags", result.bpm)
        assertTrue(result.isFavorite)
        assertEquals("New title", result.title)
    }

    @Test
    fun rescan_removesSongsNoLongerOnDevice() {
        val plan = planLibrarySync(scanned = listOf(entity(1)), existing = listOf(entity(1), entity(2), entity(3)))
        assertEquals(setOf(2L, 3L), plan.deletedIds.toSet())
    }

    @Test
    fun rescan_withNoSongsLeft_deletesEverything() {
        val plan = planLibrarySync(scanned = emptyList(), existing = listOf(entity(1), entity(2)))
        assertTrue(plan.upserts.isEmpty())
        assertEquals(setOf(1L, 2L), plan.deletedIds.toSet())
    }

    @Test
    fun rescan_isIdempotent_noDuplicates() {
        val first = planLibrarySync(scanned = listOf(entity(1), entity(2)), existing = emptyList())
        val second = planLibrarySync(scanned = listOf(entity(1), entity(2)), existing = first.upserts)
        assertEquals(listOf(1L, 2L), second.upserts.map { it.id })
        assertTrue(second.deletedIds.isEmpty())
    }

    @Test
    fun whatsAppAudio_isExcluded() {
        assertTrue(isExcludedFromLibrary("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20231108-WA0001.opus"))
        assertTrue(isExcludedFromLibrary("/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/202340/PTT-1.opus"))
        assertTrue(isExcludedFromLibrary("/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio/x.mp3"))
    }

    @Test
    fun regularMusic_isNotExcluded() {
        assertFalse(isExcludedFromLibrary("/storage/emulated/0/Music/Kanye West/BULLY/Beauty And The Beast.mp3"))
        assertFalse(isExcludedFromLibrary("/storage/emulated/0/Download/whatsapp_song_cover.mp3"))
        // Songs shared as documents are real music.
        assertFalse(isExcludedFromLibrary("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/Children_Bootleg-1.mp3"))
    }
}
