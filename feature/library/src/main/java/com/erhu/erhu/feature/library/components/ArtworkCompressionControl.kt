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
import com.erhu.erhu.core.model.ArtworkCompressionState

@Composable
internal fun ArtworkCompressionControl(
    state: ArtworkCompressionState,
    onCompress: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val compressedBytes = state.compressedBytes
    Column {
        when {
            state.running -> {
                Text("${state.message} ${state.progress}%", style = MaterialTheme.typography.bodySmall)
                Row {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            compressedBytes != null -> {
                val saved = state.originalBytes - compressedBytes
                val percent = if (state.originalBytes > 0) 100.0 * saved / state.originalBytes else 0.0
                Text(
                    "${Formatter.formatShortFileSize(context, state.originalBytes)} → " +
                        Formatter.formatShortFileSize(context, compressedBytes),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Saved ${Formatter.formatShortFileSize(context, saved)} (${"%.1f".format(percent)}%)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> {
                if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
                if (state.available) {
                    Text(
                        "Optional: smaller HEVC video at the same resolution. The square cover is replaced only after validation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onCompress, modifier = Modifier.fillMaxWidth()) {
                        Text("Compress square cover")
                    }
                }
            }
        }
    }
}
