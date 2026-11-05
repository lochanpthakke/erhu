package com.erhu.erhu.core.network.artwork

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object AlbumArtworkRanking {
    private val editionWords =
        Regex(
            "(?i)\\b(deluxe|remaster(?:ed)?|anniversary|expanded|bonus|live|remix(?:ed)?|mono|stereo|explicit|clean|" +
                "immersion|collector|promo(?:tion|tional)?|quadraphonic|box\\s*set|super\\s*deluxe)\\b",
        )

    fun normalized(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKD)
            .replace(
                Regex("\\p{M}+"),
                "",
            ).lowercase(Locale.ROOT)
            .replace('&', ' ')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun edition(value: String): Set<String> =
        editionWords
            .findAll(value)
            .map {
                when {
                    it.value.lowercase().startsWith("remaster") -> "remaster"
                    it.value.lowercase().startsWith("remix") -> "remix"
                    else -> it.value.lowercase()
                }
            }.toSet()
            .let { markers ->
                if (markers.isEmpty()) {
                    markers
                } else {
                    markers +
                        Regex("\\b(?:19|20)\\d{2}\\b").findAll(value).map { it.value }.toSet()
                }
            }

    fun searchTitle(value: String): String =
        value
            .replace(
                Regex("(?i)[(\\[].*?(?:deluxe|remaster|anniversary|expanded|bonus|edition).*?[)\\]]"),
                " ",
            ).replace(Regex("(?i)\\s*[-\u2013]\\s*(?:single|ep|deluxe.*|remaster.*)$"), " ")
            .trim()

    private fun base(value: String) =
        normalized(
            value
                .replace(
                    Regex("(?i)[(\\[].*?(?:deluxe|remaster|anniversary|expanded|bonus|edition|explicit|clean|stereo|mono).*?[)\\]]"),
                    " ",
                ).replace(
                    Regex("(?i)\\s*[-–]\\s*(?:deluxe|remaster|anniversary|expanded|bonus).*"),
                    " ",
                ).replace(Regex("(?i)\\s*[-–]\\s*(single|ep)$"), " "),
        )

    fun similarity(
        a: String,
        b: String,
    ): Double {
        val x = normalized(a)
        val y = normalized(b)
        if (x.isBlank() || y.isBlank()) return 0.0
        if (x == y) return 1.0
        val xa = x.split(' ').toSet()
        val ya = y.split(' ').toSet()
        val overlap = xa.intersect(ya).size.toDouble() / maxOf(xa.size, ya.size)
        val distance = IntArray(y.length + 1) { it }
        for (i in x.indices) {
            var diagonal = distance[0]
            distance[0] = i + 1
            for (j in y.indices) {
                val old = distance[j + 1]
                distance[j + 1] =
                    minOf(distance[j + 1] + 1, distance[j] + 1, diagonal + if (x[i] == y[j]) 0 else 1)
                diagonal = old
            }
        }
        return maxOf(overlap, 1.0 - distance[y.length].toDouble() / maxOf(x.length, y.length))
    }

    fun rank(
        query: AlbumArtworkQuery,
        candidate: AlbumArtworkCandidate,
    ): AlbumArtworkCandidate? {
        val album = similarity(base(query.album), base(candidate.album))
        val requestedArtist = query.artist.replace(Regex("(?i)\\s+(?:feat\\.?|featuring|ft\\.?).*"), "")
        val knownArtist = normalized(requestedArtist) !in setOf("", "unknown", "unknown artist", "various", "various artists")
        val artist =
            if (knownArtist) {
                similarity(
                    requestedArtist,
                    candidate.artist.replace(Regex("(?i)\\s+(?:feat\\.?|featuring|ft\\.?).*"), ""),
                )
            } else {
                0.0
            }
        if (album < 0.60 || (knownArtist && artist < 0.55)) return null
        val requestedEdition = edition(query.album)
        val foundEdition = edition(candidate.album + " " + candidate.edition)
        val reasons = candidate.reviewReasons.toMutableList()
        val idMatch = query.releaseId != null && query.releaseId == candidate.releaseId
        if (!idMatch && query.year > 0 && candidate.year > 0 && abs(query.year - candidate.year) > 1) reasons += "Release year needs review"
        if (!knownArtist || artist < 0.92) reasons += "Artist needs review"
        if (album < 0.95) reasons += "Album title needs review"
        if (requestedEdition != foundEdition) reasons += "Alternate edition"
        if (query.kind != ArtworkReleaseKind.UNKNOWN &&
            candidate.kind != ArtworkReleaseKind.UNKNOWN &&
            query.kind != candidate.kind
        ) {
            reasons += "Different release type"
        }
        if (candidate.source == "TheAudioDB") reasons += "Community artwork: confirm cover"
        val yearMatch = query.year > 0 && candidate.year > 0 && abs(query.year - candidate.year) <= 1
        val score =
            (
                album * 0.55 + artist * 0.40 + (if (yearMatch || query.year == 0) 0.03 else 0.0) + (if (idMatch) 0.10 else 0.02) -
                    (
                        if (requestedEdition !=
                            foundEdition
                        ) {
                            0.18
                        } else {
                            0.0
                        }
                    ) - (if ("Different release type" in reasons) 0.20 else 0.0) -
                    (if ("Release year needs review" in reasons) 0.10 else 0.0)
            ).coerceIn(0.0, 1.0)
        return candidate.copy(score = score, reviewReasons = reasons.distinct())
    }

    fun deduplicate(candidates: List<AlbumArtworkCandidate>): List<AlbumArtworkCandidate> {
        val result = mutableListOf<AlbumArtworkCandidate>()
        for (candidate in candidates.sortedWith(
            compareByDescending<AlbumArtworkCandidate> {
                it.score
            }.thenByDescending { minOf(it.width, it.height) },
        )) {
            val existing =
                result.indexOfFirst { previous ->
                    normalized(previous.album) == normalized(candidate.album) &&
                        edition(previous.edition) == edition(candidate.edition) &&
                        previous.kind == candidate.kind &&
                        similarity(previous.artist, candidate.artist) >= 0.95 &&
                        (
                            (candidate.contentHash.isNotEmpty() && candidate.contentHash == previous.contentHash) ||
                                sameVisual(previous.visualHash, candidate.visualHash)
                        )
                }
            if (existing <
                0
            ) {
                result += candidate
            } else {
                result[existing] =
                    result[existing].copy(
                        otherSources =
                            (result[existing].otherSources + candidate.source + candidate.otherSources).distinct().filter {
                                it !=
                                    result[existing].source
                            },
                    )
            }
        }
        return result
    }

    private fun sameVisual(
        a: String,
        b: String,
    ): Boolean =
        runCatching {
            if (a.isEmpty() || b.isEmpty()) return false
            val x = a.split(':')
            val y = b.split(':')
            val colorsA = x[1].split(',').map(String::toInt)
            val colorsB = y[1].split(',').map(String::toInt)
            java.lang.Long.bitCount(java.lang.Long.parseUnsignedLong(x[0], 16) xor java.lang.Long.parseUnsignedLong(y[0], 16)) <= 2 &&
                colorsA.indices.all { abs(colorsA[it] - colorsB[it]) <= 10 }
        }.getOrDefault(false)
}
