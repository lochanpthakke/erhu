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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.feature.transfer.server.TransferItem
import com.erhu.erhu.feature.transfer.server.TransferStatus
import com.erhu.erhu.core.common.R as CommonR

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

@Composable
private fun statusText(
    status: TransferScreenStatus,
    reason: StopReason?,
): Triple<ImageVector, String, String> {
    val (titleRes, detailRes) = statusStringRes(status, reason)
    return Triple(statusIcon(status), stringResource(titleRes), stringResource(detailRes))
}

private fun statusIcon(status: TransferScreenStatus): ImageVector =
    when (status) {
        TransferScreenStatus.STARTING -> Icons.Default.HourglassTop
        TransferScreenStatus.WAITING, TransferScreenStatus.STOPPED -> Icons.Default.Wifi
        TransferScreenStatus.IN_PROGRESS -> Icons.Default.CloudUpload
        TransferScreenStatus.COMPLETED -> Icons.Default.CheckCircle
        TransferScreenStatus.WIFI_DISCONNECTED -> Icons.Default.WifiOff
        TransferScreenStatus.PERMISSION_DENIED -> Icons.Default.Lock
        TransferScreenStatus.FAILED -> Icons.Default.ErrorOutline
    }

private fun statusStringRes(
    status: TransferScreenStatus,
    reason: StopReason?,
): Pair<Int, Int> =
    when (status) {
        TransferScreenStatus.STARTING ->
            CommonR.string.transfer_status_starting_title to CommonR.string.transfer_status_starting_detail
        TransferScreenStatus.WAITING ->
            CommonR.string.transfer_status_waiting_title to CommonR.string.transfer_status_waiting_detail
        TransferScreenStatus.IN_PROGRESS ->
            CommonR.string.transfer_status_receiving_title to CommonR.string.transfer_status_receiving_detail
        TransferScreenStatus.COMPLETED ->
            CommonR.string.transfer_status_completed_title to CommonR.string.transfer_status_completed_detail
        TransferScreenStatus.WIFI_DISCONNECTED -> {
            val title =
                if (reason == StopReason.WIFI_LOST) {
                    CommonR.string.transfer_status_wifi_lost_title
                } else {
                    CommonR.string.transfer_status_no_wifi_title
                }
            title to CommonR.string.transfer_status_wifi_detail
        }
        TransferScreenStatus.PERMISSION_DENIED -> {
            val detail =
                if (reason == StopReason.STORAGE_PERMISSION_DENIED) {
                    CommonR.string.transfer_status_permission_storage_detail
                } else {
                    CommonR.string.transfer_status_permission_nearby_detail
                }
            CommonR.string.transfer_status_permission_title to detail
        }
        TransferScreenStatus.FAILED ->
            CommonR.string.transfer_status_failed_title to CommonR.string.transfer_status_failed_detail
        TransferScreenStatus.STOPPED -> {
            val detail =
                when (reason) {
                    StopReason.IDLE_TIMEOUT -> CommonR.string.transfer_status_idle_timeout_detail
                    StopReason.SESSION_EXPIRED, StopReason.SYSTEM_TIME_LIMIT ->
                        CommonR.string.transfer_status_time_limit_detail
                    else -> CommonR.string.transfer_status_stopped_default_detail
                }
            CommonR.string.transfer_status_stopped_title to detail
        }
    }

@Composable
internal fun CountsRow(items: List<TransferItem>) {
    val counts = transferCounts(items)
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.padding(horizontal = Spacing.xs)) {
        Text(
            stringResource(CommonR.string.transfer_counts_saved, LocaleFormatters.formatInteger(counts.saved, locale)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (counts.duplicates > 0) {
            Text(
                stringResource(
                    CommonR.string.transfer_counts_duplicate,
                    LocaleFormatters.formatInteger(counts.duplicates, locale),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (counts.failed > 0) {
            Text(
                stringResource(
                    CommonR.string.transfer_counts_failed,
                    LocaleFormatters.formatInteger(counts.failed, locale),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun TransferRow(item: TransferItem) {
    val context = LocalContext.current
    val locale = AppLocaleManager.activeLocale(context)
    val fraction = if (item.sizeBytes > 0) (item.receivedBytes.toFloat() / item.sizeBytes).coerceIn(0f, 1f) else 0f
    val (label, color) =
        when (item.status) {
            TransferStatus.RECEIVING ->
                LocaleFormatters.formatPercentInt((fraction * PERCENT).toInt(), locale) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            TransferStatus.FINALIZING ->
                stringResource(CommonR.string.transfer_item_saving) to MaterialTheme.colorScheme.onSurfaceVariant
            TransferStatus.COMPLETED ->
                stringResource(CommonR.string.transfer_item_saved) to MaterialTheme.colorScheme.primary
            TransferStatus.DUPLICATE ->
                stringResource(CommonR.string.transfer_item_duplicate) to MaterialTheme.colorScheme.onSurfaceVariant
            TransferStatus.FAILED ->
                stringResource(CommonR.string.transfer_item_failed) to MaterialTheme.colorScheme.error
            TransferStatus.CANCELLED ->
                stringResource(CommonR.string.transfer_item_cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
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
        Text(
            LocaleFormatters.formatFileSize(context, item.sizeBytes, locale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
internal fun HelpCard() {
    Panel {
        Text(
            stringResource(CommonR.string.transfer_help_how_to_connect),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HelpLine(stringResource(CommonR.string.transfer_help_step_1))
        HelpLine(stringResource(CommonR.string.transfer_help_step_2))
        HelpLine(stringResource(CommonR.string.transfer_help_step_3))
        HelpLine(stringResource(CommonR.string.transfer_help_step_4, MediaStoreUploadSink.FOLDER))
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(CommonR.string.transfer_help_troubleshooting_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HelpLine(stringResource(CommonR.string.transfer_help_trouble_1))
        HelpLine(stringResource(CommonR.string.transfer_help_trouble_2))
        HelpLine(stringResource(CommonR.string.transfer_help_trouble_3, MediaStoreUploadSink.FOLDER))
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(CommonR.string.transfer_help_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HelpLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
}
