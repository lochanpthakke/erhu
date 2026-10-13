package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Release dates: strict parsing, picking the original release, and when a year is too uncertain to write. */
class ReleaseDateTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun parse(raw: String?) = ReleaseDate.parse(raw, today)

    private fun dates(vararg raw: String) = raw.map { requireNotNull(parse(it)) { it } }

    @Test
    fun validDates_keepTheirPrecision() {
        assertEquals("2010-11-19", parse("2010-11-19")?.tagValue)
        assertEquals("2010-11", parse("2010-11")?.tagValue)
        assertEquals("2010", parse(" 2010 ")?.tagValue)
        assertEquals("2026-10", parse("2026-10")?.tagValue)
    }

    @Test
    fun missingOrMalformedDates_areRejected() {
        val bad =
            listOf(null, "", "   ", "abcd", "2010-1-5", "19-11-2010", "2010/11/19", "2010-13", "2010-02-30", "2010-11-19T00:00", "201", "20100")
        for (raw in bad) assertNull("'$raw' should be rejected", parse(raw))
    }

    @Test
    fun implausibleDates_areRejected() {
        assertNull("before ${ReleaseDate.MIN_YEAR}", parse("1899-12-31"))
        assertNull(parse("0000"))
        assertNull("in the future", parse("2026-10-14"))
        assertNull("in the future", parse("2027"))
    }

    @Test
    fun earliest_isTheOriginalRelease_asPreciseAsKnown() {
        // Runaway's official MBDTF releases on MusicBrainz.
        assertEquals("2010-11-19", ReleaseDate.earliest(dates("2010-11-22", "2010-11-19", "2010-11"))?.tagValue)
        assertEquals("2010-11-19", ReleaseDate.earliest(dates("2010", "2010-11-19"))?.tagValue)
        assertEquals("2010-03", ReleaseDate.earliest(dates("2010-03", "2010-11-19"))?.tagValue)
        assertEquals("2010", ReleaseDate.earliest(dates("2012-01-01", "2010"))?.tagValue)
        assertNull(ReleaseDate.earliest(emptyList()))
    }

    @Test
    fun emptyAnd1970Years_countAsMissing_otherYearsAreKept() {
        for (missing in listOf(null, "", " ", "0", "1970", "1970-01-01", "abc", "1899")) assertFalse("'$missing'", isUsableYearTag(missing))
        for (valid in listOf("2010", "2010-11-19", "1969", "1971")) assertTrue("'$valid'", isUsableYearTag(valid))
    }

    // ---- choosing the year for a match

    private fun match(
        id: String,
        date: String?,
        score: Float = 1f,
        issue: String? = null,
    ) = MetadataCandidate(
        id = id,
        title = "Runaway",
        artist = "Kanye West",
        album = "My Beautiful Dark Twisted Fantasy",
        confidenceScore = score,
        sourceName = "MusicBrainz",
        releaseDate = date,
        releaseDateIssue = issue,
    )

    private fun decide(
        best: MetadataCandidate,
        vararg others: MetadataCandidate,
    ) = releaseDateFor(best, listOf(best) + others, today, confidentScore = 0.85f)

    @Test
    fun aDatedConfidentMatch_givesItsDate() {
        val best = match("a", "2010-11-19")
        assertEquals(ReleaseDateDecision.Found(requireNotNull(parse("2010-11-19"))), decide(best, match("b", "2010-11-22")))
    }

    @Test
    fun aMatchWithoutADate_givesItsReason() {
        val decision = decide(match("a", null, issue = "MusicBrainz lists this recording only on unofficial releases (Bootleg)"))
        assertEquals(ReleaseDateDecision.Rejected("MusicBrainz lists this recording only on unofficial releases (Bootleg)"), decision)
        assertTrue(decide(match("b", null)) is ReleaseDateDecision.Rejected)
    }

    @Test
    fun aMalformedDateOnTheMatch_isRejected() {
        assertTrue(decide(match("a", "2010-13-45")) is ReleaseDateDecision.Rejected)
    }

    @Test
    fun equallyGoodMatchesWithDifferentYears_areAmbiguous() {
        val decision = decide(match("a", "2010-11-19"), match("b", "2013-05-01", score = 0.99f))
        assertEquals(ReleaseDateDecision.Rejected("Equally good matches disagree on the year (2010, 2013)"), decision)
    }

    @Test
    fun weakerOrLowConfidenceMatches_dontMakeItAmbiguous() {
        val best = match("a", "2010-11-19")
        assertTrue(decide(best, match("b", "2013", score = 0.9f)) is ReleaseDateDecision.Found)
        assertTrue(decide(best.copy(confidenceScore = 0.86f), match("c", "2013", score = 0.6f)) is ReleaseDateDecision.Found)
    }
}
