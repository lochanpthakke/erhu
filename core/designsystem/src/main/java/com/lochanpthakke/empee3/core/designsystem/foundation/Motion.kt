package com.lochanpthakke.empee3.core.designsystem.foundation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

/**
 * The app's motion system. Every animation uses these tokens, so one user action
 * has one coordinated timeline instead of many independent ones.
 *
 * Hierarchy (later levels are longer and more expressive; lower levels never
 * compete with a higher one running at the same time):
 * - Level 1 MICRO: press, toggle, selection colour, icon swap.
 * - Level 2 CONTENT: track change, search state, list state.
 * - Level 3 NAVIGATION: detail screens, settings pages.
 * - Level 4 HERO: the player (mini player <-> Now Playing, shared artwork).
 *
 * Rules:
 * - Animate drawing properties (alpha, translation, scale), not layout size.
 *   Content transitions never animate size; containers keep stable bounds.
 * - A parent's transition moves its children; children don't add their own
 *   movement on top.
 * - Springs for anything moved by the user or that physically moves (they keep
 *   velocity and are interruptible); tweens for appearance changes.
 * - All of it scales with the system animation setting ("Remove animations"
 *   makes it instant): Compose applies the platform duration scale.
 */
object Motion {
    // ---- durations (ms), one per level

    /** Level 1: presses, toggles, selection colour. */
    const val MICRO = 120

    /** Level 2: content changing in place (track, search results). */
    const val CONTENT = 240

    /** Level 3: moving between screens. */
    const val NAVIGATION = 300

    /** Level 4: the player and its shared artwork. */
    const val HERO = 380

    // ---- easing

    /** Things that move between two positions on screen. */
    val Standard: Easing = FastOutSlowInEasing

    /** Entering: arrives fast, settles gently. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaving: starts gently, leaves fast. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // ---- springs

    /** Level 1 press / toggle response: quick, a hint of overshoot. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 700f)

    /** Selection moving between places (navigation indicator): settled, barely overshoots. */
    fun <T> selection(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 420f)

    /** Large surfaces settling without a gesture (collapsing chrome): no overshoot. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 260f)

    /**
     * A released drag finishing its journey: starts from the finger's velocity,
     * no bounce, quick enough that the release feels like a throw.
     */
    fun <T> gestureSettle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 380f)

    /**
     * Level 4: the player moving between collapsed and expanded on its own (tap,
     * Back). Heavy enough to feel physical, critically damped (no bounce).
     */
    fun <T> hero(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 220f)

    /**
     * Now Playing's track strip settling on a page (after a swipe, a skip button or
     * the end of a song): short and critically damped, so the new track is
     * established almost at once and a swipe's velocity carries straight into it.
     */
    fun <T> trackSettle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 650f)

    /** Appearance change (fade, colour) for a level's duration. */
    fun <T> fade(
        durationMillis: Int = MICRO,
        delayMillis: Int = 0,
    ): TweenSpec<T> = tween(durationMillis, delayMillis, Standard)

    // ---- level 1: in-place changes

    /** One icon replacing another (play/pause, favorite, repeat mode). */
    fun iconSwap(): ContentTransform =
        ContentTransform(
            targetContentEnter = fadeIn(fade(MICRO)) + scaleIn(press(), initialScale = ICON_SWAP_SCALE),
            initialContentExit = fadeOut(fade(MICRO)) + scaleOut(fade(MICRO), targetScale = ICON_SWAP_SCALE),
            sizeTransform = null,
        )

    // ---- level 2: content

    /**
     * The current track changing: the new content enters from the side it comes
     * from while the old one leaves the other way, on one timeline. [direction]
     * is +1 for next, -1 for previous. Size never animates (no relayout per frame).
     */
    fun directional(direction: Int): ContentTransform =
        ContentTransform(
            targetContentEnter =
                slideInHorizontally(tween(CONTENT, easing = Decelerate)) { width -> direction * width / SLIDE_FRACTION } +
                    fadeIn(fade(CONTENT)),
            initialContentExit =
                slideOutHorizontally(tween(CONTENT, easing = Accelerate)) { width -> -direction * width / SLIDE_FRACTION } +
                    fadeOut(fade(MICRO)),
            sizeTransform = null,
        )

    /** Two states of the same place (empty state <-> results): a quick crossfade. */
    fun crossfade(): ContentTransform =
        ContentTransform(
            targetContentEnter = fadeIn(fade(CONTENT, delayMillis = MICRO / 2)),
            initialContentExit = fadeOut(fade(MICRO)),
            sizeTransform = null,
        )

    // ---- level 3: navigation (the whole screen moves; its contents stay still)

    /** A detail screen pushed on top (album, artist, playlist, settings page): from the end edge. */
    val detailEnter: EnterTransition =
        slideInHorizontally(tween(NAVIGATION, easing = Decelerate)) { it / DETAIL_SLIDE_FRACTION } + fadeIn(fade(NAVIGATION))
    val detailExit: ExitTransition =
        slideOutHorizontally(tween(NAVIGATION, easing = Accelerate)) { -it / BEHIND_SLIDE_FRACTION } + fadeOut(fade(CONTENT))
    val detailPopEnter: EnterTransition =
        slideInHorizontally(tween(NAVIGATION, easing = Decelerate)) { -it / BEHIND_SLIDE_FRACTION } + fadeIn(fade(NAVIGATION))
    val detailPopExit: ExitTransition =
        slideOutHorizontally(tween(NAVIGATION, easing = Accelerate)) { it / DETAIL_SLIDE_FRACTION } + fadeOut(fade(CONTENT))

    /** Switching between top-level destinations (drawer): fade through, no direction. */
    val fadeThroughEnter: EnterTransition =
        fadeIn(fade(NAVIGATION, delayMillis = MICRO / 2)) +
            scaleIn(fade(NAVIGATION, delayMillis = MICRO / 2), initialScale = FADE_THROUGH_SCALE)
    val fadeThroughExit: ExitTransition = fadeOut(fade(MICRO))

    // ---- level 4: hero (the player)

    /** The player rising from the mini player, on the same timeline as its shared artwork. */
    val sheetEnter: EnterTransition =
        slideInVertically(tween(HERO, easing = Decelerate)) { it / SHEET_SLIDE_FRACTION } + fadeIn(fade(CONTENT))
    val sheetExit: ExitTransition =
        slideOutVertically(tween(NAVIGATION, easing = Accelerate)) { it / SHEET_SLIDE_FRACTION } + fadeOut(fade(CONTENT))

    /** Chrome that appears or disappears with the player: alpha only, its space stays fixed. */
    val chromeEnter: EnterTransition = fadeIn(fade(CONTENT))
    val chromeExit: ExitTransition = fadeOut(fade(MICRO))

    private const val ICON_SWAP_SCALE = 0.6f
    private const val SLIDE_FRACTION = 4
    private const val DETAIL_SLIDE_FRACTION = 5
    private const val BEHIND_SLIDE_FRACTION = 10
    private const val SHEET_SLIDE_FRACTION = 3
    private const val FADE_THROUGH_SCALE = 0.97f
}
