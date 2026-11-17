package com.erhu.erhu.core.database.scanner

import com.erhu.erhu.core.database.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateSongsTest {
    private fun song(
        id: Long,
        title: String = "Levitating",
        artist: String = "Dua Lipa",
        album: String = "Future Nostalgia",
        durationMs: Long = 203_000,
    ) = SongEntity(
        id = id, title = title, artist = artist, album = album, artistId = 0, albumId = 0, durationMs = durationMs,
        uri = "content://$id", filePath = "/m/$id.mp3", trackNumber = 1, year = 0, dateModified = 0,
    )

    @Test
    fun aCopyOfTheSameTrack_isListedOnce_keepingTheOneAlreadyInTheLibrary() {
        val kept = duplicateFreeIds(listOf(song(1), song(2, title = "levitating ", durationMs = 204_000)), known = setOf(2))
        assertEquals(setOf(2L), kept)
    }

    @Test
    fun otherVersions_andUntaggedSongs_allStay() {
        val songs =
            listOf(
                song(1),
                song(2, album = "Levitating (single)"),
                song(3, durationMs = 260_000),
                song(4, title = "Unknown Title", artist = "Unknown Artist", album = "Unknown Album"),
                song(5, title = "Unknown Title", artist = "Unknown Artist", album = "Unknown Album"),
            )
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), duplicateFreeIds(songs, known = emptySet()))
    }
}
