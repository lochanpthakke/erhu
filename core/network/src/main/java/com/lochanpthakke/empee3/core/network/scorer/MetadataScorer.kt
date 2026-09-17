package com.lochanpthakke.empee3.core.network.scorer

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import javax.inject.Inject
import javax.inject.Singleton
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
        ): MetadataCandidate {
            val titleSim =
                jaroWinklerDistance(
                    candidate.title.lowercase().trim(),
                    targetTitle.lowercase().trim(),
                )

            val artistSim =
                if (!targetArtist.isNullOrBlank() && candidate.artist.isNotBlank()) {
                    jaroWinklerDistance(
                        candidate.artist.lowercase().trim(),
                        targetArtist.lowercase().trim(),
                    )
                } else {
                    0.8f
                }

            val totalScore = (titleSim * 0.6f) + (artistSim * 0.4f)
            return candidate.copy(confidenceScore = totalScore)
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
