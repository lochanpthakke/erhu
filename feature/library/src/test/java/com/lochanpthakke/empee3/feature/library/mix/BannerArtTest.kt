package com.lochanpthakke.empee3.feature.library.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerArtTest {
    private val aspect = 1.9f

    /** How far left (as a share of the width) a layer reaches, or null for layers allowed anywhere. */
    private fun leftEdge(layer: ArtLayer): Float? =
        when (layer) {
            is ArtLayer.Backdrop, is ArtLayer.Glow -> null
            is ArtLayer.Disc -> layer.x - layer.radius / aspect
            is ArtLayer.Blob -> layer.x - layer.radius * (layer.wobble.maxOrNull() ?: 1f) / aspect
            is ArtLayer.Ring -> layer.x - layer.radius * (layer.wobble.maxOrNull() ?: 1f) / aspect
            is ArtLayer.Ribbon -> layer.points.chunked(2).minOf { it[0] }
            is ArtLayer.Shard -> layer.points.chunked(2).minOf { it[0] }
            is ArtLayer.Block -> layer.left
            is ArtLayer.Sector -> layer.x - layer.radius / aspect
        }

    @Test
    fun theTextSideCarriesOnlyTheBackdropAndSoftLight_forEveryConcept() {
        val limit = MixArt.BANNER_TEXT_SIDE * 0.85f - 0.05f
        for (kind in MixKind.entries) {
            val art = MixArt.banner(kind, aspect)
            // Particle blobs are faint (at most 0.35 alpha) haze: allowed to soften the edge.
            val solid = art.layers.filterNot { it is ArtLayer.Blob && it.alpha <= 0.35f }
            solid.forEach { layer ->
                val edge = leftEdge(layer) ?: return@forEach
                assertTrue("${kind.key}: $layer reaches $edge", edge >= limit)
            }
        }
    }

    @Test
    fun aBannerSharesItsCoversPaletteAndComposition() {
        for (kind in MixKind.entries) {
            val cover = MixArt.generate(kind)
            val banner = MixArt.banner(kind, aspect)
            assertEquals(cover.palette, banner.palette)
            assertEquals(cover.composition, banner.composition)
        }
    }

    @Test
    fun theSameConceptAlwaysGetsTheSameBanner() {
        for (kind in MixKind.entries) assertEquals(MixArt.banner(kind, aspect), MixArt.banner(kind, aspect))
    }
}
