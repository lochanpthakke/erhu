package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
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

/**
 * Floating, icon-first navigation between the library sections. Placed by
 * [LibraryBottomControls] in the lowest layer of the shell's bottom chrome,
 * directly under the mini player, and shares its shape and material.
 *
 * Selection is shown by ONE indicator that slides between sections: a small
 * glass surface with the Glass Effect on, the theme's tinted pill otherwise.
 * The slide is the same either way. Its position is read only while laying out
 * and drawing the indicator, so the animation never recomposes the bar.
 */
@Composable
@Suppress("LongMethod")
fun LibraryNavigationBar(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    modifier: Modifier = Modifier,
    searching: Boolean = false,
    compact: () -> Float = { 0f },
    onExpand: () -> Unit = {},
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
    val sections = LibrarySection.entries
    val selectedIndex = sections.indexOf(selected)

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
                .glassPanel(shape, strong = true, elevated = false, outlined = false),
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
                count = sections.size,
                glass = glass,
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
                onClick = onExpand,
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            if (enabled) {
                modifier
                    .clickable(interactionSource = null, indication = null, onClick = onClick)
                    .semantics { contentDescription = "Show library sections" }
            } else {
                modifier.clearAndSetSemantics { }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = section.selectedIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize.standard),
        )
    }
}

/**
 * The sliding selection surface: a horizontal pill around the selected icon
 * (the icon box of [LibraryNavigationItem], so the label sits below it), centred
 * in its section cell. [position] is a fractional section index.
 */
@Composable
private fun SelectionIndicator(
    position: Animatable<Float, AnimationVector1D>,
    count: Int,
    glass: Boolean,
    modifier: Modifier = Modifier,
) {
    val material =
        if (glass) {
            // Glass on glass: no second blur, just the material's tint, light and rim.
            Modifier.glassPanel(Radius.pillShape, elevated = false, backdrop = null)
        } else {
            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_PILL_ALPHA), Radius.pillShape)
        }
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .layout { measurable, constraints ->
                        // Intrinsic passes (the bottom row sizes itself with IntrinsicSize.Min)
                        // measure with unbounded constraints: the indicator takes no space then.
                        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
                            val empty = measurable.measure(Constraints.fixed(0, 0))
                            return@layout layout(0, 0) { empty.place(0, 0) }
                        }
                        val cell = constraints.maxWidth / count
                        val width = INDICATOR_WIDTH.roundToPx().coerceAtMost(cell)
                        val indicator = measurable.measure(Constraints.fixed(width, INDICATOR_HEIGHT.roundToPx()))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            // Read during placement only: moving it never recomposes anything.
                            indicator.place(
                                x = (position.value * cell).roundToInt() + (cell - width) / 2,
                                // Same top offset as the icon box inside the item.
                                y = Spacing.xs.roundToPx(),
                            )
                        }
                    }.graphicsLayer {
                        val stretch = (abs(position.velocity) * STRETCH_PER_VELOCITY).coerceAtMost(MAX_STRETCH)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * SQUASH_RATIO
                    }.then(material),
        )
    }
}

@Composable
private fun LibraryNavigationItem(
    section: LibrarySection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.fade(),
        label = "libraryNavContent",
    )
    // The selected icon lifts very slightly as the indicator arrives.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) SELECTED_ICON_SCALE else 1f,
        animationSpec = Motion.press(),
        label = "libraryNavIcon",
    )

    // The whole column is the touch target (at least 48 dp tall). No press
    // indication: the sliding indicator is the only selection visual, so a ripple
    // would leave a second, ghost highlight behind on the tapped item.
    Column(
        modifier =
            modifier
                .selectable(
                    selected = selected,
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ).padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Same padding as before so the bar keeps its size.
        Box(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) section.selectedIcon else section.icon,
                contentDescription = null,
                tint = contentColor,
                modifier =
                    Modifier
                        .size(IconSize.standard)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
            )
        }
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
