package com.lochanpthakke.empee3.core.network.cleaner

import javax.inject.Inject
import javax.inject.Singleton

data class CleanedFilename(
    val title: String,
    val artistGuess: String? = null,
)

@Singleton
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
class FilenameCleaner
    @Inject
    constructor() {
        fun cleanFilename(
            rawFilename: String,
            customBlocklistTokens: List<String> = emptyList(),
        ): CleanedFilename {
            var clean = rawFilename.substringBeforeLast('.')

            clean = clean.replace(LEADING_TRACK_NUMBER_REGEX, "")

            val allBlocklist = DEFAULT_BLOCKLIST + customBlocklistTokens
            for (token in allBlocklist) {
                if (token.isNotBlank()) {
                    clean = clean.replace(token, "", ignoreCase = true)
                }
            }

            clean = clean.replace('_', ' ')
            clean = clean.replace(EXTRA_WHITESPACE_REGEX, " ").trim(' ', '-', '_')

            if (clean.contains(" - ")) {
                val parts = clean.split(" - ")
                if (parts.size == 2) {
                    return CleanedFilename(
                        title = parts[1].trim(),
                        artistGuess = parts[0].trim(),
                    )
                }
            }

            return CleanedFilename(title = clean)
        }

        companion object {
            val DEFAULT_BLOCKLIST =
                listOf(
                    "pagalworld",
                    "songsmpk",
                    "pagalnew",
                    "djpunjab",
                    "downloadming",
                    "mr-jatt",
                    "riskyjatt",
                    "pendujatt",
                    "www.",
                    ".com",
                    ".in",
                    ".net",
                    "[320kbps]",
                    "[128kbps]",
                    "320kbps",
                    "128kbps",
                )

            private val LEADING_TRACK_NUMBER_REGEX = Regex("^[0-9]{1,3}[\\s._-]+")
            private val EXTRA_WHITESPACE_REGEX = Regex("\\s+")
        }
    }
