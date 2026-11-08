package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.GlassLevel
import com.erhu.erhu.core.designsystem.theme.glassPanel
import com.erhu.erhu.core.designsystem.theme.glassPressEffect
import com.erhu.erhu.core.designsystem.theme.glassRippleConfiguration

/** Visible size of the round header button (Material icon-button container; touch target stays 48 dp). */
private val HEADER_BUTTON_SIZE = 40.dp

/** Scrim opacity behind the status bar: keeps the system icons legible over any content. */
private const val SCRIM_TOP_ALPHA = 0.92f

/** Scrim opacity around the middle of the header, behind the button. */
private const val SCRIM_MID_ALPHA = 0.6f

/** Where (0..1 of the header height) the scrim reaches [SCRIM_MID_ALPHA]. */
private const val SCRIM_MID_STOP = 0.5f

/** A collapsing title stops being announced (accessibility) past this point of its collapse. */
private const val TITLE_ANNOUNCED_UNTIL = 0.5f

/**
 * Minimal edge-to-edge header that floats over scrolling content: a round
 * navigation button with an optional page title on the existing screen background.
 * Behind it a scrim in the screen's own background colour fades from nearly
 * opaque (status bar) to fully transparent, so content scrolls underneath
 * without a hard edge.
 *
 * Owns the status-bar inset like [AppTopBar], so [AppScaffold] reports its full
 * height as top padding. Pair it with `forScrollingContent(topOverlay = true)`.
 *
 * Collapsing titles merge into the left button as the page scrolls, and are gone (no title stays
 * pinned over the content); scrolling back brings them out again. The button itself never changes:
 * same action, same touch target, same semantics.
 * - [collapsingHeader] (detail pages with a hero): [title] takes over from the hero's large title
 *   at its place and size and travels into the back button ([CollapsingHeaderState]).
 * - [collapsingTitle] (pages whose title sits here): [title] moves from its place beside the button
 *   into it as the visible list scrolls ([CollapsingTitleState]).
 * All of it is read in the draw phase: scrolling doesn't recompose the header.
 */
@Composable
@Suppress("LongMethod")
fun OverlayHeader(
    navigation: TopBarNavigation,
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsingHeader: CollapsingHeaderState? = null,
    collapsingTitle: CollapsingTitleState? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scrim = ErhuGlass.backdropColor(MaterialTheme.colorScheme.background)
    val merge = remember { TitleMerge() }
    val collapse: () -> Float = { collapsingHeader?.progress ?: collapsingTitle?.progress ?: 0f }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (collapsingHeader != null) {
                        Modifier.onSizeChanged { collapsingHeader.barHeight = it.height.toFloat() }
                    } else {
                        Modifier
                    },
                ).drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to scrim.copy(alpha = SCRIM_TOP_ALPHA),
                            SCRIM_MID_STOP to scrim.copy(alpha = SCRIM_MID_ALPHA),
                            1f to Color.Transparent,
                        ),
                    )
                }.windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        val colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                // Where a collapsing title merges (layout only: the button's action and touch target are untouched).
                modifier =
                    Modifier.onGloballyPositioned {
                        val centre = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
                        merge.button = centre
                        collapsingHeader?.button = centre
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(HEADER_BUTTON_SIZE)
                            // The glass swells a touch as it takes the title in (drawing only).
                            .graphicsLayer {
                                val pulse = mergePulse(smoothstep(collapse()))
                                scaleX = pulse
                                scaleY = pulse
                            }.glassPressEffect(interactionSource, CircleShape)
                            .glassPanel(CircleShape, level = GlassLevel.Strong, elevated = false),
                )
                CompositionLocalProvider(LocalRippleConfiguration provides glassRippleConfiguration()) {
                    when (navigation) {
                        is TopBarNavigation.TopLevel ->
                            IconButton(onClick = navigation.onOpenDrawer, colors = colors, interactionSource = interactionSource) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Open navigation drawer")
                            }
                        is TopBarNavigation.Detail ->
                            IconButton(onClick = navigation.onBack, colors = colors, interactionSource = interactionSource) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                    }
                }
            }
            if (!title.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(Spacing.md))
                // A detail page's hero title is the one announced; a merging title only while it shows.
                val announced by remember(collapsingHeader, collapsingTitle) {
                    derivedStateOf { collapsingHeader == null && (collapsingTitle?.progress ?: 0f) < TITLE_ANNOUNCED_UNTIL }
                }
                val titleModifier =
                    Modifier
                        .weight(1f)
                        .then(if (announced) Modifier else Modifier.clearAndSetSemantics {})
                        .then(
                            when {
                                collapsingHeader != null -> Modifier.collapsingBarTitle(collapsingHeader)
                                collapsingTitle != null ->
                                    Modifier.mergingTitle(
                                        collapsingTitle,
                                        merge,
                                        unclipped = !subtitle.isNullOrBlank(),
                                    )
                                else -> Modifier
                            },
                        )
                if (subtitle.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = TextStyles.screenTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = titleModifier,
                    )
                } else {
                    Column(modifier = titleModifier) {
                        Text(
                            text = title,
                            style = TextStyles.screenTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            style = TextStyles.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            actions()
        }
    }
}

/** Where the header's left button is (and where a merging title rests), in root coordinates. */
@Stable
private class TitleMerge {
    var button by mutableStateOf(Offset.Unspecified)
    var left by mutableFloatStateOf(Float.NaN)
    var top by mutableFloatStateOf(Float.NaN)
    var height by mutableFloatStateOf(0f)

    val measured: Boolean get() = button.isSpecified && !left.isNaN() && height > 0f
}

/** A title beside the button, merging into it as [state] collapses. */
private fun Modifier.mergingTitle(
    state: CollapsingTitleState,
    merge: TitleMerge,
    unclipped: Boolean = false,
): Modifier =
    onGloballyPositioned {
        val position = it.positionInRoot()
        merge.left = position.x
        merge.top = position.y
        merge.height = it.size.height.toFloat()
    }.graphicsLayer {
        // Preserve the moving two-line block outside its resting layout bounds.
        if (unclipped) compositingStrategy = CompositingStrategy.ModulateAlpha
        if (merge.measured) {
            mergeIntoButton(
                t = smoothstep(state.progress),
                button = merge.button,
                restLeft = merge.left,
                restTop = merge.top,
                height = merge.height,
            )
        }
    }
