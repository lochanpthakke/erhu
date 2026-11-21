package com.erhu.erhu.feature.library.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erhu.erhu.core.database.entity.RemoteLibraryFolderEntity
import com.erhu.erhu.core.designsystem.component.ActionButton
import com.erhu.erhu.core.designsystem.component.AppScaffold
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.component.GroupDivider
import com.erhu.erhu.core.designsystem.component.GroupRow
import com.erhu.erhu.core.designsystem.component.GroupToggleRow
import com.erhu.erhu.core.designsystem.component.LocalCollapsingTitle
import com.erhu.erhu.core.designsystem.component.OverlayHeader
import com.erhu.erhu.core.designsystem.component.ProgressBar
import com.erhu.erhu.core.designsystem.component.ReportTitleScroll
import com.erhu.erhu.core.designsystem.component.SectionHeading
import com.erhu.erhu.core.designsystem.component.TopBarNavigation
import com.erhu.erhu.core.designsystem.component.forScrollingContent
import com.erhu.erhu.core.designsystem.component.groupSurface
import com.erhu.erhu.core.designsystem.component.rememberCollapsingTitleState
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteSource

/** Remote Libraries: the connected music servers, and the form to connect another. */
@Composable
fun RemoteLibrariesScreen(
    onOpenDrawer: () -> Unit,
    onOpenSource: (RemoteSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemoteLibrariesViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val syncedFolders by viewModel.syncedFolders.collectAsStateWithLifecycle()
    val addServer by viewModel.addServer.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<RemoteSource?>(null) }
    val driveState by viewModel.driveState.collectAsStateWithLifecycle()
    var driveConsentLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>? = null
    val driveConsent =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .StartIntentSenderForResult(),
        ) { result ->
            viewModel.onDriveConsent(
                resultCode = result.resultCode,
                data = result.data,
                launchConsent = { pendingIntent ->
                    driveConsentLauncher?.launch(
                        androidx.activity.result.IntentSenderRequest
                            .Builder(pendingIntent.intentSender)
                            .build(),
                    )
                },
            )
        }
    driveConsentLauncher = driveConsent

    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                title = "Remote Libraries",
                collapsingTitle = collapsingTitle,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val scroll = innerPadding.forScrollingContent(topOverlay = true)
        val listState = rememberLazyListState()
        CompositionLocalProvider(LocalCollapsingTitle provides collapsingTitle) { ReportTitleScroll(listState) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(scroll.container),
            contentPadding =
                PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = scroll.contentTop + Spacing.sm,
                    bottom = scroll.contentBottom + Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (sources.isNotEmpty()) {
                item(key = "connected-heading") { SectionHeading("Connected") }
                items(sources, key = { it.id }) { source ->
                    val sourceFolders = syncedFolders[source.id].orEmpty()
                    Column(Modifier.fillMaxWidth().groupSurface()) {
                        GroupRow(
                            title = source.name,
                            summary = source.summary(),
                            icon = if (source.provider == RemoteProvider.GOOGLE_DRIVE) Icons.Default.Cloud else Icons.Default.Dns,
                            trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            onClick = { onOpenSource(source) },
                        )
                        if (sourceFolders.isNotEmpty()) {
                            GroupDivider()
                            GroupRow(
                                title = "Refresh synced music",
                                summary = sourceSyncSummary(sourceFolders),
                                icon = Icons.Default.Refresh,
                                enabled = sourceFolders.none { it.second.running },
                                onClick = { viewModel.syncSource(source.id) },
                            )
                        }
                        GroupDivider()
                        GroupRow(title = "Disconnect", icon = Icons.Default.LinkOff, onClick = { removing = source })
                    }
                }
            }
            item(key = "drive") {
                val driveAvailable = RemoteLibraryFeatures.googleDriveAvailable
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeading("Google Drive")
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .groupSurface()
                                .alpha(if (driveAvailable) 1f else UNAVAILABLE_ALPHA)
                                .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (!driveAvailable) {
                            Text(
                                text = "Currently Unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text =
                                "Browse the folders of a Google account and play its audio files. erhu asks for read-only access: " +
                                    "it can never change or delete anything in your Drive.",
                            style = TextStyles.supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        driveState.error?.let { Text(text = it, style = TextStyles.supporting, color = MaterialTheme.colorScheme.error) }
                        if (driveState.connecting) ProgressBar()
                        ActionButton(
                            text = "Connect a Google account",
                            primary = true,
                            enabled = driveAvailable && !driveState.connecting,
                            onClick = {
                                viewModel.connectDrive {
                                    driveConsent.launch(
                                        androidx.activity.result.IntentSenderRequest
                                            .Builder(it.intentSender)
                                            .build(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item(key = "add") { AddServerForm(addServer, viewModel::connect) }
        }
    }

    removing?.let { source ->
        GlassAlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Disconnect ${source.name}?") },
            text = {
                Text(
                    if (source.provider == RemoteProvider.GOOGLE_DRIVE) {
                        "erhu forgets this account and its tracks. Nothing in your Drive is changed. To withdraw the permission " +
                            "itself, remove erhu under Third-party access in your Google Account."
                    } else {
                        "erhu forgets this server and its saved password. Its tracks leave your library. Nothing on the server is changed."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(source)
                    removing = null
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

/** The Google Drive section, greyed out in builds that cannot sign in to Google. */
private const val UNAVAILABLE_ALPHA = 0.38f

private fun sourceSyncSummary(folders: List<Pair<RemoteLibraryFolderEntity, FolderSyncStatus>>): String {
    val running = folders.map { it.second }.filter { it.running }
    val failure = folders.firstNotNullOfOrNull { it.second.message }
    val countLabel = if (folders.size == 1) "1 synced folder" else "${folders.size} synced folders"
    return when {
        running.isNotEmpty() -> {
            val found = running.sumOf { it.found }
            val tagged = running.sumOf { it.tagged }
            if (found == 0) "Looking for music..." else "Syncing: $found songs found, $tagged read"
        }
        failure != null -> "Last sync failed: $failure. Tap to try again"
        else -> "$countLabel • Tap to refresh now"
    }
}

private fun RemoteSource.summary(): String {
    if (provider == RemoteProvider.GOOGLE_DRIVE) return "Google Drive • Read-only"
    val who = listOf(account?.takeIf { it.isNotBlank() } ?: "API key", "Navidrome / Subsonic").joinToString(" • ")
    return if (allowInsecure) "$who • Not encrypted" else who
}

@Composable
private fun AddServerForm(
    state: AddServerState,
    onConnect: (address: String, username: String, password: String, allowInsecure: Boolean, useApiKey: Boolean) -> Unit,
) {
    // Plain remember on purpose: a password must not be written into saved instance state.
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var allowInsecure by remember { mutableStateOf(false) }
    var useApiKey by remember { mutableStateOf(false) }
    LaunchedEffect(state.added) {
        if (state.added > 0) {
            address = ""
            username = ""
            password = ""
            allowInsecure = false
        }
    }
    val insecure = address.trim().startsWith("http://", ignoreCase = true)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeading("Add a Navidrome or Subsonic server")
        Column(
            modifier = Modifier.fillMaxWidth().groupSurface().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Server address") },
                placeholder = { Text("music.example.com") },
                singleLine = true,
                enabled = !state.connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            GroupToggleRow(
                title = "Sign in with an API key",
                summary = "For servers that issue API keys (OpenSubsonic)",
                checked = useApiKey,
                onCheckedChange = { useApiKey = it },
                enabled = !state.connecting,
            )
            if (!useApiKey) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("User name") },
                    singleLine = true,
                    enabled = !state.connecting,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (useApiKey) "API key" else "Password") },
                singleLine = true,
                enabled = !state.connecting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (insecure) {
                Text(
                    text =
                        "This address uses http://, which is not encrypted. Anyone on the same network can see your " +
                            "user name, intercept your sign-in and see what you play. Use it only on a network you trust.",
                    style = TextStyles.supporting,
                    color = MaterialTheme.colorScheme.error,
                )
                GroupToggleRow(
                    title = "Allow an unencrypted connection",
                    summary = "Only for this server",
                    checked = allowInsecure,
                    onCheckedChange = { allowInsecure = it },
                    enabled = !state.connecting,
                )
            }
            state.error?.let { Text(text = it, style = TextStyles.supporting, color = MaterialTheme.colorScheme.error) }
            if (state.connecting) ProgressBar()
            ActionButton(
                text = if (state.connecting) "Connecting…" else "Connect",
                onClick = { onConnect(address, username, password, insecure && allowInsecure, useApiKey) },
                primary = true,
                enabled = !state.connecting,
            )
            Text(
                text =
                    "erhu checks the server before saving anything. Your password or key is stored encrypted on this " +
                        "device and is sent only to this server.",
                style = TextStyles.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
