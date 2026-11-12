package com.erhu.erhu.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.core.database.repository.AnimatedArtworkRepository
import com.erhu.erhu.core.designsystem.artwork.AlbumArtwork
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.BUTTON_PRESSED_SCALE
import com.erhu.erhu.core.designsystem.component.CollapsingHeaderState
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuDivider
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.GlassMenuSection
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.collapsingHeroBlock
import com.erhu.erhu.core.designsystem.component.collapsingHeroDetails
import com.erhu.erhu.core.designsystem.component.collapsingHeroTitle
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.component.pressScale
import com.erhu.erhu.core.designsystem.component.rememberCollapsingHeaderState
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.LocalSetFocusedArtworkUri
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.model.TransitionPairKey
import com.erhu.erhu.feature.library.components.AnimatedAlbumCoverPlayer
import com.erhu.erhu.feature.library.components.LiveCoverControl
import com.erhu.erhu.feature.library.components.rememberAnimatedArtworkPath
import com.erhu.erhu.feature.library.mix.RadioSeed
import com.erhu.erhu.feature.library.transition.CollectionTransitionConnector
import com.erhu.erhu.feature.library.transition.StudioInput
import com.erhu.erhu.feature.library.transition.StudioTrack
import com.erhu.erhu.feature.library.transition.TransitionStudioHost
import com.erhu.erhu.feature.library.transition.TransitionStudioRow
import com.erhu.erhu.feature.library.transition.TransitionStudioViewModel
import com.erhu.erhu.feature.library.transition.rememberCollectionTransitionPairs
import kotlinx.coroutines.flow.combine
import com.erhu.erhu.core.common.R as CommonR

/** The hero artwork moves at this share of the scroll speed (parallax) as the page scrolls. */
private const val HERO_PARALLAX = 0.5f

/** How much the hero artwork fades by the time it has scrolled away. */
private const val HERO_FADE = 0.6f

/** Keep the full image clear until just above the measured title and metadata. */
private val HERO_FADE_LEAD = 48.dp
private const val HERO_FADE_MIDPOINT = 0.3f
private const val HERO_FADE_MID_ALPHA = 0.18f
private const val HERO_FADE_LOWER_POINT = 0.75f
private const val HERO_FADE_LOWER_ALPHA = 0.75f

/** Width of the centered Play / Shuffle pair as a share of the screen (as on the artist page). */
private const val ACTION_PAIR_WIDTH_FRACTION = 0.92f

private const val MS_PER_MINUTE = 60_000L

/** What the album page shows: [album] is null once the album has left the library. */
private class AlbumDetailData(
    val album: Album?,
    val songs: List<Song>,
    /** False while only the tapped album is known (its tracks are still being read). */
    val loaded: Boolean = true,
)

/**
 * An album's page, artwork first: the cover fills the top edge to edge with the
 * title, artist and facts over its lower part, then Shuffle / Play, then the
 * album's tracks in album order. As the page scrolls the cover drifts and fades
 * and the title moves into the floating header. Data comes
 * from the library repository through [viewModel] and updates live, so an album
 * removed by a rescan turns into an empty state instead of failing.
 */
