package com.erhu.erhu.feature.library

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
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.network.lyrics.CachedLyrics
import com.erhu.erhu.core.network.lyrics.LyricsCandidate
import com.erhu.erhu.core.network.lyrics.LyricsTiming
import kotlin.math.roundToLong

/** One unobtrusive line saying where the lyrics come from, with refresh / search / back-to-file actions. */
@Suppress("LongMethod")
@Composable
internal fun LyricsSourceBar(
    lyrics: SongLyrics,
    actions: LyricsActions,
    onAdjustTiming: (() -> Unit)? = null,
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
            ) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.lyrics_cd_options)) }
            GlassMenu(label = stringResource(R.string.lyrics_cd_options), expanded = menu, onDismissRequest = { menu = false }) {
                if (onAdjustTiming != null) {
                    GlassMenuItem(icon = Icons.Default.Schedule, text = {
                        Text(stringResource(R.string.lyrics_menu_adjust_timing))
                    }, onClick = {
                        menu = false
                        onAdjustTiming()
                    })
                }
                if (online && lyrics.text != null && !lyrics.downloaded) {
                    GlassMenuItem(icon = Icons.Default.Download, text = {
                        Text(stringResource(R.string.lyrics_menu_download))
                    }, onClick = {
                        menu = false
                        actions.download()
                    })
                }
                if (lyrics.saved != null) {
                    GlassMenuItem(icon = Icons.Default.DeleteOutline, destructive = true, text = {
                        Text(
                            stringResource(
                                if (lyrics.saved.explicitDownload) {
                                    R.string.lyrics_menu_remove_downloaded
                                } else {
                                    R.string.lyrics_menu_remove_saved
                                },
                            ),
                        )
                    }, onClick = {
                        menu = false
                        removal = lyrics.saved
                    })
                }
                if (online) {
                    GlassMenuItem(icon = Icons.Default.Refresh, text = {
                        Text(stringResource(R.string.lyrics_menu_refresh))
                    }, onClick = {
                        menu = false
                        actions.refresh()
                    })
                }
                GlassMenuItem(icon = Icons.Default.Search, text = {
                    Text(stringResource(R.string.lyrics_menu_search_online))
                }, onClick = {
                    menu = false
                    onSearch()
                })
                if (online && lyrics.hasFileLyrics) {
                    GlassMenuItem(icon = Icons.Default.Description, text = {
                        Text(stringResource(R.string.lyrics_menu_use_file))
                    }, onClick = {
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
    val savingText = if (lyrics.saving) stringResource(R.string.action_saving) else null
    Text(
        listOfNotNull(lyricsSourceLabel(lyrics), savingText, lyrics.saveError).joinToString("\n"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    if (lyrics.saving || (lyrics.lookup == LyricsLookup.Searching && lyrics.text != null)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun lyricsSourceLabel(lyrics: SongLyrics): String? =
    when {
        lyrics.text == null -> null
        lyrics.origin == LyricsOrigin.FILE -> stringResource(R.string.lyrics_source_from_file)
        else -> {
            val stateRes =
                when {
                    lyrics.downloaded -> R.string.lyrics_source_downloaded_suffix
                    lyrics.saved?.explicitDownload == true -> R.string.lyrics_source_downloaded_available_suffix
                    lyrics.saved != null -> R.string.lyrics_source_previously_saved_suffix
                    else -> null
                }
            val provider = lyrics.provider ?: stringResource(R.string.lyrics_source_online_provider)
            listOfNotNull(
                stringResource(R.string.lyrics_source_from_provider, provider),
                lyrics.timing?.localizedLabel(),
                stateRes?.let { stringResource(it) },
            ).joinToString(" · ")
        }
    }

@Composable
private fun LyricsTiming.localizedLabel(): String =
    stringResource(
        when (this) {
            LyricsTiming.WORD -> R.string.lyrics_timing_word_synced
            LyricsTiming.LINE -> R.string.lyrics_timing_line_synced
            LyricsTiming.PLAIN -> R.string.lyrics_timing_plain_text
            LyricsTiming.NONE -> R.string.lyrics_timing_none
        },
    )

@Composable
private fun LyricsRemovalDialog(
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_remove_dialog_title)) },
        text = { Text(stringResource(R.string.lyrics_remove_dialog_body)) },
        confirmButton = { TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    val (message, retry) =
        when (lookup) {
            LyricsLookup.Searching -> stringResource(R.string.lyrics_lookup_searching) to false
            LyricsLookup.NotFound -> stringResource(R.string.lyrics_lookup_not_found) to false
            LyricsLookup.Instrumental -> stringResource(R.string.lyrics_lookup_instrumental) to false
            LyricsLookup.Offline -> stringResource(R.string.lyrics_lookup_offline) to true
            is LyricsLookup.RateLimited -> {
                val seconds = ((lookup.retryAtMs - nowMs()) / MS_PER_SECOND.toDouble()).roundToLong().coerceAtLeast(1)
                stringResource(R.string.lyrics_lookup_rate_limited, LocaleFormatters.formatInteger(seconds, locale)) to true
            }
            is LyricsLookup.Failed -> stringResource(R.string.lyrics_lookup_failed, lookup.reason) to true
            else -> stringResource(R.string.lyrics_no_embedded) to false
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
                if (retry) FilledTonalButton(onClick = actions.refresh) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onSearch) { Text(stringResource(R.string.lyrics_menu_search_online)) }
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
        Text(
            stringResource(R.string.lyrics_candidates_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            stringResource(R.string.lyrics_candidates_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f)) { items(candidates, key = { it.providerId }) { CandidateRow(it) { actions.choose(it) } } }
        TextButton(
            onClick = onSearch,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.lyrics_menu_search_online)) }
    }
}

@Composable
private fun CandidateRow(
    candidate: LyricsCandidate,
    onClick: () -> Unit,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
    ) {
        Text(candidate.trackName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val timingOrInstrumental =
            if (candidate.instrumental) {
                stringResource(R.string.lyrics_candidate_instrumental)
            } else {
                candidate.timing.takeIf { it != LyricsTiming.NONE }?.localizedLabel()
            }
        val details =
            listOfNotNull(
                candidate.artistName.takeIf { it.isNotBlank() },
                candidate.albumName?.takeIf { it.isNotBlank() },
                candidate.durationSec?.let { formatTimestamp((it * MS_PER_SECOND).roundToLong(), locale) },
                timingOrInstrumental,
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
        title = { Text(stringResource(R.string.lyrics_search_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.lyrics_search_query_label)) },
                )
                when (search) {
                    ManualLyricsSearch.Idle -> Unit
                    ManualLyricsSearch.Searching -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    is ManualLyricsSearch.Error -> Text(search.message, color = MaterialTheme.colorScheme.error)
                    is ManualLyricsSearch.Results ->
                        if (search.candidates.isEmpty()) {
                            Text(stringResource(R.string.lyrics_search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                                items(search.candidates, key = { it.providerId }) { CandidateRow(it) { actions.choose(it) } }
                            }
                        }
                }
                Spacer(Modifier.size(0.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.search(query) }, enabled = query.isNotBlank()) {
                Text(stringResource(R.string.lyrics_search_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private const val MS_PER_SECOND = 1_000L
