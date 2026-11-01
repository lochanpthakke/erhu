package com.erhu.erhu.feature.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.designsystem.artwork.GlassArtworkBackdrop
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.AppTopBar
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.glassBackdropSource
import com.erhu.erhu.core.designsystem.theme.rememberGlassBackdrop
import com.erhu.erhu.core.model.AbLoopState
import com.erhu.erhu.core.model.PlaybackStatus
import com.erhu.erhu.core.model.RepeatMode
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.feature.library.components.rememberCoverVideoHost
import kotlinx.coroutines.launch
import java.util.Locale

private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60

/** The blurred artwork background is fully there over the first part of the expansion. */
private const val BACKDROP_REVEAL_END = 0.4f

/** "Previous" restarts the current song past this position (the player's own rule). */
private const val RESTART_THRESHOLD_MS = 3_000L

/** How far Now Playing's sections travel into place as the player opens. */
private val REVEAL_RISE = 24.dp

/** How much of the lyrics panel (its heading) shows at the bottom of the first screen. */
private val LYRICS_PEEK = 64.dp

/** The lyrics panel's height: a share of the screen, within sensible bounds. */
private const val LYRICS_PANEL_SHARE = 0.62f
private val LYRICS_PANEL_MIN_HEIGHT = 300.dp
private val LYRICS_PANEL_MAX_HEIGHT = 560.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
fun NowPlayingScreen(
    playbackRepository: PlaybackRepository,
    /** With [animatedArtworkRepository]: the current card plays the album's animated cover when it has one. */
    libraryRepository: LibraryRepository? = null,
    animatedArtworkRepository: AnimatedArtworkRepository? = null,
    isFavorite: Boolean,
    lyrics: State<SongLyrics?>,
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
    /** Opens the track's tags (Music information). */
    onShowMusicInfo: (Song) -> Unit = {},
    /** Whether the current song is hidden from Smart Playlists. */
    isHiddenFromMixes: Boolean = false,
    /** Hides the song from (or returns it to) Smart Playlists; null leaves the action out. */
    onSetHiddenFromMixes: ((Song, Boolean) -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    /** Songs of the Smart Radio in the queue (empty when none): "New radio picks" shows for these. */
    radioSongIds: Set<Long> = emptySet(),
    onRefreshRadio: (() -> Unit)? = null,
    /** The outcome of an Add to playlist started here ("Added to …", "Already in …"), shown once. */
    playlistMessage: String? = null,
    onPlaylistMessageShown: () -> Unit = {},
) {
    // Stable state only; the position is read by PlaybackProgress and the lyrics alone, so the
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
    // The page scrolls (artwork and controls, then the lyrics); dragging down collapses the player
    // only while it is at the top, otherwise the drag scrolls back up.
    val pageScroll = rememberScrollState()
    val collapseDrag =
        if (transition != null) {
            Modifier.playerDrag(transition, expandsUpward = false, canStart = { pageScroll.value == 0 })
        } else {
            Modifier
        }
    val trackSwipe =
        Modifier.trackCarouselSwipe(
            state = carousel,
            canGoNext = { latestState.nextSong != null },
            canGoPrevious = { latestState.previousSong != null },
            // A swipe always changes the song (never a restart), matching the page it showed.
            skip = { direction -> if (direction > 0) playbackRepository.skipNext() else playbackRepository.skipToPreviousItem() },
        )
    val gestureRegion = collapseDrag.then(trackSwipe)
    val reduceMotion = rememberReduceMotion()
    // Which tab of the queue sheet is open, or null when it is closed.
    var queueSheetPage by remember { mutableStateOf<Int?>(null) }
    // The tabs it shows: all from the menu, only the queue and history from the Queue button.
    var queueSheetPages by remember { mutableStateOf(QUEUE_SHEET_ALL_PAGES) }
    var showVisualizer by remember { mutableStateOf(false) }
    // The neighbour cards: side by side (the default on every entry) or stacked behind the current
    // one. The animated value is read only while drawing, so the page does not recompose per frame;
    // a tap mid-way simply retargets the running spring.
    var stacked by remember { mutableStateOf(false) }
    val stack = remember { Animatable(0f) }
    // One cover video for the current card: a track change within the album keeps it running.
    val coverVideo = rememberCoverVideoHost()
    LaunchedEffect(stacked) { stack.animateTo(if (stacked) 1f else 0f, Motion.settle()) }
    var showAudioDetails by remember { mutableStateOf(false) }
    var lyricsExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(playlistMessage) {
        val text = playlistMessage ?: return@LaunchedEffect
        onPlaylistMessageShown()
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
    }

    fun playPause() = if (isPlaying) playbackRepository.pause() else playbackRepository.play()

    fun previous() {
        if (position.value > RESTART_THRESHOLD_MS || latestState.previousSong == null) {
            playbackRepository.skipPrevious()
        } else {
            carousel.skipFromButton(-1) { playbackRepository.skipToPreviousItem() }
        }
    }

    fun next() {
        if (latestState.nextSong != null) {
            carousel.skipFromButton(1) { playbackRepository.skipNext() }
        } else {
            playbackRepository.skipNext()
        }
    }

    val menuActions =
        NowPlayingMenuActions(
            onMusicInfo = { song?.let(onShowMusicInfo) },
            onAudioDetails = { showAudioDetails = true },
            onAddBookmark = {
                extras.addBookmarkAtCurrentPosition { error ->
                    Toast.makeText(context, error ?: "Bookmark added", Toast.LENGTH_SHORT).show()
                }
            },
            onBookmarks = {
                queueSheetPages = QUEUE_SHEET_ALL_PAGES
                queueSheetPage = QUEUE_SHEET_PAGE_MARKS
            },
            onAddToPlaylist = { song?.let(onAddToPlaylist) },
            onToggleHidden =
                onSetHiddenFromMixes?.let { setHidden ->
                    {
                        val target = song
                        if (target != null) {
                            val hide = !isHiddenFromMixes
                            setHidden(target, hide)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result =
                                    snackbarHostState.showSnackbar(
                                        message =
                                            if (hide) {
                                                "Hidden from Smart Playlists. It stays in your library and playlists."
                                            } else {
                                                "Back in Smart Playlists"
                                            },
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                if (result == SnackbarResult.ActionPerformed) setHidden(target, !hide)
                            }
                        }
                    }
                },
            onStartRadio = onStartRadio,
            onRefreshRadio = onRefreshRadio?.takeIf { song?.id in radioSongIds },
            onQueue = {
                queueSheetPages = QUEUE_SHEET_ALL_PAGES
                queueSheetPage = QUEUE_SHEET_PAGE_QUEUE
            },
            onVisualizer = { showVisualizer = true },
            onEqualizer = onOpenEqualizer,
        )

    // The backdrop sits behind the whole scaffold, so it also fills the area
    // behind the status and gesture bars.
    // The artwork backdrop is what the glass controls frost.
    // Already a strong blur of the artwork: the glass over it needs no blur of its own.
    val artworkBackdrop = rememberGlassBackdrop("nowPlayingArtwork", preBlurred = true)
    // The lyrics surfaces take their colours from the playing song's artwork (cached per artwork).
    val lyricsColors = rememberLyricsColors(song)
    Box(modifier = modifier.fillMaxSize()) {
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
            // Its top bar is not glass: nothing samples the page itself.
            contentIsBackdrop = false,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AppTopBar(
                    navigation = TopBarNavigation.Detail(onBackClick),
                    title = "Now Playing",
                    actions = {
                        NowPlayingMenu(
                            actions = menuActions,
                            isHidden = isHiddenFromMixes,
                            loopOn = playbackState.abLoop?.enabled == true,
                            enabled = song != null,
                        )
                    },
                    glass = false,
                    // No band of its own: the page's background runs up behind the title and status bar.
                    containerColor = Color.Transparent,
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

                    // One scrolling page: the first screen is the card carousel, progress and controls,
                    // with the lyrics heading just showing at the bottom; the lyrics follow below.
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
                                    stack = { stack.value },
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
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        // A neighbour card is a shortcut to its track: the same motion as a swipe.
                                        onSelect = { if (distance() < 0f) previous() else next() },
                                        selectable = !stacked,
                                        // The cover moves only while the player is open (it stays composed when collapsed).
                                        isPlaying = isPlaying && transition?.isOpen != false,
                                        libraryRepository = libraryRepository,
                                        animatedArtworkRepository = animatedArtworkRepository,
                                        coverVideo = coverVideo,
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.heroGutter),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    PlaybackProgress(
                                        position = position,
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
                                        // The playing song, the same as the menu's Add to playlist.
                                        onAddToPlaylist = menuActions.onAddToPlaylist,
                                        onPrevious = ::previous,
                                        onPlayPause = ::playPause,
                                        onNext = ::next,
                                        onQueue = {
                                            queueSheetPages = QUEUE_SHEET_QUEUE_PAGES
                                            queueSheetPage = QUEUE_SHEET_PAGE_QUEUE
                                        },
                                        onRepeat = { playbackRepository.setRepeatMode(playbackState.repeatMode.next()) },
                                        stacked = stacked,
                                        onToggleStack = { stacked = !stacked },
                                        modifier = Modifier.revealWithPlayer(transition, start = 0.48f, end = 0.92f),
                                    )
                                }
                            }
                            LyricsPanel(
                                songId = song.id,
                                lyrics = lyrics.value,
                                position = position,
                                isPlaying = isPlaying,
                                actions = lyricsActions,
                                colors = lyricsColors,
                                onSeek = { playbackRepository.seekTo(it) },
                                onExpand = { lyricsExpanded = true },
                                modifier =
                                    Modifier
                                        .padding(horizontal = Spacing.heroGutter)
                                        .height((viewport * LYRICS_PANEL_SHARE).coerceIn(LYRICS_PANEL_MIN_HEIGHT, LYRICS_PANEL_MAX_HEIGHT))
                                        .revealWithPlayer(transition, start = 0.54f, end = 0.98f),
                            )
                            // Clear of the gesture bar at the end of the page.
                            Spacer(modifier = Modifier.navigationBarsPadding().height(Spacing.xl))
                        }
                    }
                }
            }
        }
        // The full lyrics rise over the player (same session, same position source); back collapses them.
        AnimatedVisibility(
            visible = lyricsExpanded && song != null,
            enter = slideInVertically(tween(Motion.NAVIGATION, easing = Motion.Decelerate)) { it / 3 } + fadeIn(tween(Motion.CONTENT)),
            exit = slideOutVertically(tween(Motion.CONTENT, easing = Motion.Accelerate)) { it / 3 } + fadeOut(tween(Motion.MICRO)),
        ) {
            val shown = song ?: return@AnimatedVisibility
            ExpandedLyrics(
                song = shown,
                lyrics = lyrics.value,
                position = position,
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
            position = position,
            isPlaying = isPlaying,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            extras = extras,
            initialPage = page,
            pages = queueSheetPages,
            onDismiss = { queueSheetPage = null },
            lyricsActions = lyricsActions,
        )
    }
}

