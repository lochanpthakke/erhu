package com.erhu.erhu.core.database.library

data class AlbumPresentation(
    val displayArtist: String,
    val linkedArtistKeys: List<String>,
    val isCompilation: Boolean,
)

/**
 * Terminal sink: derives how an album is shown and which artists it is listed
 * under. Its output is never read by credit resolution or album grouping.
 */
object AlbumPresenter {
    const val VARIOUS_ARTISTS = "Various Artists"
    private const val MAX_CORE_ARTISTS = 3

    fun present(
        group: AlbumGroup,
        creditsBySong: Map<Long, List<ResolvedCredit>>,
        standaloneKeys: Set<String>,
        identity: ArtistIdentityResolver,
        displayName: (String) -> String,
    ): AlbumPresentation {
        val counts =
            group.songIds
                .flatMap { coreKeys(creditsBySong[it].orEmpty(), standaloneKeys, identity) }
                .groupingBy { it }
                .eachCount()
        val core =
            counts.entries
                .filter { it.value * 2 >= group.songIds.size }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
        val tagged = group.albumArtistKey
        val dominantCredit = if (core.isEmpty()) dominantUnresolvedCredit(group, creditsBySong, standaloneKeys, identity) else null
        return when {
            // A tagged Album Artist is shown as written; it is linked only when it names one artist.
            tagged != null -> {
                val links = if (taggedHasIdentity(group, identity)) listOf(tagged) else emptyList()
                AlbumPresentation(group.albumArtistText ?: displayName(tagged), links, false)
            }
            group.isCompilationTagged || core.size > MAX_CORE_ARTISTS -> AlbumPresentation(VARIOUS_ARTISTS, emptyList(), true)
            // Credit text only (no artist identity): shown as written, linked to nobody.
            dominantCredit != null -> AlbumPresentation(dominantCredit, emptyList(), false)
            core.isEmpty() -> AlbumPresentation(VARIOUS_ARTISTS, emptyList(), true)
            else -> AlbumPresentation(joinNames(core.map(displayName)), core, false)
        }
    }

    private fun taggedHasIdentity(
        group: AlbumGroup,
        identity: ArtistIdentityResolver,
    ): Boolean {
        val text = group.albumArtistText ?: return true
        return CreditText.segmentsOf(text).size <= 1 || identity.isAliased(CreditText.canonicalKey(text))
    }

    /** The unresolved primary credit text on at least half the tracks, when no artist qualifies. */
    private fun dominantUnresolvedCredit(
        group: AlbumGroup,
        creditsBySong: Map<Long, List<ResolvedCredit>>,
        standaloneKeys: Set<String>,
        identity: ArtistIdentityResolver,
    ): String? =
        group.songIds
            .flatMap { id ->
                creditsBySong[id]
                    .orEmpty()
                    .filter { it.role == CreditRole.PRIMARY && !identity.hasIdentity(it) && it.segmentKeys.first() !in standaloneKeys }
                    .map { it.text }
                    .toSet()
            }.groupingBy { it }
            .eachCount()
            .entries
            .filter { it.value * 2 >= group.songIds.size }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

    private fun coreKeys(
        credits: List<ResolvedCredit>,
        standaloneKeys: Set<String>,
        identity: ArtistIdentityResolver,
    ): Set<String> =
        credits
            .filter { it.role == CreditRole.PRIMARY }
            .mapNotNull { credit ->
                val lead = credit.segmentKeys.first()
                when {
                    identity.hasIdentity(credit) -> identity.canonicalKey(credit.key)
                    // Counts toward the standalone artist it starts with (raw evidence), never itself.
                    lead in standaloneKeys -> identity.canonicalKey(lead)
                    else -> null
                }
            }.toSet()

    private fun joinNames(names: List<String>): String =
        if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " & " + names.last()
}
