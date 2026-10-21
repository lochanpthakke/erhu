package com.lochanpthakke.empee3.feature.library.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixArtTest {
    @Test
    fun aMixAlwaysGetsTheSameArt() {
        MixKind.entries.forEach { kind ->
            assertEquals(kind.title, MixArt.generate(kind), MixArt.generate(kind))
        }
        // The seed is a fixed function of the key (no per-process hashing).
        assertEquals(MixArt.seedOf("chill"), MixArt.seedOf(String(charArrayOf('c', 'h', 'i', 'l', 'l'))))
        assertTrue(MixArt.seedOf("chill") != MixArt.seedOf("late-night"))
    }

    @Test
    fun mixesLookDifferent_inPaletteAndComposition() {
        val arts = MixKind.entries.associateWith { MixArt.generate(it) }
        assertTrue("palettes used: ${arts.values.map { it.palette.name }.toSet()}", arts.values.map { it.palette }.toSet().size >= 6)
        assertTrue(arts.values.map { it.composition }.toSet().size >= 5)
        // Not all dark: the covers are colourful, and some are atmospheric.
        assertTrue(arts.values.count { !it.palette.dark } >= 4)
        assertTrue(arts.values.any { it.palette.dark })
        // Each kind keeps to its art direction.
        arts.forEach { (kind, art) ->
            val direction = MixArt.direction(kind)
            assertTrue(art.palette in direction.palettes && art.composition in direction.compositions)
        }
    }

    @Test
    fun differentSeeds_vary_shapesNotJustColours() {
        val direction = MixArt.Direction(listOf(Palettes.MINT), listOf(MixArt.Composition.BLOBS))
        val a = MixArt.generate(1L, direction)
        val b = MixArt.generate(2L, direction)
        assertEquals(a.palette, b.palette)
        assertTrue(a.layers != b.layers)
    }

    @Test
    fun everyCompositionDrawsWithinTheCover_andIsOnlyShapes() {
        MixArt.Composition.entries.forEach { composition ->
            Palettes.ALL.forEach { palette ->
                val art = MixArt.generate(composition.ordinal * 31L + palette.name.length, MixArt.Direction(listOf(palette), listOf(composition)))
                assertTrue(art.layers.first() is ArtLayer.Backdrop)
                assertTrue(art.layers.size > 3)
                art.layers.filterIsInstance<ArtLayer.Disc>().forEach { assertTrue(it.radius in 0f..0.5f && it.alpha in 0f..1f) }
            }
        }
    }

    @Test
    fun thePaletteRangeSpansWarmFreshAndDark() {
        val names = Palettes.ALL.map { it.name }
        listOf("Sunrise", "Mint", "Lagoon", "Sky", "Bubblegum", "Midnight", "Burgundy").forEach { assertTrue(it in names) }
    }
}
