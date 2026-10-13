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
            rawFilenameOrTitle: String,
            customBlocklistTokens: List<String> = emptyList(),
        ): List<String> {
            val rawTitle = rawFilenameOrTitle.substringBeforeLast('.').replace('_', ' ').trim()
            val candidates = mutableListOf<String>()

            // Step 1: Complete original title
            if (rawTitle.isNotBlank()) {
                candidates.add(rawTitle)
            }

            // Step 2: Fallback to title before any hyphen "-", stripping bracket/enclosure noise
            if (rawTitle.contains("-")) {
                val headRaw = rawTitle.substringBefore("-").trim()
                val cleanHead = headRaw.replace(Regex("""\[.*?\]|\(.*?\)"""), "").replace(Regex("\\s+"), " ").trim(' ', '-', '_', '[', ']', '(', ')')
                if (cleanHead.isNotBlank() && cleanHead.length >= 2 && cleanHead != rawTitle) {
                    candidates.add(cleanHead)
                }
            }

            return candidates.distinct().filter { it.isNotBlank() && it.length >= 2 }.take(2)
        }
    }
