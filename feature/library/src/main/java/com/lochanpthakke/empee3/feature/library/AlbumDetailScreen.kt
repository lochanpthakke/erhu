package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.OverlayHeader
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.ArtworkSize
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.foundation.sharedArtwork
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.combine

/**
 * Header artwork width as a share of the screen width: prominent, while the
 * first tracks stay visible on a phone without scrolling.
 */
private const val ARTWORK_WIDTH_FRACTION = 0.62f

/**
 * Width of the centered Play/Shuffle pair as a share of the header width: the
 * width the pair had beside the former playlist icon, so the buttons keep their size.
 */
private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** What the album page shows: [album] is null once the album has left the library. */
private class AlbumDetailData(
    val album: Album?,
    val songs: List<Song>,
)

/**
 * An album's page: artwork, title, artist and facts from the library, Play /
 * Shuffle / Add to playlist, then the album's tracks in album order. Data comes
 * from the library repository through [viewModel] and updates live, so an album
 * removed by a rescan turns into an empty state instead of failing.
 */
@Composable
@Suppress("LongMethod")
fun AlbumDetailScreen(
    albumId: Long,
    viewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail by remember(albumId) {
        combine(viewModel.observeAlbum(albumId), viewModel.observeAlbumSongs(albumId), ::AlbumDetailData)
    }.collectAsState(initial = null)
    // Only the playing song's id: position ticks must not recompose this page.
    val currentSongId by viewModel.currentSongId.collectAsState()
    val candidates by viewModel.metadataCandidates.collectAsState()

    var songsForPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(viewModel.userMessage, snackbarHostState, viewModel::consumeUserMessage)
    UserMessageEffect(playlistsViewModel.userMessage, snackbarHostState, playlistsViewModel::consumeUserMessage)

    val actions =
        SongActions(
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onPlayNext = { viewModel.playNext(it) },
            onAddToQueue = { viewModel.addToQueue(it) },
            onAddToPlaylist = { songsForPlaylist = listOf(it) },
            onFixTags = { song ->
                songForFixTags = song
                viewModel.searchMetadataForSong(song)
            },
        )

    AppScaffold(
        // Floating back button only; the page scrolls underneath it, as in the Library.
        topBar = { OverlayHeader(navigation = TopBarNavigation.Detail(onBack)) },
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
                    message = "This album is no longer in your library.",
                    icon = LibrarySection.ALBUMS.icon,
                    modifier = Modifier.padding(innerPadding),
                )
            else -> {
                val songs = data.songs
                val scroll = innerPadding.forScrollingContent(topOverlay = true)
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scroll.container),
                    contentPadding = PaddingValues(top = scroll.contentTop, bottom = Spacing.lg + scroll.contentBottom),
                ) {
                    item(key = "header") {
                        AlbumHeader(
                            album = album,
                            songs = songs,
                            onPlay = { viewModel.playInOrder(songs) },
                            onShuffle = { viewModel.playShuffled(songs) },
                        )
                    }
                    if (songs.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(message = "This album has no tracks.", modifier = Modifier.padding(top = Spacing.xl))
                        }
                    } else {
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            LibrarySongRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                onClick = { viewModel.playSongsFromQueue(songs, index) },
                                actions = actions,
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
            onSaveTags = { title, artist, albumName, year, genre ->
                viewModel.writeTags(song, title, artist, albumName, year, genre)
                songForFixTags = null
            },
        )
    }

    songsForPlaylist?.let { songs ->
        AddToPlaylistDialog(
            songs = songs,
            viewModel = playlistsViewModel,
            onDismiss = { songsForPlaylist = null },
        )
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlbumArtwork(
            artworkUri = album.artworkUri,
            contentDescription = "Album artwork",
            modifier =
                Modifier
                    .sharedArtwork(albumArtworkKey(album.id))
                    .widthIn(max = ArtworkSize.heroMax)
                    .fillMaxWidth(ARTWORK_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .clip(Radius.largeShape),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = album.title,
            style = TextStyles.pageTitle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist,
            style = TextStyles.listTitle,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = albumFacts(album, songs),
            style = TextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        AlbumActions(enabled = songs.isNotEmpty(), onPlay = onPlay, onShuffle = onShuffle)
    }
}

/** Play and Shuffle as an equal-width pair, centered (the header column centers it). */
@Composable
private fun AlbumActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(ACTION_PAIR_WIDTH_FRACTION),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onPlay, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Shuffle")
        }
    }
}

/** "2024 · 12 songs · 43 min" from what the library knows; unknown parts are left out. */
private fun albumFacts(
    album: Album,
    songs: List<Song>,
): String {
    val totalMinutes = songs.sumOf { it.durationMs } / MS_PER_MINUTE
    val duration =
        when {
            totalMinutes <= 0 -> null
            totalMinutes < MINUTES_PER_HOUR -> "$totalMinutes min"
            else -> "${totalMinutes / MINUTES_PER_HOUR} hr ${totalMinutes % MINUTES_PER_HOUR} min"
        }
    return listOfNotNull(
        album.year.takeIf { it > 0 }?.toString(),
        countLabel(songs.size, "song"),
        duration,
    ).joinToString(" · ")
}
