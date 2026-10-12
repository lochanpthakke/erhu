package com.lochanpthakke.empee3.feature.library

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lochanpthakke.empee3.core.database.repository.SmartPlaylistRepository
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
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
import com.lochanpthakke.empee3.core.model.SmartPlaylist
import com.lochanpthakke.empee3.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Smart playlists shown on the Tracks page. Recently Played is left out there: the
 * page already opens with its own recently played shelves.
 */
internal val HOME_SMART_PLAYLISTS =
    listOf(SmartPlaylist.MOST_PLAYED, SmartPlaylist.RECENTLY_ADDED, SmartPlaylist.FAVORITES, SmartPlaylist.NEVER_PLAYED)

private val SmartPlaylist.icon: ImageVector
    get() =
        when (this) {
            SmartPlaylist.MOST_PLAYED -> Icons.Default.Whatshot
            SmartPlaylist.RECENTLY_ADDED -> Icons.Default.NewReleases
            SmartPlaylist.RECENTLY_PLAYED -> Icons.Default.History
            SmartPlaylist.FAVORITES -> Icons.Default.Favorite
            SmartPlaylist.NEVER_PLAYED -> Icons.Default.Explore
        }

private val SmartPlaylist.subtitle: String
    get() =
        when (this) {
            SmartPlaylist.MOST_PLAYED -> "Your top songs"
            SmartPlaylist.RECENTLY_ADDED -> "Newest first"
            SmartPlaylist.RECENTLY_PLAYED -> "Latest plays"
            SmartPlaylist.FAVORITES -> "Songs you love"
            SmartPlaylist.NEVER_PLAYED -> "Not played yet"
        }

/** A smart playlist card: the playlist card's treatment, with a collage of its albums as the cover. */
@Composable
internal fun SmartPlaylistTile(
    playlist: SmartPlaylist,
    covers: List<Song>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        SmartPlaylistCover(
            playlist = playlist,
            covers = covers,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = playlist.title, style = TextStyles.tileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = playlist.subtitle,
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The playlist's own albums as its cover: one album fills it, two to four make a 2 × 2
 * collage (album by album, in the playlist's order; missing quadrants stay the plain
 * artwork background). Each cell loads the album's artwork at the cell's size through
 * the artwork cache. With no songs, the playlist's icon on the usual placeholder.
 */
@Composable
private fun SmartPlaylistCover(
    playlist: SmartPlaylist,
    covers: List<Song>,
    modifier: Modifier = Modifier,
) {
    when (covers.size) {
        0 -> AlbumArtwork(artworkUri = null, contentDescription = null, placeholderIcon = playlist.icon, modifier = modifier)
        1 -> SongArtwork(song = covers.first(), modifier = modifier)
        else ->
            Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                repeat(COLLAGE_ROWS) { row ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        repeat(COLLAGE_ROWS) { column ->
                            val cell = Modifier.weight(1f).fillMaxHeight()
                            val song = covers.getOrNull(row * COLLAGE_ROWS + column)
                            if (song != null) SongArtwork(song = song, modifier = cell) else Spacer(modifier = cell)
                        }
                    }
                }
            }
    }
}

/** The songs of the open smart playlist, live (they follow plays, favorites and rescans). */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SmartPlaylistViewModel
    @Inject
    constructor(
        repository: SmartPlaylistRepository,
    ) : ViewModel() {
        private val open = MutableStateFlow<SmartPlaylist?>(null)

        /** Null until the open playlist has loaded. */
        val songs: StateFlow<List<Song>?> =
            open
                .filterNotNull()
                .flatMapLatest { playlist -> repository.observe(playlist).map<List<Song>, List<Song>?> { it } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun open(playlist: SmartPlaylist) {
            open.value = playlist
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/**
 * A smart playlist's page, in the playlist page's layout: cover, title, facts,
 * Play / Shuffle, then its songs with the usual song actions. Playback goes through
 * the library as everywhere else.
 */
@Composable
@Suppress("LongMethod")
fun SmartPlaylistDetailScreen(
    playlist: SmartPlaylist,
    viewModel: SmartPlaylistViewModel,
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(playlist) { viewModel.open(playlist) }
    val loaded by viewModel.songs.collectAsState()
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
        // Room answers within a frame or two: show nothing rather than a flash of "empty".
        val songs = loaded ?: return@AppScaffold
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container),
            contentPadding = PaddingValues(top = scroll.contentTop, bottom = Spacing.lg + scroll.contentBottom),
        ) {
            item(key = "header") {
                SmartPlaylistHeader(
                    playlist = playlist,
                    songs = songs,
                    onPlay = { libraryViewModel.playInOrder(songs) },
                    onShuffle = { libraryViewModel.playShuffled(songs) },
                )
            }
            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(message = emptyMessage(playlist), modifier = Modifier.padding(top = Spacing.xl))
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
            viewModel = androidx.hilt.navigation.compose.hiltViewModel(),
            libraryViewModel = libraryViewModel,
            onDismiss = { songForViewMetadata = null },
        )
    }

    songsForPlaylist?.let { picked ->
        AddToPlaylistDialog(songs = picked, viewModel = playlistsViewModel, onDismiss = { songsForPlaylist = null })
    }
}

@Composable
private fun SmartPlaylistHeader(
    playlist: SmartPlaylist,
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
            artworkUri = null,
            contentDescription = null,
            placeholderIcon = playlist.icon,
            modifier =
                Modifier
                    .widthIn(max = ArtworkSize.heroMax)
                    .fillMaxWidth(COVER_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .clip(Radius.largeShape),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(text = playlist.title, style = TextStyles.pageTitle, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = smartPlaylistFacts(songs),
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

private fun emptyMessage(playlist: SmartPlaylist): String =
    when (playlist) {
        SmartPlaylist.MOST_PLAYED, SmartPlaylist.RECENTLY_PLAYED -> "Songs you play will show up here."
        SmartPlaylist.RECENTLY_ADDED -> "No songs in your library yet."
        SmartPlaylist.FAVORITES -> "No favorites yet. Tap the heart on any song."
        SmartPlaylist.NEVER_PLAYED -> "You have played every song in your library."
    }

/** "12 songs · 43 min"; the duration is left out when unknown. */
private fun smartPlaylistFacts(songs: List<Song>): String {
    val minutes = songs.sumOf { it.durationMs } / MS_PER_MINUTE
    return listOfNotNull(countLabel(songs.size, "song"), minutes.takeIf { it > 0 }?.let { "$it min" }).joinToString(" · ")
}

private const val COVER_WIDTH_FRACTION = 0.62f
private const val COLLAGE_ROWS = 2
private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f
private const val MS_PER_MINUTE = 60_000L
