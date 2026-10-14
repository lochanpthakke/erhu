package com.lochanpthakke.empee3.core.network.cleaner

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("MagicNumber")
class TitleCandidateGenerator
    @Inject
    constructor(
        private val filenameCleaner: FilenameCleaner,
    ) {
        fun generateCandidates(
            rawFilename: String,
            customBlocklistTokens: List<String> = emptyList(),
        ): List<String> {
            val primaryResult = filenameCleaner.cleanFilenameAndExtractJunk(rawFilename, customBlocklistTokens).cleaned
            val primaryTitle = primaryResult.title.trim()
            val artistGuess = primaryResult.artistGuess?.trim()
            val rawTitle = rawFilename.substringBeforeLast('.').replace('_', ' ').trim()

            val candidates = mutableListOf<String>()

            // 1. Clean head title (artistGuess if split off by " - " e.g. "Diwali" or "Love Me Again")
            if (!artistGuess.isNullOrBlank() && !artistGuess.equals("Unknown Artist", ignoreCase = true)) {
                candidates.add(artistGuess)
            }

            // 2. Strip trailing enclosure suffix [ ... ] or ( ... )
            val trailingEnclosureRegex = Regex("\\s*[\\[(][^\\])]+[\\])]\\s*$")
            val rawNoEnclosure = rawTitle.replace(trailingEnclosureRegex, "").trim()
            if (rawNoEnclosure.isNotBlank() && rawNoEnclosure.length >= 2) {
                candidates.add(rawNoEnclosure)
            }

            // 3. Primary cleaned title
            if (primaryTitle.isNotBlank()) {
                candidates.add(primaryTitle)
            }

            // 4. Full raw title
            if (rawTitle.isNotBlank()) {
                candidates.add(rawTitle)
            }

            // 5. Fallback: Strip trailing word if raw title has >= 2 words
            val words = rawTitle.split("\\s+".toRegex())
            if (words.size >= 2) {
                val headWords = words.dropLast(1).joinToString(" ").trim()
                // Dropping a word inside "(feat. A & B)" leaves a broken "(feat. A &": not a title.
                val balanced = headWords.count { it == '(' } == headWords.count { it == ')' }
                if (headWords.isNotBlank() && headWords.length >= 2 && balanced) {
                    candidates.add(headWords)
                }
            }

            return candidates.distinct().filter { it.length >= 2 }.take(3)
        }
    }
