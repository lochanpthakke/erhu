package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.database.repository.AnimatedArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.designsystem.artwork.GlassArtworkBackdrop
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.glassBackdropSource
import com.lochanpthakke.empee3.core.designsystem.theme.rememberGlassBackdrop
import com.lochanpthakke.empee3.core.model.PlaybackStatus
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.playback.repository.PlaybackRepository

/** How far the lyrics header card sits above the gesture bar at the bottom of the player page. */
private val LYRICS_PEEK = 76.dp

/**
 * The main expanded Now Playing view, including the hero track card, playback controls, and lyrics.
 */
@Composable
@Suppress("LongParameterList")
fun NowPlayingScreen(
    playbackRepository: PlaybackRepository,
    libraryRepository: LibraryRepository? = null,
    animatedArtworkRepository: AnimatedArtworkRepository? = null,
    isFavorite: Boolean,
    lyrics: State<SongLyrics?>,
    lyricsActions: LyricsActions?,
    onBackClick: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onSaveQueueAsPlaylist: (String, List<Song>, (String) -> Unit) -> Unit,
    extras: PlayerExtrasViewModel,
    visualizer: VisualizerViewModel,
    audioDetails: AudioDetailsViewModel,
    onOpenEqualizer: () -> Unit,
    onShowMusicInfo: (Song) -> Unit = {},
    isHiddenFromMixes: Boolean = false,
    onSetHiddenFromMixes: (Song, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val playbackState by playbackRepository.collectStablePlaybackState()
    val isPlaying =
        playbackState.status == PlaybackStatus.PLAYING ||
            playbackState.status == PlaybackStatus.BUFFERING

    val song = playbackState.currentSong
    val transition = LocalPlayerTransition.current
    val gestureRegion = if (transition != null) Modifier.playerDrag(transition, expandsUpward = true) else Modifier

    val carousel = rememberTrackCarouselState()
    var lyricsExpanded by remember { mutableStateOf(false) }
    var queueSheetPage by remember { mutableStateOf<Int?>(null) }
    var showVisualizer by remember { mutableStateOf(false) }
    var showAudioDetails by remember { mutableStateOf(false) }

    val reduceMotion = rememberReduceMotion()

    val lyricsColors = rememberLyricsColors(song)
    val pageScroll = rememberScrollState()

    fun previous() {
        if (playbackState.positionMs > PREVIOUS_SONG_THRESHOLD_MS) {
            playbackRepository.seekTo(0)
        } else {
            playbackRepository.skipToPreviousItem()
        }
    }

    fun next() {
        playbackRepository.skipNext()
    }

    fun playPause() {
        if (isPlaying) {
            playbackRepository.pause()
        } else {
            playbackRepository.play()
        }
    }

    val artworkBackdrop = rememberGlassBackdrop()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .revealWithPlayer(transition, start = 0f, end = 0.40f, rise = 0.dp)
                    .glassBackdropSource(artworkBackdrop),
        ) {
            GlassArtworkBackdrop(song = song)
        }

        AppScaffold(
            contentBackdrop = artworkBackdrop,
            topBar = {
                AppTopBar(
                    navigation = TopBarNavigation.Detail(onBack = onBackClick),
                    title = "Now Playing",
                    actions = {
                        IconButton(onClick = { queueSheetPage = QUEUE_SHEET_PAGE_QUEUE }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                        }
                    },
                    modifier = Modifier.revealWithPlayer(transition, start = 0.30f, end = 0.70f),
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

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val viewport = maxHeight
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(pageScroll)) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(viewport - LYRICS_PEEK)
                                        .padding(top = Spacing.sm, bottom = Spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                TrackCarousel(
                                    state = carousel,
                                    previous = playbackState.previousSong,
                                    current = song,
                                    next = playbackState.nextSong,
                                    reduceMotion = reduceMotion,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .then(gestureRegion),
                                ) { track, isCurrent, distance ->
                                    CarouselCard(
                                        track = track,
                                        isCurrent = isCurrent,
                                        distance = distance,
                                        transition = transition,
                                        isFavorite = isFavorite,
                                        isPlaying = isPlaying,
                                        libraryRepository = libraryRepository,
                                        animatedArtworkRepository = animatedArtworkRepository,
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onSelect = { if (distance() < 0f) previous() else next() },
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.heroGutter),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    PlaybackProgress(
                                        position = remember(playbackState.positionMs) { mutableStateOf(playbackState.positionMs) },
                                        totalDurationMs = totalDurationMs,
                                        extras = extras,
                                        loop = playbackState.abLoop,
                                        queueLabel =
                                            if (playbackState.queue.size > 1) {
                                                "${playbackState.currentIndex + 1} / ${playbackState.queue.size}"
                                            } else {
                                                null
                                            },
                                        onSeek = { playbackRepository.seekTo(it) },
                                        isPlaying = isPlaying,
                                        accent = seekAccent(lyricsColors),
                                        reduceMotion = reduceMotion,
                                        modifier = Modifier.revealWithPlayer(transition, start = 0.40f, end = 0.84f),
                                    )

                                    TransportControls(
                                        playbackState = playbackState,
                                        isPlaying = isPlaying,
                                        onShuffle = { playbackRepository.setShuffleModeEnabled(!playbackState.shuffleModeEnabled) },
                                        onPrevious = ::previous,
                                        onPlayPause = ::playPause,
                                        onNext = ::next,
                                        onRepeat = { playbackRepository.setRepeatMode(playbackState.repeatMode.next()) },
                                        modifier = Modifier.revealWithPlayer(transition, start = 0.45f, end = 0.90f),
                                    )
                                }
                            }

                            LyricsPanel(
                                songId = song.id,
                                lyrics = lyrics.value,
                                position = remember(playbackState.positionMs) { mutableStateOf(playbackState.positionMs) },
                                isPlaying = isPlaying,
                                actions = lyricsActions,
                                colors = lyricsColors,
                                onSeek = { playbackRepository.seekTo(it) },
                                onExpand = { lyricsExpanded = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.heroGutter, vertical = Spacing.xs),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = lyricsExpanded && song != null,
            enter = slideInVertically(tween(Motion.NAVIGATION, easing = Motion.Decelerate)) { it / 3 } + fadeIn(tween(Motion.CONTENT)),
            exit = slideOutVertically(tween(Motion.CONTENT, easing = Motion.Accelerate)) { it / 3 } + fadeOut(tween(Motion.MICRO)),
        ) {
            val shown = song ?: return@AnimatedVisibility
            ExpandedLyrics(
                song = shown,
                lyrics = lyrics.value,
                position = remember(playbackState.positionMs) { mutableStateOf(playbackState.positionMs) },
                isPlaying = isPlaying,
                actions = lyricsActions,
                colors = lyricsColors,
                onSeek = { playbackRepository.seekTo(it) },
                onPlayPause = ::playPause,
                onPrevious = ::previous,
                onNext = ::next,
                onCollapse = { lyricsExpanded = false },
                modifier =
                    Modifier
                        .background(Brush.verticalGradient(lyricsColors.gradient))
                        .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        if (showVisualizer) {
            VisualizerScreen(
                playbackState = playbackState,
                viewModel = visualizer,
                onPlayPause = ::playPause,
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
            lyrics = lyrics.value,
            position = remember(playbackState.positionMs) { mutableStateOf(playbackState.positionMs) },
            isPlaying = isPlaying,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            extras = extras,
            initialPage = page,
            onDismiss = { queueSheetPage = null },
            lyricsActions = lyricsActions,
        )
    }
}

private const val PREVIOUS_SONG_THRESHOLD_MS = 3000L
