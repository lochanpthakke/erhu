package com.erhu.erhu.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.component.BottomChrome
import com.erhu.erhu.core.designsystem.component.CapsuleSlots
import com.erhu.erhu.core.designsystem.component.LiquidCapsule
import com.erhu.erhu.core.designsystem.component.capsuleContent
import com.erhu.erhu.core.designsystem.component.capsuleDrag
import com.erhu.erhu.core.designsystem.component.capsuleEmphasis
import com.erhu.erhu.core.designsystem.component.capsuleSlot
import com.erhu.erhu.core.designsystem.component.pressScale
import com.erhu.erhu.core.designsystem.component.selectionJiggle
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.liquidGlass
import com.erhu.erhu.core.common.R as CommonR

/** Selected icon scale: a small lift, not a pop. */
private const val SELECTED_ICON_SCALE = 1.08f

/** An item's icon box: the icon plus its padding (the capsule wraps it together with the label). */
private val ICON_BOX_WIDTH = IconSize.standard + Spacing.lg * 2
private val ICON_BOX_HEIGHT = IconSize.standard + Spacing.xs * 2

internal fun getOrderedSections(
    start: LibrarySection,
    vybeEnabled: Boolean = true,
): List<LibrarySection> {
    val defaultList =
        listOf(LibrarySection.TRACKS, LibrarySection.ALBUMS, LibrarySection.LIBRARY, LibrarySection.VYBE)
            .filter { vybeEnabled || it != LibrarySection.VYBE }
    if (start !in defaultList) return defaultList
    return listOf(start) + defaultList.filter { it != start }
}

/**
 * Floating, icon-first navigation between the library sections. Placed by
 * [LibraryBottomControls] in the lowest layer of the shell's bottom chrome,
 * directly under the mini player, and shares its shape and spacing.
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
    val glass = ErhuGlass.isActive
    val shape = BottomChrome.shape()
    val vybeEnabled = LocalVybeEnabled.current
    val sections = remember(defaultStartSection, vybeEnabled) { getOrderedSections(defaultStartSection, vybeEnabled) }
    val visibleSelected = selected.visibleWithVybe(vybeEnabled)
    val selectedIndex = sections.indexOf(visibleSelected).coerceAtLeast(0)

    // Retargeting cancels the running animation and continues from the current
    // position and velocity, so rapid taps always end on the last selection.
    val indicatorPosition = remember(sections.size) { Animatable(selectedIndex.toFloat()) }
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
                .liquidGlass(shape),
        shape = shape,
        color = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (glass) 0.dp else BottomChrome.elevation,
    ) {
        val slots = remember(sections.size) { CapsuleSlots(sections.size) }
        val currentIndex by rememberUpdatedState(selectedIndex)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs)
                    // Drag the capsule to another section: one selection, on release.
                    .capsuleDrag(
                        position = indicatorPosition,
                        slots = slots,
                        current = { currentIndex },
                        enabled = !isCompact && !searching,
                        onSettle = { index -> onSelect(sections[index]) },
                    ),
        ) {
            LiquidCapsule(
                position = indicatorPosition,
                slots = slots,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.matchParentSize(),
                visibility = { indicatorAlpha * expandedAlpha(compact()) },
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = expandedAlpha(compact()) }
                        .selectableGroup(),
            ) {
                sections.forEachIndexed { index, section ->
                    LibraryNavigationItem(
                        section = section,
                        selected = section == visibleSelected && !searching,
                        index = index,
                        slots = slots,
                        emphasis = { capsuleEmphasis(indicatorPosition.value, index) * indicatorAlpha },
                        onClick = { onSelect(section) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            CompactSectionButton(
                section = visibleSelected,
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
        val sectionLabel = stringResource(section.labelRes)
        Icon(
            imageVector = if (!hasCurrentSong) Icons.Default.Shuffle else section.icon,
            contentDescription =
                if (!hasCurrentSong) {
                    stringResource(CommonR.string.library_cd_shuffle_play)
                } else {
                    stringResource(CommonR.string.library_cd_expand_section_bar, sectionLabel)
                },
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One section item: icon plus small label underneath, spaced tightly. Its highlight follows the
 * capsule's position ([emphasis], read while drawing): the accent copy fades in under the capsule
 * as it arrives, also while it is dragged, so the label never jumps ahead of the glass.
 */
@Composable
private fun LibraryNavigationItem(
    section: LibrarySection,
    selected: Boolean,
    index: Int,
    slots: CapsuleSlots,
    emphasis: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (ErhuGlass.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val sectionLabel = stringResource(section.labelRes)
    Box(
        modifier =
            modifier
                .capsuleSlot(slots, index)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    role = Role.Tab,
                ).padding(vertical = Spacing.xs)
                .clearAndSetSemantics {
                    contentDescription = sectionLabel
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .capsuleContent(slots, index)
                    // Tactile: dips under the finger, and pops once as it becomes the current section.
                    .pressScale(interactionSource)
                    .selectionJiggle(selected, identity = section, kickWhen = { it == true }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NavigationSectionIcon(section, quiet, accent, emphasis)
            Box(contentAlignment = Alignment.Center) {
                NavigationLabel(sectionLabel, quiet, Modifier.graphicsLayer { alpha = 1f - emphasis() })
                NavigationLabel(sectionLabel, accent, Modifier.graphicsLayer { alpha = emphasis() })
            }
        }
    }
}

@Composable
private fun NavigationSectionIcon(
    section: LibrarySection,
    quiet: Color,
    accent: Color,
    emphasis: () -> Float,
) {
    Box(
        modifier =
            Modifier
                .size(ICON_BOX_WIDTH, ICON_BOX_HEIGHT)
                .graphicsLayer {
                    val scale = 1f + (SELECTED_ICON_SCALE - 1f) * emphasis()
                    scaleX = scale
                    scaleY = scale
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = quiet,
            modifier = Modifier.graphicsLayer { alpha = 1f - emphasis() },
        )
        Icon(
            imageVector = section.selectedIcon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.graphicsLayer { alpha = emphasis() },
        )
    }
}

@Composable
private fun NavigationLabel(
    text: String,
    color: Color,
    modifier: Modifier,
) {
    Text(
        text = text,
        style = TextStyles.metadata,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
