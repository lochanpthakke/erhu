package com.lochanpthakke.empee3.core.network.scorer

import com.lochanpthakke.empee3.core.model.MetadataCandidate
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
            val titleSim =
                jaroWinklerDistance(
                    candidate.title.lowercase().trim(),
                    targetTitle.lowercase().trim(),
                )

            val artistSim =
                if (!targetArtist.isNullOrBlank() &&
                    !targetArtist.equals("Unknown Artist", ignoreCase = true) &&
                    candidate.artist.isNotBlank() &&
                    !candidate.artist.equals("Unknown Artist", ignoreCase = true)
                ) {
                    jaroWinklerDistance(
                        candidate.artist.lowercase().trim(),
                        targetArtist.lowercase().trim(),
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
                    jaroWinklerDistance(
                        candidate.album.lowercase().trim(),
                        targetAlbum.lowercase().trim(),
                    )
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

            return candidate.copy(confidenceScore = totalScore.coerceIn(0.0f, 1.0f))
        }

        private fun jaroWinklerDistance(
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
    }
