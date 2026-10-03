package com.lochanpthakke.empee3.core.database.mapper

import com.lochanpthakke.empee3.core.database.entity.AlbumEntity
import com.lochanpthakke.empee3.core.database.entity.ArtistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMapperTest {
    @Test
    fun `artist key and unresolved flag reach the domain model`() {
        val artist = ArtistEntity(1, "Tyler/ The Creator", 26, 2, null, "tyler/ the creator", isUnresolved = true).toDomainModel()
        assertEquals("tyler/ the creator", artist.artistKey)
        assertTrue(artist.isUnresolved)
    }

    @Test
    fun `compilation flag reaches the domain model`() {
        val album = AlbumEntity(1, "Hits", "Various Artists", 0, 10, 2020, null, isCompilation = true).toDomainModel()
        assertTrue(album.isCompilation)
    }
}
