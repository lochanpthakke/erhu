package com.lochanpthakke.empee3.core.network.tagger

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * A release date exactly as precise as its source: a year, a year and month, or a full date.
 * Only [parse] creates one, so every instance is a real, plausible calendar date.
 */
data class ReleaseDate private constructor(
    val year: Int,
    val month: Int?,
    val day: Int?,
) {
    /** The tag value: "2010", "2010-11" or "2010-11-19". */
    val tagValue: String
        get() =
            buildString {
                append("%04d".format(year))
                month?.let { append("-%02d".format(it)) }
                day?.let { append("-%02d".format(it)) }
            }

    val hasMonthOnly: Boolean get() = month != null && day == null

    fun yearOnly(): ReleaseDate = ReleaseDate(year, null, null)

    /** True when [other] is strictly earlier at the first component both dates know. */
    internal fun isLaterThan(other: ReleaseDate): Boolean =
        when {
            year != other.year -> year > other.year
            month == null || other.month == null || month == other.month ->
                day != null && other.day != null && month == other.month && day > other.day
            else -> month > other.month
        }

    private val precision: Int
        get() =
            when {
                day != null -> 2
                month != null -> 1
                else -> 0
            }

    companion object {
        /** Before this a "release date" is noise for a music library (MediaStore keeps 1900–2099 too). */
        const val MIN_YEAR = 1900

        private val FORMAT = Regex("""^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?$""")

        /**
         * A strictly formatted ("YYYY", "YYYY-MM" or "YYYY-MM-DD"), real calendar date from [MIN_YEAR]
         * up to [today]; anything else (blank, malformed, impossible or in the future) is null.
         */
        @Suppress("ReturnCount")
        fun parse(
            raw: String?,
            today: LocalDate,
        ): ReleaseDate? {
            val match = raw?.trim()?.let(FORMAT::matchEntire) ?: return null
            val (y, m, d) = match.destructured
            val year = y.toInt()
            val month = m.takeIf { it.isNotEmpty() }?.toInt()
            val day = d.takeIf { it.isNotEmpty() }?.toInt()
            val date =
                try {
                    when {
                        day != null && month != null -> LocalDate.of(year, month, day)
                        month != null -> YearMonth.of(year, month).atDay(1)
                        else -> LocalDate.of(year, 1, 1)
                    }
                } catch (_: DateTimeException) {
                    return null
                }
            if (year < MIN_YEAR || date.isAfter(today)) return null
            return ReleaseDate(year, month, day)
        }

        /**
         * The original release among [dates]: the earliest, as precise as the data allows. A date
         * that another one is strictly earlier than is dropped; of the rest (which agree wherever both
         * are known, e.g. "2010-11" and "2010-11-19") the most precise is kept.
         */
        fun earliest(dates: Collection<ReleaseDate>): ReleaseDate? =
            dates
                .filter { date -> dates.none { other -> date.isLaterThan(other) } }
                .maxByOrNull { it.precision }
    }
}

/**
 * Is [value] (a file's year tag) a real year to keep? Blank, unparseable and the 1970 that some
 * downloaders write (the Unix epoch, not a release year) all count as missing.
 */
fun isUsableYearTag(value: String?): Boolean {
    val year =
        value
            ?.trim()
            ?.takeIf { it.length >= YEAR_DIGITS }
            ?.take(YEAR_DIGITS)
            ?.toIntOrNull() ?: return false
    return year >= ReleaseDate.MIN_YEAR && year != EPOCH_PLACEHOLDER_YEAR
}

private const val YEAR_DIGITS = 4
private const val EPOCH_PLACEHOLDER_YEAR = 1970

/** Outcome of choosing a year for a matched track. */
sealed interface ReleaseDateDecision {
    data class Found(
        val date: ReleaseDate,
    ) : ReleaseDateDecision

    data class Rejected(
        val reason: String,
    ) : ReleaseDateDecision
}

/**
 * The release date to write for [match], or why there is none: the match must carry a validated
 * date, and no other equally good confident match may name a different year (e.g. two versions of
 * the song, released years apart, both matching the file), because then the year is a guess.
 */
fun releaseDateFor(
    match: MetadataCandidate,
    candidates: List<MetadataCandidate>,
    today: LocalDate,
    confidentScore: Float,
): ReleaseDateDecision {
    val date = ReleaseDate.parse(match.releaseDate, today)
    val otherYears =
        candidates
            .filter { it !== match && it.confidenceScore >= confidentScore && match.confidenceScore - it.confidenceScore <= TIE_MARGIN }
            .mapNotNull { ReleaseDate.parse(it.releaseDate, today)?.year }
            .filter { it != date?.year }
            .distinct()
    return when {
        date == null -> ReleaseDateDecision.Rejected(match.releaseDateIssue ?: "${match.sourceName} gives no release date for this match")
        otherYears.isNotEmpty() ->
            ReleaseDateDecision.Rejected(
                "Equally good matches disagree on the year (${(listOf(date.year) + otherYears).sorted().joinToString()})",
            )
        else -> ReleaseDateDecision.Found(date)
    }
}

/** Matches within this score of the best are treated as equally good. */
private const val TIE_MARGIN = 0.02f
