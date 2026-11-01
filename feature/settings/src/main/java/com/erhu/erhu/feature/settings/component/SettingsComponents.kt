package com.erhu.erhu.feature.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.IconBadge
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupFillColor
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing

/** Minimum height of a settings row: comfortably above the 48 dp touch target. */
private val ROW_MIN_HEIGHT = 56.dp

/** Hairline between rows of a group. */
private const val DIVIDER_ALPHA = 0.14f

/** Gap between a row's leading icon and its text. */
private val ICON_TEXT_GAP = 14.dp

/** The fill of a settings group: the app's shared group surface ([groupFillColor]). */
@Composable
@ReadOnlyComposable
internal fun settingsGroupColor(): Color = groupFillColor()

/**
 * One Settings page: the app's floating header (round back / drawer button and title, the same
 * header as the album and playlist pages) over a scrolling column with the standard gutters.
 * Content scrolls underneath the header and stays clear of the mini player.
 */
@Composable
fun SettingsPage(
    title: String,
    navigation: TopBarNavigation,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // The page title merges into the header button as the page scrolls.
    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = { OverlayHeader(navigation = navigation, title = title, collapsingTitle = collapsingTitle, actions = actions) },
        snackbarHost = snackbarHost,
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val scrollState = rememberScrollState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(scrollState) }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenGutter)
                    .padding(top = scroll.contentTop + Spacing.sm, bottom = scroll.contentBottom + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            content = content,
        )
    }
}

/** A titled block of settings: a small accent heading, its content, and an optional footnote. */
@Composable
fun SettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SectionHeading(title = title)
        content()
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
            )
        }
    }
}

/** Collects the rows of a [SettingsGroup] so hairlines can be drawn between them. */
class SettingsGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * Rows on one rounded surface in the theme's card shape (tinted, with a hairline edge), separated
 * by hairlines. One surface per group, never one per row. [selectable] marks a single-choice group
 * for accessibility.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    build: SettingsGroupScope.() -> Unit,
) {
    val rows = SettingsGroupScope().apply(build).rows
    val divider = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .groupSurface()
                .then(if (selectable) Modifier.selectableGroup() else Modifier),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg), thickness = 0.5.dp, color = divider)
            row()
        }
    }
}

/** A plain content panel on the group surface (equalizer graph, band controls, release notes). */
@Composable
fun SettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .groupSurface()
                .padding(Spacing.lg),
        content = content,
    )
}

/** Leading icon of a row: the shared accent [IconBadge]. */
@Composable
fun SettingsIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    IconBadge(icon = icon, modifier = modifier)
}

/** Shared row layout: optional leading slot, title and summary, optional trailing slot. */
@Composable
private fun SettingsRowLayout(
    title: String,
    summary: String?,
    modifier: Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(ICON_TEXT_GAP))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = SUMMARY_GAP),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(Spacing.md))
            trailing()
        }
    }
}

/** Space between a row's title and its summary. */
private val SUMMARY_GAP = 2.dp

/** Content alpha of disabled settings controls (Material disabled emphasis). */
internal const val DISABLED_ALPHA = 0.38f

/** Opens another page or screen. */
@Composable
fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    SettingsRowLayout(
        title = title,
        summary = summary,
        enabled = enabled,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        leading = icon?.let { { SettingsIcon(it) } },
        trailing =
            trailing ?: {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
    )
}

/** An on/off setting. The whole row toggles; the switch only shows the state. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
) {
    SettingsRowLayout(
        title = title,
        summary = summary,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        leading = icon?.let { { SettingsIcon(it) } },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/** One option of a single-choice group: the whole row selects; the radio button shows the state. */
@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    SettingsRowLayout(
        title = title,
        summary = summary,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        leading = leading,
        trailing = { RadioButton(selected = selected, onClick = null) },
    )
}
