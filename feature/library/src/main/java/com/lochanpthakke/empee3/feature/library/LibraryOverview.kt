package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.ReportTitleScroll
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Playlist
import com.lochanpthakke.empee3.core.model.Song

/** How many items a section previews; the rest are one "More" away. */
private const val PREVIEW_LIMIT = 15

/** Cards per screen width: a fraction, so the next card peeks in and the row reads as scrollable. */
private const val CARDS_PER_WIDTH = 2.4f
private val CARD_MIN = 112.dp
private val CARD_MAX = 168.dp

/** What the overview opens: a full shelf, or (for Songs) the Tracks destination. */
internal class LibraryOverviewActions(
    val onMore: (LibraryShelf) -> Unit,
    val onMoreSongs: () -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onNewPlaylist: () -> Unit,
    val onFavoriteClick: (Song) -> Unit,
    val onSongClick: (Song) -> Unit,
    val onArtistClick: (Artist) -> Unit,
)

/**
 * The Library landing page: Playlists, Favorites, Songs and Artists stacked in
 * one vertical list, each a short horizontal preview with a "More" that opens
 * the full shelf. Every card is an existing tile, so taps behave as before.
 */
@Composable
@Suppress("LongParameterList")
internal fun LibraryOverview(
    playlists: List<Playlist>,
    favoriteSongs: LazyPagingItems<Song>,
    songs: LazyPagingItems<Song>,
    artists: LazyPagingItems<Artist>,
    artistArtwork: Map<Long, String>,
    isScanning: Boolean,
    actions: LibraryOverviewActions,
    topInset: Dp,
    bottomInset: Dp,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Card width follows the screen, within readable bounds.
        val cardWidth =
            ((maxWidth - Spacing.screenGutter - Spacing.md * 2) / CARDS_PER_WIDTH).coerceIn(CARD_MIN, CARD_MAX)
        val listState = rememberLazyListState()
        // The page title merges into the header button as this scrolls.
        ReportTitleScroll(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.sm + topInset, bottom = Spacing.lg + bottomInset),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "playlists", contentType = "section") { PlaylistsSection(playlists, actions, cardWidth) }
            item(key = "favorites", contentType = "section") {
                LibraryPagedSection(
                    title = LibraryShelf.FAVORITES.label,
                    items = favoriteSongs,
                    itemKey = { it.id },
                    emptyMessage = "No favorites yet. Tap the heart on any song to add it here.",
                    onMore = { actions.onMore(LibraryShelf.FAVORITES) },
                ) { song ->
                    SongTile(song = song, onClick = { actions.onFavoriteClick(song) }, modifier = Modifier.width(cardWidth))
                }
            }
            item(key = "artists", contentType = "section") {
                LibraryPagedSection(
                    title = LibraryShelf.ARTISTS.label,
                    items = artists,
                    itemKey = { it.id },
                    emptyMessage = "No artists found. Pull down to rescan your device.",
                    onMore = { actions.onMore(LibraryShelf.ARTISTS) },
                ) { artist ->
                    ArtistTile(
                        artist = artist,
                        artworkUri = artistArtwork[artist.id],
                        onClick = { actions.onArtistClick(artist) },
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
            item(key = "songs", contentType = "section") {
                LibraryPagedSection(
                    title = "Songs",
                    items = songs,
                    itemKey = { it.id },
                    emptyMessage = if (isScanning) "Scanning your music…" else "No songs found. Pull down to rescan your device.",
                    onMore = actions.onMoreSongs,
                ) { song ->
                    SongTile(song = song, onClick = { actions.onSongClick(song) }, modifier = Modifier.width(cardWidth))
                }
            }
        }
    }
}

/** Playlists always show: "New playlist" leads the row even when there are none yet. */
@Composable
private fun PlaylistsSection(
    playlists: List<Playlist>,
    actions: LibraryOverviewActions,
    cardWidth: Dp,
) {
    LibrarySectionRow(title = LibraryShelf.PLAYLISTS.label, onMore = { actions.onMore(LibraryShelf.PLAYLISTS) }) {
        item(key = "new_playlist", contentType = "new") {
            NewPlaylistTile(onClick = actions.onNewPlaylist, modifier = Modifier.width(cardWidth))
        }
        items(playlists.take(PREVIEW_LIMIT), key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistTile(playlist = playlist, onClick = { actions.onPlaylistClick(playlist) }, modifier = Modifier.width(cardWidth))
        }
    }
}

/** A section previewing the first [PREVIEW_LIMIT] paged items, or a one-line message when there are none. */
@Composable
private fun <T : Any> LibraryPagedSection(
    title: String,
    items: LazyPagingItems<T>,
    itemKey: (T) -> Any,
    emptyMessage: String,
    onMore: () -> Unit,
    card: @Composable (T) -> Unit,
) {
    if (items.itemCount == 0) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LibrarySectionHeader(title = title, onMore = onMore)
            Text(
                text = emptyMessage,
                style = TextStyles.tileSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenGutter),
            )
        }
    } else {
        LibrarySectionRow(title = title, onMore = onMore) {
            items(count = minOf(items.itemCount, PREVIEW_LIMIT), key = items.itemKey(itemKey)) { index ->
                items[index]?.let { card(it) }
            }
        }
    }
}

/** A section header above a horizontal row of cards. */
@Composable
internal fun LibrarySectionRow(
    title: String,
    onMore: () -> Unit,
    cards: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LibrarySectionHeader(title = title, onMore = onMore)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = cards,
        )
    }
}

/** "Title ........ More →" */
@Composable
internal fun LibrarySectionHeader(
    title: String,
    onMore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screenGutter, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyles.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = "More $title" }) {
            Text("More", maxLines = 1)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(IconSize.small))
        }
    }
}

/** A song as an artwork card: square cover, title, artist. Width comes from the caller. */
@Composable
internal fun SongTile(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        SongArtwork(
            song = song,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = song.title, style = TextStyles.tileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = song.artist,
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
