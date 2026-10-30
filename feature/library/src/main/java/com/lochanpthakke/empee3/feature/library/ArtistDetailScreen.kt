package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.database.library.ArtistArtworkCache
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtistArtwork
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.BUTTON_PRESSED_SCALE
import com.lochanpthakke.empee3.core.designsystem.component.CollapsingHeaderState
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenu
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuDivider
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuItem
import com.lochanpthakke.empee3.core.designsystem.component.GlassMenuSection
import com.lochanpthakke.empee3.core.designsystem.component.OverlayHeader
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.collapsingHeroBlock
import com.lochanpthakke.empee3.core.designsystem.component.collapsingHeroDetails
import com.lochanpthakke.empee3.core.designsystem.component.collapsingHeroTitle
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.component.pressScale
import com.lochanpthakke.empee3.core.designsystem.component.rememberCollapsingHeaderState
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.LocalSetFocusedArtworkUri
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.feature.library.mix.RadioSeed
import kotlinx.coroutines.flow.combine

private const val HERO_KEY = "artist_hero"
private const val HERO_PARALLAX = 0.4f
private const val HERO_FADE = 0.5f
private const val HERO_SCRIM_START = 0.35f
private const val HERO_SCRIM_STEPS = 12
private const val SMOOTHSTEP_A = 3f
private const val SMOOTHSTEP_B = 2f

private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f
private val COMPACT_ALBUM_CARD_WIDTH = 110.dp

