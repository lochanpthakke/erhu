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
        private val recognizedAudioExtensions = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma", "alac", "aiff")

        @Suppress("CyclomaticComplexMethod")
        fun generateCandidates(
            rawFilenameOrTitle: String,
            customBlocklistTokens: List<String> = emptyList(),
        ): List<String> {
            val extension = rawFilenameOrTitle.substringAfterLast('.', "")
            val rawTitle =
                if (extension.isNotBlank() && recognizedAudioExtensions.contains(extension.lowercase())) {
                    rawFilenameOrTitle.substringBeforeLast('.').replace('_', ' ').trim()
                } else {
                    rawFilenameOrTitle.replace('_', ' ').trim()
                }

            val normalizedTitle = autoCompleteEnclosures(rawTitle)
            val candidates = mutableListOf<String>()

            // Step 1: Complete original title
            if (normalizedTitle.isNotBlank()) {
                candidates.add(normalizedTitle)
            }

            // Step 2: Fallback to title before any hyphen "-", stripping bracket/enclosure noise
            if (normalizedTitle.contains("-")) {
                val headRaw = normalizedTitle.substringBefore("-").trim()
                val cleanHead =
                    headRaw
                        .replace(
                            Regex("""\[.*?\]|\(.*?\)"""),
                            "",
                        ).replace(Regex("\\s+"), " ")
                        .trim(' ', '-', '_', '[', ']', '(', ')')
                if (cleanHead.isNotBlank() && cleanHead.length >= 2 && cleanHead != normalizedTitle) {
                    candidates.add(cleanHead)
                }
            }

            // Step 3: Strip featured artist clause e.g. "(feat. ...)", "(ft. ...)", "ft. ..."
            val featRegex = Regex("(?i)\\s*[(\\[]?(feat|ft|featuring|with)\\.?\\s+[^)\\]]+[)\\]]?")
            val titleNoFeat = normalizedTitle.replace(featRegex, "").trim(' ', '-', '_', '[', ']', '(', ')')
            // Only when a clause was really removed: trimming alone would cut a title's own ")".
            val featRemoved = featRegex.containsMatchIn(normalizedTitle)
            if (featRemoved && titleNoFeat.length >= 2 && titleNoFeat != normalizedTitle) {
                candidates.add(titleNoFeat)
            }

            // Step 4: Strip version tag e.g. "(Remix)", "(Radio Edit)", "(Live)", "(Remastered)"
            val versionRegex = Regex("(?i)\\s*[(\\[](remix|radio edit|live|remastered|extended mix|acoustic)[^)]*[)\\]]")
            val titleNoVersion = normalizedTitle.replace(versionRegex, "").trim(' ', '-', '_', '[', ']', '(', ')')
            val versionRemoved = versionRegex.containsMatchIn(normalizedTitle)
            if (versionRemoved && titleNoVersion.length >= 2 && titleNoVersion != normalizedTitle) {
                candidates.add(titleNoVersion)
            }

            // Step 5: Watermark-cleaned title via FilenameCleaner
            val cleanedResult = filenameCleaner.cleanFilename(normalizedTitle, customBlocklistTokens).title.trim()
            if (cleanedResult.isNotBlank() && cleanedResult.length >= 2) {
                candidates.add(cleanedResult)
            }

            return candidates.distinct().filter { it.isNotBlank() && it.length >= 2 }.take(3)
        }

        private fun autoCompleteEnclosures(title: String): String {
            var result = title
            val openParens = result.count { it == '(' }
            val closeParens = result.count { it == ')' }
            if (openParens > closeParens) {
                result += ")".repeat(openParens - closeParens)
            }

            val openBrackets = result.count { it == '[' }
            val closeBrackets = result.count { it == ']' }
            if (openBrackets > closeBrackets) {
                result += "]".repeat(openBrackets - closeBrackets)
            }
            return result
        }
    }
