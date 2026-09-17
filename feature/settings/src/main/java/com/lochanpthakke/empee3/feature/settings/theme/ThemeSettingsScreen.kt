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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.theme.BuiltInThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
fun ThemeSettingsScreen(
    viewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentAppTheme by viewModel.appTheme.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Theme & Appearance") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(
                text = "App Theme & Skin",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                ThemeCard(
                    title = theme.name,
                    subtitle = "Built-in ${if (theme.isDark) "Dark" else "Light"} Theme",
                    isSelected = currentAppSkin == null && currentAppTheme.id == theme.id,
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Home Screen Widget Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            BuiltInThemes.ALL_BUILT_IN_THEMES.forEach { theme ->
                ThemeCard(
                    title = theme.name,
                    subtitle = "Widget Theme",
                    isSelected = currentWidgetTheme.id == theme.id,
                    onClick = { viewModel.selectWidgetTheme(theme.id) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Library View Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { fileLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Custom Package (.mtheme / .mskin)")
            }
        }
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
                .padding(vertical = 4.dp)
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
                    .padding(16.dp),
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
