package com.lochanpthakke.empee3.core.network.apple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Motion covers are per storefront: "$ome $exy $ongs 4 U" has none in the US catalogue (checked
 * 2026-10-21, both editions) but has one in India's.
 */
class MotionStorefrontsTest {
    @Test
    fun `the phone's country and the search storefront come first, then every other storefront`() {
        val order = AnimatedArtworkConfig.motionStorefronts(deviceCountry = "in", searchStorefront = "us")
        assertEquals(listOf("in", "us"), order.take(2))
        assertEquals(order.distinct(), order)
        assertTrue(order.containsAll(listOf("ca", "gb", "au", "jp")))
    }

    @Test
    fun `india is checked even when the phone is set to another country`() {
        val order = AnimatedArtworkConfig.motionStorefronts(deviceCountry = "us", searchStorefront = "us")
        assertEquals("us", order.first())
        assertTrue("in" in order)
        assertEquals(order.distinct(), order)
    }

    @Test
    fun `a blank or odd country is ignored`() {
        val order = AnimatedArtworkConfig.motionStorefronts(deviceCountry = "", searchStorefront = "US")
        assertEquals("us", order.first())
        assertTrue(order.none { it.isBlank() })
    }
}
