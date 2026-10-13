package com.lochanpthakke.empee3.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.model.VisualizerMode
import com.lochanpthakke.empee3.core.model.VisualizerSensitivity
import com.lochanpthakke.empee3.core.model.VisualizerSmoothing
import com.lochanpthakke.empee3.feature.library.LibraryViewModel
import com.lochanpthakke.empee3.feature.library.METADATA_SOURCE_OPTIONS
import com.lochanpthakke.empee3.feature.library.UNSUPPORTED_FIX_ACTIONS_NOTE
import com.lochanpthakke.empee3.feature.library.summary
import com.lochanpthakke.empee3.feature.library.VisualizerViewModel
import com.lochanpthakke.empee3.feature.settings.component.SettingsChoiceRow
import com.lochanpthakke.empee3.feature.settings.component.SettingsGroup
import com.lochanpthakke.empee3.feature.settings.component.SettingsIcon
import com.lochanpthakke.empee3.feature.settings.component.SettingsNavigationRow
import com.lochanpthakke.empee3.feature.settings.component.SettingsPage
import com.lochanpthakke.empee3.feature.settings.component.SettingsSection
import com.lochanpthakke.empee3.feature.settings.component.SettingsSegmentedControl
import com.lochanpthakke.empee3.feature.settings.component.SettingsSwitchRow
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
    val section: String,
)

private const val SECTION_PERSONALIZE = "Personalize"
private const val SECTION_SOUND = "Sound"
private const val SECTION_LIBRARY = "Library"
private const val SECTION_ABOUT = "About"

private val SETTING_CATEGORIES =
    listOf(
        SettingCategory(
            "themes",
            "Themes & Appearance",
            "Theme, Glass Effect, library layout and widget",
            Icons.Default.Palette,
            SECTION_PERSONALIZE,
        ),
        SettingCategory(
            "navigation",
            "Navigation & Home Page",
            "Default landing page and its place in the navigation bar",
            Icons.Default.Tune,
            SECTION_PERSONALIZE,
        ),
        SettingCategory(
            "audio",
            "Audio & Equalizer",
            "8-band equalizer, presets and audio effects",
            Icons.Default.Equalizer,
            SECTION_SOUND,
        ),
        SettingCategory(
            "visualizer",
            "Visualizer",
            "Style and feel of the Now Playing visualizer",
            Icons.Default.GraphicEq,
            SECTION_SOUND,
        ),
        SettingCategory(
            "library",
            "Library & Scan Location",
            "Scan the whole device or a chosen folder",
            Icons.Default.Folder,
            SECTION_LIBRARY,
        ),
        SettingCategory(
            "metadata",
            "Metadata & Artwork",
            "Open the metadata / tag editor",
            Icons.Default.Tag,
            SECTION_LIBRARY,
        ),
        SettingCategory(
            "diagnostics",
            "Diagnostics & About",
            "Crash logs, update checker and version info",
            Icons.Default.BugReport,
            SECTION_ABOUT,
        ),
    )

