package com.erhu.erhu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.Album
import com.erhu.erhu.core.model.Artist
import com.erhu.erhu.core.model.Playlist
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.SmartMixes

/**
 * The Tracks destination as the home feed, top to bottom: the Smart Playlists as a hero banner
 * (whenever the library has songs), Featured Radio Stations and Featured Albums, Recently
 * Played albums and playlists (from real playback history), then the full track list. One lazy
 * list, so the shelves scroll away with the tracks; each shelf scrolls sideways on its own.
 */
@Composable
@Suppress("LongParameterList")
internal fun HomeFeed(
    songs: LazyPagingItems<Song>,
    smartMixes: SmartMixes?,
    featuredAlbums: List<Album>,
    artistArtwork: Map<Long, String>,
    recentAlbums: List<Album>,
    recentPlaylists: List<Playlist>,
    currentSongId: Long?,
    isScanning: Boolean,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onOpenRadio: (Long) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSmartPlaylistClick: (mixKey: String) -> Unit,
    onSmartPlaylistPlay: (mixKey: String) -> Unit,
    actions: SongActions,
    topInset: Dp,
    bottomInset: Dp,
    emptyMessage: String = "No songs found on this device",
) {
    val mixes = visibleHomeMixes(smartMixes?.mixes.orEmpty(), LocalVybeEnabled.current)
    val hasSongs = songs.itemCount > 0
    // Shown once the library has songs: the mixes, and a note while history is still short.
    val hasSmartPlaylists = hasSongs && (mixes.isNotEmpty() || smartMixes?.needsMoreListening == true)
    val hasShelves = hasSongs || recentAlbums.isNotEmpty() || recentPlaylists.isNotEmpty()
    LazyColumn(
        state = rememberLazyListState().also { ReportTitleScroll(it) }, // the page title merges into the header button
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.sm + topInset, bottom = Spacing.sm + bottomInset),
    ) {
        // One header item that always exists (empty = zero height). The list stays anchored
        // to it at the top, so shelves appearing after the first play push the tracks down
        // into view instead of being inserted above the visible position.
        item(key = "home_header", contentType = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (hasSmartPlaylists) {
                    SmartMixHero(
                        mixes = mixes,
                        needsMoreListening = smartMixes?.needsMoreListening == true,
                        onOpen = onSmartPlaylistClick,
                        onPlay = onSmartPlaylistPlay,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg),
                    )
                }
                AlbumShelves(
                    featuredAlbums,
                    artistArtwork,
                    recentAlbums,
                    recentPlaylists,
                    showRecentNote = hasSongs,
                    onAlbumClick,
                    onOpenRadio,
                    onPlaylistClick,
                )
                if (hasShelves) SectionHeading("Tracks")
            }
        }
        if (songs.itemCount == 0) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    message = if (isScanning) "Scanning your music…" else emptyMessage,
                    icon = LibrarySection.TRACKS.icon,
                )
            }
        } else {
            items(count = songs.itemCount, key = songs.itemKey { it.id }, contentType = { "song" }) { index ->
                songs[index]?.let { song ->
                    LibrarySongRow(song = song, isCurrent = song.id == currentSongId, onClick = { onSongClick(song) }, actions = actions)
                }
            }
        }
    }
}

/**
 * Featured radios and albums, then Recently Played albums (or, once the library has songs but nothing has
 * been played, a note saying they will appear) and playlists. A shelf with nothing in it is not shown.
 */
