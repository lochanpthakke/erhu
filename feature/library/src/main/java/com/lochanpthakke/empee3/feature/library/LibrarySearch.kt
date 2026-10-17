package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.PressIconButton
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Artist
import com.lochanpthakke.empee3.core.model.Song

/** Height of the floating search field (pill) and circular back bubble. */
private val SEARCH_FIELD_HEIGHT = 52.dp

/** Width of the album / artist cards in the horizontal result rows. */
private val RESULT_TILE_WIDTH = 132.dp

/**
 * Library search header: a circular glass back button bubble on the left followed by
 * a floating search pill (no internal search icon, left-aligned text).
 */
@Composable
@Suppress("LongMethod")
internal fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Circular Glass Bubble for Back Button
        Box(
            modifier =
                Modifier
                    .size(SEARCH_FIELD_HEIGHT)
                    .glassPanel(Radius.pillShape, level = GlassLevel.Strong, elevated = false, outlined = false)
                    .then(
                        if (EmPee3Glass.isActive) {
                            Modifier
                        } else {
                            Modifier.glassFallback()
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            PressIconButton(onClick = onClose) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        }

        Spacer(modifier = Modifier.width(Spacing.xs))

        // Search Pill (Left aligned, no inner search icon)
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = SEARCH_FIELD_HEIGHT)
                    .glassPanel(Radius.pillShape, level = GlassLevel.Strong, elevated = false, outlined = false)
                    .then(
                        if (EmPee3Glass.isActive) {
                            Modifier
                        } else {
                            Modifier.glassFallback()
                        },
                    )
                    .padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Songs, Artists, Albums or Playlists",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )
            }
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(Motion.fade()) + scaleIn(Motion.press()),
                exit = fadeOut(Motion.fade(Motion.MICRO)) + scaleOut(Motion.fade(Motion.MICRO)),
            ) {
                PressIconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

/** Without the Glass Effect the search surfaces are a plain theme surface. */
@Composable
private fun Modifier.glassFallback(): Modifier = background(MaterialTheme.colorScheme.surfaceVariant, Radius.pillShape)

/** The three things search can find, for its results and its starting screen. */
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun SearchContent(
    query: String,
    songs: List<Song>,
    artists: List<Artist>,
    albums: List<Album>,
    artistArtwork: Map<Long, String>,
    currentSongId: Long?,
    recentSearchItems: List<RecentSearchItem> = emptyList(),
    onRemoveRecentItem: (RecentSearchItem) -> Unit = {},
    onClearRecentItems: () -> Unit = {},
    onSongClick: (Int) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Long) -> Unit = {},
    actions: SongActions,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val state =
        when {
            query.isBlank() && recentSearchItems.isNotEmpty() -> SearchState.RECENT
            query.isBlank() -> SearchState.START
            songs.isEmpty() && artists.isEmpty() && albums.isEmpty() -> SearchState.NOTHING
            else -> SearchState.RESULTS
        }

    AnimatedContent(targetState = state, transitionSpec = { Motion.crossfade() }, label = "searchState", modifier = modifier) {
        when (it) {
            SearchState.START -> SearchStart(modifier = Modifier.padding(top = topInset, bottom = bottomInset))
            SearchState.RECENT ->
                RecentSearchesContent(
                    recentSearchItems = recentSearchItems,
                    artistArtwork = artistArtwork,
                    currentSongId = currentSongId,
                    onSongClick = { song ->
                        val idx = songs.indexOfFirst { s -> s.id == song.id }
                        if (idx != -1) onSongClick(idx) else onSongClick(0)
                    },
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onRemoveItem = onRemoveRecentItem,
                    onClearAll = onClearRecentItems,
                    actions = actions,
                    modifier = Modifier.padding(top = topInset, bottom = bottomInset),
                )
            SearchState.NOTHING ->
                SearchMessage(
                    title = "No matches",
                    message = "Nothing in your library matches \"$query\".",
                    modifier = Modifier.padding(top = topInset, bottom = bottomInset),
                )
            SearchState.RESULTS ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Spacing.sm + topInset, bottom = Spacing.lg + bottomInset),
                ) {
                    if (artists.isNotEmpty()) {
                        item(key = "artists") {
                            ResultRow(title = "Artists") {
                                items(artists, key = { a -> a.id }) { artist ->
                                    ArtistTile(
                                        artist = artist,
                                        artworkUri = artistArtwork[artist.id],
                                        onClick = { onArtistClick(artist) },
                                        modifier = Modifier.width(RESULT_TILE_WIDTH),
                                    )
                                }
                            }
                        }
                    }
                    if (albums.isNotEmpty()) {
                        item(key = "albums") {
                            ResultRow(title = "Albums") {
                                items(albums, key = { a -> a.id }) { album ->
                                    AlbumTile(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                        modifier = Modifier.width(RESULT_TILE_WIDTH),
                                    )
                                }
                            }
                        }
                    }
                    if (songs.isNotEmpty()) {
                        item(key = "songsHeader") { SectionTitle("Songs") }
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            LibrarySongRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                onClick = { onSongClick(index) },
                                actions = actions,
                            )
                        }
                    }
                }
        }
    }
}

