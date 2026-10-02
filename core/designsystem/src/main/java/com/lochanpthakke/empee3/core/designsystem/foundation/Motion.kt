package com.lochanpthakke.empee3.core.designsystem.foundation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * The app's motion system: every animation uses these durations, easings,
 * springs and transitions, so motion feels like one language.
 *
 * Principles: short, interruptible, never blocking input. Springs for things
 * the user moves or that physically move (indicators, presses, chrome);
 * tweens for appearance changes (fades, content swaps, screen changes).
 */
object Motion {
    /** Durations in ms. */
    const val DURATION_QUICK = 120
    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 300
    const val DURATION_LONG = 380

    /** Standard: things that move on screen. */
    val Standard: Easing = FastOutSlowInEasing

    /** Entering: arrives fast, settles gently. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaving: starts gently, leaves fast. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Presses and toggles: quick, with a hint of overshoot. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 700f)

    /** Selection and layout movement (navigation indicator). */
    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 420f)

    /** Large surfaces settling (collapsing chrome): no overshoot. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 260f)

    fun <T> fade(
        durationMillis: Int = DURATION_SHORT,
        delayMillis: Int = 0,
    ): TweenSpec<T> = tween(durationMillis, delayMillis, Standard)

    // ---- content changes (AnimatedContent)

    /** One icon replacing another (play/pause, favorite, repeat mode): scale + fade. */
    fun iconSwap(): ContentTransform =
        (fadeIn(fade(DURATION_SHORT)) + scaleIn(snappy(), initialScale = ICON_SWAP_SCALE)) togetherWith
            (fadeOut(fade(DURATION_QUICK)) + scaleOut(fade(DURATION_QUICK), targetScale = ICON_SWAP_SCALE))

    /**
     * Content that moves through a sequence (the current track): the new item
     * enters from the side it comes from and the old one leaves the other way.
     * [direction] is +1 for forward (next), -1 for backward (previous).
     */
    fun directional(direction: Int): ContentTransform =
        ContentTransform(
            targetContentEnter =
                slideInHorizontally(tween(DURATION_MEDIUM, easing = Decelerate)) { width -> direction * width / SLIDE_FRACTION } +
                    fadeIn(fade(DURATION_MEDIUM)),
            initialContentExit =
                slideOutHorizontally(tween(DURATION_SHORT, easing = Accelerate)) { width -> -direction * width / SLIDE_FRACTION } +
                    fadeOut(fade(DURATION_SHORT)),
            sizeTransform = SizeTransform(clip = false),
        )

    /** Two states of the same place (empty state ↔ results): a quick crossfade. */
    fun crossfade(): ContentTransform =
        fadeIn(fade(DURATION_SHORT, delayMillis = DURATION_QUICK / 2)) togetherWith fadeOut(fade(DURATION_QUICK))

    // ---- navigation

    /** A detail screen pushed on top (album, playlist, settings page): from the end edge. */
    val detailEnter: EnterTransition =
        slideInHorizontally(tween(DURATION_MEDIUM, easing = Decelerate)) { it / DETAIL_SLIDE_FRACTION } + fadeIn(fade(DURATION_MEDIUM))
    val detailExit: ExitTransition =
        slideOutHorizontally(tween(DURATION_MEDIUM, easing = Accelerate)) { -it / BEHIND_SLIDE_FRACTION } + fadeOut(fade(DURATION_SHORT))
    val detailPopEnter: EnterTransition =
        slideInHorizontally(tween(DURATION_MEDIUM, easing = Decelerate)) { -it / BEHIND_SLIDE_FRACTION } + fadeIn(fade(DURATION_MEDIUM))
    val detailPopExit: ExitTransition =
        slideOutHorizontally(tween(DURATION_MEDIUM, easing = Accelerate)) { it / DETAIL_SLIDE_FRACTION } + fadeOut(fade(DURATION_SHORT))

    /** A full-screen sheet rising from the bottom chrome (Now Playing) and sinking back. */
    val sheetEnter: EnterTransition =
        slideInVertically(tween(DURATION_LONG, easing = Decelerate)) { it / SHEET_SLIDE_FRACTION } + fadeIn(fade(DURATION_MEDIUM))
    val sheetExit: ExitTransition =
        slideOutVertically(tween(DURATION_MEDIUM, easing = Accelerate)) { it / SHEET_SLIDE_FRACTION } + fadeOut(fade(DURATION_SHORT))

    /** Switching between top-level destinations (drawer): fade through, no direction. */
    val fadeThroughEnter: EnterTransition =
        fadeIn(fade(DURATION_MEDIUM, delayMillis = DURATION_QUICK / 2)) +
            scaleIn(fade(DURATION_MEDIUM, delayMillis = DURATION_QUICK / 2), initialScale = FADE_THROUGH_SCALE)
    val fadeThroughExit: ExitTransition = fadeOut(fade(DURATION_QUICK))

    /** Stays put under something that covers it (screen under Now Playing). */
    val hold: ExitTransition = fadeOut(fade(DURATION_LONG), targetAlpha = HOLD_ALPHA)
    val unhold: EnterTransition = fadeIn(fade(DURATION_MEDIUM), initialAlpha = HOLD_ALPHA)

    private const val ICON_SWAP_SCALE = 0.6f
    private const val SLIDE_FRACTION = 4
    private const val DETAIL_SLIDE_FRACTION = 5
    private const val BEHIND_SLIDE_FRACTION = 10
    private const val SHEET_SLIDE_FRACTION = 3
    private const val FADE_THROUGH_SCALE = 0.97f
    private const val HOLD_ALPHA = 0.6f
}
