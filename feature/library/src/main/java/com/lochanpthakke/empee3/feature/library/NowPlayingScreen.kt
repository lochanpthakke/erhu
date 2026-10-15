package com.lochanpthakke.empee3.feature.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
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
import com.lochanpthakke.empee3.core.designsystem.component.selectionJiggle
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop
import com.lochanpthakke.empee3.core.model.AbLoopState
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.model.Waveform
import com.lochanpthakke.empee3.core.model.WaveformLoad
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository
import java.util.Locale

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val INACTIVE_ALPHA = 0.6f

/** The blurred artwork background is fully there over the first part of the expansion. */
private const val BACKDROP_REVEAL_END = 0.4f

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
    /** Online lyrics lookup and choice for the lyrics page; null keeps it to the file's lyrics. */
    lyricsActions: LyricsActions?,
    onBackClick: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    /** Saves the given queue entries as a new playlist; the callback receives the message to show. */
    onSaveQueueAsPlaylist: (name: String, songs: List<Song>, onResult: (String) -> Unit) -> Unit,
    /** Waveform and bookmarks of the current track. */
    extras: PlayerExtrasViewModel,
    /** The audio visualizer's preferences and playback reading. */
    visualizer: VisualizerViewModel,
    /** The audio inspector (Audio details). */
    audioDetails: AudioDetailsViewModel,
    /** Opens the equalizer's own controls (from Audio details). */
    onOpenEqualizer: () -> Unit,
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

    // The player is one object (PlayerTransitionState): dragging the artwork / title
    // down collapses it into the mini player; sideways swipes change the track.
    val transition = LocalPlayerTransition.current
    // Tracks are a strip (previous | current | next) moved by one offset: the swipe drives
    // it directly and every track change (swipe, buttons, end of song) slides it.
    val carousel = rememberTrackCarouselState()
    TrackChangeEffect(carousel, song, playbackState.currentIndex, playbackState.previousSong, playbackState.nextSong)
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
    // Which tab of the queue sheet is open, or null when it is closed.
    var queueSheetPage by remember { mutableStateOf<Int?>(null) }
    var showVisualizer by remember { mutableStateOf(false) }
    var showAudioDetails by remember { mutableStateOf(false) }
    val waveformLoad by extras.waveform.collectAsState()
    val bookmarks by extras.bookmarks.collectAsState()
    val bookmarkPositions = remember(bookmarks) { bookmarks.map { it.positionMs } }
    val context = LocalContext.current

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
                    actions = {
                        IconButton(onClick = { showAudioDetails = true }, enabled = song != null) {
                            Icon(imageVector = Icons.Outlined.Info, contentDescription = "Audio details")
                        }
                        IconButton(onClick = { showVisualizer = true }, enabled = song != null) {
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = "Open visualizer")
                        }
                    },
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
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
                                        .graphicsLayer { alpha = if (transition == null || transition.isSettledOpen) 1f else 0f }
                                } else {
                                    Modifier
                                }
                            SongArtwork(
                                song = track,
                                contentDescription = if (isCurrent) "Album artwork" else null,
                                modifier =
                                    hero
                                        .playerArtworkLayout { bounds ->
                                            if (isCurrent) transition?.heroArtworkBounds = bounds
                                        }.glassPanel(Radius.largeShape, backdrop = null)
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

                        // Progress bar + Quick actions tightly grouped
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            PlaybackProgress(
                                position = position,
                                totalDurationMs = totalDurationMs,
                                waveform = (waveformLoad as? WaveformLoad.Ready)?.waveform,
                                bookmarkPositions = bookmarkPositions,
                                loop = playbackState.abLoop,
                                queueLabel =
                                    if (playbackState.queue.size > 1) {
                                        "${playbackState.currentIndex + 1} / ${playbackState.queue.size}"
                                    } else {
                                        null
                                    },
                                onSeek = { playbackRepository.seekTo(it) },
                                modifier = Modifier.revealWithPlayer(transition, start = 0.40f, end = 0.84f),
                            )

                            // Favorite & Add to Playlist quick actions aligned below the progress bar
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .revealWithPlayer(transition, start = 0.44f, end = 0.88f),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PressIconButton(
                                    onClick = { onToggleFavorite(song) },
                                    modifier = Modifier.offset(x = (-12).dp),
                                ) {
                                    val heartColor =
                                        animatedStateColor(
                                            if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            label = "favoriteTint",
                                        )
                                    Box(Modifier.selectionJiggle(isFavorite, identity = song.id, expressive = true)) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                            tint = heartColor,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PressIconButton(
                                        onClick = {
                                            extras.addBookmarkAtCurrentPosition { error ->
                                                error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                            }
                                        },
                                    ) {
                                        Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Bookmark this moment")
                                    }
                                    PressIconButton(onClick = { queueSheetPage = QUEUE_SHEET_PAGE_MARKS }) {
                                        Icon(
                                            imageVector =
                                                if (playbackState.abLoop?.enabled ==
                                                    true
                                                ) {
                                                    Icons.Default.RepeatOn
                                                } else {
                                                    Icons.Default.Bookmarks
                                                },
                                            contentDescription = "Bookmarks and A-B loop",
                                            tint =
                                                if (playbackState.abLoop?.enabled ==
                                                    true
                                                ) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    LocalContentColor.current
                                                },
                                        )
                                    }
                                }
                                PressIconButton(
                                    onClick = { onAddToPlaylist(song) },
                                    modifier = Modifier.offset(x = 12.dp),
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add to playlist")
                                }
                            }
                        }

                        // Main playback controls (SpaceBetween, outer edge-aligned)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .revealWithPlayer(transition, start = 0.48f, end = 0.92f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToggleIconButton(
                                active = playbackState.shuffleModeEnabled,
                                onClick = { playbackRepository.setShuffleModeEnabled(!playbackState.shuffleModeEnabled) },
                                modifier = Modifier.offset(x = (-12).dp),
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
                                        .size(ComponentSize.primaryButton),
                                shape = CircleShape,
                                interactionSource = playInteraction,
                            ) {
                                Box(Modifier.pressScale(playInteraction)) {
                                    SwapIcon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { icon ->
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(IconSize.hero),
                                        )
                                    }
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
                                feedbackValue = playbackState.repeatMode,
                                onClick = { playbackRepository.setRepeatMode(playbackState.repeatMode.next()) },
                                modifier = Modifier.offset(x = 12.dp),
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
                            onClick = { queueSheetPage = QUEUE_SHEET_PAGE_QUEUE },
                            modifier =
                                Modifier
                                    .revealWithPlayer(transition, start = 0.54f, end = 0.98f)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures { _, dragAmount ->
                                            if (dragAmount < -10f) {
                                                queueSheetPage = QUEUE_SHEET_PAGE_QUEUE
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
        if (showVisualizer) {
            VisualizerScreen(
                playbackState = playbackState,
                viewModel = visualizer,
                onPlayPause = { if (isPlaying) playbackRepository.pause() else playbackRepository.play() },
                onPrevious = { playbackRepository.skipPrevious() },
                onNext = { playbackRepository.skipNext() },
                onClose = { showVisualizer = false },
            )
        }
        if (showAudioDetails) {
            AudioDetailsScreen(
                viewModel = audioDetails,
                onOpenEqualizer = {
                    showAudioDetails = false
                    onOpenEqualizer()
                },
                onClose = { showAudioDetails = false },
            )
        }
    }

    queueSheetPage?.let { page ->
        QueueSheet(
            playbackState = playbackState,
            playbackRepository = playbackRepository,
            lyrics = lyrics,
            position = position,
            isPlaying = isPlaying,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            extras = extras,
            initialPage = page,
            onDismiss = { queueSheetPage = null },
            lyricsActions = lyricsActions,
        )
    }
}

/**
 * The track's waveform as the seek bar, with elapsed / total time. Playback ticks only reach
 * [ElapsedTime] (recomposed) and the waveform (redrawn); a drag previews the position until release.
 */
@Composable
@Suppress("LongParameterList")
private fun PlaybackProgress(
    position: State<Long>,
    totalDurationMs: Long,
    waveform: Waveform?,
    bookmarkPositions: List<Long>,
    loop: AbLoopState?,
    queueLabel: String?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewMs by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ElapsedTime(position = position, previewMs = previewMs, totalDurationMs = totalDurationMs)
            if (queueLabel != null) {
                Text(
                    text = queueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = formatMs(totalDurationMs), style = TextStyles.metadata)
        }
        WaveformSeekBar(
            waveform = waveform,
            position = position,
            previewPositionMs = previewMs,
            durationMs = totalDurationMs,
            bookmarkPositions = bookmarkPositions,
            loop = loop,
            onPreview = { previewMs = it },
            onSeek = onSeek,
        )
    }
}

/** Elapsed time (the drag preview while scrubbing). The one text that follows playback ticks. */
@Composable
private fun ElapsedTime(
    position: State<Long>,
    previewMs: Long?,
    totalDurationMs: Long,
) {
    Text(text = formatMs((previewMs ?: position.value).coerceIn(0L, totalDurationMs)), style = TextStyles.metadata)
}

@Composable
private fun ToggleIconButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    feedbackValue: Any = active,
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
    PressIconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .selectionJiggle(feedbackValue)
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
