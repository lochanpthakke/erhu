package com.erhu.erhu.core.network.apple

import com.erhu.erhu.core.network.apple.model.ItunesAlbumDto
import com.erhu.erhu.core.network.scorer.MetadataScorer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real iTunes data (2026-10-21): search for "$ome $exy $ongs 4 U" returns only the clean edition;
 * the explicit edition (the one with the motion cover) is only in the artist's album lookup.
 */
class CatalogEditionOrderTest {
    private val scorer = CatalogCandidateScorer(MetadataScorer())

    private val clean = album(6814097810, "cleaned")
    private val explicit = album(6814095592, "explicit")

    private fun album(
        id: Long,
        explicitness: String,
        name: String = "\$ome \$exy \$ongs 4 U",
    ) = ItunesAlbumDto(
        collectionId = id,
        artistId = 666648192,
        artistName = "PARTYNEXTDOOR & Drake",
        collectionName = name,
        trackCount = 21,
        collectionExplicitness = explicitness,
    )

    @Test
    fun `clean match is tried after its explicit edition`() {
        val order = scorer.editionOrder(clean, listOf(clean, explicit))
        assertEquals(listOf(explicit.collectionId, clean.collectionId), order.map { it.collectionId })
    }

    @Test
    fun `explicit match stays first, its clean edition is the fallback`() {
        val order = scorer.editionOrder(explicit, listOf(clean, explicit))
        assertEquals(listOf(explicit.collectionId, clean.collectionId), order.map { it.collectionId })
    }

    @Test
    fun `other albums by the artist are never taken as editions`() {
        val other = album(1, "explicit", name = "PARTYMOBILE")
        val order = scorer.editionOrder(clean, listOf(other, clean, explicit))
        assertEquals(listOf(explicit.collectionId, clean.collectionId), order.map { it.collectionId })
    }

    @Test
    fun `an edition with a different track count is not the same album`() {
        val deluxe = explicit.copy(collectionId = 2, trackCount = 25)
        val order = scorer.editionOrder(clean, listOf(clean, deluxe))
        assertEquals(listOf(clean.collectionId), order.map { it.collectionId })
    }

    @Test
    fun `without siblings only the match is tried`() {
        assertEquals(listOf(clean.collectionId), scorer.editionOrder(clean, emptyList()).map { it.collectionId })
    }
}
