package com.lochanpthakke.empee3.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LibraryModelBuilderTest {
    private val fixture get() = LibraryFixture.tracks

    private fun creditsOf(
        model: LibraryModel,
        title: String,
    ): List<Pair<String, CreditRole>> {
        val id = LibraryFixture.songId(title)
        return model.credits.filter { it.songId == id }.sortedBy { it.position }.map { it.creditText to it.role }
    }

    @Test
    fun `golden albums for the real library fixture`() {
        val model = LibraryModelBuilder.build(fixture, emptyMap())
        val albums = model.albums.sortedBy { it.title.lowercase() }.map { Triple(it.title, it.songIds.size, it.displayArtist) }
        assertEquals(
            listOf(
                Triple("Flower Boy", 14, "Tyler/ The Creator"),
                Triple("Her Loss", 16, "Drake & 21 Savage"),
                Triple("ICEMAN", 18, "Drake"),
                Triple("IGOR", 12, "Tyler/ The Creator"),
                Triple("Late Registration", 21, "Kanye West"),
                Triple("My Beautiful Dark Twisted Fantasy", 13, "Kanye West"),
                Triple("Without Warning", 10, "Metro Boomin, 21 Savage & Offset"),
            ),
            albums,
        )
    }

    @Test
    fun `golden credits for the investigated tracks`() {
        val m = LibraryModelBuilder.build(fixture, emptyMap())
        val p = CreditRole.PRIMARY
        val f = CreditRole.FEATURED
        assertEquals(listOf("Drake" to p, "21 Savage" to p), creditsOf(m, "Rich Flex"))
        assertEquals(listOf("Drake" to p, "21 Savage" to f), creditsOf(m, "B's On The Table (feat. 21 Savage)"))
        assertEquals(listOf("21 Savage" to p, "Offset" to p, "Metro Boomin" to p), creditsOf(m, "Darth Vader"))
        assertEquals(listOf("Offset" to p, "Metro Boomin" to p), creditsOf(m, "Nightmare"))
        assertEquals(
            listOf("21 Savage" to p, "Offset" to p, "Metro Boomin" to p, "Travis Scott" to f),
            creditsOf(m, "Ghostface Killers (feat. Travis Scott)"),
        )
        assertEquals(listOf("Tyler/ The Creator" to p), creditsOf(m, "Glitter"))
        assertEquals(listOf("Tyler/ The Creator" to p, "Kali Uchis" to f), creditsOf(m, "See You Again (feat. Kali Uchis)"))
        assertEquals(
            listOf("Tyler/ The Creator" to p, "Rex Orange County/Anna of the North" to f),
            creditsOf(m, "Boredom (feat. Rex Orange County & Anna of the North)"),
        )
        assertEquals(listOf("Tyler/ The Creator/Alexander Brettin/Jaden" to p), creditsOf(m, "Pothole (feat. Jaden Smith)"))
        assertEquals(listOf("Kanye West/JAY-Z" to p, "Rick Ross/Nicki Minaj/Bon Iver" to p), creditsOf(m, "Monster"))
    }

    @Test
    fun `album grouping ignores track artists entirely`() {
        val a = LibraryModelBuilder.build(fixture, emptyMap()).albums.map { it.id to it.songIds }
        val b = LibraryModelBuilder.build(fixture.map { it.copy(rawArtist = "X") }, emptyMap()).albums.map { it.id to it.songIds }
        assertEquals(a, b)
    }

    @Test
    fun `model is independent of input order`() {
        val expected = LibraryModelBuilder.build(fixture, emptyMap())
        repeat(20) { seed ->
            assertEquals(expected, LibraryModelBuilder.build(fixture.shuffled(Random(seed)), emptyMap()))
        }
    }

    @Test
    fun `adding tracks never removes an existing split`() {
        val ww = fixture.filter { it.album == "Without Warning" }
        val wwIds = ww.map { it.songId }.toSet()

        fun splits(model: LibraryModel) = model.credits.filter { it.songId in wwIds }.map { it.songId to it.creditText }.toSet()

        val small = splits(LibraryModelBuilder.build(ww, emptyMap()))
        val large = splits(LibraryModelBuilder.build(fixture, emptyMap()))
        small.filter { (_, text) -> '/' !in text }.forEach { assertTrue(it in large) }
    }

    @Test
    fun `credits depend only on raw rows`() {
        val evidence = EvidenceIndex.build(fixture)
        val direct = fixture.associate { it.songId to CreditResolver(evidence).resolve(it).map { c -> c.text } }
        val model = LibraryModelBuilder.build(fixture, emptyMap())
        direct.forEach { (id, texts) ->
            assertEquals(texts, model.credits.filter { it.songId == id }.sortedBy { it.position }.map { it.creditText })
        }
    }

    @Test
    fun `unverified segments link standalone artists inside unresolved credits`() {
        val model = LibraryModelBuilder.build(fixture, emptyMap())
        val kanye = IdentityHash.of("kanye west")
        val monster = LibraryFixture.songId("Monster")
        assertTrue(model.unverifiedSegments.any { it.songId == monster && it.segmentKey == "kanye west" })
        assertTrue(model.artists.none { it.id == kanye && it.isUnresolved })
    }

    @Test
    fun `ten thousand tracks build in under two seconds`() {
        val big =
            (0 until 10_000).map { i ->
                RawTrack(i.toLong(), "t$i", "Artist ${i % 2000}/Guest ${i % 7}", null, "Album ${i % 800}", i % 20, 1, "/m/${i % 800}", false)
            }
        val start = System.nanoTime()
        LibraryModelBuilder.build(big, emptyMap())
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
    }
}
