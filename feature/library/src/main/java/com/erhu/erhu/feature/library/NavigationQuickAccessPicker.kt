package com.erhu.erhu.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.component.BottomChrome
import com.erhu.erhu.core.designsystem.foundation.IconSize
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.designsystem.theme.ErhuGlass
import com.erhu.erhu.core.designsystem.theme.liquidGlass

private fun toggleQuickAccess(
    selected: List<LibrarySection>,
    section: LibrarySection,
): List<LibrarySection>? =
    when {
        section == LibrarySection.TRACKS -> null
        section in selected -> selected - section
        selected.size < NAVIGATION_SHORTCUT_LIMIT -> selected + section
        else -> null
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationQuickAccessPicker(
    selected: List<LibrarySection>,
    vybeEnabled: Boolean,
    onSelectionChange: (List<LibrarySection>) -> Unit,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = !rememberReduceMotion(),
    enabled: Boolean = true,
) {
    val available = LibrarySection.entries.filter { vybeEnabled || it != LibrarySection.VYBE }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.navigation_quick_access_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            available.forEach { section ->
                FilterChip(
                    selected = section in selected,
                    onClick = { toggleQuickAccess(selected, section)?.let(onSelectionChange) },
                    enabled = enabled && section != LibrarySection.TRACKS &&
                        (section in selected || selected.size < NAVIGATION_SHORTCUT_LIMIT),
                    label = { Text(stringResource(section.labelRes)) },
                    leadingIcon = { Icon(section.icon, contentDescription = null, Modifier.size(IconSize.small)) },
                )
            }
        }
        Text(
            text = stringResource(R.string.navigation_quick_access_preview),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        QuickAccessPreview(selected, motionEnabled)
    }
}

@Composable
private fun QuickAccessPreview(
    selected: List<LibrarySection>,
    motionEnabled: Boolean,
) {
    val shape = BottomChrome.shape()
    Surface(
        modifier = Modifier.fillMaxWidth().liquidGlass(shape),
        shape = shape,
        color = ErhuGlass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (ErhuGlass.isActive) 0.dp else BottomChrome.elevation,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                val slotWidth = maxWidth / selected.size
                val width by animateDpAsState(slotWidth, if (motionEnabled) Motion.selection() else snap(), label = "shortcutWidth")
                selected.forEachIndexed { index, section ->
                    key(section) {
                        val x by animateDpAsState(
                            slotWidth * index,
                            if (motionEnabled) Motion.selection() else snap(),
                            label = "shortcutPosition",
                        )
                        var shown by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { shown = true }
                        val scale by animateFloatAsState(
                            if (shown || !motionEnabled) 1f else 0f,
                            if (motionEnabled) Motion.selection() else snap(),
                            label = "shortcutAppearance",
                        )
                        Box(Modifier.offset(x = x).width(width).align(Alignment.CenterStart), contentAlignment = Alignment.Center) {
                            Column(
                                modifier =
                                    Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }.padding(Spacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Icon(section.icon, contentDescription = null, Modifier.size(IconSize.standard))
                                Text(
                                    stringResource(section.labelRes),
                                    style = TextStyles.metadata,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