/**
 * The seek bar with elapsed / total time. Playback ticks only reach [ElapsedTime] (recomposed) and
 * the bar (redrawn); a drag previews the position until release.
 */
@Composable
@Suppress("LongParameterList")
private fun PlaybackProgress(
    position: State<Long>,
    totalDurationMs: Long,
    extras: PlayerExtrasViewModel,
    loop: AbLoopState?,
    queueLabel: String?,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    accent: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // Bookmarks load independently on track changes. Only the progress area needs to
    // recompose when they arrive; the artwork/gesture region stays undisturbed.
    val bookmarks by extras.bookmarks.collectAsState()
    val bookmarkPositions = remember(bookmarks) { bookmarks.map { it.positionMs } }
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
        LinearSeekBar(
            position = position,
            isPlaying = isPlaying,
            previewPositionMs = previewMs,
            durationMs = totalDurationMs,
            bookmarkPositions = bookmarkPositions,
            loop = loop,
            accent = accent,
            track = MaterialTheme.colorScheme.onSurface.copy(alpha = SEEK_TRACK_ALPHA),
            bookmarkColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SEEK_BOOKMARK_ALPHA),
            reduceMotion = reduceMotion,
            onPreview = { previewMs = it },
            onSeek = onSeek,
        )
    }
}

/**
 * The seek bar's accent: the artwork palette's accent (a tint of the cover's hue, chosen to read on
 * a dark surface) on the player's dark backdrop; the theme's primary when the palette is a light one.
 */
@Composable
private fun seekAccent(colors: LyricsColors): Color =
    if (colors.surface.luminance() < LIGHT_PALETTE_LUMINANCE) colors.accent else MaterialTheme.colorScheme.primary

private const val LIGHT_PALETTE_LUMINANCE = 0.4f
private const val SEEK_TRACK_ALPHA = 0.22f
private const val SEEK_BOOKMARK_ALPHA = 0.8f

/** Elapsed time (the drag preview while scrubbing). The one text that follows playback ticks. */
@Composable
private fun ElapsedTime(
    position: State<Long>,
    previewMs: Long?,
    totalDurationMs: Long,
) {
    Text(text = formatMs((previewMs ?: position.value).coerceIn(0L, totalDurationMs)), style = TextStyles.metadata)
}

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
