package com.lochanpthakke.empee3.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lochanpthakke.empee3.core.designsystem.artwork.AlbumArtwork
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.EmptyState
import com.lochanpthakke.empee3.core.designsystem.component.OverlayHeader
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.foundation.sharedArtwork
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.LocalSetFocusedArtworkUri
import com.lochanpthakke.empee3.core.model.Album
import com.lochanpthakke.empee3.core.model.Song
import kotlinx.coroutines.flow.combine

/** The hero artwork moves at this share of the scroll speed (parallax) as the page scrolls. */
private const val HERO_PARALLAX = 0.5f

/** How much the hero artwork fades by the time it has scrolled away. */
private const val HERO_FADE = 0.6f

/** The artwork fades into the page from here down, so the title reads over its lower part (any cover). */
private const val HERO_SCRIM_START = 0.35f
private const val HERO_SCRIM_STEPS = 12

/** Smoothstep (3t² − 2t³): eases the fade at both ends. */
private const val SMOOTHSTEP_A = 3f
private const val SMOOTHSTEP_B = 2f

/** Scrolled past this share of the hero, the album title moves into the floating header. */
private const val COMPACT_HEADER_AT = 0.75f

/** Width of the centered Play / Shuffle pair as a share of the screen (as on the artist page). */
private const val ACTION_PAIR_WIDTH_FRACTION = 0.83f

/** The title over the artwork fades out over this much scrolling. */
private val TITLE_FADE_DISTANCE = 160.dp

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** What the album page shows: [album] is null once the album has left the library. */
private class AlbumDetailData(
    val album: Album?,
    val songs: List<Song>,
    /** False while only the tapped album is known (its tracks are still being read). */
    val loaded: Boolean = true,
)

/**
 * An album's page, artwork first: the cover fills the top edge to edge with the
 * title, artist and facts over its lower part, then Shuffle / Play, then the
 * album's tracks in album order. As the page scrolls the cover drifts and fades
 * and the title moves into the floating header. Data comes
 * from the library repository through [viewModel] and updates live, so an album
 * removed by a rescan turns into an empty state instead of failing.
 */
