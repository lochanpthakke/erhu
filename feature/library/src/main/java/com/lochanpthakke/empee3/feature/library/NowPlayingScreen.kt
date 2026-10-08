package com.lochanpthakke.empee3.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlin.math.roundToInt

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val INACTIVE_ALPHA = 0.6f

/** The blurred artwork background is fully there over the first part of the expansion. */
private const val BACKDROP_REVEAL_END = 0.4f

/** Environment glass of the controls pill: a little less than the mini player's. */
private const val NOW_PLAYING_OPTICS = 0.7f

/** "Previous" restarts the current song past this position (the player's own rule). */
private const val RESTART_THRESHOLD_MS = 3_000L

/** How far Now Playing's sections travel into place as the player opens. */
private val REVEAL_RISE = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
fun NowPlayingScreen(
    playbackRepository: PlaybackRepository,
    isFavorite: Boolean,
    lyrics: SongLyrics?,
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
    val isPlaying =
        playbackState.status == PlaybackStatus.PLAYING ||
            playbackState.status == PlaybackStatus.BUFFERING

    var showLyrics by remember { mutableStateOf(false) }
    // The player is one object (PlayerTransitionState): dragging the artwork / title
    // down collapses it into the mini player; sideways swipes change the track.
    val transition = LocalPlayerTransition.current
    // Tracks are a strip (previous | current | next) moved by one offset: the swipe drives
    // it directly and every track change (swipe, buttons, end of song) slides it.
    val carousel = rememberTrackCarouselState()
    TrackChangeEffect(carousel, song, playbackState.previousSong, playbackState.nextSong)
    val latestState by rememberUpdatedState(playbackState)
    val collapseDrag = if (transition != null) Modifier.playerDrag(transition, expandsUpward = false) else Modifier
    val trackSwipe =
        Modifier.trackCarouselSwipe(
            state = carousel,
            canGoNext = { latestState.nextSong != null },
            canGoPrevious = { latestState.previousSong != null },
            // A swipe always changes the song (never a restart), matching the page it showed.
            skip = { direction -> if (direction > 0) playbackRepository.skipNext() else playbackRepository.skipToPreviousItem() },
        )
    val gestureRegion = collapseDrag.then(trackSwipe)
    var showQueue by remember { mutableStateOf(false) }
    val glass = EmPee3Glass.isActive

    BackHandler(enabled = showLyrics) { showLyrics = false }

    // The backdrop sits behind the whole scaffold, so it also fills the area
    // behind the status and gesture bars.
    // The artwork backdrop is what the glass controls frost.
    val artworkBackdrop = rememberGlassBackdrop()
    Box(modifier = modifier.fillMaxSize().onSizeChanged { carousel.page = it.width.toFloat() }) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .revealWithPlayer(transition, start = 0f, end = BACKDROP_REVEAL_END, rise = 0.dp)
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
                    modifier = Modifier.revealWithPlayer(transition, start = 0.45f, end = 0.9f, rise = -REVEAL_RISE),
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
                        TrackCarousel(
                            state = carousel,
                            previous = playbackState.previousSong,
                            current = song,
                            next = playbackState.nextSong,
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .then(gestureRegion),
                        ) { track, isCurrent ->
                            // The current page is the player's hero artwork: hidden while the
                            // travelling artwork stands in for it during open / close.
                            val hero =
                                if (isCurrent) {
                                    Modifier
                                        .onGloballyPositioned { transition?.heroArtworkBounds = it.boundsInRoot() }
                                        .graphicsLayer { alpha = if (transition == null || transition.isSettledOpen) 1f else 0f }
                                } else {
                                    Modifier
                                }
                            SongArtwork(
                                song = track,
                                contentDescription = if (isCurrent) "Album artwork" else null,
                                modifier =
                                    hero
                                        .widthIn(max = ArtworkSize.heroMax)
                                        .fillMaxWidth()
                                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                        .glassPanel(Radius.largeShape, backdrop = null)
                                        .clip(Radius.largeShape),
                            )
                        }

                        TrackCarousel(
                            state = carousel,
                            previous = playbackState.previousSong,
                            current = song,
                            next = playbackState.nextSong,
                            modifier =
                                Modifier
                                    .revealWithPlayer(transition, start = 0.35f, end = 0.8f)
                                    .then(gestureRegion),
                        ) { track, _ ->
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

                        // Favorite & Add to Playlist quick actions above the progress bar
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.md)
                                    .revealWithPlayer(transition, start = 0.40f, end = 0.84f),
                            horizontalArrangement = Arrangement.SpaceBetween,
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
                            PressIconButton(onClick = { onAddToPlaylist(song) }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add to playlist")
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
                            modifier = Modifier.revealWithPlayer(transition, start = 0.42f, end = 0.86f),
                        )

                        // Main playback controls moved to the lower section
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .revealWithPlayer(transition, start = 0.48f, end = 0.92f)
                                    .then(
                                        if (glass) {
                                            Modifier
                                                .glassPanel(
                                                    Radius.pillShape,
                                                    level = GlassLevel.Strong,
                                                    optics = NOW_PLAYING_OPTICS,
                                                    environmentLens = false,
                                                ).padding(vertical = Spacing.sm)
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

                            PressIconButton(
                                onClick = {
                                    if (position.value > RESTART_THRESHOLD_MS || latestState.previousSong == null) {
                                        playbackRepository.skipPrevious()
                                    } else {
                                        carousel.skipFromButton(-1) { playbackRepository.skipToPreviousItem() }
                                    }
                                },
                            ) {
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
                                onClick = {
                                    if (latestState.nextSong != null) {
                                        carousel.skipFromButton(1) { playbackRepository.skipNext() }
                                    } else {
                                        playbackRepository.skipNext()
                                    }
                                },
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

                        // Up Arrow trigger for Queue & Lyrics Sheet
                        PressIconButton(
                            onClick = { showQueue = true },
                            modifier =
                                Modifier
                                    .revealWithPlayer(transition, start = 0.54f, end = 0.98f)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, dragAmount ->
                                            if (dragAmount < -10f) {
                                                showQueue = true
                                            }
                                        }
                                    },
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Open queue and lyrics",
                                modifier = Modifier.size(IconSize.large),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            playbackState = playbackState,
            playbackRepository = playbackRepository,
            lyrics = lyrics,
            position = position,
            isPlaying = isPlaying,
            onDismiss = { showQueue = false },
        )
    }
}

