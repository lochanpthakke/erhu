@file:Suppress("detekt:All")

package com.erhu.erhu.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.erhu.erhu.core.common.perf.Interactions
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuDivider
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.GlassMenuSection
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.PressIconButton
import com.erhu.erhu.core.designsystem.component.ReportInteraction
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.component.selectionJiggle
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.RadioSeed

/** Actions offered for a single song wherever it is listed. */
internal class SongActions(
    val onToggleFavorite: (Song) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    /** Smart Radio from this song: a new queue of songs like it from the library. */
    val onStartRadio: (Song) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onFixTags: (Song) -> Unit,
    val onViewMetadata: (Song) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    section: LibrarySection,
    onOpenDrawer: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmartPlaylist: (mixKey: String) -> Unit,
    onOpenRadio: (albumId: Long) -> Unit,
    onSelectSection: (LibrarySection) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    chromeScroll: NestedScrollConnection? = null,
) {
    val songs = viewModel.songsPaged.collectAsLazyPagingItems()
    // The library is usable once its first tracks are on screen (reported to the system as "fully drawn").
    ReportDrawnWhen { songs.itemCount > 0 }
    val favoriteSongs = viewModel.favoriteSongsPaged.collectAsLazyPagingItems()
    val albums = viewModel.albumsPaged.collectAsLazyPagingItems()
    val artists = viewModel.artistsPaged.collectAsLazyPagingItems()
    val artistArtwork by viewModel.artistArtwork.collectAsState()
    val candidates by viewModel.metadataCandidates.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    ReportInteraction(Interactions.LIBRARY) { if (isScanning) "scanning" else null }
    val searchQuery by viewModel.librarySearchQuery.collectAsState()
    val searchResults by viewModel.librarySearchResults.collectAsState()
    val searchArtists by viewModel.librarySearchArtists.collectAsState()
    val searchAlbums by viewModel.librarySearchAlbums.collectAsState()
    val recentSearchItems by viewModel.recentSearchItems.collectAsState()
    val recentAlbums by viewModel.recentAlbums.collectAsState()
    val recentPlaylists by playlistsViewModel.recentPlaylists.collectAsState()
    val playlists by playlistsViewModel.playlists.collectAsState()
    // The Library tab is an overview; "More" opens one full shelf (null = the overview).
    var openShelf by rememberSaveable { mutableStateOf<LibraryShelf?>(null) }
    val sectionStates = rememberSaveableStateHolder()
    val shownShelf = openShelf.takeIf { section == LibrarySection.LIBRARY && !isSearchActive }
    var showNewPlaylist by remember { mutableStateOf(false) }
    // Only the playing song's id: position ticks must not recompose the library.
    val currentSongId by viewModel.currentSongId.collectAsState()

    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForViewMetadata by remember { mutableStateOf<Song?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(viewModel.userMessage, snackbarHostState, viewModel::consumeUserMessage)
    UserMessageEffect(playlistsViewModel.userMessage, snackbarHostState, playlistsViewModel::consumeUserMessage)

    val actions =
        SongActions(
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onPlayNext = { viewModel.playNext(it) },
            onAddToQueue = { viewModel.addToQueue(it) },
            onStartRadio = { viewModel.startRadio(RadioSeed.Song(it.id)) },
            onAddToPlaylist = { songForPlaylist = it },
            onFixTags = { song ->
                songForFixTags = song
                viewModel.searchMetadataForSong(song)
            },
            onViewMetadata = { song ->
                songForViewMetadata = song
            },
        )

    val closeSearch = {
        onSearchActiveChange(false)
        viewModel.setLibrarySearchQuery("")
    }
    // Search is a mode of this screen: system Back leaves it (and clears the query).
    BackHandler(enabled = isSearchActive, onBack = closeSearch)
    // Back from a full shelf returns to the Library overview.
    BackHandler(enabled = shownShelf != null) { openShelf = null }

    val isCustomScanEnabled by viewModel.isCustomScanLocationEnabled.collectAsState()
    val customScanPath by viewModel.customScanLocationPath.collectAsState()
    val emptyMessage =
        when {
            isCustomScanEnabled && customScanPath.isNotBlank() -> "No songs found in $customScanPath"
            else -> "No songs found on this device"
        }

    // Picking a section in the bottom navigation while searching leaves search.
    var lastSection by rememberSaveable { mutableStateOf(section) }
    LaunchedEffect(section) {
        if (section != lastSection) {
            lastSection = section
            openShelf = null
            if (isSearchActive) closeSearch()
        }
    }

    // The page title (section or shelf) merges into the header button as the visible list scrolls.
    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            if (isSearchActive) {
                SearchHeader(
                    query = searchQuery,
                    onQueryChange = viewModel::setLibrarySearchQuery,
                    onClose = closeSearch,
                )
            } else if (shownShelf != null) {
                OverlayHeader(
                    navigation = TopBarNavigation.Detail(onBack = { openShelf = null }),
                    title = shownShelf.label,
                    collapsingTitle = collapsingTitle,
                )
            } else {
                OverlayHeader(
                    navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                    title = section.pageTitle,
                    collapsingTitle = collapsingTitle,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        // Both headers float over the content (no opaque bar), so content scrolls under them.
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        if (isSearchActive) {
            SearchContent(
                query = searchQuery,
                songs = searchResults,
                artists = searchArtists,
                albums = searchAlbums,
                artistArtwork = artistArtwork,
                currentSongId = currentSongId,
                recentSearchItems = recentSearchItems,
                onRemoveRecentItem = viewModel::removeRecentSearchItem,
                onClearRecentItems = viewModel::clearRecentSearchItems,
                onSongClick = { index ->
                    searchResults.getOrNull(index)?.let { viewModel.addRecentSong(it) }
                    viewModel.playSongsFromQueue(searchResults, index)
                },
                onArtistClick = { artist ->
                    viewModel.addRecentArtist(artist)
                    onOpenArtist(artist)
                },
                onAlbumClick = { album ->
                    viewModel.addRecentAlbum(album)
                    onOpenAlbum(album)
                },
                onPlaylistClick = onOpenPlaylist,
                actions = actions,
                topInset = scroll.contentTop,
                bottomInset = scroll.contentBottom,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scroll.container),
            )
        } else {
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = { viewModel.triggerScan() },
                state = refreshState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scroll.container)
                        .then(if (chromeScroll != null) Modifier.nestedScroll(chromeScroll) else Modifier),
                // The list starts below the overlay header, so the indicator does too.
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = refreshState,
                        isRefreshing = isScanning,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = scroll.contentTop),
                    )
                },
            ) {
                // Sections are switched by LibraryNavigationBar in the shell's bottom chrome.
                CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) {
                    LibraryContentMotion(section, shownShelf, sectionStates) { activeSection, activeShelf ->
                        when (activeSection) {
                            LibrarySection.TRACKS -> {
                                // Collected only on the Tracks section, which shows the smart mix cards.
                                val smartMixes by viewModel.smartMixes.collectAsState()
                                val featuredAlbums by hiltViewModel<FeaturedAlbumsViewModel>().albums.collectAsState()
                                HomeFeed(
                                    songs = songs,
                                    smartMixes = smartMixes,
                                    featuredAlbums = featuredAlbums,
                                    artistArtwork = artistArtwork,
                                    recentAlbums = recentAlbums,
                                    recentPlaylists = recentPlaylists,
                                    currentSongId = currentSongId,
                                    isScanning = isScanning,
                                    onSongClick = { viewModel.playFromLibrary(it, SongSource.ALL_SONGS) },
                                    onAlbumClick = onOpenAlbum,
                                    onOpenRadio = onOpenRadio,
                                    onPlaylistClick = { onOpenPlaylist(it.id) },
                                    onSmartPlaylistClick = onOpenSmartPlaylist,
                                    onSmartPlaylistPlay = viewModel::playSmartMix,
                                    actions = actions,
                                    topInset = scroll.contentTop,
                                    bottomInset = scroll.contentBottom,
                                    emptyMessage = emptyMessage,
                                )
                            }
                            LibrarySection.ALBUMS ->
                                AlbumsTab(
                                    albums = albums,
                                    topInset = scroll.contentTop,
                                    bottomInset = scroll.contentBottom,
                                    onAlbumClick = onOpenAlbum,
                                )
                            LibrarySection.LIBRARY ->
                                if (activeShelf == null) {
                                    LibraryOverview(
                                        playlists = playlists,
                                        favoriteSongs = favoriteSongs,
                                        songs = songs,
                                        artists = artists,
                                        artistArtwork = artistArtwork,
                                        isScanning = isScanning,
                                        actions =
                                            LibraryOverviewActions(
                                                onMore = { openShelf = it },
                                                onMoreSongs = { onSelectSection(LibrarySection.TRACKS) },
                                                onPlaylistClick = { onOpenPlaylist(it.id) },
                                                onNewPlaylist = { showNewPlaylist = true },
                                                onFavoriteClick = { viewModel.playFromLibrary(it, SongSource.FAVORITES) },
                                                onSongClick = { viewModel.playFromLibrary(it, SongSource.ALL_SONGS) },
                                                onArtistClick = onOpenArtist,
                                            ),
                                        topInset = scroll.contentTop,
                                        bottomInset = scroll.contentBottom,
                                    )
                                } else {
                                    LibraryHub(
                                        shelf = activeShelf,
                                        playlists = playlists,
                                        favoriteSongs = favoriteSongs,
                                        artists = artists,
                                        artistArtwork = artistArtwork,
                                        currentSongId = currentSongId,
                                        onFavoriteClick = { viewModel.playFromLibrary(it, SongSource.FAVORITES) },
                                        onPlaylistClick = { onOpenPlaylist(it.id) },
                                        onNewPlaylist = { showNewPlaylist = true },
                                        onArtistClick = onOpenArtist,
                                        actions = actions,
                                        topInset = scroll.contentTop,
                                        bottomInset = scroll.contentBottom,
                                    )
                                }
                            LibrarySection.VYBE ->
                                com.erhu.erhu.feature.library.vybe.VybeScreen(
                                    topInset = scroll.contentTop,
                                    bottomInset = scroll.contentBottom,
                                    currentSongId = currentSongId,
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
            onSaveTags = { title, artist, album, year, genre, bpm ->
                viewModel.writeTags(song, title, artist, album, year, genre, bpm)
                songForFixTags = null
            },
        )
    }

    songForViewMetadata?.let { song ->
        ViewMetadataScreen(
            song = song,
            viewModel = hiltViewModel(),
            libraryViewModel = viewModel,
            onDismiss = { songForViewMetadata = null },
        )
    }

    if (showNewPlaylist) {
        PlaylistNameDialog(
            title = "New playlist",
            onDismiss = { showNewPlaylist = false },
            onConfirm = { name ->
                playlistsViewModel.createPlaylist(name)
                showNewPlaylist = false
            },
        )
    }

    songForPlaylist?.let { song ->
        AddToPlaylistDialog(
            song = song,
            viewModel = playlistsViewModel,
            onDismiss = { songForPlaylist = null },
        )
    }
}

@Composable
internal fun SongsTab(
    songs: LazyPagingItems<Song>,
    currentSongId: Long?,
    isScanning: Boolean,
    onSongClick: (Song) -> Unit,
    actions: SongActions,
    topInset: Dp,
    bottomInset: Dp,
    emptyIcon: ImageVector,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No songs found. Pull down to rescan your device.",
) {
    if (songs.itemCount == 0) {
        EmptyState(
            message = if (isScanning) "Scanning your music…" else emptyMessage,
            icon = emptyIcon,
            modifier = Modifier.padding(top = topInset, bottom = bottomInset),
        )
    } else {
        val listState = rememberLazyListState()
        // The page title merges into the header button as this scrolls.
        ReportTitleScroll(listState)
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = listContentPadding(topInset, bottomInset),
        ) {
            items(
                count = songs.itemCount,
                key = songs.itemKey { it.id },
            ) { index ->
                val song = songs[index]
                if (song != null) {
                    LibrarySongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onSongClick(song) },
                        actions = actions,
                    )
                }
            }
        }
    }
}

