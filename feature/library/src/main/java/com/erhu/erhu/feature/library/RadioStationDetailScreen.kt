package com.erhu.erhu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.RadioSeed
import com.erhu.erhu.core.common.R as CommonR

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
    val currentSongId by libraryViewModel.currentSongId.collectAsState()
    val candidates by libraryViewModel.metadataCandidates.collectAsState()
    val content = loaded?.takeIf { it.album.id == albumId }
    val radio =
        remember(content?.album, featuredAlbums) {
            content?.album?.let { FeaturedSetlists.radioFor(it, featuredAlbums, emptyMap()) }
        }
    val listState = rememberLazyListState()
    val collapsingTitle = rememberCollapsingTitleState()
    val portraits = remember(content) { content?.let(::radioPortraits).orEmpty() }
    val palette by androidx.compose.runtime.key(albumId) { rememberRadioPalette(portraits.firstOrNull()) }

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

    MaterialTheme(colorScheme = radioColorScheme(MaterialTheme.colorScheme, palette)) {
        AppScaffold(
            topBar = {
                OverlayHeader(
                    navigation = TopBarNavigation.Detail(onBack),
                    title = radio?.let { stringResource(CommonR.string.radio_title_format, it.identity) },
                    subtitle = content?.album?.let { stringResource(CommonR.string.radio_subtitle_based_on, it.title) },
                    collapsingTitle = collapsingTitle,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier,
        ) { innerPadding ->
            if (content == null) {
                Column(
                    Modifier.fillMaxSize().padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val errorRes = error
                    if (errorRes == null) {
                        CircularProgressIndicator()
                    } else {
                        Text(stringResource(errorRes), style = TextStyles.metadata)
                        Button(onClick = { viewModel.open(albumId) }) { Text(stringResource(CommonR.string.action_retry)) }
                    }
                }
                return@AppScaffold
            }
            val songs = content.songs
            val scroll = innerPadding.forScrollingContent(topOverlay = true)
            CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) {
                ReportTitleScroll(listState)
            }
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(scroll.container),
                contentPadding = PaddingValues(top = scroll.contentTop, bottom = Spacing.lg + scroll.contentBottom),
            ) {
                item(key = "header") {
                    RadioStationHero(portraits, palette)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(songCountText(songs.size), style = TextStyles.metadata)
                        Spacer(Modifier.height(Spacing.sm))
                        Button(
                            enabled = songs.isNotEmpty() && content.plan != null,
                            onClick = { content.plan?.let { libraryViewModel.playRadioPlan(it, songs) } },
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(CommonR.string.radio_play))
                        }
                    }
                }
                if (songs.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            message = stringResource(CommonR.string.radio_empty),
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
}
