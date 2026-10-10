package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.model.AbLoopEdit
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.QueueHistoryEntry
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Tabs of the queue sheet, in pager order (Lyrics keeps its place next to the queue). */
private const val PAGE_QUEUE = 0
private const val PAGE_LYRICS = 1
private const val PAGE_HISTORY = 2
private const val PAGE_MARKS = 3
private const val PAGE_COUNT = 4

/** Pages the queue sheet can be opened on. */
internal const val QUEUE_SHEET_PAGE_QUEUE = PAGE_QUEUE
internal const val QUEUE_SHEET_PAGE_MARKS = PAGE_MARKS

/** Entries already played (before the current one) are dimmed so they read as history, not as upcoming. */
private const val PLAYED_ENTRY_ALPHA = 0.55f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun QueueSheet(
    playbackState: PlaybackState,
    playbackRepository: PlaybackRepository,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
    onSaveQueueAsPlaylist: (name: String, songs: List<Song>, onResult: (String) -> Unit) -> Unit,
    extras: PlayerExtrasViewModel,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, PAGE_COUNT - 1), pageCount = { PAGE_COUNT })
    // The sheet is its own window: its feedback is shown inside it, above the app's own snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    val removalUndo = rememberQueueRemovalUndo(playbackRepository, snackbarHostState)
    var showSaveDialog by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        PAGE_QUEUE to "Queue (${playbackState.queue.size})",
                        PAGE_LYRICS to "Lyrics",
                        PAGE_HISTORY to "History",
                        PAGE_MARKS to "Marks",
                    ).forEach { (page, label) ->
                        val selected = pagerState.currentPage == page
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(page) } },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        PAGE_QUEUE ->
                            QueuePage(
                                playbackState = playbackState,
                                playbackRepository = playbackRepository,
                                onRemove = removalUndo::remove,
                                onShuffleUpcoming = {
                                    if (!playbackRepository.shuffleUpcoming()) showMessage("Need at least two upcoming tracks to shuffle")
                                },
                                onSaveAsPlaylist = { showSaveDialog = true },
                            )
                        PAGE_LYRICS -> LyricsPage(playbackState, playbackRepository, lyrics, position, isPlaying)
                        PAGE_MARKS -> {
                            val bookmarks by extras.bookmarks.collectAsState()

                            fun report(edit: AbLoopEdit) = edit.message()?.let(::showMessage)
                            MarksPage(
                                hasTrack = playbackState.currentSong != null,
                                loop = playbackState.abLoop,
                                bookmarks = bookmarks,
                                position = position,
                                actions =
                                    MarksActions(
                                        onAddBookmark = { extras.addBookmarkAtCurrentPosition { error -> error?.let(::showMessage) } },
                                        onSeekBookmark = extras::seekTo,
                                        onRenameBookmark = extras::renameBookmark,
                                        onDeleteBookmark = extras::deleteBookmark,
                                        onSetA = { report(playbackRepository.setLoopStart()) },
                                        onSetB = { report(playbackRepository.setLoopEnd()) },
                                        onLoopEnabled = { report(playbackRepository.setLoopEnabled(it)) },
                                        onClearLoop = playbackRepository::clearLoop,
                                    ),
                            )
                        }
                        else ->
                            HistoryPage(
                                history = playbackState.history,
                                onPlayAgain = { playbackRepository.playFromHistory(it.entryId) },
                            )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
            )
        }
    }

    if (showSaveDialog) {
        PlaylistNameDialog(
            title = "Save queue as playlist",
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                // The current entry and everything after it, in queue order (duplicates kept).
                val songs = playbackState.queue.drop(playbackState.currentIndex.coerceAtLeast(0))
                onSaveQueueAsPlaylist(name, songs, ::showMessage)
            },
        )
    }
}

/**
 * Undo for queue removals. Removals in quick succession share one snackbar ("Removed 3 tracks");
 * its Undo puts all of them back, newest first, so every entry returns to its own position.
 */
private class QueueRemovalUndo(
    private val playbackRepository: PlaybackRepository,
    private val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    private var burst = 0
    private var token = 0

    fun remove(
        index: Int,
        song: Song,
    ) {
        scope.launch {
            if (playbackRepository.removeFromQueue(index) !is AppResult.Success) return@launch
            burst++
            val mine = ++token
            snackbarHostState.currentSnackbarData?.dismiss()
            val message = if (burst == 1) "Removed \u201c${song.title}\u201d" else "Removed $burst tracks"
            val result = snackbarHostState.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            // A newer removal replaced this snackbar: that one now speaks for the whole burst.
            if (mine != token) return@launch
            if (result == SnackbarResult.ActionPerformed) repeat(burst) { playbackRepository.undoQueueRemoval() }
            burst = 0
        }
    }
}

@Composable
private fun rememberQueueRemovalUndo(
    playbackRepository: PlaybackRepository,
    snackbarHostState: SnackbarHostState,
): QueueRemovalUndo {
    val scope = rememberCoroutineScope()
    return remember(playbackRepository, snackbarHostState, scope) { QueueRemovalUndo(playbackRepository, snackbarHostState, scope) }
}