/** A library track row with the favorite toggle and the song menu. Shared with the album and playlist pages. */
@Composable
internal fun LibrarySongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: SongActions,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = false,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TrackRow(
        song = song,
        isCurrent = isCurrent,
        onClick = onClick,
        modifier = modifier,
        showTrackNumber = showTrackNumber,
    ) {
        // Smaller glyphs inside full-size touch targets: the title stays the strongest element.
        PressIconButton(onClick = { actions.onToggleFavorite(song) }) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.small).selectionJiggle(song.isFavorite, identity = song.id, expressive = true),
            )
        }
        GlassMenuAnchor {
            PressIconButton(modifier = Modifier.glassMenuTrigger(), onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.small),
                )
            }
            GlassMenu(label = "Song options", expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                GlassMenuSection("Playback")
                GlassMenuItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    text = { Text("Play next") },
                    onClick = {
                        menuExpanded = false
                        actions.onPlayNext(song)
                    },
                )
                GlassMenuItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    text = { Text("Add to queue") },
                    onClick = {
                        menuExpanded = false
                        actions.onAddToQueue(song)
                    },
                )
                GlassMenuItem(
                    icon = Icons.Default.Radio,
                    text = { Text("Start radio") },
                    onClick = {
                        menuExpanded = false
                        actions.onStartRadio(song)
                    },
                )
                GlassMenuDivider()
                GlassMenuSection("Library")
                GlassMenuItem(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    text = { Text("Add to playlist") },
                    onClick = {
                        menuExpanded = false
                        actions.onAddToPlaylist(song)
                    },
                )
                GlassMenuItem(
                    icon = Icons.Default.AutoFixHigh,
                    text = { Text("Fix tags / metadata") },
                    onClick = {
                        menuExpanded = false
                        actions.onFixTags(song)
                    },
                )
                GlassMenuItem(
                    icon = Icons.Default.Info,
                    text = { Text("View metadata") },
                    onClick = {
                        menuExpanded = false
                        actions.onViewMetadata(song)
                    },
                )
                if (onRemoveFromPlaylist != null) {
                    GlassMenuItem(
                        icon = Icons.Default.RemoveCircleOutline,
                        destructive = true,
                        text = { Text("Remove from playlist") },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromPlaylist()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: LazyPagingItems<Album>,
    topInset: Dp,
    bottomInset: Dp,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.itemCount == 0) {
        EmptyState(
            message = "No albums found. Pull down to rescan your device.",
            icon = LibrarySection.ALBUMS.icon,
            modifier = Modifier.padding(top = topInset, bottom = bottomInset),
        )
    } else {
        // Column count follows the available width (2 on a typical phone).
        val gridState = rememberLazyGridState()
        // The page title merges into the header button as this scrolls.
        ReportTitleScroll(gridState)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = ArtworkSize.gridMin),
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = Spacing.sm + topInset,
                    bottom = Spacing.lg + bottomInset,
                ),
        ) {
            items(
                count = albums.itemCount,
                key = albums.itemKey { it.id },
            ) { index ->
                val album = albums[index]
                if (album != null) {
                    AlbumTile(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

@Composable
internal fun ArtistsTab(
    artists: LazyPagingItems<Artist>,
    artwork: Map<Long, String>,
    topInset: Dp,
    bottomInset: Dp,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.itemCount == 0) {
        EmptyState(
            message = "No artists found. Pull down to rescan your device.",
            icon = LibraryShelf.ARTISTS.icon,
            modifier = Modifier.padding(top = topInset, bottom = bottomInset),
        )
    } else {
        // Same artwork-first grid as Albums.
        val gridState = rememberLazyGridState()
        // The page title merges into the header button as this scrolls.
        ReportTitleScroll(gridState)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = ArtworkSize.gridMin),
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = Spacing.sm + topInset,
                    bottom = Spacing.lg + bottomInset,
                ),
        ) {
            items(
                count = artists.itemCount,
                key = artists.itemKey { it.id },
            ) { index ->
                val artist = artists[index]
                if (artist != null) {
                    ArtistTile(artist = artist, artworkUri = artwork[artist.id], onClick = { onArtistClick(artist) })
                }
            }
        }
    }
}

private fun listContentPadding(
    topInset: Dp,
    bottomInset: Dp,
) = PaddingValues(top = Spacing.sm + topInset, bottom = Spacing.sm + bottomInset)
