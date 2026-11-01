package com.erhu.erhu.core.database.library

/**
 * Boundary evidence built ONCE from raw rows. It has no API that accepts resolved
 * credits, artists or albums, so nothing downstream can feed back into it.
 *
 * - Rule 1: segments a and b are independent when a appears in some raw credit
 *   without b AND b appears in some raw credit without a.
 * - Rule 2: a title marker block (feat./ft./featuring/with) that equals the tail
 *   of the track's primary credit text proves the boundary before that tail.
 *   The block is compared whole ([CreditText.blockKey]); it is never split.
 * - Rule 3: a proven boundary applies wherever the same raw segment run appears.
 *   Only the boundary propagates; the marker's role stays on its own track.
 */
class EvidenceIndex private constructor(
    private val observationsBySegment: Map<String, List<Set<String>>>,
    private val markerRuns: Map<List<String>, Set<Int>>,
    private val ownMarkerRoles: Map<Pair<Long, Int>, CreditRole>,
    val standaloneKeys: Set<String>,
) {
    fun independent(
        a: String,
        b: String,
    ): Boolean =
        observationsBySegment[a].orEmpty().any { b !in it } &&
            observationsBySegment[b].orEmpty().any { a !in it }

    fun propagatedBoundaries(keys: List<String>): Set<Int> {
        val out = mutableSetOf<Int>()
        for ((run, boundaries) in markerRuns) {
            if (run.size > keys.size) continue
            for (start in 0..keys.size - run.size) {
                if (keys.subList(start, start + run.size) == run) boundaries.mapTo(out) { start + it }
            }
        }
        return out
    }

    fun ownMarkerRole(
        songId: Long,
        boundary: Int,
    ): CreditRole? = ownMarkerRoles[songId to boundary]

    companion object {
        const val UNKNOWN_ARTIST_KEY = "unknown artist"

        private val TITLE_MARKER =
            Regex(
                "[(\\[]\\s*(feat\\.?|ft\\.|featuring|with)\\s+([^)\\]]+)[)\\]]|\\s(feat\\.?|ft\\.|featuring)\\s+(.+)$",
                RegexOption.IGNORE_CASE,
            )

        fun build(tracks: List<RawTrack>): EvidenceIndex {
            val bySegment = HashMap<String, MutableList<Set<String>>>()
            tracks
                .flatMap { listOfNotNull(it.rawArtist, it.rawAlbumArtist) }
                .map { raw -> CreditText.runs(raw).flatMap { run -> run.segments.map { it.key } }.toSet() }
                .filter { it.isNotEmpty() }
                .forEach { observation -> observation.forEach { bySegment.getOrPut(it) { mutableListOf() }.add(observation) } }

            val markerRuns = HashMap<List<String>, MutableSet<Int>>()
            val ownRoles = HashMap<Pair<Long, Int>, CreditRole>()
            for (track in tracks) {
                val run = CreditText.runs(track.rawArtist).firstOrNull { !it.featured } ?: continue
                for ((word, block) in titleMarkers(track.title)) {
                    val boundary = markerBoundary(run, block) ?: continue
                    markerRuns.getOrPut(run.segments.map { it.key }) { mutableSetOf() }.add(boundary)
                    ownRoles[track.songId to boundary] =
                        if (word.equals("with", ignoreCase = true)) CreditRole.PRIMARY else CreditRole.FEATURED
                }
            }
            val standalone =
                tracks
                    .mapNotNull { track ->
                        CreditText
                            .runs(track.rawArtist)
                            .singleOrNull()
                            ?.segments
                            ?.singleOrNull()
                            ?.key
                    }.filter { it != UNKNOWN_ARTIST_KEY }
                    .toSet()
            return EvidenceIndex(bySegment, markerRuns, ownRoles, standalone)
        }

        private fun titleMarkers(title: String): List<Pair<String, String>> =
            TITLE_MARKER
                .findAll(title)
                .map { match ->
                    // groups: 1-2 bracketed "(feat. X)", 3-4 trailing " feat. X"
                    val (bracketWord, bracketBlock, trailingWord) = match.destructured
                    if (bracketBlock.isNotEmpty()) bracketWord to bracketBlock else trailingWord to match.groupValues.last()
                }.toList()

        /** Boundary index b (≥ 1) such that the run's raw text from segment b to the end equals [block] as a whole. */
        private fun markerBoundary(
            run: CreditRun,
            block: String,
        ): Int? {
            val target = CreditText.blockKey(block)
            return (1 until run.segments.size).firstOrNull { b ->
                CreditText.blockKey(run.raw.substring(run.segments[b].start)) == target
            }
        }
    }
}
