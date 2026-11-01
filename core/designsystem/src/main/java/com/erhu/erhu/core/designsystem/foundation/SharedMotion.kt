package com.erhu.erhu.core.designsystem.foundation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Shared-element motion: the same piece of artwork moving between two places
 * (mini player -> Now Playing, album grid -> album page). The shell provides the
 * [SharedTransitionScope]; every animated container (a navigation destination, the
 * mini player's visibility) provides its [AnimatedVisibilityScope]. Without both,
 * [sharedArtwork] does nothing, so components work anywhere.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalMotionScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Provides the animated container [scope] to shared elements inside [content]. */
@Composable
fun ProvideMotionScope(
    scope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMotionScope provides scope, content = content)
}

/** How shared artwork travels: the standard decelerating movement. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val ArtworkBounds = BoundsTransform { _, _ -> tween(Motion.HERO, easing = Motion.Decelerate) }

/**
 * Marks this artwork as the shared element [key]: when one screen with the key
 * leaves and another with the same key arrives, the artwork moves and resizes
 * between the two positions instead of disappearing and reappearing.
 *
 * [renderInOverlay] false keeps the artwork in its own place in the layout while it
 * travels, so content drawn over it (a title on a cover) stays on top throughout.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(
    key: Any,
    renderInOverlay: Boolean = true,
): Modifier {
    val shared = LocalSharedTransitionScope.current
    val scope = LocalMotionScope.current
    if (shared == null || scope == null) return this
    return with(shared) {
        this@sharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            boundsTransform = ArtworkBounds,
            renderInOverlayDuringTransition = renderInOverlay,
        )
    }
}
