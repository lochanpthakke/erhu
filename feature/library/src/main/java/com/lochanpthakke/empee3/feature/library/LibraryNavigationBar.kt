package com.lochanpthakke.empee3.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.BottomChrome
import com.lochanpthakke.empee3.core.designsystem.foundation.IconSize
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tonal strength of the selection surface without glass: a tint, not a filled block. */
private const val SELECTED_PILL_ALPHA = 0.16f

/**
 * Indicator size: exactly the item's icon box (icon plus its padding), a
 * horizontal pill rather than a cell-sized rounded square.
 */
private val INDICATOR_WIDTH = IconSize.standard + Spacing.lg * 2
private val INDICATOR_HEIGHT = IconSize.standard + Spacing.xs * 2

/** Indicator spring: quick and settled, with only a hint of overshoot. */
private const val INDICATOR_DAMPING = 0.8f
private const val INDICATOR_STIFFNESS = 420f

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
fun LibraryNavigationBar(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            animationSpec = spring(dampingRatio = INDICATOR_DAMPING, stiffness = INDICATOR_STIFFNESS),
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
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
            ) {
                sections.forEach { section ->
                    LibraryNavigationItem(
                        section = section,
                        selected = section == selected,
                        onClick = { onSelect(section) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
        label = "libraryNavContent",
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
                modifier = Modifier.size(IconSize.standard),
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