private enum class SearchState { START, RECENT, NOTHING, RESULTS }

@Composable
private fun ResultRow(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyles.sectionTitle,
        modifier =
            Modifier
                .padding(horizontal = Spacing.screenGutter, vertical = Spacing.sm)
                .semantics { heading() },
    )
}

/** Fresh search starting screen: centered search icon and description text. */
@Composable
private fun SearchStart(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(92.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Find any song, artist or album on this device",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Recently searched rich items list view when history exists. */
@Composable
private fun RecentSearchesContent(
    recentSearchItems: List<RecentSearchItem>,
    artistArtwork: Map<Long, String>,
    currentSongId: Long?,
    onSongClick: (Song) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onRemoveItem: (RecentSearchItem) -> Unit,
    onClearAll: () -> Unit,
    actions: SongActions,
    modifier: Modifier = Modifier,
) {
    val recentCollections =
        remember(recentSearchItems) {
            recentSearchItems.filter {
                it is RecentSearchItem.ArtistItem || it is RecentSearchItem.AlbumItem || it is RecentSearchItem.PlaylistItem
            }
        }
    val recentSongs =
        remember(recentSearchItems) {
            recentSearchItems.filterIsInstance<RecentSearchItem.SongItem>()
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.lg),
    ) {
        item(key = "header") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recently Searched",
                    style = TextStyles.sectionTitle,
                    modifier = Modifier.semantics { heading() },
                )
                TextButton(onClick = onClearAll) {
                    Text("Clear All", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (recentCollections.isNotEmpty()) {
            item(key = "collectionsRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screenGutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(recentCollections, key = { it.key }) { item ->
                        Box(modifier = Modifier.width(RESULT_TILE_WIDTH)) {
                            when (item) {
                                is RecentSearchItem.ArtistItem -> {
                                    ArtistTile(
                                        artist = item.artist,
                                        artworkUri = artistArtwork[item.artist.id],
                                        onClick = { onArtistClick(item.artist) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                is RecentSearchItem.AlbumItem -> {
                                    AlbumTile(
                                        album = item.album,
                                        onClick = { onAlbumClick(item.album) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                is RecentSearchItem.PlaylistItem -> {
                                    PlaylistTile(
                                        title = item.title,
                                        trackCount = item.trackCount,
                                        onClick = { onPlaylistClick(item.playlistId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                is RecentSearchItem.SongItem -> {}
                            }
                            IconButton(
                                onClick = { onRemoveItem(item) },
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                        .padding(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove item",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
            }
        }

        if (recentSongs.isNotEmpty()) {
            item(key = "songsHeader") {
                SectionTitle("Songs")
            }
            items(recentSongs, key = { it.key }) { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = Spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        LibrarySongRow(
                            song = item.song,
                            isCurrent = item.song.id == currentSongId,
                            onClick = { onSongClick(item.song) },
                            actions = actions,
                        )
                    }
                    IconButton(
                        onClick = { onRemoveItem(item) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove song",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    title: String,
    trackCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(RESULT_TILE_WIDTH)
                .clickable { onClick() },
    ) {
        Box(
            modifier =
                Modifier
                    .size(RESULT_TILE_WIDTH)
                    .clip(Radius.mediumShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "$trackCount tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = TextStyles.supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
