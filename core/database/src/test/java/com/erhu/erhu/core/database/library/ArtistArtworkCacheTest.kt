package com.erhu.erhu.core.database.library

import com.erhu.erhu.core.database.entity.ArtistArtworkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistArtworkCacheTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun auto(
        uri: String,
        version: Int = ArtistArtworkCache.MATCHER_VERSION,
        updatedAt: Long = now,
    ) = ArtistArtworkEntity("x", uri, updatedAt, ArtistArtworkEntity.SOURCE_AUTO, null, version)

    private fun needs(stored: ArtistArtworkEntity?) = ArtistArtworkCache.needsAutomaticLookup(stored, now)

    @Test
    fun `an artist never looked up needs a lookup`() {
        assertTrue(needs(null))
    }

    @Test
    fun `everything decided by an older matcher is re-checked once`() {
        // Legacy markers and urls from the first-exact-name matcher (wrong duplicates, blank photos).
        assertTrue(needs(auto("NONE", version = 0)))
        assertTrue(needs(auto("NONE:2", version = 0)))
        assertTrue(needs(auto("https://img/wrong-offset.jpg", version = 0)))
        assertTrue(needs(auto("https://cdn-images.dzcdn.net/images/artist//500x500-000000-80-0-0.jpg", version = 1)))
    }

    @Test
    fun `found artwork of the current matcher is not looked up again`() {
        assertFalse(needs(auto("https://img/drake.jpg")))
        assertFalse(needs(auto("https://img/drake.jpg", updatedAt = now - 365 * day)))
    }

    @Test
    fun `the current not-found marker is retried after about thirty days`() {
        assertEquals("NONE:3", ArtistArtworkCache.NOT_FOUND)
        assertFalse(needs(auto(ArtistArtworkCache.NOT_FOUND, updatedAt = now - 29 * day)))
        assertTrue(needs(auto(ArtistArtworkCache.NOT_FOUND, updatedAt = now - 30 * day)))
        // A clock that moved backwards does not freeze the retry.
        assertTrue(needs(auto(ArtistArtworkCache.NOT_FOUND, updatedAt = now + day)))
    }

    @Test
    fun `custom artwork is never looked up`() {
        val custom = ArtistArtworkEntity("x", "file:///data/artist_artwork/a.jpg", 0, ArtistArtworkEntity.SOURCE_CUSTOM, null, 0)
        assertFalse(needs(custom))
        assertFalse(ArtistArtworkCache.needsAutomaticLookup(custom.copy(artworkUri = "NONE:3"), Long.MAX_VALUE))
        // An unknown source is not automatic artwork either.
        assertFalse(needs(custom.copy(source = "SOMETHING")))
    }

    @Test
    fun `only real urls count as artwork`() {
        assertTrue(ArtistArtworkCache.hasArtwork("https://img/drake.jpg"))
        assertTrue(ArtistArtworkCache.hasArtwork("file:///data/artist_artwork/a.jpg"))
        assertFalse(ArtistArtworkCache.hasArtwork(null))
        assertFalse(ArtistArtworkCache.hasArtwork(""))
        assertFalse(ArtistArtworkCache.hasArtwork("NONE"))
        assertFalse(ArtistArtworkCache.hasArtwork("NONE:2"))
        assertFalse(ArtistArtworkCache.hasArtwork(ArtistArtworkCache.NOT_FOUND))
    }
}
