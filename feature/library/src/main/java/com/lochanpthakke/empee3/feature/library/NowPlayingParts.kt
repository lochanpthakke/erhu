package com.lochanpthakke.empee3.feature.library

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.database.repository.AnimatedArtworkRepository
import com.lochanpthakke.empee3.core.database.repository.LibraryRepository
import com.lochanpthakke.empee3.core.designsystem.artwork.SongArtwork
import com.lochanpthakke.empee3.core.designsystem.component.PressIconButton
import com.lochanpthakke.empee3.core.designsystem.component.pressScale
import com.lochanpthakke.empee3.core.designsystem.component.selectionJiggle
import com.lochanpthakke.empee3.core.designsystem.foundation.ComponentSize
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.model.PlaybackState
import com.lochanpthakke.empee3.core.model.RepeatMode
import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.feature.library.PlayerTransitionState
import com.lochanpthakke.empee3.feature.library.components.AnimatedAlbumCoverPlayer
import com.lochanpthakke.empee3.feature.library.components.CoverVideoHost
import com.lochanpthakke.empee3.feature.library.components.rememberAnimatedArtworkPath
import kotlin.math.abs

/*
 * Now Playing's building blocks: the carousel's artwork card, the title, the favourite button and
 * the transport row.
 */

/** The current card's share of the screen width (the neighbours peek in beside it). */
private const val CARD_WIDTH_FRACTION = 0.8f

/** Inset between the card's edge and its artwork / metadata. */
private val CARD_PADDING = 10.dp

/** The card's corners: the artwork's corners plus the inset, so the two curves run parallel. */
private val CARD_SHAPE = RoundedCornerShape(Radius.large + CARD_PADDING)

/** Title and artist lines with their gaps; the album line adds its own height when shown. */
private val METADATA_HEIGHT = 62.dp
private val ALBUM_LINE_HEIGHT = 18.dp

/** Only carousels at least this tall show the album line. */
private val ALBUM_LINE_MIN_HEIGHT = 340.dp

/** Never smaller than this, even on very short screens. */
private val MIN_ART_SIDE = 140.dp

private const val ALBUM_ALPHA = 0.8f

/** How dark a neighbour card gets one place away from the centre. */
private const val SIDE_CARD_DIM = 0.45f

/** A toggle that is off: its icon dimmed. */
private const val INACTIVE_ALPHA = 0.6f

/**
 * One card of the carousel: the artwork with the track's title, artist and album beneath it, as
 * one rounded card. It takes the room it is given: the artwork is as large as the width and height
 * allow, and the album line shows only when there is height for it.
 *
 * The complete current card travels as one composition and carries the favourite button;
 * neighbours are dimmed with their distance and select their track when tapped.
 */
