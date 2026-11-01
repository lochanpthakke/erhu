package com.erhu.erhu.core.database.scanner

import com.erhu.erhu.core.database.entity.SongEntity
import com.erhu.erhu.core.database.library.LibraryModelBuilder
import com.erhu.erhu.core.database.library.RawTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryWriterMappingTest {
    private fun song(
        id: Long,
        track: Int,
        year: Int,
    ) = SongEntity(
        id = id,
        title = "t$id",
        artist = "a",
        album = "Her Loss",
        artistId = 0,
        albumId = 0,
        durationMs = 1,
        uri = "u$id",
        filePath = "/m/$id.mp3",
        trackNumber = track,
        year = year,
        dateModified = 1,
        artworkUri = "art$id",
    )

    @Test
    fun `songs get model album and artist ids, albums take year and artwork from their first track`() {
        val raw =
            listOf(
                RawTrack(1, "Rich Flex", "Drake/21 Savage", null, "Her Loss", 1, 1, "/m", false),
                RawTrack(2, "3AM on Glenwood", "21 Savage", null, "Her Loss", 15, 1, "/m", false),
                RawTrack(3, "BackOutsideBoyz", "Drake", null, "Her Loss", 4, 1, "/m", false),
            )
        val model = LibraryModelBuilder.build(raw, emptyMap())
        val entities = model.toEntities(listOf(song(1, 1, 2022), song(2, 15, 2021), song(3, 4, 2022)), mapOf("drake" to "https://img/d.jpg"))
        assertEquals(1, entities.albums.size)
        assertEquals("art1", entities.albums[0].artworkUri)
        assertEquals(2022, entities.albums[0].year)
        assertEquals(setOf(entities.albums[0].id), entities.songs.map { it.albumId }.toSet())
        assertEquals("https://img/d.jpg", entities.artists.first { it.artistKey == "drake" }.artworkUri)
        assertEquals("Drake", entities.crossRefs.first { it.songId == 1L && it.position == 0 }.creditText)
    }
}
