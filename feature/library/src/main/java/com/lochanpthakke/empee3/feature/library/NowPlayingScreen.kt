package com.lochanpthakke.empee3.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.GlassArtworkBackdrop
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.PressIconButton
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.pressScale
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import kotlinx.coroutines.launch
import java.util.Locale

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val INACTIVE_ALPHA = 0.6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
fun NowPlayingScreen(
    playbackRepository: PlaybackRepository,
    isFavorite: Boolean,
    lyrics: String?,
    onBackClick: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stable state only; the position is read by PlaybackProgress alone, so the
    // rest of this screen does not recompose four times per second.
    val playbackState by playbackRepository.collectStablePlaybackState()
    val position = playbackRepository.collectPlaybackPosition()
    val song = playbackState.currentSong
    val isPlaying = playbackState.status == PlaybackStatus.PLAYING

    var showLyrics by remember { mutableStateOf(false) }
    // The player is one object (PlayerTransitionState): dragging the artwork / title
    // down collapses it into the mini player; sideways swipes change the track.
    val transition = LocalPlayerTransition.current
    val swipe = remember { Animatable(0f) }
    // Set when a swipe throws the current track off screen; the track transition then
    // only brings the new one in from that side (one continuous motion).
    val swipeHandOff = remember { mutableIntStateOf(0) }
    LaunchedEffect(song?.id) {
        swipe.snapTo(0f)
        swipeHandOff.intValue = 0
    }
    val latestState by rememberUpdatedState(playbackState)
    val collapseDrag = if (transition != null) Modifier.playerDrag(transition, expandsUpward = false) else Modifier
    val trackSwipe =
        Modifier.trackSwipe(
            swipeOffset = swipe,
            onNext = { playbackRepository.skipNext() },
            onPrevious = { playbackRepository.skipPrevious() },
            canSkipNext = { hasNext(latestState) },
            onSkipThrown = { swipeHandOff.intValue = it },
            currentTrackId = { latestState.currentSong?.id },
        )
    val gestureRegion = collapseDrag.then(trackSwipe)
    val followSwipe = Modifier.graphicsLayer { translationX = swipe.value }
    var showQueue by remember { mutableStateOf(false) }
    val glass = EmPee3Glass.isActive

    BackHandler(enabled = showLyrics) { showLyrics = false }

    // The backdrop sits behind the whole scaffold, so it also fills the area
    // behind the status and gesture bars.
    // The artwork backdrop is what the glass controls frost.
    val artworkBackdrop = rememberGlassBackdrop()
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .glassBackdropSource(artworkBackdrop),
        ) {
            GlassArtworkBackdrop(song = song)
        }
        AppScaffold(
            contentBackdrop = artworkBackdrop,
            topBar = {
                AppTopBar(
                    navigation = TopBarNavigation.Detail(onBackClick),
                    title = "Now Playing",
                    glass = false,
                    containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surface),
                )
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                if (song == null) {
                    EmptyState(message = "Nothing is playing. Pick a song from your library.")
                } else {
                    val totalDurationMs =
                        (if (playbackState.durationMs > 0) playbackState.durationMs else song.durationMs).coerceAtLeast(1L)

                    // Header → artwork → metadata → progress → controls → actions with fixed
                    // gaps; the artwork shrinks on short screens instead of the gaps growing.
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.heroGutter, vertical = Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
                    ) {
                        AnimatedTrack(
                            song = song,
                            index = playbackState.currentIndex,
                            shuffle = playbackState.shuffleModeEnabled,
                            handOff = { swipeHandOff.intValue },
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .then(gestureRegion)
                                    .then(followSwipe),
                            contentAlignment = Alignment.Center,
                        ) { track ->
                            SongArtwork(
                                song = track,
                                contentDescription = "Album artwork",
                                modifier =
                                    Modifier
                                        .onGloballyPositioned { transition?.heroArtworkBounds = it.boundsInRoot() }
                                        .graphicsLayer { alpha = if (transition == null || transition.isSettledOpen) 1f else 0f }
                                        .widthIn(max = ArtworkSize.heroMax)
                                        .fillMaxWidth()
                                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                        .glassPanel(Radius.largeShape, backdrop = null)
                                        .clip(Radius.largeShape),
                            )
                        }

                        AnimatedTrack(
                            song = song,
                            index = playbackState.currentIndex,
                            shuffle = playbackState.shuffleModeEnabled,
                            handOff = { swipeHandOff.intValue },
                            modifier =
                                Modifier
                                    .then(gestureRegion)
                                    .then(followSwipe),
                            contentAlignment = Alignment.Center,
                        ) { track ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = "${track.artist} — ${track.album}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        PlaybackProgress(
                            position = position,
                            totalDurationMs = totalDurationMs,
                            queueLabel =
                                if (playbackState.queue.size > 1) {
                                    "${playbackState.currentIndex + 1} / ${playbackState.queue.size}"
                                } else {
                                    null
                                },
                            onSeek = { playbackRepository.seekTo(it) },
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (glass) {
                                            Modifier
                                                .glassPanel(Radius.pillShape, level = GlassLevel.Strong)
                                                .padding(vertical = Spacing.sm)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToggleIconButton(
                                active = playbackState.shuffleModeEnabled,
                                onClick = { playbackRepository.setShuffleModeEnabled(!playbackState.shuffleModeEnabled) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (playbackState.shuffleModeEnabled) "Shuffle on" else "Shuffle off",
                                )
                            }

                            PressIconButton(onClick = { playbackRepository.skipPrevious() }) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(IconSize.large),
                                )
                            }

                            val playInteraction = rememberInteraction()
                            FilledIconButton(
                                onClick = { if (isPlaying) playbackRepository.pause() else playbackRepository.play() },
                                modifier =
                                    Modifier
                                        .size(ComponentSize.primaryButton)
                                        .pressScale(playInteraction),
                                shape = CircleShape,
                                interactionSource = playInteraction,
                            ) {
                                SwapIcon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(IconSize.hero),
                                    )
                                }
                            }

                            PressIconButton(
                                onClick = { playbackRepository.skipNext() },
                                enabled = hasNext(playbackState),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(IconSize.large),
                                )
                            }

                            ToggleIconButton(
                                active = playbackState.repeatMode != RepeatMode.OFF,
                                onClick = { playbackRepository.setRepeatMode(playbackState.repeatMode.next()) },
                            ) {
                                SwapIcon(
                                    if (playbackState.repeatMode ==
                                        RepeatMode.ONE
                                    ) {
                                        Icons.Default.RepeatOne
                                    } else {
                                        Icons.Default.Repeat
                                    },
                                ) {
                                    Icon(
                                        imageVector = it,
                                        contentDescription =
                                            when (playbackState.repeatMode) {
                                                RepeatMode.OFF -> "Repeat off"
                                                RepeatMode.ALL -> "Repeat all"
                                                RepeatMode.ONE -> "Repeat one"
                                            },
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PressIconButton(onClick = { onToggleFavorite(song) }) {
                                val heartColor =
                                    animatedStateColor(
                                        if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        label = "favoriteTint",
                                    )
                                SwapIcon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder) {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                        tint = heartColor,
                                    )
                                }
                            }
                            PressIconButton(onClick = { showLyrics = true }) {
                                Icon(imageVector = Icons.Default.Lyrics, contentDescription = "Lyrics")
                            }
                            PressIconButton(onClick = { showQueue = true }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                            }
                            PressIconButton(onClick = { onAddToPlaylist(song) }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist")
                            }
                        }
                    }
                }

                if (showLyrics && song != null) {
                    LyricsOverlay(lyrics = lyrics, onClose = { showLyrics = false })
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            playbackState = playbackState,
            playbackRepository = playbackRepository,
            onDismiss = { showQueue = false },
        )
    }
}

