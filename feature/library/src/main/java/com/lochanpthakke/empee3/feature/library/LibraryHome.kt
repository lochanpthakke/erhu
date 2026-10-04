package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.Song

/**
 * The Tracks destination as the home feed: recently played albums and playlists
 * (from real playback history; a shelf with nothing in it is not shown), then
 * the full track list. One lazy list, so the shelves scroll away with the tracks.
 */
@Composable
@Suppress("LongParameterList")
internal fun HomeFeed(
    songs: LazyPagingItems<Song>,
    recentAlbums: List<Album>,
    recentPlaylists: List<Playlist>,
    currentSongId: Long?,
    isScanning: Boolean,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    actions: SongActions,
    topInset: Dp,
    bottomInset: Dp,
) {
    val hasShelves = recentAlbums.isNotEmpty() || recentPlaylists.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.sm + topInset, bottom = Spacing.sm + bottomInset),
    ) {
        // One header item that always exists (empty = zero height). The list stays anchored
        // to it at the top, so shelves appearing after the first play push the tracks down
        // into view instead of being inserted above the visible position.
        item(key = "home_header", contentType = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (recentAlbums.isNotEmpty()) {
                    Shelf(title = "Recently played albums") {
                        items(recentAlbums, key = { it.id }) { album ->
                            AlbumTile(album = album, onClick = { onAlbumClick(album) }, modifier = Modifier.width(ArtworkSize.shelf))
                        }
                    }
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
                if (hasShelves) SectionHeading("Tracks")
            }
        }
        if (songs.itemCount == 0) {
            item(key = "empty", contentType = "empty") {
                EmptyState(
                    message = if (isScanning) "Scanning your music…" else "No songs found. Pull down to rescan your device.",
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
 * The Library destination: the place where the user's own collection is
 * organised. Chips switch between Playlists, Favorites and Artists; each uses
 * the same list or grid it had before, so their actions are unchanged.
 */
@Composable
@Suppress("LongParameterList")
internal fun LibraryHub(
    shelf: LibraryShelf,
    onShelfChange: (LibraryShelf) -> Unit,
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
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    // One line at any text size: the row scrolls instead of wrapping a label.
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            LibraryShelf.entries.forEach { entry ->
                FilterChip(
                    selected = entry == shelf,
                    onClick = { onShelfChange(entry) },
                    label = { Text(entry.label, maxLines = 1, softWrap = false) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(IconSize.small)) },
                )
            }
        }
        when (shelf) {
            LibraryShelf.PLAYLISTS ->
                PlaylistsGrid(
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                    onNewPlaylist = onNewPlaylist,
                    bottomInset = bottomInset,
                )
            LibraryShelf.FAVORITES ->
                SongsTab(
                    songs = favoriteSongs,
                    currentSongId = currentSongId,
                    isScanning = false,
                    topInset = 0.dp,
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
                    topInset = 0.dp,
                    bottomInset = bottomInset,
                    onArtistClick = onArtistClick,
                )
        }
    }
}

/** Playlists as an artwork-first grid, like Albums; the first cell creates a playlist. */
@Composable
private fun PlaylistsGrid(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
    bottomInset: Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = ArtworkSize.gridMin),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        contentPadding =
            PaddingValues(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                top = Spacing.md,
                bottom = Spacing.lg + bottomInset,
            ),
    ) {
        item(key = "new_playlist", contentType = "new") { NewPlaylistTile(onClick = onNewPlaylist) }
        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistTile(playlist = playlist, onClick = { onPlaylistClick(playlist) })
        }
    }
}
