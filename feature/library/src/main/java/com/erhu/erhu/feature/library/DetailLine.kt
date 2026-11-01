package com.erhu.erhu.feature.library

import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.model.RepairDiagnostics

/** One line of a track's search details: a label and its value, each shown as its own text. */
data class DetailLine(
    val label: String,
    val value: String,
    val indent: Int = 0,
)

/**
 * A repair lookup's search details, line by line, from separate fields: what the file says, each
 * query exactly as sent (title, artist and album apart) with every provider's answer to it, and the
 * candidates found. Nothing is joined into one string, so a value can't run into the next label.
 */
fun searchDetailLines(diag: RepairDiagnostics): List<DetailLine> =
    buildList {
        add(DetailLine("File", diag.originalFilename))
        add(DetailLine("Title tag", diag.embeddedTitle ?: "(none)"))
        diag.fileTitles.forEach { add(DetailLine("Title from file name", it)) }
        add(DetailLine("Artist tag", diag.embeddedArtist ?: "(none)"))
        add(DetailLine("Album tag", diag.embeddedAlbum ?: "(none)"))
        diag.targetDurationMs?.let { add(DetailLine("Length", clock(it))) }
        diag.queries.forEachIndexed { index, query ->
            add(DetailLine("Search ${index + 1}", "“${query.title}” (from ${query.source})"))
            query.artist?.let { add(DetailLine("artist", it, indent = 1)) }
            query.album?.let { add(DetailLine("album", it, indent = 1)) }
            query.outcomes.forEach { add(DetailLine(it.providerName, providerStatusText(it), indent = 2)) }
        }
    }

/** A candidate as one line: what it would write, its length and its score. */
fun candidateLine(candidate: MetadataCandidate): String {
    val album = if (candidate.album.isNotBlank()) " · ${candidate.album}" else ""
    val length = candidate.durationMs?.let { " · ${clock(it)}" }.orEmpty()
    val version = candidate.versionNote?.let { " [$it]" }.orEmpty()
    return "${candidate.title} — ${candidate.artist}$album$length$version"
}

private fun clock(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
