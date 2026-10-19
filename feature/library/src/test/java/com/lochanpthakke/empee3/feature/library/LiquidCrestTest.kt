package com.lochanpthakke.empee3.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidCrestTest {
    private val crest = LiquidCrest(rise = 9f, longWave = 222f, shortWave = 93f, leadIn = 66f)

    @Test
    fun theCrestStartsFlat_soTheLineKeepsItsRoundedEnd() {
        assertEquals(0f, crest.heightAt(0f, time = 3f, strength = 1f), 0f)
    }

    @Test
    fun theCrestStaysAboveTheLineAndWithinItsRise() {
        var x = 66f
        while (x < 1_000f) {
            listOf(0f, 1.7f, 12f).forEach { time ->
                val h = crest.heightAt(x, time, strength = 1f)
                assertTrue("x=$x t=$time h=$h", h >= 9f * 0.25f - 0.001f && h <= 9f + 0.001f)
            }
            x += 7f
        }
    }

    @Test
    fun itDriftsWithTime_andHoldsStillAtTheSameTime() {
        assertNotEquals(crest.heightAt(200f, 0f, 1f), crest.heightAt(200f, 1f, 1f))
        assertEquals(crest.heightAt(200f, 5f, 1f), crest.heightAt(200f, 5f, 1f), 0f)
    }

    @Test
    fun aSettledCrestIsLower() {
        val full = crest.heightAt(300f, 2f, 1f)
        assertEquals(full * 0.45f, crest.heightAt(300f, 2f, 0.45f), 0.0001f)
    }
}