@Composable
@Suppress("LongMethod")
private fun QueuePage(
    playbackState: PlaybackState,
    playbackRepository: PlaybackRepository,
    onRemove: (Int, Song) -> Unit,
    onShuffleUpcoming: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val queue = playbackState.queue

    LaunchedEffect(Unit) {
        if (playbackState.currentIndex > 0) {
            listState.scrollToItem(playbackState.currentIndex)
        }
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 68.dp.toPx() }

    if (queue.isEmpty()) {
        EmptyState(message = "The queue is empty.", icon = Icons.AutoMirrored.Filled.QueueMusic)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        QueueActions(
            canShuffle = queue.size - (playbackState.currentIndex + 1) >= 2,
            canSave = playbackState.currentIndex >= 0,
            onShuffleUpcoming = onShuffleUpcoming,
            onSaveAsPlaylist = onSaveAsPlaylist,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = queue,
                key = { index, song -> "${song.id}_$index" },
            ) { index, queuedSong ->
                val isDragging = draggingIndex == index
                val activeDragIndex = draggingIndex

                val displacement =
                    if (activeDragIndex != null && !isDragging) {
                        dragDisplacement(index, activeDragIndex, draggingOffset, rowHeightPx, queue.lastIndex)
                    } else {
                        0f
                    }

                val animatedDisplacement by animateFloatAsState(
                    targetValue = displacement,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "queueItemDisplacement",
                )

                val itemModifier =
                    if (isDragging) {
                        Modifier
                            .zIndex(2f)
                            .graphicsLayer {
                                translationY = draggingOffset
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = 8.dp.toPx()
                            }
                    } else {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationY = animatedDisplacement
                            }
                    }

                val played = index < playbackState.currentIndex
                SongRow(
                    song = queuedSong,
                    isHighlighted = index == playbackState.currentIndex,
                    onClick = { playbackRepository.skipToQueueItem(index) },
                    modifier = itemModifier.graphicsLayer { alpha = if (played) PLAYED_ENTRY_ALPHA else 1f },
                ) {
                    IconButton(
                        onClick = {},
                        modifier =
                            Modifier.pointerInput(queue) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        draggingIndex = index
                                        draggingOffset = 0f
                                    },
                                    onDragEnd = {
                                        val from = draggingIndex
                                        if (from != null) {
                                            val to = (from + (draggingOffset / rowHeightPx).roundToInt()).coerceIn(0, queue.lastIndex)
                                            if (from != to) {
                                                scope.launch { playbackRepository.reorder(from, to) }
                                            }
                                        }
                                        draggingIndex = null
                                        draggingOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        draggingOffset = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        draggingOffset += dragAmount
                                    },
                                )
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemove(index, queuedSong) }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from queue")
                    }
                }
            }
        }
    }
}

/** How far row [index] moves aside while the row at [dragIndex] is dragged by [dragOffset] px. */
private fun dragDisplacement(
    index: Int,
    dragIndex: Int,
    dragOffset: Float,
    rowHeightPx: Float,
    lastIndex: Int,
): Float {
    val targetIndex = (dragIndex + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, lastIndex)
    return when {
        dragIndex < targetIndex && index in (dragIndex + 1)..targetIndex -> -rowHeightPx
        dragIndex > targetIndex && index in targetIndex..<dragIndex -> rowHeightPx
        else -> 0f
    }
}

/** The queue's two actions, kept small so the list stays the focus. */
@Composable
private fun QueueActions(
    canShuffle: Boolean,
    canSave: Boolean,
    onShuffleUpcoming: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AssistChip(
            onClick = onShuffleUpcoming,
            enabled = canShuffle,
            label = { Text("Shuffle upcoming") },
            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        )
        AssistChip(
            onClick = onSaveAsPlaylist,
            enabled = canSave,
            label = { Text("Save as playlist") },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}

@Composable
private fun LyricsPage(
    playbackState: PlaybackState,
    playbackRepository: PlaybackRepository,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
) {
    val currentSong = playbackState.currentSong
    if (currentSong != null) {
        SongLyricsView(
            songId = currentSong.id,
            lyrics = lyrics,
            position = position,
            isPlaying = isPlaying,
            onSeek = { playbackRepository.seekTo(it) },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No track playing",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tracks played this session, newest first. Tapping one plays it again right after the current track. */
@Composable
private fun HistoryPage(
    history: List<QueueHistoryEntry>,
    onPlayAgain: (QueueHistoryEntry) -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(message = "Tracks you play in this session appear here.", icon = Icons.Default.History)
        return
    }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = history, key = { it.entryId }) { entry ->
            SongRow(song = entry.song, onClick = { onPlayAgain(entry) }) {
                Text(
                    text = timeFormat.format(Date(entry.playedAtMs)),
                    style = TextStyles.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { onPlayAgain(entry) }) {
                    Icon(imageVector = Icons.Default.Replay, contentDescription = "Play ${entry.song.title} again")
                }
            }
        }
    }
}
