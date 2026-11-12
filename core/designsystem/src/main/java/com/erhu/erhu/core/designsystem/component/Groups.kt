package com.erhu.erhu.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.ErhuGlass

/*
 * The app's standard grouped surfaces and controls, shared by Settings and the utility screens
 * (Artwork Fetcher...), so they read as one design: tinted group cards with a hairline edge (no
 * shadows), accent section headings, round icon badges, a capsule segmented selector (the
 * navigation bar's liquid capsule), a filled search field, one height for buttons, and a rounded
 * progress bar. Only theme roles are used: every colour follows the active theme.
 */

/** Glass off: a group is a light step above the background in the theme's own surface variant. */
private const val GROUP_FILL_ALPHA = 0.55f

/** The hairline that defines a group's edge against the background (instead of a shadow). */
private const val GROUP_EDGE_ALPHA = 0.14f
internal val GROUP_EDGE_WIDTH = 0.5.dp

/** Round tinted badge behind a leading icon, in the theme accent. */
private val ICON_BADGE_SIZE = 36.dp
private const val ICON_BADGE_ALPHA = 0.16f

/** Buttons, the search field and the segmented selector share one height (above the 48 dp target). */
val ControlHeight = 48.dp

/** A progress bar's track: the accent, faint. */
private const val PROGRESS_TRACK_ALPHA = 0.16f
private val PROGRESS_HEIGHT = 6.dp

/** Margin between a segmented selector's edge and its capsule. */
private val SEGMENT_INSET = 3.dp

/**
 * Fill of a group surface. Glass on: the glass chrome tint (no blur, no extra layer), so the themed
 * background continues underneath. Glass off: the theme's surface variant, softened.
 */
@Composable
@ReadOnlyComposable
fun groupFillColor(): Color = ErhuGlass.chromeColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = GROUP_FILL_ALPHA))

/** A group's surface: tinted fill, clipped to [shape], defined by a hairline edge rather than a shadow. */
@Composable
fun Modifier.groupSurface(shape: Shape = MaterialTheme.shapes.medium): Modifier {
    val edge = MaterialTheme.colorScheme.outline.copy(alpha = GROUP_EDGE_ALPHA)
    return clip(shape)
        .background(groupFillColor())
        .border(BorderStroke(GROUP_EDGE_WIDTH, edge), shape)
}

/** A section's small accent heading (with an optional quiet [trailing] note, e.g. a count). */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A leading icon: the accent on a soft round badge of the same accent. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(ICON_BADGE_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = ICON_BADGE_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize.small),
        )
    }
}

/**
 * A choice between a few [options] (tabs, a setting's values): a pill on the group surface with the
 * navigation bar's liquid capsule behind the selected one. Tap an option or drag the capsule to it;
 * [onSelect] is called once per change. Labels highlight as the capsule reaches them. A
 * [selectedIndex] of -1 (the current value is none of the options) shows no capsule.
 */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) position.animateTo(selectedIndex.toFloat(), Motion.selection()) }
    val slots = remember(options.size) { CapsuleSlots(options.size) }
    val current by rememberUpdatedState(selectedIndex)
    val shown = { if (current >= 0) 1f else 0f }
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .groupSurface(CircleShape)
                .padding(SEGMENT_INSET)
                .capsuleDrag(position, slots, current = { current }, enabled = options.size > 1, onSettle = onSelect),
    ) {
        LiquidCapsule(position = position, slots = slots, accent = accent, modifier = Modifier.matchParentSize(), visibility = shown)
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val interaction = remember { MutableInteractionSource() }
                val emphasis = { capsuleEmphasis(position.value, index) * shown() }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = ControlHeight - SEGMENT_INSET * 2)
                            .capsuleSlot(slots, index)
                            .selectable(
                                selected = index == selectedIndex,
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    // The capsule fills the whole segment.
                    Box(
                        modifier = Modifier.fillMaxWidth().capsuleContent(slots, index).pressScale(interaction),
                        contentAlignment = Alignment.Center,
                    ) {
                        SegmentLabel(label, quiet, Modifier.graphicsLayer { alpha = 1f - emphasis() })
                        SegmentLabel(label, accent, Modifier.graphicsLayer { alpha = emphasis() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentLabel(
    text: String,
    color: Color,
    modifier: Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = Spacing.sm),
    )
}

/**
 * A search box: a filled pill on the group surface with a search icon, a clear button once there is
 * text, and optional extra trailing [actions].
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val fill = groupFillColor()
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = ControlHeight),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
        trailingIcon =
            if (value.isNotEmpty() || actions != null) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotEmpty()) {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription =
                                        androidx.compose.ui.res.stringResource(
                                            com.erhu.erhu.core.common.R.string.cd_clear_search,
                                        ),
                                )
                            }
                        }
                        actions?.invoke(this)
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = fill,
                unfocusedContainerColor = fill,
                disabledContainerColor = fill,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

/**
 * A full-width action: [primary] is the screen's main action (filled accent), otherwise a quieter
 * tonal button. One height for all; dips under the finger like the app's other controls.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val sized = modifier.fillMaxWidth().heightIn(min = ControlHeight).pressScale(interaction, BUTTON_PRESSED_SCALE, enabled)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        // Long labels wrap rather than clip; the button grows to fit.
        Text(text = text, textAlign = TextAlign.Center)
    }
    if (primary) {
        Button(onClick = onClick, modifier = sized, enabled = enabled, interactionSource = interaction) { content() }
    } else {
        FilledTonalButton(onClick = onClick, modifier = sized, enabled = enabled, interactionSource = interaction) { content() }
    }
}

/**
 * Progress: a rounded accent bar on a faint accent track (no end marker). With no [progress] it is
 * indeterminate (work under way, amount unknown).
 */
@Composable
fun ProgressBar(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = accent.copy(alpha = PROGRESS_TRACK_ALPHA)
    val shaped = modifier.fillMaxWidth().height(PROGRESS_HEIGHT).clip(CircleShape)
    if (progress != null) {
        LinearProgressIndicator(
            progress = progress,
            modifier = shaped,
            color = accent,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(modifier = shaped, color = accent, trackColor = track, strokeCap = StrokeCap.Round, gapSize = 0.dp)
    }
}
