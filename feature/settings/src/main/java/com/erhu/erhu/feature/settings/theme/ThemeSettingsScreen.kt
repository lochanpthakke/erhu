package com.erhu.erhu.feature.settings.theme

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.BuiltInThemes
import com.erhu.erhu.core.designsystem.theme.ThemeManifest
import com.erhu.erhu.feature.settings.component.SettingsChoiceRow
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsIcon
import com.erhu.erhu.feature.settings.component.SettingsNavigationRow
import com.erhu.erhu.feature.settings.component.SettingsPage
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.feature.settings.component.SettingsSwitchRow
import com.erhu.erhu.core.common.R as CommonR

/** Theme preview swatch: the theme's background with its accent in the middle. */
private val SWATCH_SIZE = 36.dp

/** Displays localized feedback when a theme JSON import succeeds or fails. */
@Composable
private fun ImportMessageEffect(
    importMessage: ThemeImportMessage?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(importMessage) {
        importMessage?.let { message ->
            val localizedContext = AppLocaleManager.localizedContext(context)
            val text =
                when (message) {
                    is ThemeImportMessage.Success ->
                        localizedContext.getString(CommonR.string.theme_import_success, message.themeName)
                    is ThemeImportMessage.Failure ->
                        localizedContext.getString(CommonR.string.theme_import_failed, message.reason)
                }
            snackbarHostState.showSnackbar(text)
            onConsumed()
        }
    }
}

@Composable
@Suppress("LongMethod")
fun ThemeSettingsScreen(
    viewModel: ThemeViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = AppLocaleManager.activeLocale(context)
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

    ImportMessageEffect(
        importMessage = importMessage,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::clearImportMessage,
    )

    if (isRollbackActive) {
        ThemeRollbackDialog(onDismiss = { viewModel.acknowledgeRollback() })
    }

    SettingsPage(
        title = title,
        navigation = TopBarNavigation.Detail(onBack),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) {
        SettingsSection(title = stringResource(CommonR.string.theme_section_app_theme)) {
            SettingsGroup(selectable = true) {
                row {
                    SettingsChoiceRow(
                        title = stringResource(CommonR.string.theme_system_default_title),
                        summary = stringResource(CommonR.string.theme_system_default_summary),
                        selected = currentAppSkin == null && currentAppThemeId == BuiltInThemes.SYSTEM_DEFAULT_ID,
                        onClick = { viewModel.selectAppTheme(BuiltInThemes.SYSTEM_DEFAULT_ID) },
                        leading = { SystemThemeSwatch(light = BuiltInThemes.DEFAULT_LIGHT, dark = BuiltInThemes.DEFAULT_DARK) },
                    )
                }
                BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                    row {
                        SettingsChoiceRow(
                            title = localizedThemeName(theme),
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
                            summary = stringResource(CommonR.string.theme_skin_vst_summary),
                            selected = currentAppSkin?.id == skin.id,
                            onClick = { viewModel.selectAppTheme(skin.id) },
                        )
                    }
                }
            }
        }

        val sidebarGlassyness by viewModel.sidebarGlassyness.collectAsState()

        SettingsSection(title = stringResource(CommonR.string.theme_section_material)) {
            SettingsGroup {
                row {
                    SettingsSwitchRow(
                        title = stringResource(CommonR.string.theme_glass_effect_title),
                        summary = stringResource(CommonR.string.theme_glass_effect_summary),
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
                title = stringResource(CommonR.string.theme_glass_frostiness_title),
                footer = stringResource(CommonR.string.theme_glass_frostiness_footer),
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
                                text =
                                    stringResource(
                                        CommonR.string.theme_sidebar_frostiness_label,
                                        LocaleFormatters.formatPercent(sidebarGlassyness.toDouble(), locale),
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text =
                                    stringResource(
                                        CommonR.string.theme_sidebar_frostiness_scale,
                                        LocaleFormatters.formatPercentInt(0, locale),
                                        LocaleFormatters.formatPercentInt(100, locale),
                                    ),
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

        SettingsSection(title = stringResource(CommonR.string.theme_library_view_mode_title)) {
            SettingsGroup(selectable = true) {
                listOf(
                    Triple("GRID", CommonR.string.theme_view_mode_grid, Icons.Default.GridView),
                    Triple("LIST", CommonR.string.theme_view_mode_list, Icons.AutoMirrored.Filled.ViewList),
                    Triple("DRAWER", CommonR.string.theme_view_mode_drawer, Icons.Default.Menu),
                ).forEach { (mode, labelRes, icon) ->
                    row {
                        SettingsChoiceRow(
                            title = stringResource(labelRes),
                            selected = libraryLayout == mode,
                            onClick = { viewModel.selectLibraryLayout(mode) },
                            leading = { SettingsIcon(icon) },
                        )
                    }
                }
            }
        }

        SettingsSection(title = stringResource(CommonR.string.theme_widget_section_title)) {
            SettingsGroup(selectable = true) {
                BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                    row {
                        SettingsChoiceRow(
                            title = localizedThemeName(theme),
                            selected = currentWidgetTheme.id == theme.id,
                            onClick = { viewModel.selectWidgetTheme(theme.id) },
                            leading = { ThemeSwatch(theme) },
                        )
                    }
                }
            }
        }

        SettingsSection(title = stringResource(CommonR.string.theme_custom_packages_title)) {
            SettingsGroup {
                row {
                    SettingsNavigationRow(
                        title = stringResource(CommonR.string.theme_import_package_title),
                        summary = stringResource(CommonR.string.theme_import_package_summary),
                        icon = Icons.Default.FileUpload,
                        onClick = { fileLauncher.launch("*/*") },
                    )
                }
            }
        }
    }
}

@Composable
private fun localizedThemeName(theme: ThemeManifest): String =
    when (theme.id) {
        BuiltInThemes.DEFAULT_LIGHT.id -> stringResource(CommonR.string.theme_name_light)
        BuiltInThemes.DEFAULT_DARK.id -> stringResource(CommonR.string.theme_name_dark)
        BuiltInThemes.DOMINANT_COLOR_ID -> stringResource(CommonR.string.theme_name_dominant_color)
        BuiltInThemes.GRADIENT_UI_ID -> stringResource(CommonR.string.theme_name_gradient_ui)
        else -> theme.name
    }

@Composable
private fun themeSummary(theme: ThemeManifest): String =
    stringResource(
        when (theme.id) {
            BuiltInThemes.DOMINANT_COLOR_ID -> CommonR.string.theme_summary_dominant
            BuiltInThemes.GRADIENT_UI_ID -> CommonR.string.theme_summary_gradient
            else -> if (theme.isDark) CommonR.string.theme_summary_builtin_dark else CommonR.string.theme_summary_builtin_light
        },
    )

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
