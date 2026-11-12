package com.erhu.erhu.feature.library

import android.content.Context
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.PressIconButton
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.AbLoopEdit
import com.erhu.erhu.core.model.PlaybackState
import com.erhu.erhu.core.model.QueueHistoryEntry
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.erhu.erhu.core.common.R as CommonR

/** Tabs of the queue sheet, in pager order (Lyrics keeps its place next to the queue). */
private const val PAGE_QUEUE = 0
private const val PAGE_LYRICS = 1
private const val PAGE_HISTORY = 2
private const val PAGE_MARKS = 3

/** Pages the queue sheet can be opened on. */
internal const val QUEUE_SHEET_PAGE_QUEUE = PAGE_QUEUE
internal const val QUEUE_SHEET_PAGE_MARKS = PAGE_MARKS

/** Every tab, in order. */
internal val QUEUE_SHEET_ALL_PAGES = listOf(PAGE_QUEUE, PAGE_LYRICS, PAGE_HISTORY, PAGE_MARKS)

/** Just the queue and what has played: the player's Queue button. */
internal val QUEUE_SHEET_QUEUE_PAGES = listOf(PAGE_QUEUE, PAGE_HISTORY)

@Composable
private fun pageLabel(
    page: Int,
    queueSize: Int,
): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return when (page) {
        PAGE_QUEUE -> stringResource(CommonR.string.queue_tab_count, LocaleFormatters.formatInteger(queueSize, locale))
        PAGE_LYRICS -> stringResource(CommonR.string.queue_tab_lyrics)
        PAGE_HISTORY -> stringResource(CommonR.string.queue_tab_history)
        else -> stringResource(CommonR.string.queue_tab_marks)
    }
}

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
    lyricsActions: LyricsActions? = null,
    /** The tabs shown, in order (QUEUE_SHEET_* pages). */
    pages: List<Int> = QUEUE_SHEET_ALL_PAGES,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(initialPage = pages.indexOf(initialPage).coerceAtLeast(0), pageCount = { pages.size })
    // The sheet is its own window: its feedback is shown inside it, above the app's own snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    val removalUndo = rememberQueueRemovalUndo(context, playbackRepository, snackbarHostState)
    var showSaveDialog by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    QueueGlossSheet(
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
                    pages.forEachIndexed { index, page ->
                        val label = pageLabel(page, playbackState.queue.size)
                        val selected = pagerState.currentPage == index
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { index ->
                    when (pages[index]) {
                        PAGE_QUEUE ->
                            QueuePage(
                                playbackState = playbackState,
                                playbackRepository = playbackRepository,
                                onRemove = removalUndo::remove,
                                onShuffleUpcoming = {
                                    if (!playbackRepository.shuffleUpcoming()) {
                                        showMessage(
                                            AppLocaleManager
                                                .localizedContext(context)
                                                .getString(CommonR.string.queue_need_two_tracks),
                                        )
                                    }
                                },
                                onSaveAsPlaylist = { showSaveDialog = true },
                            )
                        PAGE_LYRICS -> LyricsPage(playbackState, playbackRepository, lyrics, position, isPlaying, lyricsActions)
                        PAGE_MARKS -> {
                            val bookmarks by extras.bookmarks.collectAsState()

                            fun report(edit: AbLoopEdit) =
                                edit.messageRes()?.let { resId ->
                                    showMessage(AppLocaleManager.localizedContext(context).getString(resId))
                                }
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
            title = stringResource(CommonR.string.queue_save_as_playlist_title),
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
    private val context: Context,
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
            val localized = AppLocaleManager.localizedContext(context)
            val locale = AppLocaleManager.activeLocale(localized)
            val message =
                if (burst == 1) {
                    localized.getString(CommonR.string.queue_removed_single, song.title)
                } else {
                    localized.resources.getQuantityString(
                        CommonR.plurals.queue_removed_count,
                        burst,
                        LocaleFormatters.formatInteger(burst, locale),
                    )
                }
            val result =
                snackbarHostState.showSnackbar(
                    message,
                    actionLabel = localized.getString(CommonR.string.action_undo),
                    duration = SnackbarDuration.Short,
                )
            // A newer removal replaced this snackbar: that one now speaks for the whole burst.
            if (mine != token) return@launch
            if (result == SnackbarResult.ActionPerformed) repeat(burst) { playbackRepository.undoQueueRemoval() }
            burst = 0
        }
    }
}

@Composable
private fun rememberQueueRemovalUndo(
    context: Context,
    playbackRepository: PlaybackRepository,
    snackbarHostState: SnackbarHostState,
): QueueRemovalUndo {
    val scope = rememberCoroutineScope()
    return remember(context, playbackRepository, snackbarHostState, scope) {
        QueueRemovalUndo(context, playbackRepository, snackbarHostState, scope)
    }
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
    val latestQueue by rememberUpdatedState(playbackState.queue)
    val density = LocalDensity.current
    var rowHeightPx by remember(density) { mutableFloatStateOf(with(density) { 68.dp.toPx() }) }
    val drag = remember(rowHeightPx) { QueueDragMotion(rowHeightPx) }
    val queue = drag.rows ?: playbackState.queue
    LaunchedEffect(drag, playbackState.queue) { drag.queueChanged(playbackState.queue) }

    LaunchedEffect(Unit) {
        if (playbackState.currentIndex > 0) {
            listState.scrollToItem(playbackState.currentIndex)
        }
    }

    if (queue.isEmpty()) {
        EmptyState(message = stringResource(CommonR.string.queue_empty), icon = Icons.AutoMirrored.Filled.QueueMusic)
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
                val isDragging = drag.index == index
                val activeDragIndex = drag.index

                val displacement =
                    if (activeDragIndex != null && !isDragging) {
                        dragDisplacement(index, activeDragIndex, drag.offset, rowHeightPx, queue.lastIndex)
                    } else {
                        0f
                    }

                val animatedDisplacement by key(drag.handoff) {
                    animateFloatAsState(
                        targetValue = displacement,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "queueItemDisplacement",
                    )
                }

                val lift by animateFloatAsState(
                    targetValue = if (isDragging && !drag.settling) 1f else 0f,
                    animationSpec = Motion.responsive(),
                    label = "queuePickup",
                )
                val itemModifier =
                    Modifier.zIndex(if (isDragging) 2f else 1f).graphicsLayer {
                        translationY = if (isDragging) drag.offset else animatedDisplacement
                        scaleX = 1f + 0.025f * lift
                        scaleY = scaleX
                        shadowElevation = 8.dp.toPx() * lift
                    }

                val played = index < playbackState.currentIndex
                SongRow(
                    song = queuedSong,
                    isHighlighted = index == playbackState.currentIndex,
                    onClick = { if (!drag.settling) playbackRepository.skipToQueueItem(index) },
                    modifier =
                        itemModifier
                            .onSizeChanged {
                                if (drag.index == null && it.height > 0) rowHeightPx = it.height.toFloat()
                            }.graphicsLayer { alpha = if (played) PLAYED_ENTRY_ALPHA else 1f },
                ) {
                    IconButton(
                        onClick = {},
                        modifier =
                            Modifier.pointerInput(queue, drag) {
                                detectVerticalDragGestures(
                                    onDragStart = { drag.begin(latestQueue, index) },
                                    onDragEnd = {
                                        scope.launch {
                                            drag.finish(true, { latestQueue }) { from, to ->
                                                playbackRepository.reorder(from, to) is AppResult.Success
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { drag.finish(false, { latestQueue }) { _, _ -> false } }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        drag.drag(index, dragAmount)
                                    },
                                )
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(CommonR.string.queue_cd_reorder),
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PressIconButton(onClick = { onRemove(index, queuedSong) }, enabled = !drag.settling) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(CommonR.string.queue_cd_remove))
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
            label = { Text(stringResource(CommonR.string.queue_shuffle_upcoming)) },
            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        )
        AssistChip(
            onClick = onSaveAsPlaylist,
            enabled = canSave,
            label = { Text(stringResource(CommonR.string.queue_save_as_playlist)) },
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
    lyricsActions: LyricsActions?,
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
            actions = lyricsActions,
            searchQuery = listOf(currentSong.title, currentSong.artist).filter { it.isNotBlank() }.joinToString(" "),
            song = currentSong,
            onPlayPause = { if (isPlaying) playbackRepository.pause() else playbackRepository.play() },
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(CommonR.string.queue_no_track_playing),
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
        EmptyState(message = stringResource(CommonR.string.queue_history_empty), icon = Icons.Default.History)
        return
    }
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = history, key = { it.entryId }) { entry ->
            SongRow(song = entry.song, onClick = { onPlayAgain(entry) }) {
                Text(
                    text = LocaleFormatters.formatShortTime(entry.playedAtMs, locale),
                    style = TextStyles.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PressIconButton(onClick = { onPlayAgain(entry) }) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = stringResource(CommonR.string.queue_history_cd_play_again, entry.song.title),
                    )
                }
            }
        }
    }
}
