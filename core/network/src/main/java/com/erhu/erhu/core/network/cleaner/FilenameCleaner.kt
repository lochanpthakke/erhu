package com.erhu.erhu.core.network.cleaner

import javax.inject.Inject
import javax.inject.Singleton

data class CleanedFilename(
    val title: String,
    val artistGuess: String? = null,
)

data class CleanedFilenameResult(
    val cleaned: CleanedFilename,
    val extractedJunkTokens: List<String> = emptyList(),
)

@Singleton
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
class FilenameCleaner
    @Inject
    constructor() {
        fun cleanFilename(
            rawFilename: String,
            customBlocklistTokens: List<String> = emptyList(),
        ): CleanedFilename = cleanFilenameAndExtractJunk(rawFilename, customBlocklistTokens).cleaned

        fun cleanFilenameAndExtractJunk(
            rawFilename: String,
            customBlocklistTokens: List<String> = emptyList(),
        ): CleanedFilenameResult {
            val extractedJunk = mutableListOf<String>()
            // Only a real audio extension is removed: titles are cleaned too, and "Mr. Lonely (feat. X)"
            // must not lose everything after "feat.".
            var clean = stripAudioExtension(rawFilename)

            val trackNumMatch = LEADING_TRACK_NUMBER_REGEX.find(clean)
            if (trackNumMatch != null) {
                clean = clean.replace(LEADING_TRACK_NUMBER_REGEX, "")
            }

            // Extract attribution watermark patterns first before blocklist word stripping
            ATTRIBUTION_WATERMARK_REGEX.findAll(clean).forEach { match ->
                val rawToken = match.value.trim(' ', '-', '_', '[', ']', '(', ')')
                val token = rawToken.replace('_', ' ').replace(EXTRA_WHITESPACE_REGEX, " ").trim()
                if (token.length >= 3) {
                    extractedJunk.add(token)
                }
            }
            clean = clean.replace(ATTRIBUTION_WATERMARK_REGEX, "")

            // Extract domain and website watermark patterns
            DOMAIN_WATERMARK_REGEX.findAll(clean).forEach { match ->
                val token = match.value.trim('[', ']', '(', ')', ' ')
                if (token.length >= 3) {
                    extractedJunk.add(token)
                }
            }

            // Extract bitrate patterns
            BITRATE_REGEX.findAll(clean).forEach { match ->
                val token = match.value.trim('[', ']', '(', ')', ' ')
                if (token.length >= 3) {
                    extractedJunk.add(token)
                }
            }

            val allBlocklist = (DEFAULT_BLOCKLIST + customBlocklistTokens).distinct()
            for (token in allBlocklist) {
                if (token.isNotBlank()) {
                    val pattern =
                        if (token.startsWith(".")) {
                            Regex("(?i)" + Regex.escape(token) + "\\b")
                        } else {
                            Regex("(?i)\\b" + Regex.escape(token) + "\\b")
                        }
                    if (pattern.containsMatchIn(clean)) {
                        clean = clean.replace(pattern, "")
                        extractedJunk.add(token.trim('[', ']', '(', ')', ' '))
                    }
                }
            }

            // Remove matched domain and bitrate substrings from clean title
            clean = clean.replace(DOMAIN_WATERMARK_REGEX, "")
            clean = clean.replace(BITRATE_REGEX, "")
            clean = clean.replace('_', ' ')
            clean = clean.replace(BRACKET_NOISE_REGEX, "")
            // Brackets emptied by the removals above ("[320kbps]" -> "[]") go too.
            clean = clean.replace(EMPTY_BRACKETS_REGEX, "")
            clean = trimUnmatchedBrackets(clean.replace(EXTRA_WHITESPACE_REGEX, " ").trim(' ', '-', '_'))

            val cleanedModel =
                if (clean.contains(" - ")) {
                    val parts = clean.split(" - ")
                    if (parts.size == 2) {
                        CleanedFilename(
                            title = parts[1].trim(),
                            artistGuess = parts[0].trim(),
                        )
                    } else {
                        CleanedFilename(title = clean)
                    }
                } else {
                    CleanedFilename(title = clean)
                }

            return CleanedFilenameResult(
                cleaned = cleanedModel,
                extractedJunkTokens = extractedJunk.distinct().filter { it.isNotBlank() && it.length >= 2 },
            )
        }

        companion object {
            /**
             * Strips brackets left dangling at either end (e.g. by removed watermarks), but keeps a
             * balanced one: "Mr. Lonely (feat. Frank Ocean & Steve Lacy)" used to lose its ")".
             */
            internal fun trimUnmatchedBrackets(text: String): String {
                var result = text.trim()
                while (true) {
                    val before = result
                    if (result.endsWith(")") && result.count { it == ')' } > result.count { it == '(' }) result = result.dropLast(1)
                    if (result.endsWith("]") && result.count { it == ']' } > result.count { it == '[' }) result = result.dropLast(1)
                    if (result.startsWith("(") && result.count { it == '(' } > result.count { it == ')' }) result = result.drop(1)
                    if (result.startsWith("[") && result.count { it == '[' } > result.count { it == ']' }) result = result.drop(1)
                    // An opener at the very end (or a closer at the very start) never encloses anything.
                    if (result.endsWith("(") || result.endsWith("[")) result = result.dropLast(1)
                    if (result.startsWith(")") || result.startsWith("]")) result = result.drop(1)
                    result = result.trim(' ', '-', '_')
                    if (result == before) return result
                }
            }

            private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "ogg", "opus", "wav", "aac", "wma", "alac", "aiff", "aif")

            /** [name] without its audio file extension; a title without one is returned whole. */
            internal fun stripAudioExtension(name: String): String {
                val extension = name.substringAfterLast('.', "")
                return if (extension.lowercase() in AUDIO_EXTENSIONS) name.substringBeforeLast('.') else name
            }

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
            private val EMPTY_BRACKETS_REGEX = Regex("""\(\s*\)|\[\s*]""")
            private val DOMAIN_WATERMARK_REGEX = Regex("(?i)[a-zA-Z0-9-]+\\.(com|in|org|net|co|site|xyz|cc|io|club|mobi|audio|mp3)")
            private val BITRATE_REGEX = Regex("(?i)\\[?\\d{2,3}\\s*kbps]?|\\(128kbps\\)|\\(320kbps\\)|320kbps|128kbps|vbr|flac\\s*hd")
            private val BRACKET_NOISE_REGEX = Regex("(?i)\\[(official|video|audio|lyric|hd|4k|clean|remastered|mp3|download|single)]")
            private val ATTRIBUTION_WATERMARK_REGEX =
                Regex("(?i)[_\\s-]+(tagged|downloaded|uploaded|ripped)[_\\s]+(by|from)[_\\s]+[a-zA-Z0-9._-]+$")
        }
    }
