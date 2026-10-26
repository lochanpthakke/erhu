package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.text.MojibakeText
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.ProviderOutcome
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.model.QueryAttempt
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.foldForMatch
import com.lochanpthakke.empee3.core.network.scorer.primaryArtist
import com.lochanpthakke.empee3.core.network.scorer.stripFeaturing
import com.lochanpthakke.empee3.core.network.tagger.isUsableTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What is known about a local track. [title], [artist] and [album] come from the file's own tags
 * (null when missing, a placeholder or unreadable); [fileTitles] are the titles read from the file
 * name, a fallback only: downloaders shorten names ("THANK YOU.mp3" for "GONE, GONE / THANK YOU").
 */
data class TrackIdentity(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val fileTitles: List<String> = emptyList(),
    val fileArtist: String? = null,
)

/**
 * One search sent to the providers, as separate values (never one string): a title, and the
 * artist credit or the album to narrow it, plus where the title came from.
 */
data class TrackQuery(
    val title: String,
    val artist: String?,
    val source: Source,
    val album: String? = null,
) {
    enum class Source(
        val label: String,
    ) {
        TAG("file tags"),
        FILE_NAME("file name"),
    }
}

sealed interface MatchDecision {
    data class Confident(
        val match: MetadataCandidate,
    ) : MatchDecision

    /** Equally good matches would write different tags: nothing is chosen. */
    data class Ambiguous(
        val matches: List<MetadataCandidate>,
        val reason: String,
    ) : MatchDecision

    data class LowConfidence(
        val best: MetadataCandidate,
        val reason: String,
    ) : MatchDecision

    data class NoMatch(
        val reason: String?,
    ) : MatchDecision
}

data class MatchResult(
    val decision: MatchDecision,
    /** Every candidate found, once each, scored against the track itself (not the query text), best first. */
    val candidates: List<MetadataCandidate>,
    val providerOutcomes: List<ProviderOutcome>,
    val queries: List<TrackQuery>,
    /** Each query actually sent, with every provider's answer to it, in order. */
    val attempts: List<QueryAttempt> = emptyList(),
)

/**
 * Finds the online record of a local track. Searches start from the file's own tags and fall
 * back to the file name; every candidate is scored against the track's best-known identity
 * (embedded title, artist, album, duration), so a short file name can widen the search but never
 * lowers a correct candidate's score. Thresholds are the shared ones, and a tie between matches
 * that would write different tags is reported, never resolved silently.
 */
@Singleton
class TrackMatcher
    @Inject
    constructor(
        private val scorer: MetadataScorer,
    ) {
        /** [acceptAt]: the score a match needs to be written (Settings; 0.85 unless the user changes it). */
        suspend fun match(
            identity: TrackIdentity,
            acceptAt: Float = HIGH_CONFIDENCE_THRESHOLD,
            search: suspend (TrackQuery) -> DetailedSearchResponse,
        ): MatchResult {
            val queries = planQueries(identity)
            val found = linkedMapOf<String, MetadataCandidate>()
            val responses = mutableListOf<DetailedSearchResponse>()
            val attempts = mutableListOf<QueryAttempt>()
            for (query in queries) {
                val response = search(query)
                responses += response
                attempts += QueryAttempt(query.title, query.artist, query.album, query.source.label, response.providerOutcomes)
                for (candidate in response.candidates) {
                    val scored = rescore(candidate, identity, query)
                    val previous = found[candidate.id]
                    if (previous == null || scored.confidenceScore > previous.confidenceScore) found[candidate.id] = scored
                }
                val ranked = found.values.sortedByDescending { it.confidenceScore }
                if (decide(ranked, identity, acceptAt) is MatchDecision.Confident) break
            }
            val ranked = found.values.sortedByDescending { it.confidenceScore }
            val sent = queries.take(attempts.size)
            return MatchResult(decide(ranked, identity, acceptAt), ranked, mergeOutcomes(responses, ranked), sent, attempts)
        }

        /**
         * Scores [candidate] against the track itself, not the query text: the embedded title,
         * artist, album and length. File-name titles count as alternates (a little below the tag
         * title), so a shortened file name can only add evidence, never take it away.
         */
        private fun rescore(
            candidate: MetadataCandidate,
            identity: TrackIdentity,
            query: TrackQuery,
        ): MetadataCandidate {
            val artist = identity.artist ?: identity.fileArtist ?: query.artist

            fun against(title: String) = scorer.scoreCandidate(candidate, title, artist, identity.album, identity.durationMs)
            val primary = against(identity.title ?: query.title)
            val alternate =
                identity.fileTitles
                    .map { against(it) }
                    .maxByOrNull { it.confidenceScore }
                    ?.let { it.copy(confidenceScore = it.confidenceScore * ALTERNATE_TITLE_FACTOR) }
            return if (alternate != null && alternate.confidenceScore > primary.confidenceScore) alternate else primary
        }
    }

/**
 * The searches to run, best evidence first: the tagged title, then file-name titles. A title is
 * sent without its featured-artist note (MusicBrainz titles don't carry one, and the names are in
 * the artist credit), first with the artist credit, then with the album. Duplicates are dropped.
 */