/** Top-level Settings: the categories, grouped. Each category is its own route. */
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(
        title = "Settings",
        navigation = TopBarNavigation.TopLevel(onOpenDrawer),
        modifier = modifier,
    ) {
        SETTING_CATEGORIES.groupBy { it.section }.forEach { (section, categories) ->
            SettingsSection(title = section) {
                SettingsGroup {
                    categories.forEach { category ->
                        row {
                            SettingsNavigationRow(
                                title = category.title,
                                summary = category.description,
                                icon = category.icon,
                                onClick = { onOpenCategory(category.id) },
                            )
                        }
                    }
                }
            }
        }
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
    libraryViewModel: LibraryViewModel,
    visualizerViewModel: VisualizerViewModel,
) {
    val title = SETTING_CATEGORIES.find { it.id == categoryId }?.title ?: "Settings"
    when (categoryId) {
        "themes" -> ThemeSettingsScreen(viewModel = themeViewModel, title = title, onBack = onBack)
        "navigation" -> NavigationSettingsSubPage(viewModel = themeViewModel, title = title, onBack = onBack)
        "audio" ->
            SoundSettingsScreen(
                viewModel = soundViewModel,
                title = title,
                navigation = TopBarNavigation.Detail(onBack),
            )
        "library" ->
            LibrarySettingsSubPage(
                viewModel = libraryViewModel,
                title = title,
                onBack = onBack,
            )
        "visualizer" -> VisualizerSettingsSubPage(viewModel = visualizerViewModel, title = title, onBack = onBack)
        "diagnostics" -> DiagnosticsScreen(viewModel = diagnosticsViewModel, title = title, onBack = onBack)
        else -> MetadataSettingsSubPage(viewModel = libraryViewModel, title = title, onBack = onBack, onNavigateToMetadataEditor = onNavigateToMetadataEditor)
    }
}

@Composable
private fun NavigationSettingsSubPage(
    viewModel: ThemeViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val defaultStartSection by viewModel.defaultStartSection.collectAsState()

    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(
            title = "Home page",
            footer =
                "The page empee3 opens on. It is also placed as the leftmost icon in the bottom navigation bar.",
        ) {
            SettingsGroup(selectable = true) {
                listOf(
                    "TRACKS" to ("Tracks" to "Recently played shelves and the full track list"),
                    "ALBUMS" to ("Albums" to "Grid of all your albums"),
                    "LIBRARY" to ("Library" to "Playlists, favorites, artists and songs"),
                ).forEach { (key, info) ->
                    val (label, description) = info
                    row {
                        SettingsChoiceRow(
                            title = label,
                            summary = description,
                            selected = defaultStartSection.equals(key, ignoreCase = true),
                            onClick = { viewModel.setDefaultStartSection(key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun LibrarySettingsSubPage(
    viewModel: LibraryViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isCustomScanEnabled by viewModel.isCustomScanLocationEnabled.collectAsState()
    val customScanPath by viewModel.customScanLocationPath.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val folderPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
                val displayPath = parseFolderDisplayName(uri)
                viewModel.setCustomScanLocation(uri.toString(), displayPath)
            }
        }

    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(title = "Scan location") {
            SettingsGroup(selectable = true) {
                row {
                    SettingsChoiceRow(
                        title = "Entire device",
                        summary = "Scan all storage volumes for music files",
                        selected = !isCustomScanEnabled,
                        onClick = { viewModel.setScanEntireDevice() },
                        leading = { SettingsIcon(Icons.Default.PhoneAndroid) },
                    )
                }
                row {
                    SettingsChoiceRow(
                        title = "Custom folder",
                        summary =
                            if (isCustomScanEnabled && customScanPath.isNotBlank()) {
                                "Location: $customScanPath"
                            } else {
                                "Tap to choose a specific scan directory"
                            },
                        selected = isCustomScanEnabled,
                        onClick = { folderPickerLauncher.launch(null) },
                        leading = { SettingsIcon(Icons.Default.Folder) },
                    )
                }
            }
        }

        SettingsSection(title = "Scan") {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title = if (isScanning) "Scanning library…" else "Rescan library now",
                        summary = "Find new, changed and removed music",
                        icon = Icons.Default.Refresh,
                        enabled = !isScanning,
                        onClick = { viewModel.triggerScan() },
                        trailing =
                            if (isScanning) {
                                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                            } else {
                                {}
                            },
                    )
                }
            }
        }
    }
}

/** Visualizer preferences: style, how strongly and how smoothly it moves, and a calmer option. */
@Composable
private fun VisualizerSettingsSubPage(
    viewModel: VisualizerViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(title = "Style", footer = "Open it from the visualizer button at the top of Now Playing.") {
            SettingsGroup(selectable = true) {
                listOf(
                    Triple(VisualizerMode.SPECTRUM_BARS, "Bars", "Bass to treble, left to right"),
                    Triple(VisualizerMode.CIRCULAR_SPECTRUM, "Circle", "The spectrum around the artwork"),
                    Triple(VisualizerMode.OSCILLOSCOPE, "Waveform", "The sound wave itself"),
                ).forEach { (mode, label, summary) ->
                    row {
                        SettingsChoiceRow(
                            title = label,
                            summary = summary,
                            selected = settings.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                        )
                    }
                }
            }
        }
        SettingsSection(title = "Sensitivity", footer = "Higher shows more movement in quiet passages.") {
            SettingsSegmentedControl(
                options = listOf("Low", "Normal", "High"),
                selectedIndex = settings.sensitivity.ordinal,
                onSelect = { viewModel.setSensitivity(VisualizerSensitivity.entries[it]) },
                describe = { "Sensitivity " + listOf("low", "normal", "high")[it] },
            )
        }
        SettingsSection(title = "Movement") {
            SettingsSegmentedControl(
                options = listOf("Responsive", "Balanced", "Smooth"),
                selectedIndex = settings.smoothing.ordinal,
                onSelect = { viewModel.setSmoothing(VisualizerSmoothing.entries[it]) },
            )
        }
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = "Reduce motion",
                    summary = "Fewer frames and gentler movement; also saves battery",
                    checked = settings.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
            }
        }
    }
}