@Composable
@Suppress("LongMethod")
fun AlbumDetailScreen(
    albumId: Long,
    viewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    transitionsViewModel: TransitionStudioViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArtworkFetcher: () -> Unit = {},
) {
    val detail = rememberAlbumDetail(albumId, viewModel)
    val albumRef = remember(albumId) { CollectionRef.album(albumId) }
    var showStudio by remember { mutableStateOf(false) }
    var studioPair by remember { mutableStateOf<TransitionPairKey?>(null) }
    // Only the playing song's id: position ticks must not recompose this page.
    val openArtworkSearch = rememberArtworkWebSearchLauncher()
    val currentSongId by viewModel.currentSongId.collectAsState()
    val candidates by viewModel.metadataCandidates.collectAsState()

    var songsForPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForViewMetadata by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(viewModel.userMessage, snackbarHostState, viewModel::consumeUserMessage)
    UserMessageEffect(playlistsViewModel.userMessage, snackbarHostState, playlistsViewModel::consumeUserMessage)

    val actions =
        SongActions(
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onPlayNext = { viewModel.playNext(it) },
            onAddToQueue = { viewModel.addToQueue(it) },
            onStartRadio = { viewModel.startRadio(RadioSeed.Song(it.id)) },
            onAddToPlaylist = { songsForPlaylist = listOf(it) },
            onFixTags = { song ->
                songForFixTags = song
                viewModel.searchMetadataForSong(song)
            },
            onViewMetadata = { song ->
                songForViewMetadata = song
            },
        )

    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null && detail?.album != null) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        viewModel.applyArtworkBytesToAlbum(detail.album, bytes)
                    }
                } catch (_: Exception) {
                }
            }
        }

    val listState = rememberLazyListState()
    val header = rememberCollapsingHeaderState(listState)
    AppScaffold(
        // Floating glass back button over the cover; as the album title rises to it, the title
        // hands over to the header's own (see CollapsingHeaderState) and the header turns solid.
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack),
                title = detail?.album?.title,
                collapsingHeader = header,
                actions = {
                    val album = detail?.album
                    val songs = detail?.songs.orEmpty()
                    if (album != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (viewModel.animatedArtworkRepository != null) {
                                AlbumLiveCover(album)
                            }
                            GlassMenuAnchor {
                                IconButton(
                                    modifier = Modifier.glassMenuTrigger(),
                                    onClick = { menuExpanded = true },
                                    colors =
                                        IconButtonDefaults.iconButtonColors(
                                            containerColor = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(CommonR.string.album_cd_options),
                                    )
                                }
                                GlassMenu(
                                    albumMotionPilot = true,
                                    label = stringResource(CommonR.string.album_cd_options),
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    GlassMenuSection(stringResource(CommonR.string.menu_section_playback))
                                    GlassMenuItem(
                                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        text = { Text(stringResource(CommonR.string.menu_play_next)) },
                                        onClick = {
                                            menuExpanded = false
                                            songs.asReversed().forEach { song -> viewModel.playNext(song) }
                                        },
                                    )
                                    GlassMenuItem(
                                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                                        text = { Text(stringResource(CommonR.string.menu_add_to_queue)) },
                                        onClick = {
                                            menuExpanded = false
                                            songs.forEach { song -> viewModel.addToQueue(song) }
                                        },
                                    )
                                    if (LocalVybeEnabled.current) {
                                        GlassMenuItem(
                                            icon = Icons.Default.Radio,
                                            text = { Text(stringResource(CommonR.string.menu_start_radio)) },
                                            onClick = {
                                                menuExpanded = false
                                                viewModel.startRadio(RadioSeed.Album(album.id))
                                            },
                                        )
                                    }
                                    GlassMenuDivider()
                                    GlassMenuSection(stringResource(CommonR.string.menu_section_album))
                                    GlassMenuItem(
                                        icon = Icons.Default.AutoFixHigh,
                                        text = { Text(stringResource(CommonR.string.menu_auto_fix_tags)) },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.enqueueAlbumForRepair(album)
                                        },
                                    )
                                    GlassMenuItem(
                                        icon = Icons.Default.ImageSearch,
                                        text = { Text(stringResource(CommonR.string.menu_fetch_cover_online)) },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.enqueueAlbumForArtwork(album)
                                            onNavigateToArtworkFetcher()
                                        },
                                    )
                                    albumArtworkSearchUrl(album.title, album.artist)?.let { searchUrl ->
                                        GlassMenuItem(
                                            icon = Icons.Default.ImageSearch,
                                            text = { Text(stringResource(CommonR.string.menu_search_images_browser)) },
                                            onClick = {
                                                menuExpanded = false
                                                openArtworkSearch(searchUrl)
                                            },
                                        )
                                    }
                                    GlassMenuItem(
                                        icon = Icons.Default.PhotoLibrary,
                                        text = { Text(stringResource(CommonR.string.menu_set_custom_cover_local)) },
                                        onClick = {
                                            menuExpanded = false
                                            imagePickerLauncher.launch("image/*")
                                        },
                                    )
                                    GlassMenuItem(
                                        icon = Icons.Default.Tune,
                                        text = { Text(stringResource(CommonR.string.menu_transition_studio)) },
                                        onClick = {
                                            menuExpanded = false
                                            showStudio = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        val data = detail
        val album = data?.album
        when {
            // Loading: Room answers within a frame or two; show nothing rather than a flash.
            data == null -> Unit
            album == null ->
                EmptyState(
                    message = stringResource(CommonR.string.album_missing),
                    icon = LibrarySection.ALBUMS.icon,
                    modifier = Modifier.padding(innerPadding),
                )
            else -> {
                val songs =
                    remember(data.songs) {
                        data.songs.sortedWith(
                            compareBy<Song> { it.discNumber }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                        )
                    }

                val studioTracks = remember(songs) { songs.map { StudioTrack(it, TrackOccurrence(it.id)) } }
                val transitionPairs = rememberCollectionTransitionPairs(albumRef, studioTracks, transitionsViewModel)

                val setFocusedArtwork = LocalSetFocusedArtworkUri.current
                val albumArtUri = album.artworkUri ?: songs.firstOrNull()?.artworkUri ?: songs.firstOrNull()?.uri
                LaunchedEffect(albumArtUri) {
                    // At once (the tapped album's cover is known): an artwork-based theme re-colours
                    // the app as the page arrives, not as a separate step after it.
                    setFocusedArtwork(albumArtUri)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        setFocusedArtwork(null)
                    }
                }
                val scroll = innerPadding.forScrollingContent(topOverlay = true)
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scroll.container),
                    // The cover starts at the very top, under the status bar and the floating header.
                    contentPadding = PaddingValues(bottom = Spacing.lg + scroll.contentBottom),
                ) {
                    item(key = HERO_KEY) {
                        AlbumHero(
                            album = album,
                            songs = songs.takeIf { data.loaded },
                            listState = listState,
                            header = header,
                            animatedArtworkRepository = viewModel.animatedArtworkRepository,
                        )
                    }
                    item(key = "actions") {
                        AlbumActions(
                            // Enabled while the tracks load too (no flicker); an empty album disables them.
                            enabled = !data.loaded || songs.isNotEmpty(),
                            onPlay = { viewModel.playInOrder(songs, albumRef) },
                            onShuffle = { viewModel.playShuffled(songs, albumRef) },
                        )
                    }
                    item(key = "transitions") {
                        TransitionStudioRow(albumRef, transitionsViewModel, onOpen = {
                            studioPair = null
                            showStudio = true
                        })
                    }
                    if (songs.isEmpty() && data.loaded) {
                        item(key = "empty") {
                            EmptyState(message = stringResource(CommonR.string.album_empty), modifier = Modifier.padding(top = Spacing.xl))
                        }
                    } else {
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            Column {
                                LibrarySongRow(
                                    song = song,
                                    isCurrent = song.id == currentSongId,
                                    onClick = { viewModel.playSongsFromQueue(songs, index, albumRef) },
                                    actions = actions,
                                    showTrackNumber = true,
                                )
                                transitionPairs.getOrNull(index)?.let { pair ->
                                    CollectionTransitionConnector(pair) {
                                        studioPair = pair.key
                                        showStudio = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    songForFixTags?.let { song ->
        FixTagsDialog(
            song = song,
            candidates = candidates,
            onDismiss = { songForFixTags = null },
            onSaveTags = { title, artist, albumName, year, genre, bpm ->
                viewModel.writeTags(song, title, artist, albumName, year, genre, bpm)
                songForFixTags = null
            },
        )
    }

    songForViewMetadata?.let { song ->
        ViewMetadataScreen(
            song = song,
            viewModel =
                androidx.hilt.navigation.compose
                    .hiltViewModel(),
            libraryViewModel = viewModel,
            onDismiss = { songForViewMetadata = null },
        )
    }

    if (showStudio) AlbumStudio(albumRef, detail, transitionsViewModel, onClose = { showStudio = false }, initialPair = studioPair)

    songsForPlaylist?.let { songs ->
        AddToPlaylistDialog(
            songs = songs,
            viewModel = playlistsViewModel,
            onDismiss = { songsForPlaylist = null },
        )
    }
}

/**
 * The album page's data. The album that was tapped is already known: the page enters
 * complete (cover, title, artist, buttons) on its first frame, and Room then keeps it
 * current and adds the tracks. Null only when nothing is known yet.
 */
@Composable
private fun rememberAlbumDetail(
    albumId: Long,
    viewModel: LibraryViewModel,
): AlbumDetailData? {
    val opening =
        remember(albumId) {
            viewModel.openingAlbum(albumId)?.let { (album, songs) ->
                AlbumDetailData(album, songs.orEmpty(), loaded = songs != null)
            }
        }
    val loaded by remember(albumId) {
        combine(viewModel.observeAlbum(albumId), viewModel.observeAlbumSongs(albumId)) { album, songs -> AlbumDetailData(album, songs) }
    }.collectAsState(initial = null)
    return loaded ?: opening
}

/**
 * The cover, edge to edge, fading into the page at the bottom where the title,
 * artist and facts sit. While the page scrolls the cover moves at half speed and
 * fades, and the title fades out (read in the draw phase: no recomposition).
 */
@Composable
internal fun AlbumHero(
    album: Album,
    songs: List<Song>?,
    listState: LazyListState,
    header: CollapsingHeaderState,
    /** The album's animated cover, looping muted over the still one, when one has been fetched. */
    animatedArtworkRepository: AnimatedArtworkRepository? = null,
) {
    // The opaque colour the track list sits on (the theme background is translucent under glass).
    val page = ErhuGlass.backdropColor(MaterialTheme.colorScheme.background).copy(alpha = 1f)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds(),
    ) {
        val coverModifier =
            Modifier
                // Keep the cover with its scrim and title during navigation.
                // A separate shared overlay can outlive the page on Back.
                .fillMaxSize()
                .graphicsLayer {
                    val scrolled = heroScroll(listState, size.height)
                    translationY = scrolled * HERO_PARALLAX
                    alpha = 1f - HERO_FADE * (scrolled / size.height).coerceIn(0f, 1f)
                }
        val video = animatedArtworkRepository?.let { rememberAnimatedArtworkPath(album.id, it) }
        // Let go of the video while Now Playing covers the page: only one cover player at a time.
        val covered = LocalPlayerTransition.current?.isOpen == true
        AnimatedAlbumCoverPlayer(
            filePath = video.takeUnless { covered },
            isPlaying = true,
            modifier = coverModifier,
        ) {
            AlbumArtwork(
                artworkUri = album.artworkUri,
                contentDescription = stringResource(CommonR.string.cd_album_artwork),
                modifier = Modifier.fillMaxSize(),
            )
        }
        AlbumTitleBlock(
            album = album,
            songs = songs,
            header = header,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .collapsingHeroBlock(header)
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0f to page.copy(alpha = 0f),
                                HERO_FADE_MIDPOINT to page.copy(alpha = HERO_FADE_MID_ALPHA),
                                HERO_FADE_LOWER_POINT to page.copy(alpha = HERO_FADE_LOWER_ALPHA),
                                1f to page,
                                startY = -HERO_FADE_LEAD.toPx(),
                                endY = size.height,
                            ),
                            topLeft = Offset(0f, -HERO_FADE_LEAD.toPx()),
                            size = Size(size.width, size.height + HERO_FADE_LEAD.toPx()),
                        )
                    },
        )
    }
}

/**
 * The album's live cover download control (see [LiveCoverViewModel]), beside the floating header's end button.
 */
@Composable
private fun AlbumLiveCover(
    album: Album,
    modifier: Modifier = Modifier,
) {
    val liveCover: LiveCoverViewModel = hiltViewModel(key = "live-cover-${album.id}")
    LaunchedEffect(album.id) { liveCover.bind(album) }
    val state by liveCover.state.collectAsState()
    val compression by liveCover.compression.collectAsState()
    LiveCoverControl(
        state = state,
        artworkUri = album.artworkUri,
        onDownload = liveCover::download,
        onCancel = liveCover::cancel,
        onRemove = liveCover::remove,
        onSelectVariant = liveCover::selectVariant,
        compression = compression,
        onCompress = liveCover::compress,
        onCancelCompression = liveCover::cancelCompression,
        modifier = modifier,
    )
}

/** Title, artist and facts, centered over the lower part of the cover. */
@Composable
private fun AlbumTitleBlock(
    album: Album,
    songs: List<Song>?,
    header: CollapsingHeaderState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.heroGutter)
                .padding(bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = header::onHeroTitleLayout,
            modifier = Modifier.collapsingHeroTitle(header),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = album.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.collapsingHeroDetails(header),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        val factsText = songs?.let { albumFacts(album, it) }.orEmpty()
        val isLossless = songs?.any { it.isLossless } == true
        val isDolbyAtmos = songs?.any { it.isDolbyAtmos } == true

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.collapsingHeroDetails(header),
        ) {
            Text(
                text =
                    if (isLossless && factsText.isNotBlank()) {
                        stringResource(CommonR.string.album_facts_lossless, factsText)
                    } else {
                        factsText
                    },
                style = TextStyles.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isDolbyAtmos) {
                Spacer(modifier = Modifier.width(Spacing.xs))
                DolbyAtmosBadge()
            }
        }
    }
}

@Composable
private fun DolbyAtmosBadge() {
    Surface(
        shape = Radius.pillShape,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(CommonR.string.album_badge_dd),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(CommonR.string.album_badge_atmos),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** How far the hero has scrolled up, px ([whenGone] once it is off screen). */
private fun heroScroll(
    state: LazyListState,
    whenGone: Float,
): Float = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else whenGone

/** Play and Shuffle as an equal-width pair, centered (the same pair as the artist page). */
@Composable
private fun AlbumActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(ACTION_PAIR_WIDTH_FRACTION),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val playPress = remember { MutableInteractionSource() }
            val shufflePress = remember { MutableInteractionSource() }
            Button(
                onClick = onPlay,
                enabled = enabled,
                interactionSource = playPress,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                modifier = Modifier.weight(1f).pressScale(playPress, BUTTON_PRESSED_SCALE, enabled),
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(IconSize.small))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(CommonR.string.action_play),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onShuffle,
                enabled = enabled,
                interactionSource = shufflePress,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                modifier = Modifier.weight(1f).pressScale(shufflePress, BUTTON_PRESSED_SCALE, enabled),
            ) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(IconSize.small))
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(CommonR.string.action_shuffle),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "2018 · 12 songs · 43 min" from the album's facts and tracks; unknown parts are left out. */
@Composable
private fun albumFacts(
    album: Album,
    songs: List<Song>,
): String {
    val yearStr = album.year.takeIf { it in 1900..2099 }?.toString()
    val totalMinutes = songs.sumOf { it.durationMs } / MS_PER_MINUTE
    val duration = totalMinutes.takeIf { it > 0 }?.let { minutesDurationText(it) }
    return listOfNotNull(
        yearStr,
        songCountText(songs.size),
        duration,
    ).joinToString(" · ")
}

private const val HERO_KEY = "hero"

/** The Transition Studio for this album, in album order (disc, track, title); album tracks are unique, so the song id is the occurrence. */
@Composable
private fun AlbumStudio(
    albumRef: CollectionRef,
    detail: AlbumDetailData?,
    viewModel: TransitionStudioViewModel,
    onClose: () -> Unit,
    initialPair: TransitionPairKey? = null,
) {
    val album = detail?.album ?: return
    val tracks =
        remember(detail.songs) {
            detail.songs
                .sortedWith(
                    compareBy<Song> { it.discNumber }
                        .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                ).map { StudioTrack(it, TrackOccurrence(it.id)) }
        }
    TransitionStudioHost(StudioInput(albumRef, album.title, tracks), viewModel, onClose, initialPair)
}