@Composable
@Suppress("LongParameterList")
private fun AlbumShelves(
    featuredAlbums: List<Album>,
    artistArtwork: Map<Long, String>,
    recentAlbums: List<Album>,
    recentPlaylists: List<Playlist>,
    showRecentNote: Boolean,
    onAlbumClick: (Album) -> Unit,
    onOpenRadio: (Long) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val setlists = remember(featuredAlbums, artistArtwork) { FeaturedSetlists.select(featuredAlbums, artistArtwork) }
    val radios = setlists.filterIsInstance<FeaturedSetlist.RadioPick>()
    val albums = setlists.filterIsInstance<FeaturedSetlist.AlbumPick>()
    if (LocalVybeEnabled.current && radios.isNotEmpty()) {
        Shelf(title = "Featured Radio Stations") {
            items(radios, key = { it.key }, contentType = { "radio" }) { radio ->
                RadioTile(
                    radio = radio,
                    onClick = { onOpenRadio(radio.album.id) },
                    modifier = Modifier.width(ArtworkSize.shelf),
                )
            }
        }
    }
    if (albums.isNotEmpty()) {
        Shelf(title = "Featured Albums") {
            items(albums, key = { it.key }, contentType = { "album" }) { pick ->
                AlbumTile(
                    album = pick.album,
                    onClick = { onAlbumClick(pick.album) },
                    modifier = Modifier.width(ArtworkSize.shelf),
                )
            }
        }
    }
    if (recentAlbums.isNotEmpty()) {
        Shelf(title = "Recently played albums") {
            items(recentAlbums, key = { it.id }) { album ->
                AlbumTile(album = album, onClick = { onAlbumClick(album) }, modifier = Modifier.width(ArtworkSize.shelf))
            }
        }
    } else if (showRecentNote) {
        ShelfNote(title = "Recently played albums", note = "Albums you play show up here.")
    }
    if (recentPlaylists.isNotEmpty()) {
        Shelf(title = "Recently played playlists") {
            items(recentPlaylists, key = { it.id }) { playlist ->
                PlaylistTile(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    modifier = Modifier.width(ArtworkSize.shelf),
                )
            }
        }
    }
}

/** A shelf with nothing in it yet: its heading and one quiet line saying what will appear. */
@Composable
private fun ShelfNote(
    title: String,
    note: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        SectionHeading(title)
        Text(
            text = note,
            style = TextStyles.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenGutter),
        )
    }
}

/** A titled horizontal row of artwork cards. */
@Composable
private fun Shelf(
    title: String,
    cards: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        SectionHeading(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = cards,
        )
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = TextStyles.sectionTitle,
        modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
    )
}

/**
 * A full Library shelf, opened from the overview's "More": Playlists, Favorites
 * or Artists, each the same list or grid it was before, so its actions are unchanged.
 */
@Composable
@Suppress("LongParameterList")
internal fun LibraryHub(
    shelf: LibraryShelf,
    playlists: List<Playlist>,
    favoriteSongs: LazyPagingItems<Song>,
    artists: LazyPagingItems<Artist>,
    artistArtwork: Map<Long, String>,
    currentSongId: Long?,
    onFavoriteClick: (Song) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    actions: SongActions,
    topInset: Dp,
    bottomInset: Dp,
) {
    when (shelf) {
        LibraryShelf.PLAYLISTS ->
            PlaylistsGrid(
                playlists = playlists,
                onPlaylistClick = onPlaylistClick,
                onNewPlaylist = onNewPlaylist,
                topInset = topInset,
                bottomInset = bottomInset,
            )
        LibraryShelf.FAVORITES ->
            SongsTab(
                songs = favoriteSongs,
                currentSongId = currentSongId,
                isScanning = false,
                topInset = topInset,
                bottomInset = bottomInset,
                emptyIcon = LibraryShelf.FAVORITES.icon,
                emptyMessage = "No favorites yet. Tap the heart on any song to add it here.",
                onSongClick = onFavoriteClick,
                actions = actions,
            )
        LibraryShelf.ARTISTS ->
            ArtistsTab(
                artists = artists,
                artwork = artistArtwork,
                topInset = topInset,
                bottomInset = bottomInset,
                onArtistClick = onArtistClick,
            )
    }
}

/** Playlists as an artwork-first grid, like Albums; the first cell creates a playlist. */
@Composable
private fun PlaylistsGrid(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
    topInset: Dp,
    bottomInset: Dp,
) {
    val gridState = rememberLazyGridState()
    // The page title merges into the header button as this scrolls.
    ReportTitleScroll(gridState)
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = ArtworkSize.gridMin),
        modifier = Modifier.fillMaxSize(),
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
        item(key = "new_playlist", contentType = "new") { NewPlaylistTile(onClick = onNewPlaylist) }
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistTile(playlist = playlist, onClick = { onPlaylistClick(playlist) })
        }
    }
}
