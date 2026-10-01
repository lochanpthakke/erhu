@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song

/** Actions offered for a single song wherever it is listed. */
private class SongActions(
    val onToggleFavorite: (Song) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onFixTags: (Song) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    section: LibrarySection,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = viewModel.songsPaged.collectAsLazyPagingItems()
    val favoriteSongs = viewModel.favoriteSongsPaged.collectAsLazyPagingItems()
    val albums = viewModel.albumsPaged.collectAsLazyPagingItems()
    val artists = viewModel.artistsPaged.collectAsLazyPagingItems()
    val candidates by viewModel.metadataCandidates.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val searchQuery by viewModel.librarySearchQuery.collectAsState()
    val searchResults by viewModel.librarySearchResults.collectAsState()
    // Only the playing song's id: position ticks must not recompose the library.
    val currentSongId by viewModel.currentSongId.collectAsState()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(viewModel.userMessage, snackbarHostState, viewModel::consumeUserMessage)
    UserMessageEffect(playlistsViewModel.userMessage, snackbarHostState, playlistsViewModel::consumeUserMessage)

    val actions =
        SongActions(
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onPlayNext = { viewModel.playNext(it) },
            onAddToQueue = { viewModel.addToQueue(it) },
            onAddToPlaylist = { songForPlaylist = it },
            onFixTags = { song ->
                songForFixTags = song
                viewModel.searchMetadataForSong(song)
            },
        )

    val closeSearch = {
        isSearchActive = false
        viewModel.setLibrarySearchQuery("")
    }
    // Search is a mode of this screen: system Back leaves it (and clears the query).
    BackHandler(enabled = isSearchActive, onBack = closeSearch)

    // Picking a section in the bottom navigation while searching leaves search.
    var lastSection by rememberSaveable { mutableStateOf(section) }
    LaunchedEffect(section) {
        if (section != lastSection) {
            lastSection = section
            if (isSearchActive) closeSearch()
        }
    }

    AppScaffold(
        topBar = {
            if (isSearchActive) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setLibrarySearchQuery,
                    onClose = closeSearch,
                )
            } else {
                AppTopBar(
                    navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                    title = "Library",
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search library",
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent()
        if (isSearchActive) {
            SearchResults(
                query = searchQuery,
                results = searchResults,
                currentSongId = currentSongId,
                onSongClick = { index -> viewModel.playSongsFromQueue(searchResults, index) },
                actions = actions,
                bottomInset = scroll.contentBottom,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scroll.container),
            )
        } else {
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = { viewModel.triggerScan() },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scroll.container),
            ) {
                // Sections are switched by LibraryNavigationBar in the shell's bottom chrome.
                Column(modifier = Modifier.fillMaxSize()) {
                    when (section) {
                        LibrarySection.TRACKS ->
                            SongsTab(
                                songs = songs,
                                currentSongId = currentSongId,
                                isScanning = isScanning,
                                bottomInset = scroll.contentBottom,
                                onSongClick = { viewModel.playFromLibrary(it, SongSource.ALL_SONGS) },
                                actions = actions,
                            )
                        LibrarySection.FAVORITES ->
                            SongsTab(
                                songs = favoriteSongs,
                                currentSongId = currentSongId,
                                isScanning = false,
                                bottomInset = scroll.contentBottom,
                                emptyMessage = "No favorites yet. Tap the heart on any song to add it here.",
                                onSongClick = { viewModel.playFromLibrary(it, SongSource.FAVORITES) },
                                actions = actions,
                            )
                        LibrarySection.ALBUMS ->
                            AlbumsTab(
                                albums = albums,
                                bottomInset = scroll.contentBottom,
                                onAlbumClick = { viewModel.playAlbum(it) },
                            )
                        LibrarySection.ARTISTS ->
                            ArtistsTab(
                                artists = artists,
                                bottomInset = scroll.contentBottom,
                                onArtistClick = { viewModel.playArtist(it) },
                            )
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
            onSaveTags = { title, artist, album, year, genre ->
                viewModel.writeTags(song, title, artist, album, year, genre)
                songForFixTags = null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AppTopBar(
        navigation = TopBarNavigation.Detail(onBack = onClose),
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Songs, artists or albums") },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
    )
}

@Composable
private fun SearchResults(
    query: String,
    results: List<Song>,
    currentSongId: Long?,
    onSongClick: (Int) -> Unit,
    actions: SongActions,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    when {
        query.isBlank() ->
            EmptyState(message = "Search your library by title, artist or album.", modifier = modifier.padding(bottom = bottomInset))
        results.isEmpty() -> EmptyState(message = "No songs match \"$query\".", modifier = modifier.padding(bottom = bottomInset))
        else ->
            LazyColumn(modifier = modifier, contentPadding = listContentPadding(bottomInset)) {
                itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                    LibrarySongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { onSongClick(index) },
                        actions = actions,
                    )
                }
            }
    }
}

@Composable
private fun SongsTab(
    songs: LazyPagingItems<Song>,
    currentSongId: Long?,
    isScanning: Boolean,
    onSongClick: (Song) -> Unit,
    actions: SongActions,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No songs found. Pull down to rescan your device.",
) {
    if (songs.itemCount == 0) {
        EmptyState(
            message = if (isScanning) "Scanning your music…" else emptyMessage,
            modifier = Modifier.padding(bottom = bottomInset),
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = listContentPadding(bottomInset),
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

@Composable
private fun LibrarySongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    actions: SongActions,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    SongRow(song = song, onClick = onClick, isHighlighted = isCurrent) {
        IconButton(onClick = { actions.onToggleFavorite(song) }) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Song options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        menuExpanded = false
                        actions.onPlayNext(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        menuExpanded = false
                        actions.onAddToQueue(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = {
                        menuExpanded = false
                        actions.onAddToPlaylist(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Fix tags / metadata") },
                    onClick = {
                        menuExpanded = false
                        actions.onFixTags(song)
                    },
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: LazyPagingItems<Album>,
    bottomInset: Dp,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.itemCount == 0) {
        EmptyState(
            message = "No albums found. Pull down to rescan your device.",
            modifier = Modifier.padding(bottom = bottomInset),
        )
    } else {
        // Cards carry an sm margin of their own, so sm here gives the standard gutter.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter - Spacing.sm,
                    end = Spacing.screenGutter - Spacing.sm,
                    top = Spacing.sm,
                    bottom = Spacing.sm + bottomInset,
                ),
        ) {
            items(
                count = albums.itemCount,
                key = albums.itemKey { it.id },
            ) { index ->
                val album = albums[index]
                if (album != null) {
                    AlbumItem(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: LazyPagingItems<Artist>,
    bottomInset: Dp,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.itemCount == 0) {
        EmptyState(
            message = "No artists found. Pull down to rescan your device.",
            modifier = Modifier.padding(bottom = bottomInset),
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = Spacing.sm,
                    bottom = Spacing.sm + bottomInset,
                ),
        ) {
            items(
                count = artists.itemCount,
                key = artists.itemKey { it.id },
            ) { index ->
                val artist = artists[index]
                if (artist != null) {
                    ArtistItem(artist = artist, onClick = { onArtistClick(artist) })
                }
            }
        }
    }
}

@Composable
private fun AlbumItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .padding(Spacing.sm)
                .fillMaxWidth()
                .glassPanel(MaterialTheme.shapes.medium, elevated = false)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${album.songCount} songs • tap to play",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .glassPanel(MaterialTheme.shapes.medium, elevated = false)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${artist.songCount} songs • ${artist.albumCount} albums • tap to play",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun listContentPadding(bottomInset: Dp) = PaddingValues(top = Spacing.sm, bottom = Spacing.sm + bottomInset)
