package com.erhu.erhu.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.ListeningHistoryRepository
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.EmptyState
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.mix.MixArtwork
import com.erhu.erhu.feature.library.mix.MixKind
import com.erhu.erhu.feature.library.mix.RadioSeed
import com.erhu.erhu.feature.library.mix.SmartMix
import com.erhu.erhu.feature.library.mix.SmartMixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A smart mix card: its generated cover, title and what it holds. */
@Composable
internal fun SmartMixTile(
    mix: SmartMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        MixArtwork(
            kind = mix.kind,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Radius.mediumShape),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(text = mix.title, style = TextStyles.tileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = countLabel(mix.songIds.size, "song"),
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown on the shelf while the play history is too short for personal mixes. */
@Composable
internal fun MoreListeningNote(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(Radius.mediumShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = NOTE_FILL_ALPHA))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize.small),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "Keep listening and erhu will build mixes from your plays.",
            style = TextStyles.tileSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An open mix: the mix (null when it isn't offered right now) and its songs, in mix order. */
data class MixContent(
    val mix: SmartMix?,
    val songs: List<Song>,
)

/**
 * The open smart mix, live: it follows the cached mixes (rebuilt when listening or the library
 * changes) and the songs themselves (favourites, tag edits).
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SmartPlaylistViewModel
    @Inject
    constructor(
        mixes: SmartMixRepository,
        history: ListeningHistoryRepository,
        library: LibraryRepository,
    ) : ViewModel() {
        private val open = MutableStateFlow<String?>(null)

        /** Null until the open mix has loaded. */
        val content: StateFlow<MixContent?> =
            open
                .filterNotNull()
                .flatMapLatest { key ->
                    // Waits for the first build; then the mix, or null when it isn't offered.
                    val mix =
                        mixes.mixes
                            .filterNotNull()
                            .map { all -> all.mixes.firstOrNull { it.kind.key == key } }
                            .distinctUntilChanged()
                    combine(mix, history.observeTracks()) { m, _ -> m }
                }.mapLatest { mix -> MixContent(mix, mix?.let { library.getSongsByIds(it.songIds) }.orEmpty()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun open(mixKey: String) {
            open.value = mixKey
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/**
 * A smart mix's page, in the playlist page's layout: generated cover, title, how it was
 * made, facts, Play / Shuffle, then its songs with the usual song actions. Playback goes through
 * the library as everywhere else.
 */
@Composable
@Suppress("LongMethod")
fun SmartPlaylistDetailScreen(
    mixKey: String,
    viewModel: SmartPlaylistViewModel,
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(mixKey) { viewModel.open(mixKey) }
    val loaded by viewModel.content.collectAsState()
    val kind = MixKind.fromKey(mixKey)
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
        // Room answers within a frame or two: show nothing rather than a flash of "empty".
        val content = loaded ?: return@AppScaffold
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
                SmartMixHeader(
                    kind = kind,
                    title = content.mix?.title,
                    description = content.mix?.description,
                    songs = songs,
                    onPlay = { libraryViewModel.playInOrder(songs) },
                    onShuffle = { libraryViewModel.playShuffled(songs) },
                )
            }
            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        message = "This mix isn't available right now. It comes back as your listening and library grow.",
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

@Composable
private fun SmartMixHeader(
    kind: MixKind?,
    title: String?,
    description: String?,
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
        val cover =
            Modifier
                .widthIn(max = ArtworkSize.heroMax)
                .fillMaxWidth(COVER_WIDTH_FRACTION)
                .aspectRatio(1f)
                .clip(Radius.largeShape)
        if (kind != null) MixArtwork(kind = kind, modifier = cover) else Spacer(modifier = cover)
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(text = title ?: kind?.title ?: "Smart Playlist", style = TextStyles.pageTitle, textAlign = TextAlign.Center, maxLines = 1)
        if (description != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = description,
                style = TextStyles.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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

/** "12 songs · 43 min"; the duration is left out when unknown. */
private fun smartPlaylistFacts(songs: List<Song>): String {
    val minutes = songs.sumOf { it.durationMs } / MS_PER_MINUTE
    return listOfNotNull(countLabel(songs.size, "song"), minutes.takeIf { it > 0 }?.let { "$it min" }).joinToString(" · ")
}

private const val COVER_WIDTH_FRACTION = 0.62f
private const val NOTE_FILL_ALPHA = 0.5f
private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f
private const val MS_PER_MINUTE = 60_000L
