// Numeric bounds here describe token edit distance, catalog segmentation, BPM and calendar ranges.
@file:Suppress("MagicNumber")

package com.erhu.erhu.core.vybe

import java.text.Normalizer
import java.util.Locale

data class VybeNameMatch(
    val kind: EntityKind,
    val name: String,
    val artist: String,
    val songIds: List<Long>,
) {
    val id: String get() = kind.name + ":" + nameKey(name) + ":" + nameKey(artist)
    val label: String get() = if (kind == EntityKind.ARTIST) name else name + " · " + artist
}

/** Search-only catalog identities. Rebuilt when metadata/approved aliases change, never per frame. */
class VybeNameIndex(
    val songs: List<SongText>,
    aliases: Map<String, String> = emptyMap(),
) {
    private val aliases =
        (mapOf("ye" to "kanye west", "kanye" to "kanye west") + aliases)
            .mapKeys { nameKey(it.key) }
            .mapValues { nameKey(it.value) }
    val entries: List<VybeNameMatch>
    private val exact: Map<String, List<VybeNameMatch>>
    private val artistKeys: List<String>
    private val tokens: Map<String, List<String>>
    private val lookups = linkedMapOf<Triple<String, EntityKind?, Boolean>, List<VybeNameMatch>>()

    init {
        val artists =
            songs
                .flatMap { song ->
                    artistCredits(song.artist).map { it to song }
                }.groupBy { nameKey(it.first) }
        entries =
            artists.map { (_, rows) ->
                VybeNameMatch(EntityKind.ARTIST, rows.first().first, "", rows.map { it.second.songId }.distinct())
            } +
            songs
                .groupBy {
                    nameKey(it.album) to
                        nameKey(
                            it.artist
                                .split(Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+""", RegexOption.IGNORE_CASE))
                                .firstOrNull()
                                .orEmpty(),
                        )
                }.filterKeys { it.first.isNotBlank() }
                .map { (_, tracks) ->
                    VybeNameMatch(EntityKind.ALBUM, tracks.first().album, tracks.first().artist, tracks.map { it.songId }.distinct())
                } +
            songs.groupBy { nameKey(it.title) to nameKey(it.artist) }.filterKeys { it.first.isNotBlank() }.map { (_, tracks) ->
                VybeNameMatch(EntityKind.TITLE, tracks.first().title, tracks.first().artist, tracks.map { it.songId }.distinct())
            }
        exact = entries.groupBy { nameKey(it.name) }
        artistKeys =
            (entries.filter { it.kind == EntityKind.ARTIST }.map { nameKey(it.name) } + this.aliases.keys)
                .distinct()
                .sortedByDescending { it.length }
        tokens = entries.associate { it.id to nameWords(it.name) }
    }

    fun artistNames(name: String): Set<String> {
        val key = nameKey(name)
        return setOf(key, aliases[key] ?: key)
    }

    fun isArtist(
        credit: String,
        name: String,
    ): Boolean = artistNames(name).any { containsWords(nameWords(credit), it.split(' ')) }

    fun expandArtistAliases(text: String): String {
        var expanded = text
        for ((alias, canonical) in aliases.entries.sortedByDescending { it.key.length }) {
            if (exact[canonical].orEmpty().none { it.kind == EntityKind.ARTIST }) continue
            val expression = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(alias) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            expanded = expanded.replace(expression, canonical)
        }
        return expanded
    }

    fun find(
        text: String,
        preferred: EntityKind? = null,
        partial: Boolean = true,
    ): List<VybeNameMatch> {
        val key = Triple(nameKey(text), preferred, partial)
        lookups[key]?.let { return it }
        val found = findUncached(text, preferred, partial)
        if (lookups.size >= 64) lookups.remove(lookups.keys.first())
        lookups[key] = found
        return found
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // Ordered catalog precedence, with explicit rejection guards.
    private fun findUncached(
        text: String,
        preferred: EntityKind? = null,
        partial: Boolean = true,
    ): List<VybeNameMatch> {
        val key = nameKey(text.trim().trim('"', '“', '”'))
        if (key.isEmpty()) return emptyList()
        val direct = exact[key].orEmpty()
        if (direct.isNotEmpty() &&
            (preferred != EntityKind.ARTIST || direct.any { it.kind == EntityKind.ARTIST })
        ) {
            return prioritize(direct, preferred)
        }
        val alias =
            aliases[key]
                ?.let { canonical ->
                    entries.filter { it.kind == EntityKind.ARTIST && nameKey(it.name) == canonical }
                }.orEmpty()
        if (alias.isNotEmpty()) return alias
        if (direct.isNotEmpty()) return prioritize(direct, preferred)
        qualified(key, preferred, partial)?.let { return it }
        if (!partial || key.replace(" ", "").length < 3) return emptyList()
        val words = key.split(' ')
        val candidates =
            entries.filter { entry ->
                val tokens = tokens.getValue(entry.id)
                // Digits are exact: Bound 3 must never turn into Bound 2.
                words.filter { it.any(Char::isDigit) }.all { it in tokens } &&
                    words.all { word -> tokens.any { token -> token == word || (word.length >= 3 && token.startsWith(word)) } }
            }
        if (candidates.isNotEmpty()) return prioritize(candidates, preferred)
        if (words.any { it.length < 4 }) return emptyList()
        return prioritize(
            entries.filter { entry ->
                val tokens = tokens.getValue(entry.id)
                if (tokens.size != words.size) {
                    false
                } else {
                    val differences = words.zip(tokens).count { (a, c) -> a != c }
                    differences == 1 &&
                        words.zip(tokens).all { (a, c) ->
                            a == c || (!a.any(Char::isDigit) && !c.any(Char::isDigit) && a.length >= 5 && oneEdit(a, c))
                        }
                }
            },
            preferred,
        )
    }

    @Suppress("ReturnCount")
    private fun qualified(
        key: String,
        preferred: EntityKind?,
        partial: Boolean,
    ): List<VybeNameMatch>? {
        val by = key.lastIndexOf(" by ")
        if (by > 0) {
            val artist = key.substring(by + 4)
            val titles = key.substring(0, by)
            if (entries.any { it.kind == EntityKind.ARTIST && isArtist(it.name, artist) }) return scoped(titles, artist, preferred, partial)
        }
        // Longest known artist/alias first, including possessives with a missing apostrophe.
        for (artist in artistKeys) {
            for (prefix in listOf(artist + " s ", artist + "s ", artist + " ")) {
                if (key.startsWith(prefix) &&
                    key.length > prefix.length
                ) {
                    return scoped(key.removePrefix(prefix), artist, preferred, partial)
                }
            }
        }
        return null
    }

    @Suppress("ReturnCount")
    private fun scoped(
        title: String,
        artist: String,
        preferred: EntityKind?,
        partial: Boolean,
    ): List<VybeNameMatch> {
        val pool = entries.filter { it.kind != EntityKind.ARTIST && isArtist(it.artist, artist) }
        val exact = pool.filter { nameKey(it.name) == title || nameKey(it.name.substringBefore(" (feat", it.name)) == title }
        if (exact.isNotEmpty()) return prioritize(exact, preferred)
        if (!partial) return emptyList()
        val words = title.split(' ')
        return prioritize(
            pool.filter { entry ->
                val tokens = tokens.getValue(entry.id)
                words.all { it in tokens || (it.length >= 3 && !it.any(Char::isDigit) && tokens.any { t -> t.startsWith(it) }) }
            },
            preferred,
        )
    }

    private fun prioritize(
        matches: List<VybeNameMatch>,
        preferred: EntityKind?,
    ): List<VybeNameMatch> {
        val clean = matches.distinctBy { it.id }.sortedBy { it.id }
        return clean.filter { it.kind == preferred }.ifEmpty { clean }
    }
}

internal fun nameWords(text: String): List<String> = nameKey(text).split(' ').filter(String::isNotEmpty)

internal fun nameKey(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFKD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}$]+"""), " ")
        .trim()

internal fun artistCredits(text: String): List<String> =
    (listOf(text) + text.split(Regex("""\s+(?:feat\.?|ft\.?|featuring|with|&)\s+|/|;""", RegexOption.IGNORE_CASE)))
        .map(String::trim)
        .filter { it.isNotEmpty() && nameKey(it) !in setOf("unknown", "unknown artist", "various artists") }
        .distinct()

internal fun containsWords(
    haystack: List<String>,
    needle: List<String>,
): Boolean =
    needle.isNotEmpty() &&
        needle.size <= haystack.size &&
        (0..haystack.size - needle.size).any { haystack.subList(it, it + needle.size) == needle }

@Suppress("ReturnCount", "ComplexCondition") // Bounded single-edit/transposition comparison.
private fun oneEdit(a: String, b: String): Boolean {
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < a.length && j < b.length) {
        if (a[i] == b[j]) {
            i++
            j++
            continue
        }
        if (++edits > 1) return false
        if (i + 1 < a.length && j + 1 < b.length && a[i] == b[j + 1] && a[i + 1] == b[j]) {
            i += 2
            j += 2
        } else if (a.length > b.length) {
            i++
        } else if (a.length < b.length) {
            j++
        } else {
            i++
            j++
        }
    }
    return edits + (a.length - i) + (b.length - j) <= 1
}