fun planQueries(identity: TrackIdentity): List<TrackQuery> {
    val artist = identity.artist ?: identity.fileArtist
    val titles =
        (
            listOfNotNull(identity.title?.let { it to TrackQuery.Source.TAG }) +
                identity.fileTitles.map { it to TrackQuery.Source.FILE_NAME }
        ).map { (title, source) -> stripFeaturing(title) to source }
            .filter { (title, _) -> title.isNotBlank() }
            .distinctBy { (title, _) -> foldForMatch(title) }
    val narrowed =
        titles.flatMap { (title, source) ->
            listOfNotNull(
                artist?.let { TrackQuery(title, it, source) },
                identity.album?.let { TrackQuery(title, null, source, album = it) },
            )
        }
    // Last resort (Lochan's fallback): the best title alone, for when the artist and album tags
    // match nothing online. It runs only if nothing above was confident; scoring guards against
    // the many unrelated songs a bare title finds.
    val unconstrained = titles.firstOrNull()?.let { (title, source) -> TrackQuery(title, null, source) }
    return (narrowed.take(MAX_QUERIES - 1) + listOfNotNull(unconstrained))
        .distinctBy { Triple(foldForMatch(it.title), foldForMatch(it.artist.orEmpty()), foldForMatch(it.album.orEmpty())) }
}

/** A tag value worth searching and matching with: present, not a placeholder, not misread text, not a downloader watermark. */
fun usableIdentityText(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { isUsableTag(it) && !MojibakeText.looksMisread(it) && !WATERMARK.containsMatchIn(it) }

private val WATERMARK = Regex("""(?i)\.(com|net|org|mp3|m4a)\b|\b(www|download(ed)?|ripped)\b""")

/** The decision for [ranked] candidates (best first), with a reason a person can act on. */
internal fun decide(
    ranked: List<MetadataCandidate>,
    identity: TrackIdentity,
    acceptAt: Float = HIGH_CONFIDENCE_THRESHOLD,
): MatchDecision {
    val best = ranked.firstOrNull() ?: return MatchDecision.NoMatch(null)
    val rivals =
        ranked.drop(1).filter {
            it.confidenceScore >= acceptAt &&
                best.confidenceScore - it.confidenceScore <= TIE_MARGIN &&
                !sameSongIdentity(it, best)
        }
    return when {
        best.confidenceScore < minOf(UNCERTAIN_THRESHOLD, acceptAt) ->
            MatchDecision.NoMatch("No close match. Best: ${describe(best, identity)}")
        best.confidenceScore < acceptAt ->
            MatchDecision.LowConfidence(best, "Not confident enough to write. Best: ${describe(best, identity)}")
        rivals.isNotEmpty() ->
            MatchDecision.Ambiguous(
                listOf(best) + rivals,
                "Equally good matches disagree: " + (listOf(best) + rivals).joinToString(" / ") { describe(it, identity) },
            )
        else -> MatchDecision.Confident(best)
    }
}

/** Whether two candidates represent the same underlying song (spelling, "feat." vs comma formatting aside). */
private fun sameSongIdentity(
    c1: MetadataCandidate,
    c2: MetadataCandidate,
): Boolean {
    val sameTitle = foldForMatch(c1.title) == foldForMatch(c2.title)
    val sameAlbum = foldForMatch(c1.album) == foldForMatch(c2.album) || c1.album.isBlank() || c2.album.isBlank()
    val sameArtist =
        foldForMatch(primaryArtist(c1.artist)) == foldForMatch(primaryArtist(c2.artist)) ||
            com.lochanpthakke.empee3.core.network.scorer
                .creditSimilarity(c1.artist, c2.artist) >= 0.80f
    return sameTitle && sameAlbum && sameArtist
}

/** What a match would write, compared loosely (spelling noise and "feat." credits aside). */
private fun writtenIdentity(candidate: MetadataCandidate): Triple<String, String, String> =
    Triple(foldForMatch(candidate.title), foldForMatch(primaryArtist(candidate.artist)), foldForMatch(candidate.album))

/** e.g. "GONE, GONE / THANK YOU" by Tyler, The Creator (IGOR), 4:32 vs file 6:15, 69% [MusicBrainz] */
fun describe(
    candidate: MetadataCandidate,
    identity: TrackIdentity,
): String {
    val album = if (candidate.album.isNotBlank()) " (${candidate.album})" else ""
    val fileLength = identity.durationMs?.let { " vs file ${clock(it)}" }.orEmpty()
    val duration = candidate.durationMs?.let { ", ${clock(it)}$fileLength" }.orEmpty()
    val version = candidate.versionNote?.let { " [$it]" }.orEmpty()
    val percent = (candidate.confidenceScore * PERCENT).toInt()
    return "\"${candidate.title}\" by ${candidate.artist}$album$version$duration, $percent% [${candidate.sourceName}]"
}

private fun clock(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/** One outcome per provider across all queries: failed only if every query failed, else counted from the merged candidates. */
private fun mergeOutcomes(
    responses: List<DetailedSearchResponse>,
    ranked: List<MetadataCandidate>,
): List<ProviderOutcome> =
    responses
        .flatMap { it.providerOutcomes }
        .groupBy { it.providerName }
        .map { (name, outcomes) ->
            val mine = ranked.filter { it.sourceName == name }
            val top = mine.maxOfOrNull { it.confidenceScore }
            when {
                top != null ->
                    ProviderOutcome(
                        name,
                        candidateCount = mine.size,
                        status = if (top >= HIGH_CONFIDENCE_THRESHOLD) ProviderStatus.SUCCESS else ProviderStatus.LOW_CONFIDENCE,
                    )
                outcomes.any { it.status == ProviderStatus.NO_RESULTS } -> ProviderOutcome(name, status = ProviderStatus.NO_RESULTS)
                else -> outcomes.last()
            }
        }

private const val MAX_QUERIES = 6

/** A file-name title is weaker evidence than the tag title. */
private const val ALTERNATE_TITLE_FACTOR = 0.97f
private const val TIE_MARGIN = 0.02f
private const val PERCENT = 100
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