private fun parseFolderDisplayName(uri: Uri): String {
    val treeId = Uri.decode(uri.toString()).substringAfter("/tree/", "").substringBefore("/document/")
    if (treeId.isNotBlank()) {
        val pathPart = treeId.substringAfter(':')
        if (pathPart.isNotBlank()) {
            return pathPart.trim('/')
        }
    }
    return uri.lastPathSegment
        ?.substringAfter(':')
        ?.trim('/')
        ?.ifBlank { "Selected Folder" } ?: "Selected Folder"
}

@Composable
private fun MetadataSettingsSubPage(
    viewModel: LibraryViewModel,
    title: String,
    onBack: () -> Unit,
    onNavigateToMetadataEditor: () -> Unit,
) {
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val selectedFixAction by viewModel.selectedFixAction.collectAsState()

    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(
            title = "Tags",
            footer =
                "Tag fixes look up MusicBrainz and Deezer and write into the audio file itself; the library " +
                    "updates after the rescan. Android asks before empee3 may modify files other apps created. " +
                    "Online lyrics (LRCLIB) are separate: they're found on the Lyrics page and never written to files.",
        ) {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title = "Metadata editor",
                        summary = "Edit ID3 tags and clean watermarks",
                        icon = Icons.Default.EditNote,
                        onClick = onNavigateToMetadataEditor,
                    )
                }
            }
        }

        SettingsSection(
            title = "Primary Source Provider",
            footer = "Last.fm isn't offered: it rejects the app's API key, and its search has no album, genre or year.",
        ) {
            SettingsGroup(selectable = true) {
                METADATA_SOURCE_OPTIONS.forEach { option ->
                    row {
                        SettingsChoiceRow(
                            title = option.label,
                            summary = option.summary,
                            selected = selectedProvider == option.key,
                            onClick = { viewModel.setSelectedProvider(option.key) },
                        )
                    }
                }
            }
        }

        SettingsSection(title = "Primary Fix Action", footer = UNSUPPORTED_FIX_ACTIONS_NOTE) {
            SettingsGroup(selectable = true) {
                com.lochanpthakke.empee3.core.model.MetadataFixAction.supported.forEach { action ->
                    row {
                        SettingsChoiceRow(
                            title = action.displayName,
                            summary = action.summary(),
                            selected = selectedFixAction == action,
                            onClick = { viewModel.setSelectedFixAction(action) },
                        )
                    }
                }
            }
        }
    }
}
