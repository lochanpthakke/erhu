package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.common.text.MojibakeText
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.ProviderOutcome
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.foldForMatch
import com.lochanpthakke.empee3.core.network.scorer.primaryArtist
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

/** One search sent to the providers, and where its text came from. */
data class TrackQuery(
    val title: String,
    val artist: String?,
    val source: Source,
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
        suspend fun match(
            identity: TrackIdentity,
            search: suspend (TrackQuery) -> DetailedSearchResponse,
        ): MatchResult {
            val queries = planQueries(identity)
            val found = linkedMapOf<String, MetadataCandidate>()
            val responses = mutableListOf<DetailedSearchResponse>()
            for (query in queries) {
                val response = search(query)
                responses += response
                for (candidate in response.candidates) {
                    val scored = rescore(candidate, identity, query)
                    val previous = found[candidate.id]
                    if (previous == null || scored.confidenceScore > previous.confidenceScore) found[candidate.id] = scored
                }
                val ranked = found.values.sortedByDescending { it.confidenceScore }
                if (decide(ranked, identity) is MatchDecision.Confident) break
            }
            val ranked = found.values.sortedByDescending { it.confidenceScore }
            return MatchResult(decide(ranked, identity), ranked, mergeOutcomes(responses, ranked), queries)
        }

        private fun rescore(
            candidate: MetadataCandidate,
            identity: TrackIdentity,
            query: TrackQuery,
        ): MetadataCandidate =
            scorer.scoreCandidate(
                candidate = candidate,
                targetTitle = identity.title ?: query.title,
                targetArtist = identity.artist ?: query.artist,
                targetAlbum = identity.album,
                targetDurationMs = identity.durationMs,
            )
    }

/** The searches to run, best evidence first: the tagged title and artist, then file-name variants. Duplicates are dropped. */
fun planQueries(identity: TrackIdentity): List<TrackQuery> {
    val queries = mutableListOf<TrackQuery>()
    identity.title?.let { queries += TrackQuery(it, identity.artist, TrackQuery.Source.TAG) }
    for (fileTitle in identity.fileTitles) {
        queries += TrackQuery(fileTitle, identity.fileArtist ?: identity.artist, TrackQuery.Source.FILE_NAME)
    }
    return queries
        .filter { it.title.isNotBlank() }
        .distinctBy { foldForMatch(it.title) to foldForMatch(it.artist.orEmpty()) }
        .take(MAX_QUERIES)
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
): MatchDecision {
    val best = ranked.firstOrNull() ?: return MatchDecision.NoMatch(null)
    val rivals =
        ranked.drop(1).filter {
            it.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD &&
                best.confidenceScore - it.confidenceScore <= TIE_MARGIN &&
                writtenIdentity(it) != writtenIdentity(best)
        }
    return when {
        best.confidenceScore < UNCERTAIN_THRESHOLD ->
            MatchDecision.NoMatch("No close match. Best: ${describe(best, identity)}")
        best.confidenceScore < HIGH_CONFIDENCE_THRESHOLD ->
            MatchDecision.LowConfidence(best, "Not confident enough to write. Best: ${describe(best, identity)}")
        rivals.isNotEmpty() ->
            MatchDecision.Ambiguous(
                listOf(best) + rivals,
                "Equally good matches disagree: " + (listOf(best) + rivals).joinToString(" / ") { describe(it, identity) },
            )
        else -> MatchDecision.Confident(best)
    }
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

private const val MAX_QUERIES = 4
private const val TIE_MARGIN = 0.02f
private const val PERCENT = 100
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
