package com.lochanpthakke.empee3.core.network.scorer

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.network.lyrics.LyricsMatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
@Suppress("detekt:All")
class MetadataScorer
    @Inject
    constructor() {
        fun scoreCandidate(
            candidate: MetadataCandidate,
            targetTitle: String,
            targetArtist: String?,
            targetAlbum: String? = null,
            targetDurationMs: Long? = null,
            candidateDurationMs: Long? = candidate.durationMs,
        ): MetadataCandidate {
            val titleSim = titleSimilarity(candidate.title, targetTitle)

            val artistSim =
                if (!targetArtist.isNullOrBlank() &&
                    !targetArtist.equals("Unknown Artist", ignoreCase = true) &&
                    candidate.artist.isNotBlank() &&
                    !candidate.artist.equals("Unknown Artist", ignoreCase = true)
                ) {
                    // "A feat. B" against "A": the credited artist is the same; and the same names joined
                    // differently ("A/ B/C" against "A ft. B & C") are the same credit.
                    maxOf(
                        textSimilarity(candidate.artist, targetArtist),
                        textSimilarity(primaryArtist(candidate.artist), primaryArtist(targetArtist)),
                        creditSimilarity(candidate.artist, targetArtist),
                    )
                } else {
                    null
                }

            val albumSim =
                if (!targetAlbum.isNullOrBlank() &&
                    !targetAlbum.equals("Unknown Album", ignoreCase = true) &&
                    candidate.album.isNotBlank() &&
                    !candidate.album.equals("Unknown Album", ignoreCase = true)
                ) {
                    textSimilarity(candidate.album, targetAlbum)
                } else {
                    null
                }

            val hasArtist = artistSim != null
            val hasAlbum = albumSim != null

            var totalScore =
                when {
                    hasArtist && hasAlbum -> (titleSim * 0.5f) + (artistSim!! * 0.3f) + (albumSim!! * 0.2f)
                    hasArtist && !hasAlbum -> (titleSim * 0.625f) + (artistSim!! * 0.375f)
                    !hasArtist && hasAlbum -> (titleSim * 0.714f) + (albumSim!! * 0.286f)
                    else -> {
                        // When neither artist nor album confirmation is available, cap unverified title matches at < 0.85
                        if (titleSim >= 1.0f) 0.80f else (titleSim * 0.80f)
                    }
                }

            val effectiveCandidateDuration = candidateDurationMs ?: candidate.durationMs
            if (targetDurationMs != null && targetDurationMs > 0 && effectiveCandidateDuration != null && effectiveCandidateDuration > 0) {
                val deltaSec = abs(targetDurationMs - effectiveCandidateDuration) / 1000
                if (deltaSec > 30) {
                    totalScore *= 0.7f
                } else if (deltaSec > 15) {
                    totalScore *= 0.9f
                }
            }

            // A live / remix / acoustic / … recording is a different version, however similar the
            // names: such a candidate drops below the uncertain threshold and is never applied.
            val candidateVersion = LyricsMatcher.strictTags("${candidate.title} ${candidate.versionNote.orEmpty()}")
            if (candidateVersion != LyricsMatcher.strictTags(targetTitle)) {
                totalScore *= VERSION_MISMATCH_FACTOR
            }
            // A Dolby Atmos / spatial mix of the same song ranks just below the plain recording.
            val candidateMix = isSpecialMix(candidate.title) || isSpecialMix(candidate.versionNote)
            if (candidateMix && !isSpecialMix(targetTitle)) {
                totalScore *= SPECIAL_MIX_FACTOR
            }

            return candidate.copy(confidenceScore = totalScore.coerceIn(0.0f, 1.0f))
        }

        /**
         * Title similarity that doesn't penalise a featured-artist note on one side only, and
         * accepts one side of a two-sided title ("Mr. Lonely" in "911 / Mr. Lonely") as a near match.
         */
        private fun titleSimilarity(
            candidate: String,
            target: String,
        ): Float {
            val plainCandidate = stripFeaturing(candidate)
            val plainTarget = stripFeaturing(target)
            val sides = titleSides(plainCandidate).map(::foldForMatch)
            val oneSide = if (sides.size > 1 && foldForMatch(plainTarget) in sides) ONE_SIDE_OF_TITLE else 0f
            return maxOf(textSimilarity(candidate, target), textSimilarity(plainCandidate, plainTarget), oneSide)
        }

        /**
         * Similarity that ignores spelling noise: case, accents, typographic quotes and dashes, and
         * separators ("Tyler/ The Creator" = "Tyler, The Creator", "IGOR’S" = "IGOR'S"). The plain
         * comparison is kept as a floor, so this can only find two spellings closer, never further.
         */
        private fun textSimilarity(
            a: String,
            b: String,
        ): Float =
            max(
                jaroWinklerDistance(a.lowercase().trim(), b.lowercase().trim()),
                jaroWinklerDistance(foldForMatch(a), foldForMatch(b)),
            )

        internal fun jaroWinklerDistance(
            s1: String,
            s2: String,
        ): Float {
            if (s1 == s2) return 1.0f
            if (s1.isEmpty() || s2.isEmpty()) return 0.0f

            val matchDistance = max(s1.length, s2.length) / 2 - 1
            val s1Matches = BooleanArray(s1.length)
            val s2Matches = BooleanArray(s2.length)

            var matches = 0
            var transpositions = 0

            for (i in s1.indices) {
                val start = max(0, i - matchDistance)
                val end = min(i + matchDistance + 1, s2.length)

                for (j in start until end) {
                    if (s2Matches[j]) continue
                    if (s1[i] != s2[j]) continue
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }

            if (matches == 0) return 0.0f

            var k = 0
            for (i in s1.indices) {
                if (!s1Matches[i]) continue
                while (!s2Matches[k]) k++
                if (s1[i] != s2[k]) transpositions++
                k++
            }

            val jaro =
                (
                    matches.toFloat() / s1.length +
                        matches.toFloat() / s2.length +
                        (matches - transpositions / 2.0f) / matches
                ) / 3.0f

            var prefix = 0
            for (i in 0 until min(4, min(s1.length, s2.length))) {
                if (s1[i] == s2[i]) prefix++ else break
            }

            return jaro + prefix * 0.1f * (1.0f - jaro)
        }

        companion object {
            /** Applied when version markers differ; keeps even a perfect name match below [UNCERTAIN_THRESHOLD]. */
            const val VERSION_MISMATCH_FACTOR = 0.5f

            /** Enough to rank a special mix below the plain recording when everything else is equal. */
            const val SPECIAL_MIX_FACTOR = 0.95f

            /** "Mr. Lonely" for "911 / Mr. Lonely": strong, but below an exact title. */
            const val ONE_SIDE_OF_TITLE = 0.95f
        }
    }

private val ACCENTS = Regex("""\p{Mn}+""")
private val APOSTROPHES = Regex("""['‘’ʼ`´]""")
private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")
private val FEATURING = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

/** Lowercase, accents and apostrophes removed, every other separator a single space. */
internal fun foldForMatch(text: String): String =
    java.text.Normalizer
        .normalize(text, java.text.Normalizer.Form.NFKD)
        .replace(ACCENTS, "")
        .lowercase()
        .replace(APOSTROPHES, "")
        .replace(NOT_WORD, " ")
        .trim()

/** The credit without its "feat." part: "Kanye West feat. Pusha T" -> "Kanye West". */
internal fun primaryArtist(credit: String): String = credit.replace(FEATURING, "").trim()