@Composable
@Suppress("LongMethod")
fun AlbumDetailScreen(
    albumId: Long,
    viewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = rememberAlbumDetail(albumId, viewModel)
    // Only the playing song's id: position ticks must not recompose this page.
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

    val listState = rememberLazyListState()
    val compactHeader by remember(listState) {
        derivedStateOf {
            val hero = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == HERO_KEY }
            hero == null || listState.firstVisibleItemScrollOffset > hero.size * COMPACT_HEADER_AT
        }
    }
    AppScaffold(
        // Floating glass back button over the cover; once the cover has scrolled away
        // the album title joins it as a compact header.
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.Detail(onBack),
                title = detail?.album?.title?.takeIf { compactHeader },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        val data = detail
        val album = data?.album
        when {
            // Loading: Room answers within a frame or two; show nothing rather than a flash.
            data == null -> Unit
            album == null ->
                EmptyState(
                    message = "This album is no longer in your library.",
                    icon = LibrarySection.ALBUMS.icon,
                    modifier = Modifier.padding(innerPadding),
                )
            else -> {
                val songs =
                    remember(data.songs) {
                        data.songs.sortedWith(
                            compareBy<Song> { it.discNumber }
                                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                        )
                    }

                val setFocusedArtwork = LocalSetFocusedArtworkUri.current
                val albumArtUri = album.artworkUri ?: songs.firstOrNull()?.artworkUri ?: songs.firstOrNull()?.uri
                LaunchedEffect(albumArtUri) {
                    // At once (the tapped album's cover is known): an artwork-based theme re-colours
                    // the app as the page arrives, not as a separate step after it.
                    setFocusedArtwork(albumArtUri)
                }
                DisposableEffect(Unit) {
                    onDispose {
                        setFocusedArtwork(null)
                    }
                }
                val scroll = innerPadding.forScrollingContent(topOverlay = true)
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scroll.container),
                    // The cover starts at the very top, under the status bar and the floating header.
                    contentPadding = PaddingValues(bottom = Spacing.lg + scroll.contentBottom),
                ) {
                    item(key = HERO_KEY) { AlbumHero(album = album, songs = songs.takeIf { data.loaded }, listState = listState) }
                    item(key = "actions") {
                        AlbumActions(
                            // Enabled while the tracks load too (no flicker); an empty album disables them.
                            enabled = !data.loaded || songs.isNotEmpty(),
                            onPlay = { viewModel.playInOrder(songs) },
                            onShuffle = { viewModel.playShuffled(songs) },
                        )
                    }
                    if (songs.isEmpty() && data.loaded) {
                        item(key = "empty") {
                            EmptyState(message = "This album has no tracks.", modifier = Modifier.padding(top = Spacing.xl))
                        }
                    } else {
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            LibrarySongRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                onClick = { viewModel.playSongsFromQueue(songs, index) },
                                actions = actions,
                                showTrackNumber = true,
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
            onSaveTags = { title, artist, albumName, year, genre ->
                viewModel.writeTags(song, title, artist, albumName, year, genre)
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

/**
 * The album page's data. The album that was tapped is already known: the page enters
 * complete (cover, title, artist, buttons) on its first frame, and Room then keeps it
 * current and adds the tracks. Null only when nothing is known yet.
 */
@Composable
private fun rememberAlbumDetail(
    albumId: Long,
    viewModel: LibraryViewModel,
): AlbumDetailData? {
    val opening =
        remember(albumId) {
            viewModel.openingAlbum(albumId)?.let { (album, songs) ->
                AlbumDetailData(album, songs.orEmpty(), loaded = songs != null)
            }
        }
    val loaded by remember(albumId) {
        combine(viewModel.observeAlbum(albumId), viewModel.observeAlbumSongs(albumId)) { album, songs -> AlbumDetailData(album, songs) }
    }.collectAsState(initial = null)
    return loaded ?: opening
}

/**
 * The cover, edge to edge, fading into the page at the bottom where the title,
 * artist and facts sit. While the page scrolls the cover moves at half speed and
 * fades, and the title fades out (read in the draw phase: no recomposition).
 */
@Composable
private fun AlbumHero(
    album: Album,
    songs: List<Song>?,
    listState: LazyListState,
) {
    // The opaque colour the track list sits on (the theme background is translucent under glass).
    val page = EmPee3Glass.backdropColor(MaterialTheme.colorScheme.background).copy(alpha = 1f)
    val scrim =
        remember(page) {
            List(HERO_SCRIM_STEPS + 1) { i ->
                val t = i / HERO_SCRIM_STEPS.toFloat()
                page.copy(alpha = t * t * (SMOOTHSTEP_A - SMOOTHSTEP_B * t))
            }
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds(),
    ) {
        AlbumArtwork(
            artworkUri = album.artworkUri,
            contentDescription = "Album artwork",
            modifier =
                Modifier
                    // In place, not in the transition overlay: the title, artist and fade drawn
                    // over the cover stay visible while it travels in from the grid.
                    .sharedArtwork(albumArtworkKey(album.id), renderInOverlay = false)
                    .fillMaxSize()
                    .graphicsLayer {
                        val scrolled = heroScroll(listState, size.height)
                        translationY = scrolled * HERO_PARALLAX
                        alpha = 1f - HERO_FADE * (scrolled / size.height).coerceIn(0f, 1f)
                    },
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Eased in many small steps: the cover flows into the page with no visible boundary.
                    .drawBehind {
                        drawRect(Brush.verticalGradient(scrim, startY = size.height * HERO_SCRIM_START, endY = size.height))
                    },
        )
        AlbumTitleBlock(
            album = album,
            songs = songs,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val scrolled = heroScroll(listState, Float.MAX_VALUE)
                        alpha = 1f - (scrolled / TITLE_FADE_DISTANCE.toPx()).coerceIn(0f, 1f)
                    },
        )
    }
}

/** Title, artist and facts, centered over the lower part of the cover. */
@Composable
private fun AlbumTitleBlock(
    album: Album,
    songs: List<Song>?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.heroGutter)
                .padding(bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = album.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        val factsText = songs?.let(::albumFacts).orEmpty()
        val isLossless = songs?.any { it.isLossless } == true
        val isDolbyAtmos = songs?.any { it.isDolbyAtmos } == true

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isLossless && factsText.isNotBlank()) "$factsText · Lossless" else factsText,
                style = TextStyles.metadata,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isDolbyAtmos) {
                Spacer(modifier = Modifier.width(Spacing.xs))
                DolbyAtmosBadge()
            }
        }
    }
}

@Composable
private fun DolbyAtmosBadge() {
    Surface(
        shape = Radius.pillShape,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Atmos",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** How far the hero has scrolled up, px ([whenGone] once it is off screen). */
private fun heroScroll(
    state: LazyListState,
    whenGone: Float,
): Float = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else whenGone

/** Play and Shuffle as an equal-width pair, centered (the same pair as the artist page). */
@Composable
private fun AlbumActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
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
}

/** "12 songs · 43 min" from the album's tracks; unknown parts are left out. */
private fun albumFacts(songs: List<Song>): String {
    val totalMinutes = songs.sumOf { it.durationMs } / MS_PER_MINUTE
    val duration =
        when {
            totalMinutes <= 0 -> null
            totalMinutes < MINUTES_PER_HOUR -> "$totalMinutes min"
            else -> "${totalMinutes / MINUTES_PER_HOUR} hr ${totalMinutes % MINUTES_PER_HOUR} min"
        }
    return listOfNotNull(
        countLabel(songs.size, "song"),
        duration,
    ).joinToString(" · ")
}

private const val HERO_KEY = "hero"
