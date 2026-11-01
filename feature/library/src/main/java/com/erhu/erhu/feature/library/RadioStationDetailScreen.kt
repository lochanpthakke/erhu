package com.erhu.erhu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.RadioSeed

@Composable
@Suppress("LongMethod")
fun RadioStationDetailScreen(
    albumId: Long,
    featuredAlbums: List<com.erhu.erhu.core.model.Album>,
    viewModel: RadioStationViewModel,
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(albumId) { viewModel.open(albumId) }
    val loaded by viewModel.content.collectAsState()
    val error by viewModel.error.collectAsState()
    val artwork by libraryViewModel.artistArtwork.collectAsState()
    val currentSongId by libraryViewModel.currentSongId.collectAsState()
    val candidates by libraryViewModel.metadataCandidates.collectAsState()
    var songsForPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForViewMetadata by remember { mutableStateOf<Song?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(libraryViewModel.userMessage, snackbarHostState, libraryViewModel::consumeUserMessage)
    UserMessageEffect(playlistsViewModel.userMessage, snackbarHostState, playlistsViewModel::consumeUserMessage)

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
        topBar = { OverlayHeader(navigation = TopBarNavigation.Detail(onBack)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        val content = loaded
        if (content == null) {
            Column(
                Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (error == null) {
                    CircularProgressIndicator()
                } else {
                    Text(error.orEmpty(), style = TextStyles.metadata)
                    Button(onClick = { viewModel.open(albumId) }) { Text("Retry") }
                }
            }
            return@AppScaffold
        }
        val songs = content.songs
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container),
            contentPadding = PaddingValues(top = scroll.contentTop, bottom = Spacing.lg + scroll.contentBottom),
        ) {
            item(key = "header") {
                val radio =
                    remember(content.album, featuredAlbums, artwork) {
                        FeaturedSetlists.radioFor(content.album, featuredAlbums, artwork)
                    }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RadioArtwork(radio, Modifier.fillMaxWidth(0.62f).aspectRatio(1f))
                    Spacer(Modifier.height(Spacing.lg))
                    Text("${radio.identity} Radio", style = TextStyles.pageTitle, textAlign = TextAlign.Center)
                    Text(
                        "Based on ${content.album.title}",
                        style = TextStyles.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(countLabel(songs.size, "song"), style = TextStyles.metadata)
                    Spacer(Modifier.height(Spacing.lg))
                    Button(
                        enabled = songs.isNotEmpty() && content.plan != null,
                        onClick = { content.plan?.let { libraryViewModel.playRadioPlan(it, songs) } },
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Play Radio")
                    }
                }
            }
            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        message = "No available songs for this station. Add more music to your library and reopen it.",
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    LibrarySongRow(
                        song = song,
                        isCurrent = song.id == currentSongId,
                        onClick = { libraryViewModel.playSongsFromQueue(songs, index) },
                        actions = actions,
                    )
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
                libraryViewModel.writeTags(song, title, artist, album, year, genre, bpm)
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
            libraryViewModel = libraryViewModel,
            onDismiss = { songForViewMetadata = null },
        )
    }

    songsForPlaylist?.let { picked ->
        AddToPlaylistDialog(songs = picked, viewModel = playlistsViewModel, onDismiss = { songsForPlaylist = null })
    }
}
