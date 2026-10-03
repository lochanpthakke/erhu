package com.lochanpthakke.empee3.core.database.library

const val UNKNOWN_ARTIST = "Unknown Artist"

/** One credited unit on a track. [isUnresolved]: the unit spans a candidate boundary that had no evidence. */
data class ResolvedCredit(
    val text: String,
    val key: String,
    val role: CreditRole,
    val position: Int,
    val isUnresolved: Boolean,
    val segmentKeys: List<String>,
)

/** Layer A. Splits a credit only at boundaries the [evidence] proves; otherwise keeps the original text. */
class CreditResolver(
    private val evidence: EvidenceIndex,
) {
    fun resolve(track: RawTrack): List<ResolvedCredit> {
        val runs = CreditText.runs(track.rawArtist)
        val credits =
            if (runs.isEmpty() || CreditText.canonicalKey(track.rawArtist) == EvidenceIndex.UNKNOWN_ARTIST_KEY) {
                listOf(unknown())
            } else {
                runs.flatMap { unitsOf(track.songId, it) }
            }
        return credits.distinctBy { it.key }.mapIndexed { index, credit -> credit.copy(position = index) }
    }

    private fun unitsOf(
        songId: Long,
        run: CreditRun,
    ): List<ResolvedCredit> {
        val keys = run.segments.map { it.key }
        val proven = evidence.propagatedBoundaries(keys)
        val units = mutableListOf<ResolvedCredit>()
        var first = 0
        var role = if (run.featured) CreditRole.FEATURED else CreditRole.PRIMARY
        for (i in 1..keys.size) {
            val boundary = i == keys.size || i in proven || evidence.independent(keys[i - 1], keys[i])
            if (!boundary) continue
            units += unit(run, first, i, role)
            if (i < keys.size && !run.featured) role = evidence.ownMarkerRole(songId, i) ?: role
            first = i
        }
        return units
    }

    private fun unit(
        run: CreditRun,
        from: Int,
        to: Int,
        role: CreditRole,
    ): ResolvedCredit {
        val text = run.raw.substring(run.segments[from].start, run.segments[to - 1].end)
        return ResolvedCredit(text, CreditText.canonicalKey(text), role, 0, to - from > 1, run.segments.subList(from, to).map { it.key })
    }

    private fun unknown() =
        ResolvedCredit(
            text = UNKNOWN_ARTIST,
            key = EvidenceIndex.UNKNOWN_ARTIST_KEY,
            role = CreditRole.PRIMARY,
            position = 0,
            isUnresolved = false,
            segmentKeys = listOf(EvidenceIndex.UNKNOWN_ARTIST_KEY),
        )
}
