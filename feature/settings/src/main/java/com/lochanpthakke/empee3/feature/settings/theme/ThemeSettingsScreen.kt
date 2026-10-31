package com.lochanpthakke.empee3.feature.settings.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes
import com.lochanpthakke.empee3.core.designsystem.theme.ThemeManifest
import com.lochanpthakke.empee3.feature.settings.component.SettingsChoiceRow
import com.lochanpthakke.empee3.feature.settings.component.SettingsGroup
import com.lochanpthakke.empee3.feature.settings.component.SettingsIcon
import com.lochanpthakke.empee3.feature.settings.component.SettingsNavigationRow
import com.lochanpthakke.empee3.feature.settings.component.SettingsPage
import com.lochanpthakke.empee3.feature.settings.component.SettingsSection
import com.lochanpthakke.empee3.feature.settings.component.SettingsSwitchRow
import kotlin.math.roundToInt

/** Theme preview swatch: the theme's background with its accent in the middle. */
private val SWATCH_SIZE = 36.dp

@Composable
@Suppress("LongMethod")
fun ThemeSettingsScreen(
    viewModel: ThemeViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentAppThemeId by viewModel.appThemeId.collectAsState()
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()
    val popupGlassOpacity by viewModel.popupGlassOpacity.collectAsState()
    val currentAppSkin by viewModel.appSkin.collectAsState()
    val currentWidgetTheme by viewModel.widgetTheme.collectAsState()
    val libraryLayout by viewModel.libraryLayout.collectAsState()
    val isRollbackActive by viewModel.isRollbackActive.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val fileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            uri?.let {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    viewModel.importPackage(inputStream)
                }
            }
        }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearImportMessage()
        }
    }

    if (isRollbackActive) {
        ThemeRollbackDialog(onDismiss = { viewModel.acknowledgeRollback() })
    }

    SettingsPage(
        title = title,
        navigation = TopBarNavigation.Detail(onBack),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) {
        SettingsSection(title = "App theme") {
            SettingsGroup(selectable = true) {
                row {
                    SettingsChoiceRow(
                        title = "System Default",
                        summary = "Light or Dark, following your device",
                        selected = currentAppSkin == null && currentAppThemeId == BuiltInThemes.SYSTEM_DEFAULT_ID,
                        onClick = { viewModel.selectAppTheme(BuiltInThemes.SYSTEM_DEFAULT_ID) },
                        leading = { SystemThemeSwatch(light = BuiltInThemes.DEFAULT_LIGHT, dark = BuiltInThemes.DEFAULT_DARK) },
                    )
                }
                BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                    row {
                        SettingsChoiceRow(
                            title = theme.name,
                            summary = themeSummary(theme),
                            selected = currentAppSkin == null && currentAppThemeId == theme.id,
                            onClick = { viewModel.selectAppTheme(theme.id) },
                            leading = { ThemeSwatch(theme) },
                        )
                    }
                }
                BuiltInThemes.ALL_BUILT_IN_SKINS.forEach { skin ->
                    row {
                        SettingsChoiceRow(
                            title = skin.name,
                            summary = "3D VST Studio Skin",
                            selected = currentAppSkin?.id == skin.id,
                            onClick = { viewModel.selectAppTheme(skin.id) },
                        )
                    }
                }
            }
        }

        val sidebarGlassyness by viewModel.sidebarGlassyness.collectAsState()

        SettingsSection(title = "Material") {
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        title = "Glass Effect",
                        summary = "Translucent glass on the mini player, navigation and floating buttons. Works with any theme.",
                        icon = Icons.Default.AutoAwesome,
                        checked = glassEffectEnabled,
                        onCheckedChange = viewModel::setGlassEffectEnabled,
                    )
                }
            }
        }

        if (glassEffectEnabled) {
            PopupGlassControls(opacity = popupGlassOpacity, onOpacityChange = viewModel::setPopupGlassOpacity)
        }

        if (glassEffectEnabled) {
            SettingsSection(
                title = "Glass Frostiness",
                footer = "Adjust the balance between clear translucent glass and frosted glass.",
            ) {
                SettingsGroup {
                    row {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                        ) {
                            Text(
                                text = "Main Sidebar Frostiness: ${(sidebarGlassyness * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "0% = Clear Glass · 100% = Full Frosted Glass",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = sidebarGlassyness,
                                onValueChange = viewModel::setSidebarGlassyness,
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(title = "Library view mode") {
            SettingsGroup(selectable = true) {
                listOf(
                    Triple("GRID", "2-Column Grid", Icons.Default.GridView),
                    Triple("LIST", "Single-Column List", Icons.AutoMirrored.Filled.ViewList),
                    Triple("DRAWER", "Side Navigation Drawer", Icons.Default.Menu),
                ).forEach { (mode, label, icon) ->
                    row {
                        SettingsChoiceRow(
                            title = label,
                            selected = libraryLayout == mode,
                            onClick = { viewModel.selectLibraryLayout(mode) },
                            leading = { SettingsIcon(icon) },
                        )
                    }
                }
            }
        }

        SettingsSection(title = "Home screen widget") {
            SettingsGroup(selectable = true) {
                BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                    row {
                        SettingsChoiceRow(
                            title = theme.name,
                            selected = currentWidgetTheme.id == theme.id,
                            onClick = { viewModel.selectWidgetTheme(theme.id) },
                            leading = { ThemeSwatch(theme) },
                        )
                    }
                }
            }
        }

        SettingsSection(title = "Custom packages") {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title = "Import theme or skin",
                        summary = "Load a .mtheme or .mskin package",
                        icon = Icons.Default.FileUpload,
                        onClick = { fileLauncher.launch("*/*") },
                    )
                }
            }
        }
    }
}

private fun themeSummary(theme: ThemeManifest): String =
    when (theme.id) {
        BuiltInThemes.DOMINANT_COLOR_ID -> "Accent colour from the current artwork"
        BuiltInThemes.GRADIENT_UI_ID -> "Gradient from the current artwork's colours"
        else -> "Built-in ${if (theme.isDark) "Dark" else "Light"} Theme"
    }

/** The theme's own background with its accent; a thin ring keeps it visible on any surface. */
@Composable
private fun ThemeSwatch(theme: ThemeManifest) {
    val background = theme.colors.getBackgroundColor()
    val accent = theme.colors.getPrimaryColor()
    val ring = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.size(SWATCH_SIZE)) {
        drawCircle(background)
        drawCircle(accent, radius = size.minDimension * ACCENT_RADIUS)
        drawCircle(ring, style = Stroke(width = 1.dp.toPx()))
    }
}

/** System Default: half the light theme, half the dark theme. */
@Composable
private fun SystemThemeSwatch(
    light: ThemeManifest,
    dark: ThemeManifest,
) {
    val ring = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.size(SWATCH_SIZE)) {
        drawHalf(light.colors.getBackgroundColor(), startAngle = HALF_START_LEFT)
        drawHalf(dark.colors.getBackgroundColor(), startAngle = HALF_START_RIGHT)
        drawCircle(ring, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHalf(
    color: Color,
    startAngle: Float,
) = drawArc(
    color = color,
    startAngle = startAngle,
    sweepAngle = HALF_SWEEP,
    useCenter = true,
    topLeft = Offset.Zero,
    size = Size(size.width, size.height),
)

private const val ACCENT_RADIUS = 0.22f
private const val HALF_START_LEFT = 90f
private const val HALF_START_RIGHT = 270f
private const val HALF_SWEEP = 180f
