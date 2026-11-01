package com.erhu.erhu.feature.library

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.Playlist
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.feature.library.mix.RadioSeed
import com.erhu.erhu.feature.library.transition.StudioInput
import com.erhu.erhu.feature.library.transition.StudioTrack
import com.erhu.erhu.feature.library.transition.TransitionStudioHost
import com.erhu.erhu.feature.library.transition.TransitionStudioRow
import com.erhu.erhu.feature.library.transition.TransitionStudioViewModel

/** Same proportions as the album page header. */
private const val COVER_WIDTH_FRACTION = 0.62f
private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f
private const val MS_PER_MINUTE = 60_000L

/**
 * A playlist's page, built like the album page: the playlist's cover, name and
 * facts, Play / Shuffle and a menu (Edit playlist, Delete playlist), then its
 * songs in playlist order. Tapping a song plays the playlist from that song.
 */
@Composable
@Suppress("LongMethod")
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    libraryViewModel: LibraryViewModel,
    transitionsViewModel: TransitionStudioViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(playlistId) { viewModel.openPlaylist(playlistId) }
    val playlist by viewModel.openPlaylist.collectAsState()
    val songs by viewModel.openPlaylistSongs.collectAsState()
    val currentSongId by libraryViewModel.currentSongId.collectAsState()
    val candidates by libraryViewModel.metadataCandidates.collectAsState()

    var showEditor by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }
    val entries by viewModel.openPlaylistEntries.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var songsForPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForViewMetadata by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(viewModel.userMessage, snackbarHostState, viewModel::consumeUserMessage)
    UserMessageEffect(libraryViewModel.userMessage, snackbarHostState, libraryViewModel::consumeUserMessage)

    val actions =
        SongActions(
            onToggleFavorite = { libraryViewModel.toggleFavorite(it) },
            onPlayNext = { libraryViewModel.playNext(it) },
            onAddToQueue = { libraryViewModel.addToQueue(it) },
            onStartRadio = { libraryViewModel.startRadio(RadioSeed.Song(it.id)) },
            onAddToPlaylist = { songsForPlaylist = listOf(it) },
            onFixTags = { song ->
                songForFixTags = song
                libraryViewModel.searchMetadataForSong(song)
            },
            onViewMetadata = { song ->
                songForViewMetadata = song
            },
        )

    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack),
                actions = {
                    playlist?.takeIf { it.id == playlistId }?.let {
                        PlaylistMenu(
                            onEdit = { showEditor = true },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        // Shown once the playlist has loaded (it is never null for long: Room answers within a frame).
        val current = playlist?.takeIf { it.id == playlistId } ?: return@AppScaffold
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container),
            contentPadding = PaddingValues(top = scroll.contentTop, bottom = Spacing.lg + scroll.contentBottom),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    playlist = current,
                    songs = songs,
                    onPlay = { viewModel.playOpenPlaylist() },
                    onShuffle = { viewModel.shuffleOpenPlaylist() },
                )
            }
            item(key = "transitions") {
                TransitionStudioRow(CollectionRef.playlist(playlistId), transitionsViewModel, onOpen = { showStudio = true })
            }
            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        message = "This playlist is empty. Add songs with \"Add to playlist\" in any song's menu.",
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
            } else {
                // A playlist can hold the same song twice (a saved queue): the position makes the key unique.
                itemsIndexed(songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
                    LibrarySongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { viewModel.playOpenPlaylist(index) },
                        actions = actions,
                        onRemoveFromPlaylist = { viewModel.removeSongFromOpenPlaylist(song) },
                    )
                }
            }
        }

        if (showEditor) {
            EditPlaylistDialog(
                playlist = current,
                onDismiss = { showEditor = false },
                onSave = { name, cover ->
                    if (name.trim() != current.name) viewModel.renameOpenPlaylist(name)
                    when (cover) {
                        is CoverChange.Replace -> viewModel.setOpenPlaylistArtwork(cover.jpegBytes)
                        CoverChange.Remove -> viewModel.clearOpenPlaylistArtwork()
                        CoverChange.None -> Unit
                    }
                    showEditor = false
                },
            )
        }

        if (confirmDelete) {
            GlassAlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete playlist?") },
                text = { Text("\"${current.name}\" will be deleted. The songs stay in your library.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.deletePlaylist(current)
                        onBack()
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }

    songForFixTags?.let { song ->
        FixTagsDialog(
            song = song,
            candidates = candidates,
            onDismiss = { songForFixTags = null },
            onSaveTags = { title, artist, album, year, genre, bpm ->
                libraryViewModel.writeTags(song, title, artist, album, year, genre, bpm)
                songForFixTags = null
            },
        )
    }

    songForViewMetadata?.let { song ->
        ViewMetadataScreen(
            song = song,
            viewModel = hiltViewModel(),
            libraryViewModel = libraryViewModel,
            onDismiss = { songForViewMetadata = null },
        )
    }

    val studioPlaylist = playlist?.takeIf { it.id == playlistId }
    if (showStudio && studioPlaylist != null) {
        // Playlist order; each entry's position is its identity, so duplicates are separate tracks here.
        val tracks = entries.map { StudioTrack(it.song, TrackOccurrence(it.song.id, it.position)) }
        TransitionStudioHost(
            StudioInput(CollectionRef.playlist(playlistId), studioPlaylist.name, tracks),
            transitionsViewModel,
            onClose = { showStudio = false },
        )
    }

    songsForPlaylist?.let { picked ->
        AddToPlaylistDialog(songs = picked, viewModel = viewModel, onDismiss = { songsForPlaylist = null })
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
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
        PlaylistCover(
            playlist = playlist,
            modifier =
                Modifier
                    .widthIn(max = ArtworkSize.heroMax)
                    .fillMaxWidth(COVER_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .clip(Radius.largeShape),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = playlist.name,
            style = TextStyles.pageTitle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = playlistFacts(songs),
            style = TextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(ACTION_PAIR_WIDTH_FRACTION),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onPlay, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(IconSize.small))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text("Play", maxLines = 1, softWrap = false)
            }
            FilledTonalButton(onClick = onShuffle, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(IconSize.small))
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text("Shuffle", maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun PlaylistMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    GlassMenuAnchor {
        IconButton(modifier = Modifier.glassMenuTrigger(), onClick = { expanded = true }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Playlist options")
        }
        GlassMenu(label = "Playlist options", expanded = expanded, onDismissRequest = { expanded = false }) {
            GlassMenuItem(
                icon = Icons.Default.Edit,
                text = { Text("Edit playlist") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            GlassMenuItem(
                icon = Icons.Default.DeleteOutline,
                destructive = true,
                text = { Text("Delete playlist") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** "12 songs · 43 min"; the duration is left out when unknown. */
private fun playlistFacts(songs: List<Song>): String {
    val minutes = songs.sumOf { it.durationMs } / MS_PER_MINUTE
    return listOfNotNull(countLabel(songs.size, "song"), minutes.takeIf { it > 0 }?.let { "$it min" }).joinToString(" · ")
}
