package com.erhu.erhu.feature.library.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GroupDivider
import com.erhu.erhu.core.designsystem.component.GroupRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SearchField
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteRef
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.feature.library.SongRow

/**
 * One page of a remote server's library. The same screen shows the server's menu and search, a
 * list of artists, albums or playlists, and the tracks of an album or playlist; [kind] says which.
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun RemoteBrowseScreen(
    sourceId: Long,
    kind: RemotePageKind,
    itemId: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (kind: RemotePageKind, itemId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemoteBrowseViewModel = hiltViewModel(),
) {
    LaunchedEffect(sourceId, kind, itemId) { viewModel.start(sourceId, kind, itemId, title) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentSongId by viewModel.currentSongId.collectAsStateWithLifecycle()
    val libraryEntry by viewModel.libraryEntry.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val searching = kind == RemotePageKind.HOME && query.isNotBlank()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.noticeShown()
        }
    }

    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack),
                title = title.ifBlank { state.sourceName },
                collapsingTitle = collapsingTitle,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val listState = rememberLazyListState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(listState) }
        val art = { key: String? -> key?.let { RemoteRef.artworkUri(RemoteProvider.SUBSONIC, sourceId, it) } }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding = PaddingValues(top = scroll.contentTop + Spacing.sm, bottom = scroll.contentBottom + Spacing.xl),
        ) {
            if (kind == RemotePageKind.HOME) {
                item(key = "search") {
                    SearchField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.search(it)
                        },
                        placeholder = "Search this server",
                        modifier = Modifier.padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.lg),
                    )
                }
                if (!searching) item(key = "menu") { HomeMenu(onOpen) }
            }
            if (!searching && libraryEntry.available) {
                item(key = "library") {
                    LibraryRows(
                        row = libraryEntry,
                        addSummary =
                            when (kind) {
                                RemotePageKind.ARTIST -> "Show this artist's music with your other songs"
                                RemotePageKind.ALBUM -> "Show this album with your other songs"
                                RemotePageKind.PLAYLIST -> "Show this playlist's songs with your other songs"
                                RemotePageKind.FAVORITES -> "Show your favourites from this server with your other songs"
                                else -> "Show this server's music with your other songs"
                            },
                        onAdd = viewModel::addLibraryToErhu,
                        onSync = viewModel::syncLibraryNow,
                        onRemove = viewModel::removeLibraryFromErhu,
                    )
                }
            }
            if (state.loading) {
                item(key = "loading") { ProgressBar(Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg)) }
            }
            state.error?.let { error ->
                item(key = "error") { Problem(error, onRetry = { if (searching) viewModel.search(query) else viewModel.reload() }) }
            }
            if (!state.loading && state.error == null && state.isEmpty && (kind != RemotePageKind.HOME || searching)) {
                item(key = "empty") { Note(if (searching) "Nothing on this server matches." else "Nothing here yet.") }
            }

            if (state.artists.isNotEmpty()) {
                item(key = "artists-heading") { Heading("Artists", state) }
                items(state.artists, key = { "artist-${it.id}" }) { artist ->
                    BrowseRow(
                        title = artist.name,
                        subtitle = if (artist.albumCount > 0) plural(artist.albumCount, "album") else "",
                        artworkUri = art(artist.artworkKey),
                        placeholder = Icons.Default.Person,
                        shape = CircleShape,
                        onClick = { onOpen(RemotePageKind.ARTIST, artist.id, artist.name) },
                    )
                }
            }
            if (state.albums.isNotEmpty()) {
                item(key = "albums-heading") { Heading("Albums", state) }
                items(state.albums, key = { "album-${it.id}" }) { album ->
                    BrowseRow(
                        title = album.name,
                        subtitle = listOfNotNull(album.artist.takeIf { it.isNotBlank() }, album.year.takeIf { it > 0 }?.toString()).joinToString(" • "),
                        artworkUri = art(album.artworkKey),
                        placeholder = Icons.Default.Album,
                        shape = Radius.smallShape,
                        onClick = { onOpen(RemotePageKind.ALBUM, album.id, album.name) },
                    )
                }
                if (state.canLoadMore) {
                    item(key = "more-albums") {
                        // Reaching the end of the list asks for the next page.
                        LaunchedEffect(state.albums.size) { viewModel.loadMoreAlbums() }
                        ProgressBar(Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg))
                    }
                }
            }
            if (state.playlists.isNotEmpty()) {
                items(state.playlists, key = { "playlist-${it.id}" }) { playlist ->
                    BrowseRow(
                        title = playlist.name,
                        subtitle = plural(playlist.songCount, "song"),
                        artworkUri = art(playlist.artworkKey),
                        placeholder = Icons.AutoMirrored.Filled.QueueMusic,
                        shape = Radius.smallShape,
                        onClick = { onOpen(RemotePageKind.PLAYLIST, playlist.id, playlist.name) },
                    )
                }
            }
            if (state.tracks.isNotEmpty()) {
                item(key = "songs-heading") { Heading("Songs", state) }
                // Index in the key: a playlist can hold the same song twice.
                itemsIndexed(state.tracks, key = { index, song -> "song-$index-${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.play(index) },
                        isHighlighted = song.id == currentSongId,
                        trailing = {
                            IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (song.isFavorite) "Remove from favourites" else "Add to favourites",
                                    tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun plural(
    count: Int,
    noun: String,
): String = if (count == 1) "1 $noun" else "$count ${noun}s"

/** Headings only where a page mixes kinds (search results, favourites). */
@Composable
private fun Heading(
    title: String,
    state: RemotePageState,
) {
    val kinds = listOf(state.artists.isNotEmpty(), state.albums.isNotEmpty(), state.tracks.isNotEmpty()).count { it }
    if (kinds > 1) SectionHeading(title, Modifier.padding(top = Spacing.lg))
}

