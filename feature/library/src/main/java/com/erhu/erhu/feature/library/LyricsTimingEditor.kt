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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import java.text.DateFormat
import java.util.Date

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
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Text("Lyrics timing", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
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
                    ) { Text(if (saving) "Saving…" else "Save") }
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
                        "Move lyrics earlier if they arrive late, or later if they arrive too soon.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Earlier", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = draft.offsetMs.toFloat(),
                            onValueChange = draft::slide,
                            enabled = !saving,
                            valueRange = -LyricsTimingOffset.LIMIT_MS.toFloat()..LyricsTimingOffset.LIMIT_MS.toFloat(),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Lyrics timing offset" },
                        )
                        Text("Later", style = MaterialTheme.typography.labelMedium)
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
                            label = { Text("Offset") },
                            isError = !draft.valid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp).semantics { contentDescription = "Timing offset in milliseconds" },
                        )
                        Text("ms")
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { draft.step(-1) }, enabled = !saving && draft.offsetMs > -LyricsTimingOffset.LIMIT_MS) {
                            Icon(Icons.Default.Remove, "Lyrics earlier by 50 milliseconds")
                        }
                        IconButton(onClick = { draft.step(1) }, enabled = !saving && draft.offsetMs < LyricsTimingOffset.LIMIT_MS) {
                            Icon(Icons.Default.Add, "Lyrics later by 50 milliseconds")
                        }
                        TextButton(onClick = { draft.set(0) }, enabled = !saving) { Text("Reset") }
                    }
                    if (!draft.valid) {
                        Text(
                            "Enter a whole number of milliseconds.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    PreviousLyricsTiming(lyrics, enabled = !saving, onSelect = draft::set)
                    Button(onClick = { onPlayPause?.invoke() }, enabled = onPlayPause != null && !saving) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(if (isPlaying) "Pause preview" else "Preview timing")
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
    GlassMenuAnchor {
        TextButton(onClick = {
            expanded = true
        }, enabled = enabled, modifier = Modifier.glassMenuTrigger()) { Text("Use timing from another source") }
        GlassMenu(label = "Previous lyrics timing", expanded = expanded, onDismissRequest = { expanded = false }) {
            lyrics.previousTiming.take(20).forEach { saved ->
                GlassMenuItem(text = {
                    Text(
                        "${saved.label} · ${saved.offsetMs} ms · ${DateFormat.getDateInstance(
                            DateFormat.MEDIUM,
                        ).format(Date(saved.savedAtMs))}",
                    )
                }, onClick = {
                    expanded = false
                    onSelect(saved.offsetMs)
                })
            }
        }
    }
}