private class ArtistDetailData(
    val artist: Artist?,
    val albums: List<Album>,
    val primarySongs: List<Song>,
    val featuredSongs: List<Song>,
    val unverifiedSongs: List<Song>,
)

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun ArtistDetailScreen(
    artistId: Long,
    viewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail by remember(artistId) {
        combine(
            viewModel.observeArtist(artistId),
            viewModel.observeArtistAlbums(artistId),
            viewModel.observeArtistPrimarySongs(artistId),
            viewModel.observeArtistFeaturedSongs(artistId),
            viewModel.observeArtistUnverifiedSongs(artistId),
            ::ArtistDetailData,
        )
    }.collectAsState(initial = null)

    val currentSongId by viewModel.currentSongId.collectAsState()
    val candidates by viewModel.metadataCandidates.collectAsState()

    var songsForPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForViewMetadata by remember { mutableStateOf<Song?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }
    val customArtworkKeys by viewModel.customArtworkArtistKeys.collectAsState()

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

    val listState = rememberLazyListState()
    val header = rememberCollapsingHeaderState(listState)

    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack),
                title = detail?.artist?.name,
                collapsingHeader = header,
                actions = {
                    val artist = detail?.artist
                    val allSongs = detail?.let { it.primarySongs.ifEmpty { it.featuredSongs } }.orEmpty()
                    if (artist != null) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Artist options")
                            }
                            GlassMenu(
                                label = "Artist options",
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                GlassMenuSection("Playback")
                                GlassMenuItem(
                                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    text = { Text("Play Next") },
                                    onClick = {
                                        menuExpanded = false
                                        allSongs.asReversed().forEach { song -> viewModel.playNext(song) }
                                    },
                                )
                                GlassMenuItem(
                                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                                    text = { Text("Add to Queue") },
                                    onClick = {
                                        menuExpanded = false
                                        allSongs.forEach { song -> viewModel.addToQueue(song) }
                                    },
                                )
                                GlassMenuItem(
                                    icon = Icons.Default.Radio,
                                    text = { Text("Start Radio") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.startRadio(RadioSeed.Artist(artist.id))
                                    },
                                )
                                GlassMenuDivider()
                                GlassMenuSection("Artist")
                                GlassMenuItem(
                                    icon = Icons.Default.AutoFixHigh,
                                    text = { Text("Auto Fix Tags") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.enqueueArtistForRepair(artist)
                                    },
                                )
                                GlassMenuItem(
                                    icon = Icons.Default.ImageSearch,
                                    text = { Text("Fetch Photo Online") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.enqueueArtistForArtwork(artist)
                                    },
                                )
                                GlassMenuItem(
                                    icon = Icons.Default.PhotoLibrary,
                                    text = { Text("Set Custom Photo (Local File)") },
                                    onClick = {
                                        menuExpanded = false
                                        showImageDialog = true
                                    },
                                )
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
        val artist = data?.artist
        when {
            data == null -> Unit
            artist == null ->
                EmptyState(
                    message = "This artist is no longer in your library.",
                    icon = LibraryShelf.ARTISTS.icon,
                    modifier = Modifier.padding(innerPadding),
                )
            else -> {
                val albums = data.albums
                val primarySongs = data.primarySongs
                val featuredSongs = data.featuredSongs
                val allArtistSongs = primarySongs.ifEmpty { featuredSongs }
                val scroll = innerPadding.forScrollingContent(topOverlay = true)

                val setFocusedArtwork = LocalSetFocusedArtworkUri.current
                val validArtistPhoto = artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork)
                val artistArtUri = validArtistPhoto ?: primarySongs.firstOrNull()?.artworkUri ?: primarySongs.firstOrNull()?.uri
                LaunchedEffect(artistArtUri) {
                    setFocusedArtwork(artistArtUri)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        setFocusedArtwork(null)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scroll.container),
                    contentPadding = PaddingValues(bottom = Spacing.lg + scroll.contentBottom),
                ) {
                    item(key = HERO_KEY) {
                        ArtistHero(
                            artist = artist,
                            songs = allArtistSongs,
                            listState = listState,
                            header = header,
                            onPlay = { viewModel.playInOrder(allArtistSongs) },
                            onShuffle = { viewModel.playShuffled(allArtistSongs) },
                            onArtworkClick = { showImageDialog = true },
                        )
                    }

                    val lps = albums.filter { it.songCount >= 7 }
                    val eps = albums.filter { it.songCount in 3..6 }
                    val singles = albums.filter { it.songCount in 1..2 }

                    if (lps.isNotEmpty()) {
                        item(key = "lps_section") {
                            AlbumSectionRow(
                                title = "Albums",
                                albums = lps,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }

                    if (eps.isNotEmpty()) {
                        item(key = "eps_section") {
                            AlbumSectionRow(
                                title = "EPs",
                                albums = eps,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }

                    if (singles.isNotEmpty()) {
                        item(key = "singles_section") {
                            AlbumSectionRow(
                                title = "Singles",
                                albums = singles,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }

                    if (featuredSongs.isNotEmpty()) {
                        item(key = "features_header") {
                            Text(
                                text = "Features",
                                style = TextStyles.sectionTitle,
                                modifier =
                                    Modifier.padding(
                                        start = Spacing.screenGutter,
                                        end = Spacing.screenGutter,
                                        top = Spacing.md,
                                        bottom = Spacing.xs,
                                    ),
                            )
                        }
                        itemsIndexed(featuredSongs, key = { _, song -> "feat_${song.id}" }) { index, song ->
                            LibrarySongRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                onClick = { viewModel.playSongsFromQueue(featuredSongs, index) },
                                actions = actions,
                            )
                        }
                    }

                    if (data.unverifiedSongs.isNotEmpty()) {
                        item(key = "unverified_header") {
                            Text(
                                text = "Credited",
                                style = TextStyles.sectionTitle,
                                modifier =
                                    Modifier.padding(
                                        start = Spacing.screenGutter,
                                        end = Spacing.screenGutter,
                                        top = Spacing.md,
                                        bottom = Spacing.xs,
                                    ),
                            )
                        }
                        itemsIndexed(data.unverifiedSongs, key = { _, song -> "unverified_${song.id}" }) { index, song ->
                            LibrarySongRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                onClick = { viewModel.playSongsFromQueue(data.unverifiedSongs, index) },
                                actions = actions,
                            )
                        }
                    }

                    if (primarySongs.isNotEmpty()) {
                        item(key = "tracks_header") {
                            Text(
                                text = "Tracks",
                                style = TextStyles.sectionTitle,
                                modifier =
                                    Modifier.padding(
                                        start = Spacing.screenGutter,
                                        end = Spacing.screenGutter,
                                        top = Spacing.md,
                                        bottom = Spacing.xs,
                                    ),
                            )
                        }
                        itemsIndexed(primarySongs, key = { _, song -> "primary_${song.id}" }) { index, song ->
                            LibrarySongRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                onClick = { viewModel.playSongsFromQueue(primarySongs, index) },
                                actions = actions,
                                showTrackNumber = true,
                            )
                        }
                    }

                    if (primarySongs.isEmpty() && featuredSongs.isEmpty()) {
                        item(key = "empty_tracks") {
                            EmptyState(
                                message = "No tracks found for this artist.",
                                modifier = Modifier.padding(top = Spacing.xl),
                            )
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
            onSaveTags = { title, artistName, albumName, year, genre, bpm ->
                viewModel.writeTags(song, title, artistName, albumName, year, genre, bpm)
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

    songsForPlaylist?.let { songs ->
        AddToPlaylistDialog(
            songs = songs,
            viewModel = playlistsViewModel,
            onDismiss = { songsForPlaylist = null },
        )
    }

    val shownArtist = detail?.artist
    if (showImageDialog && shownArtist != null) {
        ArtistImageDialog(
            artist = shownArtist,
            hasCustomImage = shownArtist.artistKey in customArtworkKeys,
            onDismiss = { showImageDialog = false },
            onSave = { change ->
                when (change) {
                    is CoverChange.Replace -> viewModel.setCustomArtistArtwork(shownArtist, change.jpegBytes)
                    CoverChange.Remove -> viewModel.clearCustomArtistArtwork(shownArtist)
                    CoverChange.None -> Unit
                }
                showImageDialog = false
            },
        )
    }
}

@Composable
private fun ArtistHero(
    artist: Artist,
    songs: List<Song>,
    listState: LazyListState,
    header: CollapsingHeaderState,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onArtworkClick: () -> Unit,
) {
    val page = EmPee3Glass.backdropColor(MaterialTheme.colorScheme.background).copy(alpha = 1f)
    val scrim =
        remember(page) {
            List(HERO_SCRIM_STEPS + 1) { i ->
                val t = i / HERO_SCRIM_STEPS.toFloat()
                page.copy(alpha = t * t * (SMOOTHSTEP_A - SMOOTHSTEP_B * t))
            }
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds()
                .clickable(onClickLabel = "Change artist image", onClick = onArtworkClick),
    ) {
        ArtistArtwork(
            artworkUri = artist.artworkUri?.takeIf(ArtistArtworkCache::hasArtwork),
            artistName = artist.name,
            clipToCircle = false,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scrolled = heroScroll(listState, size.height)
                        translationY = scrolled * HERO_PARALLAX
                        alpha = 1f - HERO_FADE * (scrolled / size.height).coerceIn(0f, 1f)
                    },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Brush.verticalGradient(scrim, startY = size.height * HERO_SCRIM_START, endY = size.height))
                    },
        )
        ArtistTitleBlock(
            artist = artist,
            header = header,
            songs = songs,
            onPlay = onPlay,
            onShuffle = onShuffle,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .collapsingHeroBlock(header),
        )
    }
}

@Composable
private fun ArtistTitleBlock(
    artist: Artist,
    header: CollapsingHeaderState,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
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
            text = artist.name,
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
            text = countLabel(artist.songCount, "song") + " · " + countLabel(artist.albumCount, "album"),
            style = TextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.collapsingHeroDetails(header),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        ArtistActions(
            enabled = songs.isNotEmpty(),
            onPlay = onPlay,
            onShuffle = onShuffle,
        )
    }
}

private fun heroScroll(
    listState: LazyListState,
    maxScroll: Float,
): Float {
    val hero = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == HERO_KEY } ?: return maxScroll
    return (-hero.offset).coerceIn(0, maxScroll.toInt()).toFloat()
}

@Composable
private fun ArtistActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
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
            modifier = Modifier.weight(1f).pressScale(playPress, BUTTON_PRESSED_SCALE, enabled),
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Play")
        }
        FilledTonalButton(
            onClick = onShuffle,
            enabled = enabled,
            interactionSource = shufflePress,
            modifier = Modifier.weight(1f).pressScale(shufflePress, BUTTON_PRESSED_SCALE, enabled),
        ) {
            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Shuffle")
        }
    }
}

@Composable
private fun AlbumSectionRow(
    title: String,
    albums: List<Album>,
    onOpenAlbum: (Album) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
    ) {
        Text(
            text = title,
            style = TextStyles.sectionTitle,
            modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(albums, key = { it.id }) { album ->
                CompactAlbumTile(
                    album = album,
                    onClick = { onOpenAlbum(album) },
                )
            }
        }
    }
}

@Composable
private fun CompactAlbumTile(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(COMPACT_ALBUM_CARD_WIDTH)
                .clickable(onClick = onClick),
    ) {
        AlbumArtwork(
            artworkUri = album.artworkUri,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape),
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = album.title,
            style = TextStyles.tileTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (album.year in 1900..2099) {
            Text(
                text = album.year.toString(),
                style = TextStyles.tileSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
