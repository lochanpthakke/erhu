package com.erhu.erhu.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceIndexTest {
    private fun track(
        id: Long,
        artist: String,
        title: String = "t$id",
        albumArtist: String? = null,
    ) = RawTrack(id, title, artist, albumArtist, "A", id.toInt(), 1, "/m", false)

    @Test
    fun `two-sided independence needs each segment without the other`() {
        val index =
            EvidenceIndex.build(
                listOf(
                    track(1, "21 Savage"),
                    track(2, "Offset/Metro Boomin"),
                    track(3, "21 Savage/Metro Boomin"),
                    track(4, "Tyler/ The Creator"),
                ),
            )
        assertTrue(index.independent("21 savage", "metro boomin"))
        assertFalse(index.independent("offset", "metro boomin")) // offset never appears without metro boomin
        assertFalse(index.independent("tyler", "the creator"))
    }

    @Test
    fun `title marker block gives a boundary and the own track role only`() {
        val index = EvidenceIndex.build(listOf(track(7, "Offset/Metro Boomin", "Ric Flair Drip (with Metro Boomin)")))
        assertEquals(setOf(1), index.propagatedBoundaries(listOf("offset", "metro boomin")))
        assertEquals(setOf(2), index.propagatedBoundaries(listOf("21 savage", "offset", "metro boomin")))
        assertEquals(CreditRole.PRIMARY, index.ownMarkerRole(7, 1))
        assertNull(index.ownMarkerRole(8, 1))
    }

    @Test
    fun `title marker is compared as one block and never split`() {
        val index = EvidenceIndex.build(listOf(track(1, "Kali Uchis/Tyler/ The Creator", "Song (feat. Tyler, The Creator)")))
        assertEquals(setOf(1), index.propagatedBoundaries(listOf("kali uchis", "tyler", "the creator")))
    }

    @Test
    fun `marker that matches no suffix gives no evidence and several markers are each checked`() {
        val index =
            EvidenceIndex.build(
                listOf(
                    track(1, "Tyler/ The Creator/Alexander Brettin/Jaden", "Pothole (feat. Jaden Smith)"),
                    track(2, "A/B/C", "Song (feat. B) [with C]"),
                ),
            )
        assertTrue(index.propagatedBoundaries(listOf("tyler", "the creator", "alexander brettin", "jaden")).isEmpty())
        assertEquals(setOf(2), index.propagatedBoundaries(listOf("a", "b", "c")))
    }

    @Test
    fun `standalone keys are whole single-segment raw credits only`() {
        val index = EvidenceIndex.build(listOf(track(1, "Kanye West"), track(2, "Kanye West/JAY-Z"), track(3, "")))
        assertEquals(setOf("kanye west"), index.standaloneKeys)
    }

    @Test
    fun `album artist tags are raw observations`() {
        val index = EvidenceIndex.build(listOf(track(1, "A/B", albumArtist = "A"), track(2, "B")))
        assertTrue(index.independent("a", "b"))
    }
}
