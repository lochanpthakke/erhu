package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuAnchor
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.component.glassMenuTrigger
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.network.lyrics.CachedLyrics
import com.lochanpthakke.empee3.core.network.lyrics.LyricsCandidate
import com.lochanpthakke.empee3.core.network.lyrics.LyricsTiming
import kotlin.math.roundToLong

/** One unobtrusive line saying where the lyrics come from, with refresh / search / back-to-file actions. */
@Composable
internal fun LyricsSourceBar(
    lyrics: SongLyrics,
    actions: LyricsActions,
    onSearch: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var removal by remember(lyrics.songId) { mutableStateOf<CachedLyrics?>(null) }
    val online = lyrics.origin != LyricsOrigin.FILE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.xs),
    ) {
        LyricsSourceText(lyrics)
        GlassMenuAnchor {
            IconButton(
                modifier = Modifier.glassMenuTrigger(),
                enabled = !lyrics.saving,
                onClick = { menu = true },
            ) { Icon(Icons.Default.MoreVert, contentDescription = "Lyrics options") }
            GlassMenu(label = "Lyrics options", expanded = menu, onDismissRequest = { menu = false }) {
                if (online && lyrics.text != null && !lyrics.downloaded) {
                    GlassMenuItem(icon = Icons.Default.Download, text = { Text("Download lyrics") }, onClick = {
                        menu = false
                        actions.download()
                    })
                }
                if (lyrics.saved != null) {
                    GlassMenuItem(icon = Icons.Default.DeleteOutline, destructive = true, text = {
                        Text(if (lyrics.saved.explicitDownload) "Remove downloaded lyrics" else "Remove previously saved lyrics")
                    }, onClick = {
                        menu = false
                        removal = lyrics.saved
                    })
                }
                if (online) {
                    GlassMenuItem(icon = Icons.Default.Refresh, text = { Text("Refresh online lyrics") }, onClick = {
                        menu = false
                        actions.refresh()
                    })
                }
                GlassMenuItem(icon = Icons.Default.Search, text = { Text("Search online…") }, onClick = {
                    menu = false
                    onSearch()
                })
                if (online && lyrics.hasFileLyrics) {
                    GlassMenuItem(icon = Icons.Default.Description, text = { Text("Use the file's lyrics") }, onClick = {
                        menu = false
                        actions.useFileLyrics()
                    })
                }
            }
        }
    }
    removal?.let { selected ->
        LyricsRemovalDialog(onDismiss = { removal = null }, onRemove = {
            removal = null
            actions.removeDownload(selected)
        })
    }
}

@Composable
private fun RowScope.LyricsSourceText(lyrics: SongLyrics) {
    Text(
        listOfNotNull(lyricsSourceLabel(lyrics), if (lyrics.saving) "Saving…" else null, lyrics.saveError).joinToString("\n"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    if (lyrics.saving || (lyrics.lookup == LyricsLookup.Searching && lyrics.text != null)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    }
}

private fun lyricsSourceLabel(lyrics: SongLyrics): String? =
    when {
        lyrics.text == null -> null
        lyrics.origin == LyricsOrigin.FILE -> "From the file's tags"
        else -> {
            val state =
                when {
                    lyrics.downloaded -> " · downloaded"
                    lyrics.saved?.explicitDownload == true -> " · downloaded version available"
                    lyrics.saved != null -> " · previously saved"
                    else -> ""
                }
            "From ${lyrics.provider ?: "online"}${lyrics.timing?.let { " · ${it.label()}" }.orEmpty()}$state"
        }
    }

@Composable
private fun LyricsRemovalDialog(
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove saved lyrics?") },
        text = { Text("Remove this song's saved online lyrics? Lyrics in the music file will be kept.") },
        confirmButton = { TextButton(onClick = onRemove) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What to show when there are no lyrics to display yet: the lookup's state and what can be done. */
@Composable
internal fun LyricsLookupStatus(
    lookup: LyricsLookup,
    actions: LyricsActions?,
    onSearch: () -> Unit,
    nowMs: () -> Long = System::currentTimeMillis,
) {
    if (lookup is LyricsLookup.NeedsChoice && actions != null) {
        CandidateChoice(lookup.candidates, actions, onSearch)
        return
    }
    val (message, retry) =
        when (lookup) {
            LyricsLookup.Searching -> "Looking for lyrics online…" to false
            LyricsLookup.NotFound -> "No lyrics found for this song." to false
            LyricsLookup.Instrumental -> "This track is instrumental." to false
            LyricsLookup.Offline -> "You are offline. Lyrics will be looked up when you are back online." to true
            is LyricsLookup.RateLimited -> {
                val seconds = ((lookup.retryAtMs - nowMs()) / MS_PER_SECOND.toDouble()).roundToLong().coerceAtLeast(1)
                "LRCLIB asked to wait ($seconds s). Try again shortly." to true
            }
            is LyricsLookup.Failed -> "Could not look up lyrics (${lookup.reason})." to true
            else -> "This file has no embedded lyrics." to false
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (lookup == LyricsLookup.Searching) CircularProgressIndicator()
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actions != null && lookup != LyricsLookup.Searching) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (retry) FilledTonalButton(onClick = actions.refresh) { Text("Retry") }
                TextButton(onClick = onSearch) { Text("Search online…") }
            }
        }
    }
}

/** Possible matches when none was certain: nothing is shown until the user picks one. */
@Composable
private fun CandidateChoice(
    candidates: List<LyricsCandidate>,
    actions: LyricsActions,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        Text("Is it one of these?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.md))
        Text(
            "These results might match, but none clearly does. Pick the right one, or search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f)) { items(candidates, key = { it.providerId }) { CandidateRow(it) { actions.choose(it) } } }
        TextButton(onClick = onSearch, modifier = Modifier.align(Alignment.End)) { Text("Search online…") }
    }
}

@Composable
private fun CandidateRow(
    candidate: LyricsCandidate,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
    ) {
        Text(candidate.trackName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details =
            listOfNotNull(
                candidate.artistName.takeIf { it.isNotBlank() },
                candidate.albumName?.takeIf { it.isNotBlank() },
                candidate.durationSec?.let { formatTimestamp((it * MS_PER_SECOND).roundToLong()) },
                if (candidate.instrumental) "instrumental" else candidate.timing.takeIf { it != LyricsTiming.NONE }?.label(),
            ).joinToString(" · ")
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Manual online search: edit the query, pick a result. Only the typed text is sent. */
@Composable
internal fun ManualLyricsSearchDialog(
    initialQuery: String,
    search: ManualLyricsSearch,
    actions: LyricsActions,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search lyrics online") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Title and artist") })
                when (search) {
                    ManualLyricsSearch.Idle -> Unit
                    ManualLyricsSearch.Searching -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    is ManualLyricsSearch.Error -> Text(search.message, color = MaterialTheme.colorScheme.error)
                    is ManualLyricsSearch.Results ->
                        if (search.candidates.isEmpty()) {
                            Text("No results.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                                items(search.candidates, key = { it.providerId }) { CandidateRow(it) { actions.choose(it) } }
                            }
                        }
                }
                Spacer(Modifier.size(0.dp))
            }
        },
        confirmButton = { TextButton(onClick = { actions.search(query) }, enabled = query.isNotBlank()) { Text("Search") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val MS_PER_SECOND = 1_000L
