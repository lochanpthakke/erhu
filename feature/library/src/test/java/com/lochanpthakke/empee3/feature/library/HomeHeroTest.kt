package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.feature.library.mix.MixKind
import com.lochanpthakke.empee3.feature.library.mix.Palettes
import com.lochanpthakke.empee3.feature.library.mix.SmartMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroTest {
    @Test
    fun bannerText_readsOnEveryColourOfTheTextSide_forEveryPalette() {
        for (palette in Palettes.ALL) {
            val look = BannerLook.of(palette)
            val textSide = palette.background + palette.accents.flatMap { a -> palette.background.map { BannerLook.mix(a, it, 0.45f) } }
            textSide.forEach { colour ->
                val behind = BannerLook.mix(look.veil, colour, look.veilAlpha)
                assertTrue("${palette.name}: ${contrast(look.text, behind)}", contrast(look.text, behind) >= 4.5)
            }
            assertTrue(look.veilAlpha <= 0.92f)
        }
    }

    @Test
    fun theVeilNeverHidesTheArt() {
        val veils = Palettes.ALL.associate { it.name to BannerLook.of(it).veilAlpha }
        assertTrue("veils: $veils", veils.values.all { it <= 0.7f })
    }

    private fun mixes() = MixKind.entries.map { SmartMix(it, listOf(1L, 2L, 3L), "how") }

    @Test
    fun aSessionKeepsOneOrder_aNewSessionUsuallyLeadsWithAnotherConcept() {
        val first = HomeSession.feature(mixes(), 6, sessionSeed = 1)
        assertEquals(first, HomeSession.feature(mixes(), 6, sessionSeed = 1))
        assertEquals(6, first.size)
        val leaders = (1L..20L).map { HomeSession.feature(mixes(), 1, sessionSeed = it).single().kind }.toSet()
        assertTrue("leaders: $leaders", leaders.size >= 4)
        assertNotEquals(first, HomeSession.feature(mixes(), 6, sessionSeed = 2))
    }

    @Test
    fun aRebuiltMixListKeepsTheOrderOfTheMixesItStillHas() {
        val all = HomeSession.feature(mixes(), 15, sessionSeed = 7).map { it.kind }
        val fewer = HomeSession.feature(mixes().filter { it.kind != all[1] }, 15, sessionSeed = 7).map { it.kind }
        assertEquals(all - all[1], fewer)
    }
}
