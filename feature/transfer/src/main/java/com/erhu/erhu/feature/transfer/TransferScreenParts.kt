package com.erhu.erhu.feature.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.feature.transfer.server.TransferItem
import com.erhu.erhu.feature.transfer.server.TransferStatus
import java.util.Locale

@Composable
internal fun StatusCard(
    status: TransferScreenStatus,
    state: TransferServerState,
) {
    val (icon, title, detail) = statusText(status, (state as? TransferServerState.Stopped)?.reason)
    val tint =
        when (status) {
            TransferScreenStatus.FAILED,
            TransferScreenStatus.PERMISSION_DENIED,
            TransferScreenStatus.WIFI_DISCONNECTED,
            -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint) }
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun statusText(
    status: TransferScreenStatus,
    reason: StopReason?,
): Triple<ImageVector, String, String> =
    when (status) {
        TransferScreenStatus.STARTING ->
            Triple(
                Icons.Default.HourglassTop,
                "Starting…",
                "Opening a private transfer session on this Wi-Fi.",
            )
        TransferScreenStatus.WAITING ->
            Triple(
                Icons.Default.Wifi,
                "Waiting for a computer",
                "Open the address below in a browser on the same Wi-Fi.",
            )
        TransferScreenStatus.IN_PROGRESS ->
            Triple(
                Icons.Default.CloudUpload,
                "Receiving music",
                "Keep both devices on this Wi-Fi until it finishes.",
            )
        TransferScreenStatus.COMPLETED ->
            Triple(
                Icons.Default.CheckCircle,
                "Transfer finished",
                "Send more from the browser, or stop when you are done.",
            )
        TransferScreenStatus.WIFI_DISCONNECTED ->
            Triple(
                Icons.Default.WifiOff,
                if (reason == StopReason.WIFI_LOST) "Wi-Fi disconnected" else "Not connected to Wi-Fi",
                "Connect the phone to the same Wi-Fi as your computer, then start again.",
            )
        TransferScreenStatus.PERMISSION_DENIED ->
            Triple(
                Icons.Default.Lock,
                "Permission needed",
                if (reason == StopReason.STORAGE_PERMISSION_DENIED) {
                    "erhu needs storage access to save the music it receives."
                } else {
                    "erhu needs the Nearby devices (local network) permission to let your computer connect."
                },
            )
        TransferScreenStatus.FAILED ->
            Triple(
                Icons.Default.ErrorOutline,
                "Could not start",
                "The transfer server could not open a port. Try again.",
            )
        TransferScreenStatus.STOPPED ->
            Triple(
                Icons.Default.Wifi,
                "Transfer is off",
                when (reason) {
                    StopReason.IDLE_TIMEOUT -> "Stopped after 10 minutes without activity."
                    StopReason.SESSION_EXPIRED, StopReason.SYSTEM_TIME_LIMIT -> "The session reached its time limit."
                    else -> "Send songs and folders from a computer's browser straight to this phone."
                },
            )
    }

@Composable
internal fun CountsRow(items: List<TransferItem>) {
    val counts = transferCounts(items)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(horizontal = Spacing.xs)) {
        Text("${counts.saved} saved", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (counts.duplicates > 0) Text("${counts.duplicates} already on phone", style = MaterialTheme.typography.labelLarge)
        if (counts.failed >
            0
        ) {
            Text("${counts.failed} not saved", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun TransferRow(item: TransferItem) {
    val fraction = if (item.sizeBytes > 0) (item.receivedBytes.toFloat() / item.sizeBytes).coerceIn(0f, 1f) else 0f
    val (label, color) =
        when (item.status) {
            TransferStatus.RECEIVING -> "${(fraction * PERCENT).toInt()}%" to MaterialTheme.colorScheme.onSurfaceVariant
            TransferStatus.FINALIZING -> "Saving…" to MaterialTheme.colorScheme.onSurfaceVariant
            TransferStatus.COMPLETED -> "Saved" to MaterialTheme.colorScheme.primary
            TransferStatus.DUPLICATE -> "Already on phone" to MaterialTheme.colorScheme.onSurfaceVariant
            TransferStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
            TransferStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
        Text(formatSize(item.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!item.status.isTerminal) {
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        item.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (item.status ==
                        TransferStatus.FAILED
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

private const val PERCENT = 100

@Suppress("MagicNumber")
private fun formatSize(bytes: Long): String =
    when {
        bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.ROOT, "%.0f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

@Composable
internal fun HelpCard() {
    Panel {
        Text("How to connect", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HelpLine("1. Put the phone and the computer on the same Wi-Fi network.")
        HelpLine("2. Tap Start transfer, then scan the QR code or type the address in the computer's browser.")
        HelpLine("3. Choose files or a folder (or drag them in) and press Send to phone.")
        HelpLine("Music is saved to Music/${MediaStoreUploadSink.FOLDER} and appears in your library shortly after.")
        Spacer(Modifier.height(Spacing.md))
        Text("If the page does not load", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HelpLine("Check both devices use the same network (not a guest network: those often block devices from seeing each other).")
        HelpLine("Turn off a VPN on either device, and type the address exactly, starting with http://.")
        HelpLine("If you limited library scanning to a custom folder in Settings, include Music/${MediaStoreUploadSink.FOLDER}.")
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Privacy: the page works only on your local network, only while this screen shows it as on, " +
                "and only with this session's code. " +
                "It uses plain HTTP, so on a shared or untrusted Wi-Fi others on the network could see what is sent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HelpLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
}
