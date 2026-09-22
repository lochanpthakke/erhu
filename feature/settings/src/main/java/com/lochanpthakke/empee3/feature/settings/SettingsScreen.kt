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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
    onNavigateToArtworkManager: () -> Unit,
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
                "library",
                "Library & Scanner",
                "Auto-rescan, short tracks, blacklists & directory exclusions",
                Icons.AutoMirrored.Filled.LibraryBooks,
            ),
            SettingCategory(
                "interface",
                "Interface & Layout",
                "Category tabs, grid/list view styles & navigation rules",
                Icons.Default.Menu,
            ),
            SettingCategory(
                "nowplaying",
                "Now Playing",
                "Full cover vs carousel, dominant colors & title animation",
                Icons.Default.PlayCircle,
            ),
            SettingCategory(
                "miniplayer",
                "Mini Player",
                "Skip buttons, YouTube-style progress line & gestures",
                Icons.Default.MusicNote,
            ),
            SettingCategory(
                "themes",
                "Themes & Appearance",
                "Built-in themes, Custom Skins & dark mode customization",
                Icons.Default.Palette,
            ),
            SettingCategory(
                "audio",
                "Audio & Equalizer",
                "8-band DSP equalizer, crossfade & Bluetooth auto-start",
                Icons.Default.Equalizer,
            ),
            SettingCategory(
                "metadata",
                "Metadata & Artwork",
                "Online providers, watermark stripper list & artwork quality",
                Icons.Default.Tag,
            ),
            SettingCategory(
                "widgets",
                "Widgets",
                "Home screen widget presets & layout options",
                Icons.Default.Widgets,
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
                "library" ->
                    GenericSettingsSubPage(
                        "Library & Scanner Options",
                        listOf("Auto-rescan on startup", "Hide short tracks (< 30s)", "Ignore .nomedia files"),
                    )
                "interface" ->
                    GenericSettingsSubPage(
                        "Interface Options",
                        listOf("Hide category bar", "Move tabs to bottom", "Standard track view"),
                    )
                "nowplaying" ->
                    GenericSettingsSubPage(
                        "Now Playing Options",
                        listOf("Use dominant artwork color", "Show position counter (325/3456)", "Marquee title animation"),
                    )
                "miniplayer" ->
                    GenericSettingsSubPage(
                        "Mini Player Options",
                        listOf("Show prev / next skip buttons", "Enable YouTube-style top progress line", "Swipe gesture to skip"),
                    )
                "themes" -> ThemeSettingsScreen(viewModel = themeViewModel)
                "audio" -> SoundSettingsScreen(viewModel = soundViewModel)
                "metadata" ->
                    MetadataSettingsSubPage(
                        onNavigateToMetadataEditor = onNavigateToMetadataEditor,
                        onNavigateToArtworkManager = onNavigateToArtworkManager,
                    )
                "widgets" ->
                    GenericSettingsSubPage(
                        "Widget Presets",
                        listOf("CD Case preset", "Tape Cassette preset", "Stacked layout", "Compact row layout"),
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
                        .clickable { onSelectCategory(cat.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
private fun GenericSettingsSubPage(
    title: String,
    items: List<String>,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(items) { item ->
            var checked by remember { mutableStateOf(true) }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun MetadataSettingsSubPage(
    onNavigateToMetadataEditor: () -> Unit,
    onNavigateToArtworkManager: () -> Unit,
) {
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

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(onClick = onNavigateToArtworkManager),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Launch Artwork Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Search high-res covers and embed album art", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Online Metadata Providers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        GenericSettingsSubPage(
            "Metadata Options",
            listOf(
                "MusicBrainz Provider",
                "Deezer Provider",
                "Last.fm Provider",
                "LRCLIB Provider",
                "Auto-strip watermarks ([320kbps], pagalworld)",
            ),
        )
    }
}
