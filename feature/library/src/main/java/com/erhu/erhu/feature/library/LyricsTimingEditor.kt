package com.erhu.erhu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.LyricsTimingContext
import com.erhu.erhu.core.model.LyricsTimingOffset
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.launch

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
internal fun LyricsTimingEditor(
    song: Song,
    lyrics: SongLyrics,
    position: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: (() -> Unit)?,
    save: suspend (LyricsTimingContext, Long) -> AppResult<Unit>,
    onDismiss: () -> Unit,
) {
    val context = lyrics.timingContext ?: return
    val draft = remember(context.id) { LyricsTimingDraft(lyrics.offsetMs) }
    var saving by remember(context.id) { mutableStateOf(false) }
    var error by remember(context.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = !saving,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
                    Text(
                        stringResource(R.string.lyrics_timing_title),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        enabled = !saving && draft.valid,
                        onClick = {
                            saving = true
                            error = null
                            scope.launch {
                                when (val result = save(context, draft.offsetMs)) {
                                    is AppResult.Success -> onDismiss()
                                    is AppResult.Error -> {
                                        error = result.error.message
                                        saving = false
                                    }
                                }
                            }
                        },
                    ) { Text(stringResource(if (saving) R.string.action_saving else R.string.action_save)) }
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.lyrics_timing_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    val sliderDescription = stringResource(R.string.lyrics_timing_cd_slider)
                    val inputDescription = stringResource(R.string.lyrics_timing_cd_input_ms)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.lyrics_timing_earlier), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = draft.offsetMs.toFloat(),
                            onValueChange = draft::slide,
                            enabled = !saving,
                            valueRange = -LyricsTimingOffset.LIMIT_MS.toFloat()..LyricsTimingOffset.LIMIT_MS.toFloat(),
                            modifier = Modifier.weight(1f).semantics { contentDescription = sliderDescription },
                        )
                        Text(stringResource(R.string.lyrics_timing_later), style = MaterialTheme.typography.labelMedium)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        OutlinedTextField(
                            value = draft.input,
                            onValueChange = draft::input,
                            enabled = !saving,
                            singleLine = true,
                            label = { Text(stringResource(R.string.lyrics_timing_offset_label)) },
                            isError = !draft.valid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp).semantics { contentDescription = inputDescription },
                        )
                        Text(stringResource(R.string.lyrics_timing_unit_ms))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { draft.step(-1) }, enabled = !saving && draft.offsetMs > -LyricsTimingOffset.LIMIT_MS) {
                            Icon(Icons.Default.Remove, stringResource(R.string.lyrics_timing_cd_step_earlier))
                        }
                        IconButton(onClick = { draft.step(1) }, enabled = !saving && draft.offsetMs < LyricsTimingOffset.LIMIT_MS) {
                            Icon(Icons.Default.Add, stringResource(R.string.lyrics_timing_cd_step_later))
                        }
                        TextButton(onClick = { draft.set(0) }, enabled = !saving) { Text(stringResource(R.string.lyrics_timing_reset)) }
                    }
                    if (!draft.valid) {
                        Text(
                            stringResource(R.string.lyrics_timing_error_integer),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    PreviousLyricsTiming(lyrics, enabled = !saving, onSelect = draft::set)
                    Button(onClick = { onPlayPause?.invoke() }, enabled = onPlayPause != null && !saving) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(if (isPlaying) R.string.lyrics_timing_pause_preview else R.string.lyrics_timing_preview))
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalLyricsLook provides LyricsLook(alignStart = true)) {
                        LyricsContent(lyrics.copy(offsetMs = draft.offsetMs), position, isPlaying, onSeek)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousLyricsTiming(
    lyrics: SongLyrics,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    if (lyrics.previousTiming.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    GlassMenuAnchor {
        TextButton(onClick = {
            expanded = true
        }, enabled = enabled, modifier = Modifier.glassMenuTrigger()) {
            Text(stringResource(R.string.lyrics_timing_use_other_source))
        }
        GlassMenu(
            label = stringResource(R.string.lyrics_timing_previous_menu),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            lyrics.previousTiming.take(20).forEach { saved ->
                val sourceLabel =
                    when {
                        saved.sourceKey == "file" -> stringResource(R.string.lyrics_source_file_short)
                        saved.label == "Online lyrics" -> stringResource(R.string.lyrics_source_online_short)
                        else -> saved.label
                    }
                val entryText =
                    stringResource(
                        R.string.lyrics_timing_previous_entry,
                        sourceLabel,
                        LocaleFormatters.formatInteger(saved.offsetMs, locale),
                        LocaleFormatters.formatMediumDate(saved.savedAtMs, locale),
                    )
                GlassMenuItem(text = { Text(entryText) }, onClick = {
                    expanded = false
                    onSelect(saved.offsetMs)
                })
            }
        }
    }
}
