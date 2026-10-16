package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.foundation.TextStyles
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Selected icon scale: a small lift, not a pop. */
private const val SELECTED_ICON_SCALE = 1.08f

/** Tonal strength of the selection surface without glass: a tint, not a filled block. */
private const val SELECTED_PILL_ALPHA = 0.16f

/**
 * Indicator size: exactly the item's icon box (icon plus its padding), a
 * horizontal pill rather than a cell-sized rounded square.
 */
private val INDICATOR_WIDTH = IconSize.standard + Spacing.lg * 2
private val INDICATOR_HEIGHT = IconSize.standard + Spacing.xs * 2

/**
 * Liquid feel while moving: the indicator stretches along its motion in
 * proportion to its speed (in sections per second) and gives back a little
 * height. At rest the velocity is zero, so it is a clean pill again.
 */
private const val STRETCH_PER_VELOCITY = 0.03f
private const val MAX_STRETCH = 0.12f
private const val SQUASH_RATIO = 0.4f

internal fun getOrderedSections(start: LibrarySection): List<LibrarySection> {
    val defaultList = listOf(LibrarySection.TRACKS, LibrarySection.ALBUMS, LibrarySection.LIBRARY)
    if (start !in defaultList) return defaultList
    return listOf(start) + defaultList.filter { it != start }
}

/**
 * Floating, icon-first navigation between the library sections. Placed by
 * [LibraryBottomControls] in the lowest layer of the shell's bottom chrome,
 * directly under the mini player, and shares its shape and material.
 */
@Composable
@Suppress("LongMethod")
fun LibraryNavigationBar(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    modifier: Modifier = Modifier,
    defaultStartSection: LibrarySection = LibrarySection.TRACKS,
    searching: Boolean = false,
    compact: () -> Float = { 0f },
    onExpand: () -> Unit = {},
    hasCurrentSong: Boolean = true,
    onShufflePlayLibrary: () -> Unit = {},
) {
    // Compact (scrolled library): the sections fade out and the current one's icon
    // remains as a single button that brings the sections back.
    val isCompact by remember { derivedStateOf { compact() > COMPACT_SWITCH } }
    // While searching no section is current: the indicator fades out (it keeps its
    // place, so it comes back where it was) and every label goes quiet.
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (searching) 0f else 1f,
        animationSpec = Motion.fade(),
        label = "libraryNavIndicatorAlpha",
    )
    val glass = EmPee3Glass.isActive
    val shape = BottomChrome.shape()
    val sections = remember(defaultStartSection) { getOrderedSections(defaultStartSection) }
    val selectedIndex = sections.indexOf(selected).coerceAtLeast(0)

    // Retargeting cancels the running animation and continues from the current
    // position and velocity, so rapid taps always end on the last selection.
    val indicatorPosition = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        indicatorPosition.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = Motion.selection(),
        )
    }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .glassPanel(shape, strong = true, elevated = false, outlined = false, optics = NAVIGATION_OPTICS, environmentLens = true),
        shape = shape,
        color = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (glass) 0.dp else BottomChrome.elevation,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
        ) {
            SelectionIndicator(
                position = indicatorPosition,
                itemCount = sections.size,
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = indicatorAlpha * expandedAlpha(compact()) },
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = expandedAlpha(compact()) }
                        .selectableGroup(),
            ) {
                sections.forEach { section ->
                    LibraryNavigationItem(
                        section = section,
                        selected = section == selected && !searching,
                        onClick = { onSelect(section) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            CompactSectionButton(
                section = selected,
                enabled = isCompact,
                onClick = if (!hasCurrentSong) onShufflePlayLibrary else onExpand,
                hasCurrentSong = hasCurrentSong,
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = compactAlpha(compact()) },
            )
        }
    }
}

/** Past this compactness the bar acts as the single "sections" button. */
private const val COMPACT_SWITCH = 0.5f

/** Sections fade out over the first half of compacting, the compact icon in over the second. */
private fun expandedAlpha(compact: Float): Float = (1f - compact * 2f).coerceIn(0f, 1f)

private fun compactAlpha(compact: Float): Float = (compact * 2f - 1f).coerceIn(0f, 1f)

/** The compact bar: the current section's icon; tapping it expands the chrome again. */
@Composable
private fun CompactSectionButton(
    section: LibrarySection,
    enabled: Boolean,
    onClick: () -> Unit,
    hasCurrentSong: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    Box(
        modifier =
            modifier.clickable(
                enabled = enabled,
                onClick = onClick,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (!hasCurrentSong) Icons.Default.Shuffle else section.icon,
            contentDescription = if (!hasCurrentSong) "Shuffle play library" else "Expand section bar (${section.label})",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The sliding selection surface: a horizontal pill around the selected icon
 * that stretches and squashes while moving. Drawn as a layout child that
 * offsets itself, so position updates avoid recomposition.
 */
@Composable
private fun SelectionIndicator(
    position: Animatable<Float, AnimationVector1D>,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val glass = EmPee3Glass.isActive
    val shape = Radius.pillShape
    val color =
        if (glass) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = SELECTED_PILL_ALPHA)
        }

    Box(
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight
                    val itemWidth = width.toFloat() / itemCount.coerceAtLeast(1)

                    val placeable =
                        measurable.measure(
                            Constraints.fixed(
                                INDICATOR_WIDTH.roundToPx(),
                                INDICATOR_HEIGHT.roundToPx(),
                            ),
                        )

                    layout(width, height) {
                        val current = position.value
                        val velocity = position.velocity

                        val center = (current + 0.5f) * itemWidth
                        val x = (center - placeable.width / 2f).roundToInt()
                        val y = Spacing.xs.roundToPx()

                        // Velocity stretch along the direction of motion.
                        val stretch = (abs(velocity) * STRETCH_PER_VELOCITY).coerceAtMost(MAX_STRETCH)
                        val scaleX = 1f + stretch
                        val scaleY = (1f - stretch * SQUASH_RATIO).coerceAtLeast(0.5f)

                        placeable.placeWithLayer(x, y) {
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                        }
                    }
                }.glassPanel(
                    shape = shape,
                    strong = false,
                    elevated = false,
                    outlined = false,
                    bar = true,
                ).background(color, shape),
    )
}

/** One section item: icon plus small label underneath, spaced tightly. */
@Composable
private fun LibraryNavigationItem(
    section: LibrarySection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.fade(),
        label = "libraryNavItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) SELECTED_ICON_SCALE else 1f,
        animationSpec = Motion.fade(),
        label = "libraryNavItemScale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    role = Role.Tab,
                ).padding(vertical = Spacing.xs)
                .clearAndSetSemantics {
                    contentDescription = section.label
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(INDICATOR_WIDTH, INDICATOR_HEIGHT)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) section.selectedIcon else section.icon,
                contentDescription = null,
                tint = iconColor,
            )
        }
        Text(
            text = section.label,
            style = TextStyles.metadata,
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Environment glass of the section bar: the mini player's material, thinner. */
private const val NAVIGATION_OPTICS = 0.62f
