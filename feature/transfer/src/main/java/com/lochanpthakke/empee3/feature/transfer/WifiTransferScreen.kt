package com.lochanpthakke.empee3.feature.transfer

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.lochanpthakke.empee3.core.designsystem.component.AppScaffold
import com.lochanpthakke.empee3.core.designsystem.component.LocalCollapsingTitle
import com.lochanpthakke.empee3.core.designsystem.component.OverlayHeader
import com.lochanpthakke.empee3.core.designsystem.component.ReportTitleScroll
import com.lochanpthakke.empee3.core.designsystem.component.TopBarNavigation
import com.lochanpthakke.empee3.core.designsystem.component.forScrollingContent
import com.lochanpthakke.empee3.core.designsystem.component.rememberCollapsingTitleState
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass

private val QR_SIZE = 220.dp
private const val QR_PIXELS = 512
private const val SURFACE_ALPHA = 0.55f

/** Wi-Fi Transfer: send music from a computer's browser to this phone over the local network. */
@Composable
fun WifiTransferScreen(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WifiTransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val status = transferScreenStatus(state, items)
    val context = LocalContext.current

    val onStart = rememberStartAction(viewModel)
    val openAppSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    // The page title merges into the header button as the list scrolls.
    val collapsingTitle = rememberCollapsingTitleState()
    AppScaffold(
        topBar = {
            OverlayHeader(
                navigation = TopBarNavigation.TopLevel(onOpenDrawer),
                title = "Wi-Fi Transfer",
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
            item(key = "status") { StatusCard(status, state) }
            item(key = "action") {
                val running = state as? TransferServerState.Running
                if (running != null) {
                    ConnectCard(running, onStop = viewModel::stop)
                } else {
                    StartCard(
                        status = status,
                        starting = state == TransferServerState.Starting,
                        onStart = onStart,
                        onOpenSettings = openAppSettings,
                    )
                }
            }
            if (items.isNotEmpty()) {
                item(key = "counts") { CountsRow(items) }
                items(items.asReversed(), key = { it.id }) { TransferRow(it) }
            }
            item(key = "help") { HelpCard() }
        }
    }
}

/** Asks for whatever permission is missing (explained on screen first), then starts the session. */
@Composable
private fun rememberStartAction(viewModel: WifiTransferViewModel): () -> Unit {
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val required = viewModel.missingPermissions()
            if (required.all { results[it] == true }) viewModel.start() else viewModel.permissionDenied()
        }
    return {
        val missing = viewModel.missingPermissions()
        // Notifications are optional (the service runs either way); asked together with the required ones.
        val optional =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        if (missing.isEmpty()) viewModel.start() else permissions.launch((missing + optional).toTypedArray())
    }
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(EmPee3Glass.chromeColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_ALPHA)))
                .padding(Spacing.lg),
    ) { content() }
}

@Composable
private fun StartCard(
    status: TransferScreenStatus,
    starting: Boolean,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Panel {
        Text(
            text =
                "Starting opens a temporary, private transfer page on this Wi-Fi network only. It stops when you stop it, " +
                    "when Wi-Fi disconnects, or after 10 minutes without activity.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (Build.VERSION.SDK_INT >= WifiTransferCoordinator.ANDROID_17) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Android asks for the Nearby devices permission so your computer can reach the phone on your network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onStart, enabled = !starting) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(if (starting) "Starting…" else "Start transfer")
            }
            if (status == TransferScreenStatus.PERMISSION_DENIED) {
                OutlinedButton(onClick = onOpenSettings) { Text("App settings") }
            }
        }
    }
}

@Composable
private fun ConnectCard(
    running: TransferServerState.Running,
    onStop: () -> Unit,
) {
    val qr = rememberQrBitmap(running.pairingUrl)
    Panel {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Always dark on white with a quiet zone, whatever the theme: that is what scanners read reliably.
            if (qr != null) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(Spacing.sm)
                            .semantics { contentDescription = "QR code for the transfer page" },
                ) {
                    Image(bitmap = qr, contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(QR_SIZE))
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text("Scan with your computer's camera app, or open:", style = MaterialTheme.typography.bodyMedium)
            SelectionContainer {
                Text(running.address, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("and enter the code", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                running.pairingCode.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.semantics { contentDescription = "Pairing code ${running.pairingCode.toList().joinToString(" ")}" },
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(onClick = onStop) { Text("Stop transfer") }
        }
    }
}

@Composable
private fun rememberQrBitmap(content: String): ImageBitmap? =
    remember(content) {
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS, mapOf(EncodeHintType.MARGIN to 1))
            val pixels =
                IntArray(matrix.width * matrix.height) { i -> if (matrix.get(i % matrix.width, i / matrix.width)) BLACK else WHITE }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
    }

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
