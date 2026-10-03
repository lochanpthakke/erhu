package com.lochanpthakke.empee3.core.database.library

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
        return when {
            tagged != null -> AlbumPresentation(group.albumArtistText ?: displayName(tagged), listOf(tagged), false)
            group.isCompilationTagged || core.isEmpty() || core.size > MAX_CORE_ARTISTS ->
                AlbumPresentation(VARIOUS_ARTISTS, emptyList(), true)
            else -> AlbumPresentation(joinNames(core.map(displayName)), core, false)
        }
    }

    private fun coreKeys(
        credits: List<ResolvedCredit>,
        standaloneKeys: Set<String>,
        identity: ArtistIdentityResolver,
    ): Set<String> =
        credits
            .filter { it.role == CreditRole.PRIMARY }
            .map { credit ->
                val lead = credit.segmentKeys.first()
                identity.canonicalKey(if (credit.isUnresolved && lead in standaloneKeys) lead else credit.key)
            }.toSet()

    private fun joinNames(names: List<String>): String =
        if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " & " + names.last()
}
