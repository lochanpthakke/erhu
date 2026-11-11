package com.erhu.erhu.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.VisualizerMode
import com.erhu.erhu.core.model.VisualizerSensitivity
import com.erhu.erhu.core.model.VisualizerSmoothing
import com.erhu.erhu.feature.library.FIELD_SELECTION_NOTE
import com.erhu.erhu.feature.library.LibraryViewModel
import com.erhu.erhu.feature.library.VisualizerViewModel
import com.erhu.erhu.feature.library.summary
import com.erhu.erhu.feature.settings.component.SettingsChoiceRow
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsIcon
import com.erhu.erhu.feature.settings.component.SettingsNavigationRow
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.feature.settings.component.SettingsSegmentedControl
import com.erhu.erhu.feature.settings.component.SettingsSwitchRow
import com.erhu.erhu.feature.settings.diagnostics.DiagnosticsScreen
import com.erhu.erhu.feature.settings.diagnostics.DiagnosticsViewModel
import com.erhu.erhu.feature.settings.language.LanguageSettingsSubPage
import com.erhu.erhu.feature.settings.sound.PlaybackSettingsScreen
import com.erhu.erhu.feature.settings.sound.SoundSettingsScreen
import com.erhu.erhu.feature.settings.sound.SoundViewModel
import com.erhu.erhu.feature.settings.theme.ThemeSettingsScreen
import com.erhu.erhu.feature.settings.theme.ThemeViewModel
import kotlin.math.roundToInt

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
            "language",
            "Language",
            "App language and system default",
            Icons.Default.Language,
            SECTION_PERSONALIZE,
        ),
        SettingCategory(
            "audio",
            "Audio & Equalizer",
            "Parametric EQ, channels, level and normalization",
            Icons.Default.Equalizer,
            SECTION_SOUND,
        ),
        SettingCategory(
            "playback",
            "Playback",
            "Crossfade, sleep timer, speed, pitch and silence skipping",
            Icons.Default.Tune,
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
        val context = LocalContext.current
        val selectedTag = remember(context) { AppLocaleManager.selectedLanguageTag(context) }
        val activeLanguageLabel =
            AppLocaleManager.findLanguage(selectedTag)?.nativeName
                ?: stringResource(R.string.settings_language_system_default)
        val languageRowTitle = stringResource(R.string.settings_language_title)
        val languageRowSummary = stringResource(R.string.settings_language_summary_current, activeLanguageLabel)

        SETTING_CATEGORIES.groupBy { it.section }.forEach { (section, categories) ->
            SettingsSection(title = section) {
                SettingsGroup {
                    categories.forEach { category ->
                        row {
                            SettingsNavigationRow(
                                title = if (category.id == "language") languageRowTitle else category.title,
                                summary = if (category.id == "language") languageRowSummary else category.description,
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
    onOpenIntroduction: (() -> Unit)? = null,
    onOpenAnnouncements: (() -> Unit)? = null,
) {
    val title =
        if (categoryId == "language") {
            stringResource(R.string.settings_language_title)
        } else {
            SETTING_CATEGORIES.find { it.id == categoryId }?.title ?: "Settings"
        }
    when (categoryId) {
        "themes" -> ThemeSettingsScreen(viewModel = themeViewModel, title = title, onBack = onBack)
        "navigation" -> NavigationSettingsSubPage(viewModel = themeViewModel, title = title, onBack = onBack)
        "language" -> LanguageSettingsSubPage(title = title, onBack = onBack)
        "audio" ->
            SoundSettingsScreen(
                viewModel = soundViewModel,
                title = title,
                navigation = TopBarNavigation.Detail(onBack),
            )
        "playback" -> PlaybackSettingsScreen(soundViewModel, title, onBack)
        "library" ->
            LibrarySettingsSubPage(
                viewModel = libraryViewModel,
                title = title,
                onBack = onBack,
            )
        "visualizer" -> VisualizerSettingsSubPage(viewModel = visualizerViewModel, title = title, onBack = onBack)
        "diagnostics" ->
            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                title = title,
                onBack = onBack,
                onOpenIntroduction = onOpenIntroduction,
                onOpenAnnouncements = onOpenAnnouncements,
            )
        else ->
            MetadataSettingsSubPage(
                viewModel = libraryViewModel,
                title = title,
                onBack = onBack,
                onNavigateToMetadataEditor = onNavigateToMetadataEditor,
            )
    }
}

@Composable
private fun NavigationSettingsSubPage(
    viewModel: ThemeViewModel,
    title: String,
    onBack: () -> Unit,
) {
    val defaultStartSection by viewModel.defaultStartSection.collectAsState()
    val vybeEnabled by viewModel.vybeEnabled.collectAsState()

    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(title = "Navigation & Home") {
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        title = "Enable VYBE",
                        summary = "Show VYBE in navigation and Home.",
                        checked = vybeEnabled != false,
                        onCheckedChange = viewModel::setVybeEnabled,
                    )
                }
            }
        }
        SettingsSection(
            title = "Home page",
            footer =
                "The page erhu opens on. It is also placed as the leftmost icon in the bottom navigation bar.",
        ) {
            SettingsGroup(selectable = true) {
                listOf(
                    "TRACKS" to ("Tracks" to "Home: Smart Playlists, featured and recent albums, and every track"),
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
                // Write access is needed by Library Builder to move files inside this folder.
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) {
                    }
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

        val preferredResolution by viewModel.preferredLiveCoverResolution.collectAsState()

        SettingsSection(
            title = "Live Artwork Quality",
            footer = "Preferred default video stream resolution for live animated cover downloads.",
        ) {
            SettingsGroup(selectable = true) {
                listOf(
                    "1080p" to "1080p (Full HD — Recommended)",
                    "720p" to "720p (HD)",
                    "480p" to "480p (SD — Data Saver)",
                    "Maximum" to "Maximum Available (Highest Quality)",
                ).forEach { (resolutionKey, label) ->
                    row {
                        SettingsChoiceRow(
                            title = label,
                            selected = preferredResolution == resolutionKey,
                            onClick = { viewModel.setPreferredLiveCoverResolution(resolutionKey) },
                        )
                    }
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
    val selectedFields by viewModel.selectedFields.collectAsState()
    val sourceOptions by viewModel.metadataSourceOptions.collectAsState()
    val confidenceThreshold by viewModel.confidenceThreshold.collectAsState()
    val context = LocalContext.current
    val hasAllFilesAccess =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }

    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(
            title = "Tags",
            footer =
                "Tag fixes look up MusicBrainz and Deezer and write into the audio file itself; the library " +
                    "updates after the rescan. Android asks before erhu may modify files other apps created. " +
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

        StorageAccessSection(hasAllFilesAccess = hasAllFilesAccess, context = context)
        ConfidenceThresholdSection(threshold = confidenceThreshold, onChange = viewModel::setConfidenceThreshold)

        SettingsSection(
            title = "Primary Source Provider",
            footer = "Used for tag matching. Artwork Fetcher searches music catalogues in priority order.",
        ) {
            SettingsGroup(selectable = true) {
                sourceOptions.forEach { option ->
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

        val activeWatermarks by viewModel.activeWatermarks.collectAsState()

        WatermarksBlocklistSection(
            activeWatermarks = activeWatermarks,
            onAddWatermark = viewModel::addCustomWatermark,
            onRemoveWatermark = viewModel::removeWatermark,
        )

        FieldsToFixSection(selectedFields = selectedFields, onToggle = viewModel::toggleField)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatermarksBlocklistSection(
    activeWatermarks: List<String>,
    onAddWatermark: (String) -> Unit,
    onRemoveWatermark: (String) -> Unit,
) {
    var textInput by remember { mutableStateOf("") }
    SettingsSection(
        title = "Watermarks & Blocklist",
        footer = "Tokens in this list are automatically stripped from filenames and metadata during Auto Fix Tags search.",
    ) {
        SettingsGroup {
            row {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Add custom watermark token") },
                            placeholder = { Text("e.g. pagalworld, djmaza") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                onAddWatermark(textInput)
                                textInput = ""
                            },
                        ) {
                            Text("Add")
                        }
                    }

                    if (activeWatermarks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Stored Watermarks (${activeWatermarks.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            activeWatermarks.forEach { token ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemoveWatermark(token) },
                                    label = { Text(token, style = MaterialTheme.typography.bodySmall) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove watermark",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldsToFixSection(
    selectedFields: Set<com.erhu.erhu.core.model.MetadataField>,
    onToggle: (com.erhu.erhu.core.model.MetadataField) -> Unit,
) {
    SettingsSection(title = "Fields to Fix", footer = FIELD_SELECTION_NOTE) {
        SettingsGroup {
            com.erhu.erhu.core.model.MetadataField.supported.forEach { field ->
                row {
                    SettingsSwitchRow(
                        title = field.displayName,
                        summary = field.summary(),
                        checked = field in selectedFields,
                        onCheckedChange = { onToggle(field) },
                    )
                }
            }
        }
    }
}

/**
 * Lochan's storage section: "All files access" lets erhu write tags and rename files without
 * per-file consent (and on SD cards).
 */
@Composable
private fun StorageAccessSection(
    hasAllFilesAccess: Boolean,
    context: android.content.Context,
) {
    SettingsSection(
        title = "Storage Permissions",
        footer =
            "Granting Full Storage Access allows erhu to write ID3 tags and rename music files on external SD cards " +
                "without permission errors.",
    ) {
        SettingsGroup {
            row {
                SettingsNavigationRow(
                    title = "Full Storage Access",
                    summary =
                        if (hasAllFilesAccess) {
                            "Granted — full read/write access to internal & SD card storage"
                        } else {
                            "Tap to grant full device storage write permission in System Settings"
                        },
                    icon = Icons.Default.Folder,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            } catch (_: android.content.ActivityNotFoundException) {
                                context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Lochan's threshold slider; the default stays at 85 % (the matcher's own threshold). */
@Composable
private fun ConfidenceThresholdSection(
    threshold: Float,
    onChange: (Float) -> Unit,
) {
    SettingsSection(
        title = "Confidence Threshold",
        footer =
            "Automated metadata fixes are accepted when search result confidence is at or above this threshold. " +
                "Lower values write more matches but risk wrong ones; uncertain matches are always listed for review.",
    ) {
        SettingsGroup {
            row {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Auto-Accept Threshold", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${(threshold * PERCENT).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Slider(
                        value = threshold,
                        onValueChange = onChange,
                        valueRange = LibraryViewModel.MIN_THRESHOLD..LibraryViewModel.MAX_THRESHOLD,
                        steps = THRESHOLD_STEPS,
                    )
                }
            }
        }
    }
}

private const val PERCENT = 100
private const val THRESHOLD_STEPS = 8
