package com.lochanpthakke.empee3.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToMetadataEditor: () -> Unit,
    themeViewModel: ThemeViewModel,
    soundViewModel: SoundViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    modifier: Modifier = Modifier,
    initialCategory: String? = null,
) {
    var activeCategory by remember { mutableStateOf(initialCategory) }

    val categories =
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (activeCategory == null) {
                            "Settings"
                        } else {
                            categories.find { it.id == activeCategory }?.title ?: "Settings"
                        },
                    )
                },
                navigationIcon = {
                    if (activeCategory == null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    } else {
                        IconButton(onClick = { activeCategory = null }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when (activeCategory) {
                null -> MainSettingsList(categories = categories, onSelectCategory = { activeCategory = it })
                "themes" -> ThemeSettingsScreen(viewModel = themeViewModel)
                "audio" -> SoundSettingsScreen(viewModel = soundViewModel)
                "metadata" ->
                    MetadataSettingsSubPage(
                        onNavigateToMetadataEditor = onNavigateToMetadataEditor,
                    )
                "diagnostics" -> DiagnosticsScreen(viewModel = diagnosticsViewModel)
                else -> MainSettingsList(categories = categories, onSelectCategory = { activeCategory = it })
            }
        }
    }
}

@Composable
private fun MainSettingsList(
    categories: List<SettingCategory>,
    onSelectCategory: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(categories) { cat ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .glassPanel(MaterialTheme.shapes.medium, elevated = false)
                        .clickable { onSelectCategory(cat.id) },
                colors = CardDefaults.cardColors(containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant)),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = cat.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
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
private fun MetadataSettingsSubPage(onNavigateToMetadataEditor: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(onClick = onNavigateToMetadataEditor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Launch Metadata Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Edit ID3 tags and clean watermarks", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                "Lookups use MusicBrainz, Deezer, Last.fm and LRCLIB. Tag writing needs write access to the file, " +
                    "which Android may deny for files other apps created.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
