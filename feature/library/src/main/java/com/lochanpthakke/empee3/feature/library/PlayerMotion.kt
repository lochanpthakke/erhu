package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.model.Song

/**
 * Content that belongs to the current track (artwork, title) changes with a
 * directional slide: forward (next) enters from the end, backward (previous)
 * from the start. Shuffled queues always move forward, since their positions
 * do not say which way the user went. Only a real track change animates;
 * metadata updates of the same song do not.
 */
@Composable
fun AnimatedTrack(
    song: Song,
    index: Int,
    shuffle: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    animate: Boolean = true,
    content: @Composable (Song) -> Unit,
) {
    if (!animate) {
        Box(modifier = modifier, contentAlignment = contentAlignment) { content(song) }
        return
    }
    AnimatedContent(
        targetState = song to index,
        contentKey = { it.first.id },
        transitionSpec = {
            val forward = shuffle || targetState.second >= initialState.second
            Motion.directional(if (forward) 1 else -1)
        },
        contentAlignment = contentAlignment,
        label = "track",
        modifier = modifier,
    ) { (track, _) -> content(track) }
}

/** An icon that changes with a short scale + fade (play/pause, favorite, repeat mode). */
@Composable
fun SwapIcon(
    icon: ImageVector,
    content: @Composable (ImageVector) -> Unit,
) {
    AnimatedContent(
        targetState = icon,
        transitionSpec = { Motion.iconSwap() },
        contentAlignment = Alignment.Center,
        label = "swapIcon",
    ) { content(it) }
}

/** A colour that follows a state change with the standard fade. */
@Composable
internal fun animatedStateColor(
    target: Color,
    label: String,
): Color {
    val color by animateColorAsState(targetValue = target, animationSpec = Motion.fade(), label = label)
    return color
}

/** A remembered interaction source, for controls that drive press feedback. */
@Composable
internal fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Shared-element key of the playing song's artwork (mini player <-> Now Playing). */
fun nowPlayingArtworkKey(songId: Long): String = "nowPlayingArtwork-$songId"

/** Shared-element key of an album's cover (album grid / search <-> album page). */
fun albumArtworkKey(albumId: Long): String = "albumArtwork-$albumId"
