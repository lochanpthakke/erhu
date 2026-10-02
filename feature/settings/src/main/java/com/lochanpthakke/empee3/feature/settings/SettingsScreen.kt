package com.lochanpthakke.empee3.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.feature.settings.diagnostics.DiagnosticsScreen
import com.lochanpthakke.empee3.feature.settings.diagnostics.DiagnosticsViewModel
import com.lochanpthakke.empee3.feature.settings.sound.SoundSettingsScreen
import com.lochanpthakke.empee3.feature.settings.sound.SoundViewModel
import com.lochanpthakke.empee3.feature.settings.theme.ThemeSettingsScreen
import com.lochanpthakke.empee3.feature.settings.theme.ThemeViewModel

private data class SettingCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

private val SETTING_CATEGORIES =
    listOf(
        SettingCategory(
            "themes",
            "Themes & Appearance",
            "Built-in themes, Custom Skins & dark mode customization",
            Icons.Default.Palette,
        ),
        SettingCategory(
            "audio",
            "Audio & Equalizer",
            "8-band equalizer, presets and audio effects",
            Icons.Default.Equalizer,
        ),
        SettingCategory(
            "metadata",
            "Metadata & Artwork",
            "Open the metadata / tag editor",
            Icons.Default.Tag,
        ),
        SettingCategory(
            "diagnostics",
            "Diagnostics & About",
            "Local crash log viewer, GitHub update checker & version info",
            Icons.Default.BugReport,
        ),
    )

/** Top-level Settings: the list of categories. Each category is its own route. */
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        topBar = { AppTopBar(navigation = TopBarNavigation.TopLevel(onOpenDrawer), title = "Settings") },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent()
        MainSettingsList(
            categories = SETTING_CATEGORIES,
            onSelectCategory = onOpenCategory,
            topInset = scroll.contentTop,
            bottomInset = scroll.contentBottom,
            modifier = Modifier.padding(scroll.container),
        )
    }
}

/**
 * A settings category, pushed on top of [SettingsScreen] so system Back returns
 * to the category list. Each page brings exactly one header.
 */
@Suppress("LongParameterList")
@Composable
fun SettingsCategoryScreen(
    categoryId: String,
    onBack: () -> Unit,
    onNavigateToMetadataEditor: () -> Unit,
    themeViewModel: ThemeViewModel,
    soundViewModel: SoundViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
) {
    val title = SETTING_CATEGORIES.find { it.id == categoryId }?.title ?: "Settings"
    when (categoryId) {
        "themes" -> ThemeSettingsScreen(viewModel = themeViewModel, title = title, onBack = onBack)
        "audio" ->
            SoundSettingsScreen(
                viewModel = soundViewModel,
                title = title,
                navigation = TopBarNavigation.Detail(onBack),
            )
        "diagnostics" -> DiagnosticsScreen(viewModel = diagnosticsViewModel, title = title, onBack = onBack)
        else ->
            AppScaffold(
                topBar = { AppTopBar(navigation = TopBarNavigation.Detail(onBack), title = title) },
            ) { innerPadding ->
                MetadataSettingsSubPage(
                    onNavigateToMetadataEditor = onNavigateToMetadataEditor,
                    modifier = Modifier.padding(innerPadding),
                )
            }
    }
}

@Composable
private fun MainSettingsList(
    categories: List<SettingCategory>,
    onSelectCategory: (String) -> Unit,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Spacing.sm + topInset, bottom = Spacing.sm + bottomInset),
    ) {
        items(categories) { cat ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenGutter, vertical = 6.dp)
                        .glassPanel(MaterialTheme.shapes.medium, elevated = false, level = GlassLevel.Minimal)
                        .clickable { onSelectCategory(cat.id) },
                colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = cat.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(Spacing.lg))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cat.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun MetadataSettingsSubPage(
    onNavigateToMetadataEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.screenGutter),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm)
                    .clickable(onClick = onNavigateToMetadataEditor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text("Launch Metadata Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Edit ID3 tags and clean watermarks", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text =
                "Lookups use MusicBrainz, Deezer, Last.fm and LRCLIB. Tag writing needs write access to the file, " +
                    "which Android may deny for files other apps created.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