@Composable
private fun HomeMenu(onOpen: (RemotePageKind, String, String) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.screenGutter).fillMaxWidth().groupSurface()) {
        val chevron = Icons.AutoMirrored.Filled.KeyboardArrowRight
        GroupRow("Artists", { onOpen(RemotePageKind.ARTISTS, "", "Artists") }, icon = Icons.Default.Person, trailing = chevron)
        GroupDivider()
        GroupRow("Albums", { onOpen(RemotePageKind.ALBUMS, "", "Albums") }, icon = Icons.Default.Album, trailing = chevron)
        GroupDivider()
        GroupRow(
            "Playlists",
            { onOpen(RemotePageKind.PLAYLISTS, "", "Playlists") },
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            trailing = chevron,
        )
        GroupDivider()
        GroupRow("Favourites", { onOpen(RemotePageKind.FAVORITES, "", "Favourites") }, icon = Icons.Default.Favorite, trailing = chevron)
    }
}

@Composable
internal fun Note(text: String) {
    Text(
        text = text,
        style = TextStyles.supporting,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
    )
}

@Composable
internal fun Problem(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = message, style = TextStyles.supporting, color = MaterialTheme.colorScheme.error)
        ActionButton(text = "Try again", onClick = onRetry)
    }
}

@Composable
internal fun BrowseRow(
    title: String,
    subtitle: String,
    artworkUri: String?,
    placeholder: ImageVector,
    shape: Shape,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteCover(artworkUri, placeholder, Modifier.size(ArtworkSize.list).clip(shape))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.itemTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = TextStyles.itemSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A cover the server holds, through the app's artwork cache; [placeholder] while loading or when there is none. */
@Composable
private fun RemoteCover(
    artworkUri: String?,
    placeholder: ImageVector,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val sizePx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val bitmap by produceState(initialValue = artworkUri?.let { ArtworkLoader.peek(it) }, artworkUri, sizePx) {
            value = artworkUri?.let { ArtworkLoader.load(context, it, it, sizePx) }
        }
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Add to Library" for the page on screen, or its Library status, sync and removal. */
@Composable
private fun LibraryRows(
    row: LibraryRowState,
    addSummary: String,
    onAdd: () -> Unit,
    onSync: (com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity) -> Unit,
    onRemove: (com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity) -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.screenGutter).padding(bottom = Spacing.md).fillMaxWidth().groupSurface()) {
        val entry = row.entry
        when {
            entry != null -> {
                val (folder, status) = entry
                GroupRow(
                    "In your Library",
                    { onSync(folder) },
                    summary = librarySummary(status),
                    icon = Icons.Default.LibraryAddCheck,
                    enabled = !status.running,
                )
                GroupDivider()
                GroupRow("Remove from Library", { onRemove(folder) }, summary = "The server is not changed", icon = Icons.Default.RemoveCircleOutline)
            }
            row.coveredByServer ->
                GroupRow(
                    "In your Library",
                    {},
                    summary = "Included: this whole server is in your Library",
                    icon = Icons.Default.LibraryAddCheck,
                    enabled = false,
                )
            else -> GroupRow("Add to Library", onAdd, summary = addSummary, icon = Icons.Default.LibraryAdd)
        }
    }
}
