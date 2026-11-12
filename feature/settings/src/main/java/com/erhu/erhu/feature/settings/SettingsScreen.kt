package com.erhu.erhu.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.VisualizerMode
import com.erhu.erhu.core.model.VisualizerSensitivity
import com.erhu.erhu.core.model.VisualizerSmoothing
import com.erhu.erhu.feature.library.LibraryViewModel
import com.erhu.erhu.feature.library.VisualizerViewModel
import com.erhu.erhu.feature.library.localizedFieldSelectionNote
import com.erhu.erhu.feature.library.localizedLabel
import com.erhu.erhu.feature.library.localizedName
import com.erhu.erhu.feature.library.localizedSummary
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

private data class SettingCategory(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    @param:StringRes val sectionRes: Int,
)

private val SETTING_CATEGORIES =
    listOf(
        SettingCategory(
            "themes",
            R.string.settings_category_themes_title,
            R.string.settings_category_themes_desc,
            Icons.Default.Palette,
            R.string.settings_section_personalize,
        ),
        SettingCategory(
            "navigation",
            R.string.settings_category_navigation_title,
            R.string.settings_category_navigation_desc,
            Icons.Default.Tune,
            R.string.settings_section_personalize,
        ),
        SettingCategory(
            "language",
            R.string.settings_language_title,
            R.string.settings_language_summary,
            Icons.Default.Language,
            R.string.settings_section_personalize,
        ),
        SettingCategory(
            "audio",
            R.string.settings_category_audio_title,
            R.string.settings_category_audio_desc,
            Icons.Default.Equalizer,
            R.string.settings_section_sound,
        ),
        SettingCategory(
            "playback",
            R.string.settings_category_playback_title,
            R.string.settings_category_playback_desc,
            Icons.Default.Tune,
            R.string.settings_section_sound,
        ),
        SettingCategory(
            "visualizer",
            R.string.settings_category_visualizer_title,
            R.string.settings_category_visualizer_desc,
            Icons.Default.GraphicEq,
            R.string.settings_section_sound,
        ),
        SettingCategory(
            "library",
            R.string.settings_category_library_title,
            R.string.settings_category_library_desc,
            Icons.Default.Folder,
            R.string.settings_section_library,
        ),
        SettingCategory(
            "metadata",
            R.string.settings_category_metadata_title,
            R.string.settings_category_metadata_desc,
            Icons.Default.Tag,
            R.string.settings_section_library,
        ),
        SettingCategory(
            "diagnostics",
            R.string.settings_category_diagnostics_title,
            R.string.settings_category_diagnostics_desc,
            Icons.Default.BugReport,
            R.string.settings_section_about,
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
        title = stringResource(R.string.settings_title),
        navigation = TopBarNavigation.TopLevel(onOpenDrawer),
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val selectedTag = remember(context) { AppLocaleManager.selectedLanguageTag(context) }
        val activeLanguageLabel =
            AppLocaleManager.findLanguage(selectedTag)?.nativeName
                ?: stringResource(R.string.settings_language_system_default)
        val languageRowSummary = stringResource(R.string.settings_language_summary_current, activeLanguageLabel)

        SETTING_CATEGORIES.groupBy { it.sectionRes }.forEach { (sectionRes, categories) ->
            SettingsSection(title = stringResource(sectionRes)) {
                SettingsGroup {
                    categories.forEach { category ->
                        row {
                            SettingsNavigationRow(
                                title = stringResource(category.titleRes),
                                summary =
                                    if (category.id == "language") {
                                        languageRowSummary
                                    } else {
                                        stringResource(category.descriptionRes)
                                    },
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
    val titleRes = SETTING_CATEGORIES.find { it.id == categoryId }?.titleRes ?: R.string.settings_title
    val title = stringResource(titleRes)
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
        SettingsSection(title = stringResource(R.string.settings_nav_section)) {
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_nav_enable_vybe_title),
                        summary = stringResource(R.string.settings_nav_enable_vybe_summary),
                        checked = vybeEnabled != false,
                        onCheckedChange = viewModel::setVybeEnabled,
                    )
                }
            }
        }
        SettingsSection(
            title = stringResource(R.string.settings_nav_home_page_title),
            footer = stringResource(R.string.settings_nav_home_page_footer),
        ) {
            SettingsGroup(selectable = true) {
                listOf(
                    Triple(
                        "TRACKS",
                        R.string.settings_nav_home_tracks_title,
                        R.string.settings_nav_home_tracks_summary,
                    ),
                    Triple(
                        "ALBUMS",
                        R.string.settings_nav_home_albums_title,
                        R.string.settings_nav_home_albums_summary,
                    ),
                    Triple(
                        "LIBRARY",
                        R.string.settings_nav_home_library_title,
                        R.string.settings_nav_home_library_summary,
                    ),
                ).forEach { (key, labelRes, descriptionRes) ->
                    row {
                        SettingsChoiceRow(
                            title = stringResource(labelRes),
                            summary = stringResource(descriptionRes),
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
                val displayPath = parseFolderDisplayName(context, uri)
                viewModel.setCustomScanLocation(uri.toString(), displayPath)
            }
        }

    SettingsPage(title = title, navigation = TopBarNavigation.Detail(onBack)) {
        SettingsSection(title = stringResource(R.string.settings_scan_location_section)) {
            SettingsGroup(selectable = true) {
                row {
                    SettingsChoiceRow(
                        title = stringResource(R.string.settings_scan_entire_device_title),
                        summary = stringResource(R.string.settings_scan_entire_device_summary),
                        selected = !isCustomScanEnabled,
                        onClick = { viewModel.setScanEntireDevice() },
                        leading = { SettingsIcon(Icons.Default.PhoneAndroid) },
                    )
                }
                row {
                    SettingsChoiceRow(
                        title = stringResource(R.string.settings_scan_custom_folder_title),
                        summary =
                            if (isCustomScanEnabled && customScanPath.isNotBlank()) {
                                stringResource(R.string.settings_scan_custom_folder_location, customScanPath)
                            } else {
                                stringResource(R.string.settings_scan_custom_folder_summary)
                            },
                        selected = isCustomScanEnabled,
                        onClick = { folderPickerLauncher.launch(null) },
                        leading = { SettingsIcon(Icons.Default.Folder) },
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_scan_section)) {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title =
                            stringResource(
                                if (isScanning) R.string.settings_scan_running else R.string.settings_scan_now,
                            ),
                        summary = stringResource(R.string.settings_scan_now_summary),
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
            title = stringResource(R.string.settings_live_artwork_quality_title),
            footer = stringResource(R.string.settings_live_artwork_quality_footer),
        ) {
            SettingsGroup(selectable = true) {
                listOf(
                    "1080p" to R.string.settings_live_artwork_1080p,
                    "720p" to R.string.settings_live_artwork_720p,
                    "480p" to R.string.settings_live_artwork_480p,
                    "Maximum" to R.string.settings_live_artwork_max,
                ).forEach { (resolutionKey, labelRes) ->
                    row {
                        SettingsChoiceRow(
                            title = stringResource(labelRes),
                            selected = preferredResolution == resolutionKey,
                            onClick = { viewModel.setPreferredLiveCoverResolution(resolutionKey) },
                        )
                    }
                }
            }
        }
    }
}

/** Visualizer style picker section extracted to keep the visualizer subpage concise. */
@Composable
private fun VisualizerStyleSection(
    selectedMode: VisualizerMode,
    onSelectMode: (VisualizerMode) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_visualizer_style_title),
        footer = stringResource(R.string.settings_visualizer_style_footer),
    ) {
        SettingsGroup(selectable = true) {
            listOf(
                Triple(
                    VisualizerMode.SPECTRUM_BARS,
                    R.string.settings_visualizer_mode_bars_title,
                    R.string.settings_visualizer_mode_bars_summary,
                ),
                Triple(
                    VisualizerMode.CIRCULAR_SPECTRUM,
                    R.string.settings_visualizer_mode_circle_title,
                    R.string.settings_visualizer_mode_circle_summary,
                ),
                Triple(
                    VisualizerMode.OSCILLOSCOPE,
                    R.string.settings_visualizer_mode_waveform_title,
                    R.string.settings_visualizer_mode_waveform_summary,
                ),
            ).forEach { (mode, labelRes, summaryRes) ->
                row {
                    SettingsChoiceRow(
                        title = stringResource(labelRes),
                        summary = stringResource(summaryRes),
                        selected = selectedMode == mode,
                        onClick = { onSelectMode(mode) },
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
        VisualizerStyleSection(
            selectedMode = settings.mode,
            onSelectMode = viewModel::setMode,
        )
        val sensitivityOptions =
            listOf(
                stringResource(R.string.settings_visualizer_sensitivity_low),
                stringResource(R.string.settings_visualizer_sensitivity_normal),
                stringResource(R.string.settings_visualizer_sensitivity_high),
            )
        val sensitivityDescriptions =
            sensitivityOptions.map {
                stringResource(R.string.settings_visualizer_sensitivity_desc, it)
            }
        SettingsSection(
            title = stringResource(R.string.settings_visualizer_sensitivity_title),
            footer = stringResource(R.string.settings_visualizer_sensitivity_footer),
        ) {
            SettingsSegmentedControl(
                options = sensitivityOptions,
                selectedIndex = settings.sensitivity.ordinal,
                onSelect = { viewModel.setSensitivity(VisualizerSensitivity.entries[it]) },
                describe = { sensitivityDescriptions[it] },
            )
        }
        SettingsSection(title = stringResource(R.string.settings_visualizer_movement_title)) {
            SettingsSegmentedControl(
                options =
                    listOf(
                        stringResource(R.string.settings_visualizer_movement_responsive),
                        stringResource(R.string.settings_visualizer_movement_balanced),
                        stringResource(R.string.settings_visualizer_movement_smooth),
                    ),
                selectedIndex = settings.smoothing.ordinal,
                onSelect = { viewModel.setSmoothing(VisualizerSmoothing.entries[it]) },
            )
        }
        SettingsGroup {
            row {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_visualizer_reduce_motion_title),
                    summary = stringResource(R.string.settings_visualizer_reduce_motion_summary),
                    checked = settings.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
            }
        }
    }
}

private fun parseFolderDisplayName(
    context: Context,
    uri: Uri,
): String {
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
        ?.ifBlank { AppLocaleManager.localizedContext(context).getString(R.string.scan_location_selected_folder) }
        ?: AppLocaleManager.localizedContext(context).getString(R.string.scan_location_selected_folder)
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
            title = stringResource(R.string.settings_metadata_tags_title),
            footer = stringResource(R.string.settings_metadata_tags_footer),
        ) {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_metadata_editor_title),
                        summary = stringResource(R.string.settings_metadata_editor_summary),
                        icon = Icons.Default.EditNote,
                        onClick = onNavigateToMetadataEditor,
                    )
                }
            }
        }

        StorageAccessSection(hasAllFilesAccess = hasAllFilesAccess, context = context)
        ConfidenceThresholdSection(threshold = confidenceThreshold, onChange = viewModel::setConfidenceThreshold)

        SettingsSection(
            title = stringResource(R.string.settings_metadata_provider_title),
            footer = stringResource(R.string.settings_metadata_provider_footer),
        ) {
            SettingsGroup(selectable = true) {
                sourceOptions.forEach { option ->
                    row {
                        SettingsChoiceRow(
                            title = option.localizedLabel(),
                            summary = option.localizedSummary(),
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
        title = stringResource(R.string.settings_metadata_watermarks_title),
        footer = stringResource(R.string.settings_metadata_watermarks_footer),
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
                            label = { Text(stringResource(R.string.settings_metadata_watermark_label)) },
                            placeholder = { Text(stringResource(R.string.settings_metadata_watermark_placeholder)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                onAddWatermark(textInput)
                                textInput = ""
                            },
                        ) {
                            Text(stringResource(R.string.action_add))
                        }
                    }

                    if (activeWatermarks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.settings_metadata_watermark_stored, activeWatermarks.size),
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
                                            contentDescription = stringResource(R.string.settings_metadata_watermark_remove_cd),
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
    SettingsSection(title = stringResource(R.string.settings_metadata_fields_title), footer = localizedFieldSelectionNote()) {
        SettingsGroup {
            com.erhu.erhu.core.model.MetadataField.supported.forEach { field ->
                row {
                    SettingsSwitchRow(
                        title = field.localizedName(),
                        summary = field.localizedSummary(),
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
    context: Context,
) {
    SettingsSection(
        title = stringResource(R.string.settings_metadata_storage_title),
        footer = stringResource(R.string.settings_metadata_storage_footer),
    ) {
        SettingsGroup {
            row {
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_metadata_full_storage_title),
                    summary =
                        stringResource(
                            if (hasAllFilesAccess) {
                                R.string.settings_metadata_full_storage_granted
                            } else {
                                R.string.settings_metadata_full_storage_tap
                            },
                        ),
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
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    SettingsSection(
        title = stringResource(R.string.settings_metadata_confidence_title),
        footer = stringResource(R.string.settings_metadata_confidence_footer),
    ) {
        SettingsGroup {
            row {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_metadata_confidence_label), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            LocaleFormatters.formatPercent(threshold.toDouble(), locale),
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

private const val THRESHOLD_STEPS = 8
