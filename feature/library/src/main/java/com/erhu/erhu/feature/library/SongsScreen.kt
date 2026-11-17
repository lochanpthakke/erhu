package com.erhu.erhu.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassMenu
import com.erhu.erhu.core.designsystem.component.GlassMenuAnchor
import com.erhu.erhu.core.designsystem.component.GlassMenuItem
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.glassMenuTrigger
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.SongsMood
import com.erhu.erhu.core.model.SongsSort
import com.erhu.erhu.feature.library.mix.RadioSeed
import com.erhu.erhu.core.common.R as CommonR

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun SongsScreen(
    viewModel: SongsBrowserViewModel,
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    chromeScroll: NestedScrollConnection,
) {
    val state by viewModel.state.collectAsState()
    val currentSongId by libraryViewModel.currentSongId.collectAsState()
    val candidates by libraryViewModel.metadataCandidates.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.songs_browser_save_error)
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbar.showSnackbar(saveError)
            viewModel.consumeSaveError()
        }
    }
    DisposableEffect(viewModel) {
        viewModel.setVisible(true)
        onDispose { viewModel.setVisible(false) }
    }
    UserMessageEffect(libraryViewModel.userMessage, snackbar, libraryViewModel::consumeUserMessage)
    UserMessageEffect(playlistsViewModel.userMessage, snackbar, playlistsViewModel::consumeUserMessage)
    val closeSearch = {
        onSearchActiveChange(false)
        viewModel.setQuery("")
    }
    BackHandler(enabled = isSearchActive, onBack = closeSearch)
    var openMenu by remember { mutableStateOf<String?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForFixTags by remember { mutableStateOf<Song?>(null) }
    var songForViewMetadata by remember { mutableStateOf<Song?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    val actions =
        SongActions(
            onToggleFavorite = libraryViewModel::toggleFavorite,
            onPlayNext = libraryViewModel::playNext,
            onAddToQueue = libraryViewModel::addToQueue,
            onStartRadio = { libraryViewModel.startRadio(RadioSeed.Song(it.id)) },
            onAddToPlaylist = { songForPlaylist = it },
            onFixTags = {
                songForFixTags = it
                libraryViewModel.searchMetadataForSong(it)
            },
            onViewMetadata = { songForViewMetadata = it },
            onDeleteMusic = { songToDelete = it },
        )
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    val collapsingTitle = rememberCollapsingTitleState()
    CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) {
        ReportTitleScroll(listState)
    }
    LaunchedEffect(state.preferences, state.query) { listState.scrollToItem(0) }
    AppScaffold(
        topBar = {
            if (isSearchActive) {
                SearchHeader(state.query, viewModel::setQuery, closeSearch)
            } else {
                OverlayHeader(
                    navigation = TopBarNavigation.Detail(onBack),
                    title = stringResource(R.string.songs_browser_title),
                    collapsingTitle = collapsingTitle,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
    ) { padding ->
        val scroll = padding.forScrollingContent(topOverlay = true)
        val alphabetLabels =
            remember(state.songs, state.preferences.sort) {
                when (state.preferences.sort) {
                    SongsSort.ARTIST -> state.songs.map { it.artist }
                    SongsSort.ALBUM -> state.songs.map { it.album }
                    SongsSort.TITLE_ASC, SongsSort.TITLE_DESC -> state.songs.map { it.title }
                    else -> emptyList()
                }
            }
        val hasFilters =
            state.preferences.genre.isNotEmpty() || state.preferences.mood != SongsMood.ALL || state.query.isNotEmpty()
        LibraryFastScroll(
            labels = alphabetLabels,
            itemCount = if (state.loading || state.failed) 0 else state.songs.size,
            topInset = scroll.contentTop,
            bottomInset = scroll.contentBottom,
            onJump = { listState.requestScrollToItem(it + 1) }, // The filters occupy item zero.
            modifier = Modifier.fillMaxSize().nestedScroll(chromeScroll),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = scroll.contentTop + Spacing.sm, bottom = scroll.contentBottom + Spacing.sm),
            ) {
                item(key = "filters") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BrowserPill(
                                label = stringResource(sortLabel(state.preferences.sort)),
                                icon = Icons.AutoMirrored.Filled.Sort,
                                expanded = openMenu == "sort",
                                onOpen = { openMenu = "sort" },
                                onDismiss = { openMenu = null },
                                enabled = !state.loading && !state.failed,
                            ) {
                                SongsSort.entries.forEach { sort ->
                                    GlassMenuItem(
                                        text = { Text(stringResource(sortLabel(sort))) },
                                        icon = Icons.Default.Check.takeIf { state.preferences.sort == sort },
                                        onClick = { viewModel.setSort(sort) },
                                    )
                                }
                            }
                            val selectedGenre =
                                when {
                                    state.preferences.genre.isEmpty() -> stringResource(R.string.songs_browser_genre)
                                    state.preferences.genre == "untagged" -> stringResource(R.string.songs_browser_untagged)
                                    else -> state.preferences.genre.removePrefix("genre:")
                                }
                            BrowserPill(
                                selectedGenre,
                                Icons.Default.Tune,
                                openMenu == "genre",
                                { openMenu = "genre" },
                                { openMenu = null },
                                selected = state.preferences.genre.isNotEmpty(),
                                enabled = !state.loading && !state.failed,
                            ) {
                                val choices =
                                    listOf(
                                        "" to stringResource(R.string.songs_browser_all_genres),
                                        "untagged" to stringResource(R.string.songs_browser_untagged),
                                    ) + state.genres.map { "genre:$it" to it }
                                choices.forEach { (key, label) ->
                                    GlassMenuItem(
                                        text = { Text(label) },
                                        icon = Icons.Default.Check.takeIf { state.preferences.genre.equals(key, ignoreCase = true) },
                                        onClick = { viewModel.setGenre(key) },
                                    )
                                }
                            }
                            BrowserPill(
                                stringResource(
                                    if (state.preferences.mood ==
                                        SongsMood.ALL
                                    ) {
                                        R.string.songs_browser_mood
                                    } else {
                                        moodLabel(state.preferences.mood)
                                    },
                                ),
                                Icons.Default.AutoAwesome,
                                openMenu == "mood",
                                { openMenu = "mood" },
                                { openMenu = null },
                                selected = state.preferences.mood != SongsMood.ALL,
                                enabled = !state.loading && !state.failed,
                            ) {
                                SongsMood.entries.forEach { mood ->
                                    GlassMenuItem(
                                        text = { Text(stringResource(moodLabel(mood))) },
                                        icon = Icons.Default.Check.takeIf { state.preferences.mood == mood },
                                        onClick = { viewModel.setMood(mood) },
                                    )
                                }
                            }
                            IconButton(onClick = {
                                openMenu = null
                                onSearchActiveChange(true)
                            }) {
                                Icon(Icons.Default.Search, stringResource(R.string.songs_browser_search))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                pluralStringResource(CommonR.plurals.library_songs_count, state.songs.size, state.songs.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (hasFilters) {
                                TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.songs_browser_clear)) }
                            }
                        }
                    }
                }
                when {
                    state.loading ->
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    state.failed ->
                        item {
                            Column(Modifier.padding(20.dp)) {
                                Text(stringResource(R.string.songs_browser_error))
                                TextButton(onClick = viewModel::reload) { Text(stringResource(R.string.songs_browser_retry)) }
                            }
                        }
                    state.songs.isEmpty() ->
                        item {
                            Column(Modifier.padding(20.dp)) {
                                val emptyMessage =
                                    if (state.total == 0) R.string.songs_browser_empty else R.string.songs_browser_no_matches
                                Text(stringResource(emptyMessage))
                                if (hasFilters) {
                                    TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.songs_browser_clear)) }
                                }
                            }
                        }
                    else ->
                        itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                            LibrarySongRow(song, song.id == currentSongId, { viewModel.play(state.songs, index) }, actions)
                        }
                }
            }
        }
    }
    songForPlaylist?.let { song -> AddToPlaylistDialog(song, playlistsViewModel, { songForPlaylist = null }) }
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
        ViewMetadataScreen(song = song, viewModel = hiltViewModel(), libraryViewModel = libraryViewModel, onDismiss = {
            songForViewMetadata =
                null
        })
    }
    songToDelete?.let { song ->
        DeleteSongConfirmationDialog(song = song, onConfirm = { libraryViewModel.deleteSong(song) }, onDismiss = { songToDelete = null })
    }
}

