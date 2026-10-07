package com.lochanpthakke.empee3.feature.library

/** A word of a line with its own timestamp (enhanced LRC `<mm:ss.xx>`). */
data class LyricWord(
    val timeMs: Long,
    val text: String,
)

/** A timed lyric line; [words] only when the file times every word of it. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord>? = null,
)

/** Lyrics as the file provides them: timed (LRC) or plain text. */
sealed interface Lyrics {
    data class Synced(
        val lines: List<LyricLine>,
    ) : Lyrics {
        /** Whether any line carries word timestamps. */
        val hasWordTiming: Boolean = lines.any { it.words != null }

        private val times = LongArray(lines.size) { lines[it].timeMs }

        /** The line playing at [positionMs] (the last one started), -1 before the first. Binary search. */
        fun lineAt(positionMs: Long): Int = lastStartedAt(times, positionMs)

        /** The word of [line] playing at [positionMs], -1 before its first word or without word timing. */
        fun wordAt(
            line: Int,
            positionMs: Long,
        ): Int {
            val words = lines.getOrNull(line)?.words ?: return -1
            var low = 0
            var high = words.lastIndex
            var found = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (words[mid].timeMs <= positionMs) {
                    found = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return found
        }
    }

    data class Plain(
        val lines: List<String>,
    ) : Lyrics
}

private fun lastStartedAt(
    times: LongArray,
    positionMs: Long,
): Int {
    var low = 0
    var high = times.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (times[mid] <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/**
 * Reads lyrics text from a file's tags. LRC is recognised by its line timestamps
 * (`[mm:ss]`, `[mm:ss.xx]`, several per line for repeated lines) with optional
 * enhanced-LRC word timestamps (`<mm:ss.xx>`) and an `[offset:±ms]` tag; other
 * `[tag:value]` lines (artist, title, …) are dropped. Text without any valid line
 * timestamp is plain lyrics. Malformed timestamps are ignored, never fatal.
 */
object LyricsParser {
    private val LINE_TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TIME = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val META = Regex("""^\[[a-zA-Z#]+:.*]$""")
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val CENTI_DIGITS = 2
    private const val CENTI_TO_MS = 10L
    private const val DECI_TO_MS = 100L
    private const val MILLI_DIGITS = 3
    private const val FRACTION_GROUP = 3

    fun parse(text: String?): Lyrics? = text?.takeIf { it.isNotBlank() }?.let(::parseText)

    private fun parseText(text: String): Lyrics? {
        val rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        // LRC offset: positive means the lyrics come earlier.
        val offset =
            rawLines.firstNotNullOfOrNull {
                OFFSET
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
            } ?: 0L
        val synced = mutableListOf<LyricLine>()
        for (raw in rawLines) {
            val line = raw.trim()
            val stamps = mutableListOf<Long>()
            var rest = line
            while (true) {
                val match = LINE_TIME.matchAt(rest, 0) ?: break
                toMs(match)?.let(stamps::add)
                rest = rest.substring(match.range.last + 1)
            }
            if (stamps.isEmpty()) continue
            val words = parseWords(rest, offset)
            val content = if (words != null) words.joinToString("") { it.text }.trim() else rest.trim()
            stamps.forEach { synced += LyricLine((it - offset).coerceAtLeast(0L), content, words) }
        }
        if (synced.isNotEmpty()) return Lyrics.Synced(synced.sortedBy { it.timeMs })
        val plain =
            rawLines
                .map { it.trimEnd() }
                .filterNot {
                    META.matches(
                        it.trim(),
                    )
                }.dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
        return if (plain.isEmpty()) null else Lyrics.Plain(plain)
    }

    /** Words of a line with `<mm:ss.xx>` stamps before them; null if the line has no word timing. */
    private fun parseWords(
        content: String,
        offset: Long,
    ): List<LyricWord>? {
        val stamps = WORD_TIME.findAll(content).toList()
        if (stamps.isEmpty()) return null
        val words = mutableListOf<LyricWord>()
        stamps.forEachIndexed { index, match ->
            val time = toMs(match) ?: return@forEachIndexed
            val end = stamps.getOrNull(index + 1)?.range?.first ?: content.length
            val word = content.substring(match.range.last + 1, end)
            if (word.isNotBlank()) words += LyricWord((time - offset).coerceAtLeast(0L), word)
        }
        // Keep the text exactly as written (spaces inside the words); untimed text before the first stamp is lost by design.
        return words.takeIf { it.isNotEmpty() }?.sortedBy { it.timeMs }
    }

    /** Milliseconds of a `mm:ss(.fff)` stamp; null when it is malformed (seconds of 60 or more). */
    private fun toMs(match: MatchResult): Long? {
        // The regex guarantees digits; only the seconds can still be out of range.
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[FRACTION_GROUP]
        val fractionMs =
            when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * DECI_TO_MS
                CENTI_DIGITS -> fraction.toLong() * CENTI_TO_MS
                else -> fraction.take(MILLI_DIGITS).toLong()
            }
        return ((minutes * SECONDS_PER_MINUTE + seconds) * MS_PER_SECOND + fractionMs).takeIf { seconds < SECONDS_PER_MINUTE }
    }
}
