package com.lochanpthakke.empee3.feature.library.vybe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.feature.library.SongRow

/** Prompts the library's tags can answer (genre, tempo, artist): offered as starting points. */
private val SUGGESTIONS =
    listOf(
        "Dark, atmospheric hip-hop",
        "High-energy music for the gym",
        "Mellow tracks with warm production",
        "Late-night Frank Ocean vibes",
        "Southern rap",
    )

/**
 * The VYBE section: describe what you want to hear, get songs from your own library. The model
 * runs on the phone; results say what they were matched on.
 */
@Composable
@Suppress("LongMethod")
fun VybeScreen(
    topInset: Dp,
    bottomInset: Dp,
    currentSongId: Long?,
    modifier: Modifier = Modifier,
    viewModel: VybeViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsState()
    val query by viewModel.query.collectAsState()
    val timings by viewModel.timings.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val message by viewModel.message.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val pickModel =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importModel)
        }

    LaunchedEffect(Unit) { viewModel.start() }
    DisposableEffect(Unit) {
        viewModel.setVisible(true)
        onDispose { viewModel.setVisible(false) }
    }

    fun submit(value: String) {
        text = value
        focus.clearFocus()
        viewModel.submit(value)
    }

    val results = (query as? VybeQueryState.Done)?.results
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + Spacing.sm, bottom = bottomInset + Spacing.lg),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = Spacing.screenGutter)) {
                Text("VYBE", style = TextStyles.screenTitle)
                Text(
                    "Find music in your library by mood, genre, tempo or artist. Runs on this phone.",
                    style = TextStyles.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Describe what you want to hear") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = {
                                text = ""
                                viewModel.clear()
                            }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    enabled = status is VybeStatus.Ready || status is VybeStatus.Indexing,
                    shape = Radius.pillShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                )
                Spacer(Modifier.height(Spacing.sm))
                StatusLine(status, timings, onPickModel = { pickModel.launch(arrayOf("*/*")) }, onRetry = viewModel::start)
                message?.let {
                    Text(it, style = TextStyles.metadata, color = MaterialTheme.colorScheme.primary)
                    LaunchedEffect(it) {
                        kotlinx.coroutines.delay(MESSAGE_MS)
                        viewModel.consumeMessage()
                    }
                }
                if (query is VybeQueryState.Idle && (status is VybeStatus.Ready || status is VybeStatus.Indexing)) {
                    Suggestions(
                        canMatchCurrent = currentSong != null,
                        currentTitle = currentSong?.title,
                        onSuggestion = ::submit,
                        onCurrent = viewModel::similarToCurrent,
                    )
                }
            }
        }
        when (val state = query) {
            is VybeQueryState.Searching ->
                item(key = "searching") {
                    Row(
                        modifier = Modifier.padding(Spacing.screenGutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Searching on this phone…", style = TextStyles.supporting)
                    }
                }
            is VybeQueryState.Error ->
                item(key = "error") {
                    Text(
                        "Couldn't search: ${state.message}",
                        style = TextStyles.supporting,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Spacing.screenGutter),
                    )
                }
            else -> Unit
        }
        if (results != null) {
            item(key = "results-header") {
                ResultsHeader(
                    results = results,
                    onPlay = { viewModel.play(results.songs, 0) },
                    onShuffle = { viewModel.shuffle(results.songs) },
                    onQueue = { viewModel.addAllToQueue(results.songs) },
                )
            }
            if (results.songs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing in your library matched.",
                        style = TextStyles.supporting,
                        modifier = Modifier.padding(Spacing.screenGutter),
                    )
                }
            }
            itemsIndexed(results.songs, key = { _, row -> "song-${row.song.id}" }) { index, result ->
                Column {
                    SongRow(
                        song = result.song,
                        onClick = { viewModel.play(results.songs, index) },
                        isHighlighted = result.song.id == currentSongId,
                    )
                    if (result.reasons.isNotEmpty()) {
                        Text(
                            result.reasons.joinToString(" · "),
                            style = TextStyles.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Spacing.screenGutter + REASON_INDENT, end = Spacing.screenGutter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    status: VybeStatus,
    timings: VybeTimings,
    onPickModel: () -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        VybeStatus.NoModel ->
            Surface(shape = Radius.largeShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text("VYBE needs its model", style = TextStyles.itemTitle)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "VYBE uses Google's EmbeddingGemma (embedding_gemma.task, about 184 MB) on this phone. " +
                            "Download it after accepting the Gemma terms (ai.google.dev/gemma/terms), then choose the file. " +
                            "It is copied into empee3's storage; nothing is uploaded.",
                        style = TextStyles.supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Button(onClick = onPickModel) { Text("Choose model file") }
                }
            }
        VybeStatus.LoadingModel -> ProgressLine("Loading the model…", null)
        is VybeStatus.Indexing ->
            ProgressLine(
                "Indexing ${status.done} of ${status.total} songs on this phone" +
                    (timings.averageEmbedMs?.let { " (~${it / MS_PER_S}.${it % MS_PER_S / TENTHS} s per description)" } ?: ""),
                status.done.toFloat() / status.total.coerceAtLeast(1),
            )
        is VybeStatus.Ready ->
            Text(
                "${status.songs} songs indexed" + (timings.lastQueryMs?.let { " · last search $it ms" } ?: ""),
                style = TextStyles.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        is VybeStatus.Failed ->
            Column {
                Text(status.message, style = TextStyles.supporting, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry) { Text("Try again") }
            }
    }
}

@Composable
private fun ProgressLine(
    label: String,
    progress: Float?,
) {
    Column {
        Text(label, style = TextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    canMatchCurrent: Boolean,
    currentTitle: String?,
    onSuggestion: (String) -> Unit,
    onCurrent: () -> Unit,
) {
    Spacer(Modifier.height(Spacing.md))
    Text("Try", style = TextStyles.sectionTitle)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (canMatchCurrent) {
            AssistChip(
                onClick = onCurrent,
                label = { Text("Like \"${currentTitle.orEmpty()}\"", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        SUGGESTIONS.forEach { suggestion ->
            AssistChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
        }
    }
}

@Composable
private fun ResultsHeader(
    results: VybeResults,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onQueue: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm)) {
        Text(results.title, style = TextStyles.sectionTitle)
        results.note?.let {
            Text(it, style = TextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (results.songs.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Play ${results.songs.size}")
                }
                FilledTonalButton(onClick = onShuffle) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                }
                FilledTonalButton(onClick = onQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add all to queue")
                }
            }
        }
    }
}

private const val MESSAGE_MS = 2_500L
private const val MS_PER_S = 1000
private const val TENTHS = 100
private val REASON_INDENT = 64.dp