@Composable
private fun BrowserPill(
    label: String,
    icon: ImageVector,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassMenuAnchor {
        FilterChip(
            shape = androidx.compose.foundation.shape.CircleShape,
            selected = selected,
            enabled = enabled,
            onClick = onOpen,
            label = { Text(label) },
            leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
            modifier = Modifier.glassMenuTrigger(),
        )
        GlassMenu(expanded = expanded, onDismissRequest = onDismiss, label = label) {
            Column(
                Modifier.widthIn(min = 220.dp, max = 320.dp).heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

private fun sortLabel(sort: SongsSort): Int =
    when (sort) {
        SongsSort.TITLE_ASC -> R.string.songs_browser_title_asc
        SongsSort.TITLE_DESC -> R.string.songs_browser_title_desc
        SongsSort.RECENTLY_ADDED -> R.string.songs_browser_added
        SongsSort.RECENTLY_PLAYED -> R.string.songs_browser_played
        SongsSort.ARTIST -> R.string.songs_browser_artist
        SongsSort.ALBUM -> R.string.songs_browser_album
        SongsSort.MOST_PLAYED -> R.string.songs_browser_most_played
    }

private fun moodLabel(mood: SongsMood): Int =
    when (mood) {
        SongsMood.ALL -> R.string.songs_browser_all_moods
        SongsMood.CHILL -> R.string.songs_browser_chill
        SongsMood.HIGH_ENERGY -> R.string.songs_browser_high_energy
        SongsMood.LATE_NIGHT -> R.string.songs_browser_late_night
        SongsMood.FEEL_GOOD -> R.string.songs_browser_feel_good
        SongsMood.PARTY -> R.string.songs_browser_party
        SongsMood.LOVE -> R.string.songs_browser_love
        SongsMood.SAD -> R.string.songs_browser_sad
        SongsMood.DARK -> R.string.songs_browser_dark
        SongsMood.FOCUS -> R.string.songs_browser_focus
        SongsMood.THROWBACK -> R.string.songs_browser_throwback
    }
