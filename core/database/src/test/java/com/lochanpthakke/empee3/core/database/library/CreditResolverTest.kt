package com.lochanpthakke.empee3.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditResolverTest {
    private fun track(
        id: Long,
        artist: String,
        title: String = "t$id",
    ) = RawTrack(id, title, artist, null, "A", id.toInt(), 1, "/m", false)

    private fun resolveIn(
        library: List<RawTrack>,
        id: Long,
    ): List<Triple<String, CreditRole, Boolean>> =
        CreditResolver(EvidenceIndex.build(library))
            .resolve(library.first { it.songId == id })
            .map { Triple(it.text, it.role, it.isUnresolved) }

    @Test
    fun `no evidence keeps the original text as one unresolved credit`() {
        val names =
            listOf(
                "Tyler/ The Creator",
                "nothing/nowhere.",
                "AC/DC",
                "Earth, Wind & Fire",
                "Mumford & Sons",
                "Simon and Garfunkel",
                "Florence + The Machine",
                "suicideboys x SHAKEWELL",
            )
        for (name in names) {
            assertEquals(listOf(Triple(name, CreditRole.PRIMARY, true)), resolveIn(listOf(track(1, name)), 1))
        }
        assertEquals(listOf(Triple("Guns N' Roses", CreditRole.PRIMARY, false)), resolveIn(listOf(track(1, "Guns N' Roses")), 1))
        assertEquals(listOf(Triple("Lil Nas X", CreditRole.PRIMARY, false)), resolveIn(listOf(track(1, "Lil Nas X")), 1))
        // unbracketed "with" is neither a marker nor a candidate separator: one plain credit
        assertEquals(listOf(Triple("Drake with Future", CreditRole.PRIMARY, false)), resolveIn(listOf(track(1, "Drake with Future")), 1))
    }

    @Test
    fun `independent segments split for every candidate separator`() {
        for (sep in listOf(" & ", " / ", "; ", ", ", " x ")) {
            val lib = listOf(track(1, "Drake${sep}Future"), track(2, "Drake"), track(3, "Future"))
            assertEquals(listOf("Drake", "Future"), resolveIn(lib, 1).map { it.first })
        }
        val sb = listOf(track(1, "\$uicideboy\$ x SHAKEWELL"), track(2, "\$uicideboy\$"), track(3, "SHAKEWELL"))
        assertEquals(listOf("\$uicideboy\$", "SHAKEWELL"), resolveIn(sb, 1).map { it.first })
    }

    @Test
    fun `artist text feat markers split primary from featured`() {
        for (raw in listOf("Drake feat. Future", "Drake ft. Future", "Drake featuring Future", "Drake (with Future)")) {
            assertEquals(
                listOf(Triple("Drake", CreditRole.PRIMARY, false), Triple("Future", CreditRole.FEATURED, false)),
                resolveIn(listOf(track(1, raw)), 1),
            )
        }
    }

    @Test
    fun `featured block needs evidence too`() {
        val lonely = listOf(track(1, "Drake feat. Future & Young Thug"))
        assertEquals(listOf("Drake", "Future & Young Thug"), resolveIn(lonely, 1).map { it.first })
        val known = lonely + listOf(track(2, "Future"), track(3, "Young Thug"))
        assertEquals(listOf("Drake", "Future", "Young Thug"), resolveIn(known, 1).map { it.first })
    }

    @Test
    fun `title marker resolves with the marker role on its own track only`() {
        val lib =
            listOf(
                track(1, "Drake, 21 Savage", "B's On The Table (feat. 21 Savage)"),
                track(2, "Drake/21 Savage", "Rich Flex"),
            )
        assertEquals(listOf(Triple("Drake", CreditRole.PRIMARY, false), Triple("21 Savage", CreditRole.FEATURED, false)), resolveIn(lib, 1))
        // the boundary propagates through the same segment run, the FEATURED role does not
        assertEquals(listOf(Triple("Drake", CreditRole.PRIMARY, false), Triple("21 Savage", CreditRole.PRIMARY, false)), resolveIn(lib, 2))
    }

    @Test
    fun `Without Warning resolves through rules 1 to 3 and stays unresolved without the marker`() {
        val ww =
            listOf(
                track(1, "21 Savage/Offset/Metro Boomin", "Darth Vader"),
                track(2, "Offset/Metro Boomin", "Ric Flair Drip (with Metro Boomin)"),
                track(3, "21 Savage/Metro Boomin", "Run Up the Racks"),
                track(4, "21 Savage", "3AM on Glenwood"),
            )
        assertEquals(listOf("21 Savage", "Offset", "Metro Boomin"), resolveIn(ww, 1).map { it.first })
        val withoutMarker = ww.map { if (it.songId == 2L) it.copy(title = "Nightmare") else it }
        assertEquals(
            listOf(Triple("21 Savage", CreditRole.PRIMARY, false), Triple("Offset/Metro Boomin", CreditRole.PRIMARY, true)),
            resolveIn(withoutMarker, 1),
        )
    }

    @Test
    fun `marker block containing punctuation is kept whole`() {
        val lib = listOf(track(1, "Kali Uchis/Tyler/ The Creator", "Song (feat. Tyler, The Creator)"))
        assertEquals(
            listOf(Triple("Kali Uchis", CreditRole.PRIMARY, false), Triple("Tyler/ The Creator", CreditRole.FEATURED, true)),
            resolveIn(lib, 1),
        )
    }

    @Test
    fun `punctuation only, blank and unknown become Unknown Artist`() {
        for (raw in listOf("", " / ", " & ", "Unknown Artist", "unknown artist")) {
            assertEquals(listOf(Triple(UNKNOWN_ARTIST, CreditRole.PRIMARY, false)), resolveIn(listOf(track(1, raw)), 1))
        }
    }

    @Test
    fun `positions are sequential and duplicates removed`() {
        val lib = listOf(track(1, "A/B feat. A"), track(2, "A"), track(3, "B"))
        val credits = CreditResolver(EvidenceIndex.build(lib)).resolve(lib[0])
        assertEquals(listOf("A", "B"), credits.map { it.text })
        assertEquals(listOf(0, 1), credits.map { it.position })
    }
}
