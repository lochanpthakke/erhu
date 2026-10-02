package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.artwork.ArtistArtwork
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
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.combine

private const val ARTWORK_WIDTH_FRACTION = 0.55f
private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f
private val COMPACT_ALBUM_CARD_WIDTH = 110.dp

private class ArtistDetailData(
    val artist: Artist?,
    val albums: List<Album>,
    val primarySongs: List<Song>,
    val featuredSongs: List<Song>,
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
            ::ArtistDetailData,
        )
    }.collectAsState(initial = null)

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
        topBar = { OverlayHeader(navigation = TopBarNavigation.Detail(onBack)) },
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
                    icon = LibrarySection.ARTISTS.icon,
                    modifier = Modifier.padding(innerPadding),
                )
            else -> {
                val albums = data.albums
                val primarySongs = data.primarySongs
                val featuredSongs = data.featuredSongs
                val allArtistSongs = primarySongs.ifEmpty { featuredSongs }
                val scroll = innerPadding.forScrollingContent(topOverlay = true)

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scroll.container),
                    contentPadding = PaddingValues(top = scroll.contentTop, bottom = Spacing.lg + scroll.contentBottom),
                ) {
                    item(key = "header") {
                        ArtistHeader(
                            artist = artist,
                            songs = allArtistSongs,
                            onPlay = { viewModel.playInOrder(allArtistSongs) },
                            onShuffle = { viewModel.playShuffled(allArtistSongs) },
                        )
                    }

                    if (albums.isNotEmpty()) {
                        item(key = "albums_section") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                            ) {
                                Text(
                                    text = "Albums",
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
            onSaveTags = { title, artistName, albumName, year, genre ->
                viewModel.writeTags(song, title, artistName, albumName, year, genre)
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
private fun ArtistHeader(
    artist: Artist,
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
        ArtistArtwork(
            artworkUri = artist.artworkUri,
            artistName = artist.name,
            modifier =
                Modifier
                    .widthIn(max = ArtworkSize.heroMax)
                    .fillMaxWidth(ARTWORK_WIDTH_FRACTION)
                    .aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = artist.name,
            style = TextStyles.pageTitle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = countLabel(artist.songCount, "song") + " · " + countLabel(artist.albumCount, "album"),
            style = TextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        ArtistActions(enabled = songs.isNotEmpty(), onPlay = onPlay, onShuffle = onShuffle)
    }
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
                    .sharedArtwork(albumArtworkKey(album.id))
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
        if (album.year > 0) {
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