@Composable
@Suppress("LongParameterList")
internal fun CarouselCard(
    track: Song,
    isCurrent: Boolean,
    distance: () -> Float,
    transition: PlayerTransitionState?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelect: () -> Unit,
    /** Plays the current card's animated cover (when the album has one). */
    isPlaying: Boolean = false,
    libraryRepository: LibraryRepository? = null,
    animatedArtworkRepository: AnimatedArtworkRepository? = null,
    /** Now Playing's one cover video, handed from card to card (see CoverVideoHost). */
    coverVideo: CoverVideoHost? = null,
    /** False while the neighbours are stacked behind the current card: a tap there must not reach them. */
    selectable: Boolean = true,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val showAlbum = maxHeight >= ALBUM_LINE_MIN_HEIGHT
        val metadataHeight = if (showAlbum) METADATA_HEIGHT + ALBUM_LINE_HEIGHT else METADATA_HEIGHT
        val artSide =
            minOf(maxWidth * CARD_WIDTH_FRACTION - CARD_PADDING * 2, maxHeight - metadataHeight - CARD_PADDING * 2)
                .coerceAtLeast(MIN_ART_SIDE)
        val select = if (isCurrent || !selectable) Modifier else Modifier.clickable(onClickLabel = "Play this track", onClick = onSelect)
        // Each card in its own artwork's colours (cached per artwork, cross-faded on change).
        val colors = rememberLyricsColors(track)
        val inset = with(LocalDensity.current) { CARD_PADDING.toPx() }
        Column(
            modifier =
                Modifier
                    .width(artSide + CARD_PADDING * 2)
                    .onSizeChanged {
                        if (isCurrent && transition != null) {
                            transition.cardSize = Size(it.width.toFloat(), it.height.toFloat())
                            transition.cardArtworkInset = inset
                        }
                    }.then(select)
                    .carouselCardSurface(colors, CARD_SHAPE, infoTop = CARD_PADDING + artSide)
                    .clip(CARD_SHAPE)
                    .drawWithContent {
                        drawContent()
                        val dim = abs(distance()).coerceAtMost(1f) * SIDE_CARD_DIM
                        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                    }.padding(CARD_PADDING),
        ) {
            HeroAnimatedArtwork(
                song = track,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                libraryRepository = libraryRepository,
                animatedArtworkRepository = animatedArtworkRepository,
                coverVideo = coverVideo,
                modifier =
                    Modifier
                        .size(artSide)
                        .then(if (isCurrent) Modifier.playerArtworkClip(transition) else Modifier.clip(Radius.largeShape)),
            )
            LyricsSurfaceTheme(colors) {
                CardMetadata(
                    track = track,
                    showAlbum = showAlbum,
                    favorite = if (isCurrent) ({ FavoriteButton(isFavorite, track.id, onToggleFavorite) }) else null,
                    modifier =
                        Modifier.height(metadataHeight).graphicsLayer {
                            if (isCurrent && transition != null) alpha = (transition.fraction / 0.25f).coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
}

/** Title, artist and (when there is room) album under the artwork; long names end in an ellipsis. */
@Composable
private fun CardMetadata(
    track: Song,
    showAlbum: Boolean,
    favorite: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAlbum && track.album.isNotBlank()) {
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ALBUM_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        favorite?.invoke()
    }
}

@Composable
internal fun FavoriteButton(
    isFavorite: Boolean,
    songId: Long,
    onClick: () -> Unit,
) {
    PressIconButton(onClick = onClick) {
        val heartColor =
            animatedStateColor(
                if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                label = "favoriteTint",
            )
        Box(Modifier.selectionJiggle(isFavorite, identity = songId, expressive = true)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = heartColor,
            )
        }
    }
}

/**
 * Shuffle, previous, play / pause (the centrepiece), next, repeat: the player's real state and
 * commands. Below them, under shuffle, play / pause and repeat, the secondary actions: add the
 * playing song to a playlist, the album stack toggle and the queue. Both rows share one grid of
 * five equal columns, so every control is evenly spaced and the layout is symmetrical around play / pause.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun TransportControls(
    playbackState: PlaybackState,
    isPlaying: Boolean,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onRepeat: () -> Unit,
    /** Whether the neighbour cards are stacked into one behind the current card. */
    stacked: Boolean,
    onToggleStack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slot {
                ToggleIconButton(active = playbackState.shuffleModeEnabled, onClick = onShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (playbackState.shuffleModeEnabled) "Shuffle on" else "Shuffle off",
                    )
                }
            }
            Slot {
                PressIconButton(onClick = onPrevious, nudge = -1) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(IconSize.large),
                    )
                }
            }
            Slot {
                val playInteraction = rememberInteraction()
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(ComponentSize.primaryButton),
                    shape = CircleShape,
                    interactionSource = playInteraction,
                ) {
                    Box(Modifier.pressScale(playInteraction)) {
                        SwapIcon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(IconSize.hero),
                            )
                        }
                    }
                }
            }
            Slot {
                PressIconButton(onClick = onNext, enabled = hasNext(playbackState), nudge = 1) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(IconSize.large))
                }
            }
            Slot {
                ToggleIconButton(
                    active = playbackState.repeatMode != RepeatMode.OFF,
                    feedbackValue = playbackState.repeatMode,
                    onClick = onRepeat,
                ) {
                    SwapIcon(if (playbackState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat) {
                        Icon(
                            imageVector = it,
                            contentDescription =
                                when (playbackState.repeatMode) {
                                    RepeatMode.OFF -> "Repeat off"
                                    RepeatMode.ALL -> "Repeat all"
                                    RepeatMode.ONE -> "Repeat one"
                                },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slot {
                PressIconButton(onClick = onAddToPlaylist) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add to playlist")
                }
            }
            // The actions sit under shuffle, play / pause and repeat; the columns between stay empty.
            Slot {}
            Slot {
                PressIconButton(onClick = onToggleStack) {
                    SwapIcon(if (stacked) Icons.Default.ViewCarousel else Icons.Default.Layers) { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = if (stacked) "Show albums side by side" else "Stack background albums",
                        )
                    }
                }
            }
            Slot {}
            Slot {
                PressIconButton(onClick = onQueue) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                }
            }
        }
    }
}

/** One column of the controls grid: an equal share of the row, its control centred. */
@Composable
private fun RowScope.Slot(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

/** The system's "remove animations" (animator duration scale 0): the carousel then slides without turning. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
private fun ToggleIconButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    feedbackValue: Any = active,
    content: @Composable () -> Unit,
) {
    // On/off is a colour change that fades, never a jump.
    val background =
        animatedStateColor(
            if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
            label = "toggleBackground",
        )
    val foreground =
        animatedStateColor(
            if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_ALPHA),
            label = "toggleForeground",
        )
    PressIconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .selectionJiggle(feedbackValue)
                    .clip(CircleShape)
                    .background(background)
                    .padding(6.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides foreground) {
                content()
            }
        }
    }
}

private fun hasNext(state: PlaybackState): Boolean =
    state.queue.size > 1 && (state.shuffleModeEnabled || state.repeatMode == RepeatMode.ALL || state.currentIndex < state.queue.lastIndex)

/**
 * The card's artwork: on the current card, the album's animated cover when one has been fetched
 * (moving while [isPlaying]); neighbours and albums without one show the still artwork. Only the
 * current card ever has a video player.
 */
@Composable
internal fun HeroAnimatedArtwork(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    libraryRepository: LibraryRepository?,
    animatedArtworkRepository: AnimatedArtworkRepository?,
    modifier: Modifier = Modifier,
    coverVideo: CoverVideoHost? = null,
) {
    val description = if (isCurrent) "Album artwork" else "${song.title} by ${song.artist}"
    if (!isCurrent || animatedArtworkRepository == null || libraryRepository == null) {
        SongArtwork(song = song, contentDescription = description, modifier = modifier)
        return
    }
    // Remembered: the card recomposes during the player's motion; a new flow each time would resubscribe.
    val albumId by remember(libraryRepository, song.id) { libraryRepository.getAlbumIdForSong(song.id) }
        .collectAsState(initial = null)
    AnimatedAlbumCoverPlayer(
        filePath = rememberAnimatedArtworkPath(albumId, animatedArtworkRepository),
        isPlaying = isPlaying,
        modifier = modifier,
        host = coverVideo,
    ) {
        SongArtwork(song = song, contentDescription = description, modifier = Modifier.fillMaxSize())
    }
}
