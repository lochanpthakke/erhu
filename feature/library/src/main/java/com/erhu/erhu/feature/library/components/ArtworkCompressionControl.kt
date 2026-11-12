package com.erhu.erhu.feature.library.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.model.ArtworkCompressionState
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun ArtworkCompressionControl(
    state: ArtworkCompressionState,
    onCompress: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val localizedContext = AppLocaleManager.localizedContext(context)
    val locale = AppLocaleManager.activeLocale(localizedContext)
    val compressedBytes = state.compressedBytes
    Column {
        when {
            state.running -> {
                Text(
                    stringResource(
                        CommonR.string.live_cover_compress_progress_percent,
                        state.message,
                        LocaleFormatters.formatInteger(state.progress, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(CommonR.string.action_cancel)) }
                }
            }
            compressedBytes != null -> {
                ArtworkCompressionSummary(
                    localizedContext = localizedContext,
                    locale = locale,
                    originalBytes = state.originalBytes,
                    compressedBytes = compressedBytes,
                )
            }
            else -> {
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
                if (state.available) {
                    Text(
                        stringResource(CommonR.string.live_cover_compress_full_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onCompress, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(CommonR.string.live_cover_compress_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkCompressionSummary(
    localizedContext: android.content.Context,
    locale: java.util.Locale,
    originalBytes: Long,
    compressedBytes: Long,
) {
    val saved = originalBytes - compressedBytes
    val percent = if (originalBytes > 0) 100.0 * saved / originalBytes else 0.0
    Text(
        stringResource(
            CommonR.string.live_cover_compress_progress,
            Formatter.formatShortFileSize(localizedContext, originalBytes),
            Formatter.formatShortFileSize(localizedContext, compressedBytes),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        stringResource(
            CommonR.string.live_cover_compress_saved,
            Formatter.formatShortFileSize(localizedContext, saved),
            LocaleFormatters.formatDecimal(percent, 1, locale),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}
