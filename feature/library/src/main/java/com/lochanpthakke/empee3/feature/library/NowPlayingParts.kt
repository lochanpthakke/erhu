package com.lochanpthakke.empee3.feature.library

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val showAlbum = maxHeight >= ALBUM_LINE_MIN_HEIGHT
        val metadataHeight = if (showAlbum) METADATA_HEIGHT + ALBUM_LINE_HEIGHT else METADATA_HEIGHT
        val artSide =
            minOf(maxWidth * CARD_WIDTH_FRACTION - CARD_PADDING * 2, maxHeight - metadataHeight - CARD_PADDING * 2)
                .coerceAtLeast(MIN_ART_SIDE)
        val select = if (isCurrent) Modifier else Modifier.clickable(onClickLabel = "Play this track", onClick = onSelect)
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
            SongArtwork(
                song = track,
                contentDescription = if (isCurrent) "Album artwork" else "${track.title} by ${track.artist}",
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
 * commands. Below them, under shuffle and repeat, the secondary actions: add the playing song to a
 * playlist and the queue. Both rows share one grid of five equal columns, so every control is
 * evenly spaced and the layout is symmetrical around play / pause.
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
            // The middle three columns stay empty: the actions sit under shuffle and repeat.
            Slot {}
            Slot {}
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