/**
 * Seek bar with elapsed / total time. The only part of Now Playing that reads
 * the playback position, so position ticks recompose just this composable.
 */
@Composable
private fun PlaybackProgress(
    position: State<Long>,
    totalDurationMs: Long,
    queueLabel: String?,
    onSeek: (Long) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    val displayPositionMs = (if (isDragging) dragPositionMs.toLong() else position.value).coerceIn(0L, totalDurationMs)

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayPositionMs.toFloat(),
            onValueChange = {
                isDragging = true
                dragPositionMs = it
            },
            onValueChangeFinished = {
                onSeek(dragPositionMs.toLong())
                isDragging = false
            },
            valueRange = 0f..totalDurationMs.toFloat(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Seek" },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = formatMs(displayPositionMs), style = TextStyles.metadata)
            if (queueLabel != null) {
                Text(
                    text = queueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = formatMs(totalDurationMs), style = TextStyles.metadata)
        }
    }
}

@Composable
private fun ToggleIconButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    // On/off is a colour change that fades, never a jump.
    val background =
        animatedStateColor(
            if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
            label = "toggleBackground",
        )
    val foreground =
        animatedStateColor(
            if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_ALPHA),
            label = "toggleForeground",
        )
    PressIconButton(onClick = onClick) {
        Box(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(background)
                    .padding(6.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides foreground) {
                content()
            }
        }
    }
}

@Composable
private fun LyricsOverlay(
    lyrics: String?,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Lyrics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close lyrics")
            }
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = lyrics ?: "This file has no embedded lyrics.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (lyrics == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
private fun QueueSheet(
    playbackState: PlaybackState,
    playbackRepository: PlaybackRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val queue = playbackState.queue
    LaunchedEffect(Unit) {
        if (playbackState.currentIndex > 0) listState.scrollToItem(playbackState.currentIndex)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Queue (${queue.size})",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        if (queue.isEmpty()) {
            Text(
                text = "The queue is empty.",
                modifier = Modifier.padding(Spacing.lg),
            )
        } else {
            LazyColumn(state = listState) {
                // Keys must be unique even if the same song is queued twice.
                itemsIndexed(queue, key = { index, song -> "${song.id}_$index" }) { index, queuedSong ->
                    SongRow(
                        song = queuedSong,
                        isHighlighted = index == playbackState.currentIndex,
                        onClick = { playbackRepository.skipToQueueItem(index) },
                    ) {
                        IconButton(
                            onClick = { scope.launch { playbackRepository.reorder(index, index - 1) } },
                            enabled = index > 0,
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { scope.launch { playbackRepository.reorder(index, index + 1) } },
                            enabled = index < queue.lastIndex,
                        ) {
                            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { scope.launch { playbackRepository.removeFromQueue(index) } }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from queue")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

private fun hasNext(state: PlaybackState): Boolean =
    state.queue.size > 1 && (state.shuffleModeEnabled || state.repeatMode == RepeatMode.ALL || state.currentIndex < state.queue.lastIndex)

private fun RepeatMode.next(): RepeatMode =
    when (this) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
    }

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / MS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
