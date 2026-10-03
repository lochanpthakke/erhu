package com.lochanpthakke.empee3.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistArtworkCacheTest {
    @Test
    fun `an artist never looked up needs a lookup`() {
        assertTrue(ArtistArtworkCache.needsLookup(null))
    }

    @Test
    fun `a legacy not-found marker is retried once`() {
        // "NONE" was written by the old lookup (artist:"…" query, which missed e.g. the real Drake)
        assertTrue(ArtistArtworkCache.needsLookup("NONE"))
    }

    @Test
    fun `a not-found marker written by the current lookup is never retried`() {
        assertFalse(ArtistArtworkCache.needsLookup(ArtistArtworkCache.NOT_FOUND))
        assertEquals("NONE:2", ArtistArtworkCache.NOT_FOUND)
    }

    @Test
    fun `found artwork is never looked up again`() {
        assertFalse(ArtistArtworkCache.needsLookup("https://img/drake.jpg"))
    }

    @Test
    fun `only real urls count as artwork`() {
        assertTrue(ArtistArtworkCache.hasArtwork("https://img/drake.jpg"))
        assertFalse(ArtistArtworkCache.hasArtwork(null))
        assertFalse(ArtistArtworkCache.hasArtwork(""))
        assertFalse(ArtistArtworkCache.hasArtwork("NONE"))
        assertFalse(ArtistArtworkCache.hasArtwork(ArtistArtworkCache.NOT_FOUND))
    }
}
