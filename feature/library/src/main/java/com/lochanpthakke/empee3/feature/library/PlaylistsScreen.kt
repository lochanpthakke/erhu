package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.GlassAlertDialog
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onOpenDrawer: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(viewModel.userMessage, snackbarHostState, viewModel::consumeUserMessage)

    AppScaffold(
        topBar = { AppTopBar(navigation = TopBarNavigation.TopLevel(onOpenDrawer), title = "Playlists") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New playlist")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (playlists.isEmpty()) {
            EmptyState(
                message = "No playlists yet. Tap + to create one, or use \"Add to playlist\" on any song.",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            val scroll = innerPadding.forScrollingContent()
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scroll.container),
                contentPadding = PaddingValues(top = scroll.contentTop, bottom = scroll.contentBottom),
            ) {
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { _, playlist ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPlaylist(playlist.id) }
                                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.lg))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = TextStyles.itemTitle)
                            Text(
                                text = "${playlist.songCount} songs",
                                style = TextStyles.itemSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { playlistPendingDelete = playlist }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete playlist")
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
        )
    }

    playlistPendingDelete?.let { playlist ->
        GlassAlertDialog(
            onDismissRequest = { playlistPendingDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${playlist.name}\" will be deleted. The songs stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist)
                    playlistPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { playlistPendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    currentSongId: Long?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(playlistId) { viewModel.openPlaylist(playlistId) }
    val playlist by viewModel.openPlaylist.collectAsState()
    val songs by viewModel.openPlaylistSongs.collectAsState()

    AppScaffold(
        topBar = { AppTopBar(navigation = TopBarNavigation.Detail(onBackClick), title = playlist?.name ?: "Playlist") },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container)
                    // The header row is not scrollable, so it simply starts below the bar.
                    .padding(top = scroll.contentTop),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { viewModel.playOpenPlaylist() },
                    enabled = songs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Play")
                }
                OutlinedButton(
                    onClick = { viewModel.shuffleOpenPlaylist() },
                    enabled = songs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Shuffle")
                }
            }

            if (songs.isEmpty()) {
                EmptyState(
                    message = "This playlist is empty. Add songs from the library's song menu.",
                    modifier = Modifier.padding(bottom = scroll.contentBottom),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = scroll.contentBottom),
                ) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isHighlighted = song.id == currentSongId,
                            onClick = { viewModel.playOpenPlaylist(index) },
                        ) {
                            IconButton(onClick = { viewModel.removeSongFromOpenPlaylist(song) }) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Remove from playlist",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
