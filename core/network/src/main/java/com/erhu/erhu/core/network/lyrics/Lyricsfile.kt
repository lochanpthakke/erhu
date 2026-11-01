package com.erhu.erhu.core.network.lyrics

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import java.util.Locale

/**
 * LRCLIB's Lyricsfile: a YAML document with `lines` (`text`, `start_ms`, `end_ms`) and, for
 * word-synced lyrics, `words` per line (`text`, `start_ms`). Converted to enhanced LRC
 * (`[mm:ss.mmm]<mm:ss.mmm>word…`), which the app's lyrics parser already reads, so genuine word
 * timing reaches the karaoke highlight unchanged. A line whose words are not all timed (in order)
 * keeps only its line time; nothing is estimated.
 */
object Lyricsfile {
    private const val MAX_CODE_POINTS = 2_000_000
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_SECOND = 1_000L

    /** Enhanced LRC with the file's word timing, or null when it has none (or cannot be read). */
    @Suppress("ReturnCount") // unreadable, or no timed word at all
    fun toEnhancedLrc(yaml: String?): String? {
        val document = load(yaml) ?: return null
        val lines = (document["lines"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val written = lines.mapNotNull(::lineToLrc)
        if (written.none { it.timedWords }) return null
        val offset = ((document["metadata"] as? Map<*, *>)?.get("offset_ms") as? Number)?.toLong()?.takeIf { it != 0L }
        val header = offset?.let { listOf("[offset:$it]") }.orEmpty()
        return (header + written.map { it.text }).joinToString("\n")
    }

    /** Whether the Lyricsfile says the track is instrumental. */
    fun isInstrumental(yaml: String?): Boolean = ((load(yaml)?.get("metadata") as? Map<*, *>)?.get("instrumental") as? Boolean) == true

    private class LrcLine(
        val text: String,
        val timedWords: Boolean,
    )

    private fun lineToLrc(line: Map<*, *>): LrcLine? {
        val start = (line["start_ms"] as? Number)?.toLong() ?: return null
        val words = (line["words"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val timed =
            words
                .map { (it["start_ms"] as? Number)?.toLong() to it["text"]?.toString().orEmpty() }
                .takeIf { list -> list.isNotEmpty() && list.all { it.first != null } }
                ?.map { it.first!! to it.second }
                ?.takeIf { list -> list.zipWithNext().all { (a, b) -> a.first <= b.first } }
        return if (timed != null) {
            LrcLine("[${stamp(start)}]" + timed.joinToString("") { (time, text) -> "<${stamp(time)}>$text" }, timedWords = true)
        } else {
            val text = line["text"]?.toString() ?: words.joinToString("") { it["text"]?.toString().orEmpty() }
            LrcLine("[${stamp(start)}]$text", timedWords = false)
        }
    }

    @Suppress("SwallowedException")
    private fun load(yaml: String?): Map<*, *>? {
        if (yaml.isNullOrBlank()) return null
        return try {
            Load(LoadSettings.builder().setCodePointLimit(MAX_CODE_POINTS).build()).loadFromString(yaml) as? Map<*, *>
        } catch (e: YamlEngineException) {
            null // A broken Lyricsfile: the line-timed lyrics are used instead.
        }
    }

    /** `mm:ss.mmm` (milliseconds kept exactly; the parser reads up to three fraction digits). */
    fun stamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return "%02d:%02d.%03d".format(Locale.ROOT, safe / MS_PER_MINUTE, safe % MS_PER_MINUTE / MS_PER_SECOND, safe % MS_PER_SECOND)
    }
}
