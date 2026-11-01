package com.erhu.erhu.core.network.lyrics

/** Reject plain text, invalid/out-of-order times and timestamps beyond this recording. */
internal fun usableSyncedLyrics(
    text: String?,
    durationMs: Long = 0,
): Boolean {
    if (text.isNullOrBlank()) return false
    val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    var previous = -1L
    var timedLines = 0
    for (line in text.lineSequence().map(String::trim).filter(String::isNotEmpty)) {
        if (Regex("""\[(ar|al|ti|au|by|offset|re|ve|length):.*]""", RegexOption.IGNORE_CASE).matches(line)) continue
        val stamps = stamp.findAll(line).toList()
        if (stamps.isEmpty() || stamps.first().range.first != 0) return false
        for (match in stamps) {
            val seconds = match.groupValues[2].toLong()
            if (seconds >= 60) return false
            val fraction = match.groupValues[3].padEnd(3, '0').toLongOrNull() ?: 0
            val time = match.groupValues[1].toLong() * 60_000 + seconds * 1000 + fraction
            if (time < previous || (durationMs > 0 && time > durationMs + 3000)) return false
            previous = time
        }
        if (line.substring(stamps.last().range.last + 1).isNotBlank()) timedLines++
    }
    return timedLines > 0
}
