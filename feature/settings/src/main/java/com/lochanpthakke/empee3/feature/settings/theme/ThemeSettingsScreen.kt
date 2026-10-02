package com.lochanpthakke.empee3.feature.settings.theme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.AppTopBar
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun ThemeSettingsScreen(
    viewModel: ThemeViewModel,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentAppThemeId by viewModel.appThemeId.collectAsState()
    val glassEffectEnabled by viewModel.glassEffectEnabled.collectAsState()
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

    AppScaffold(
        topBar = { AppTopBar(navigation = TopBarNavigation.Detail(onBack), title = title) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scroll.container)
                    .verticalScroll(rememberScrollState())
                    .padding(top = scroll.contentTop)
                    .padding(Spacing.lg)
                    .padding(bottom = scroll.contentBottom),
        ) {
            Text(
                text = "App Theme & Skin",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))

            ThemeCard(
                title = "System Default",
                subtitle = "Light or Dark, following your device",
                isSelected = currentAppSkin == null && currentAppThemeId == BuiltInThemes.SYSTEM_DEFAULT_ID,
                onClick = { viewModel.selectAppTheme(BuiltInThemes.SYSTEM_DEFAULT_ID) },
            )
            BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                ThemeCard(
                    title = theme.name,
                    subtitle = "Built-in ${if (theme.isDark) "Dark" else "Light"} Theme",
                    isSelected = currentAppSkin == null && currentAppThemeId == theme.id,
                    onClick = { viewModel.selectAppTheme(theme.id) },
                )
            }

            BuiltInThemes.ALL_BUILT_IN_SKINS.forEach { skin ->
                ThemeCard(
                    title = skin.name,
                    subtitle = "3D VST Studio Skin",
                    isSelected = currentAppSkin?.id == skin.id,
                    onClick = { viewModel.selectAppTheme(skin.id) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            GlassEffectSetting(
                enabled = glassEffectEnabled,
                onEnabledChange = viewModel::setGlassEffectEnabled,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Home Screen Widget Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))

            BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                ThemeCard(
                    title = theme.name,
                    subtitle = "Widget Theme",
                    isSelected = currentWidgetTheme.id == theme.id,
                    onClick = { viewModel.selectWidgetTheme(theme.id) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Library View Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))

            listOf(
                "GRID" to "2-Column Grid",
                "LIST" to "Single-Column List",
                "DRAWER" to "Side Navigation Drawer",
            ).forEach { (mode, label) ->
                ThemeCard(
                    title = label,
                    subtitle = "Layout View Preference",
                    isSelected = libraryLayout == mode,
                    onClick = { viewModel.selectLibraryLayout(mode) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Button(
                onClick = { fileLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text("Import Custom Package (.mtheme / .mskin)")
            }
        }
    }
}

/** Glass Effect on/off: a material for floating controls, independent of the theme. */
@Composable
private fun GlassEffectSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Glass Effect",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Translucent glass on the mini player, navigation and floating buttons. Works with any theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        // The whole row toggles; the switch only shows the state.
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun ThemeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs)
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