/**
 * Seek bar with elapsed / total time. The only part of Now Playing that reads
 * the playback position, so position ticks recompose just this composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackProgress(
    position: State<Long>,
    totalDurationMs: Long,
    queueLabel: String?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    val displayPositionMs = (if (isDragging) dragPositionMs.toLong() else position.value).coerceIn(0L, totalDurationMs)

    Column(modifier = modifier.fillMaxWidth()) {
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
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
            thumb = {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
private fun QueueSheet(
    playbackState: PlaybackState,
    playbackRepository: PlaybackRepository,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val queue = playbackState.queue
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(Unit) {
        if (playbackState.currentIndex > 0) {
            listState.scrollToItem(playbackState.currentIndex)
        }
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 68.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queue (${queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(0) } },
                )
                Spacer(modifier = Modifier.width(Spacing.xl))
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(1) } },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                if (page == 0) {
                    if (queue.isEmpty()) {
                        Text(
                            text = "The queue is empty.",
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    } else {
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
                                        val targetIndex =
                                            (activeDragIndex + (draggingOffset / rowHeightPx).roundToInt()).coerceIn(0, queue.lastIndex)
                                        when {
                                            activeDragIndex < targetIndex && index in (activeDragIndex + 1)..targetIndex -> -rowHeightPx
                                            activeDragIndex > targetIndex && index in targetIndex..<activeDragIndex -> rowHeightPx
                                            else -> 0f
                                        }
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

                                SongRow(
                                    song = queuedSong,
                                    isHighlighted = index == playbackState.currentIndex,
                                    onClick = { playbackRepository.skipToQueueItem(index) },
                                    modifier = itemModifier,
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
                                    IconButton(onClick = { scope.launch { playbackRepository.removeFromQueue(index) } }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove from queue")
                                    }
                                }
                            }
                        }
                    }
                } else {
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
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        }
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
