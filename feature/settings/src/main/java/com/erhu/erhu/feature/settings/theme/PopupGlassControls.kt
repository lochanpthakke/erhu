package com.erhu.erhu.feature.settings.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.PopupGlassPreview
import com.erhu.erhu.core.designsystem.theme.LocalPopupGlassOpacity
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun PopupGlassControls(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    SettingsSection(
        title = stringResource(CommonR.string.theme_liquid_glass_title),
        footer = stringResource(CommonR.string.theme_liquid_glass_footer),
    ) {
        SettingsGroup { row { GlassIntensityControl(opacity, onOpacityChange) } }
    }
}

/** Shared saved setting, with a compact preview for the bounded onboarding page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassIntensityControl(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    var preview by remember { mutableFloatStateOf(opacity) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(opacity) { if (!dragging) preview = opacity }
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
        CompositionLocalProvider(LocalPopupGlassOpacity provides preview) {
            PopupGlassPreview(
                modifier = if (compact) Modifier.weight(1f) else Modifier,
                compact = compact,
            )
        }
        Text(
            stringResource(CommonR.string.theme_liquid_glass_strength, LocaleFormatters.formatPercent(preview.toDouble(), locale)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!compact) Text(stringResource(CommonR.string.theme_liquid_glass_scale), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = preview,
            onValueChange = {
                dragging = true
                preview = it
                onOpacityChange(it)
            },
            onValueChangeFinished = {
                dragging = false
                onOpacityChange(preview)
            },
            valueRange = 0f..1f,
            enabled = enabled,
            thumb = { Box(Modifier.size(12.dp).background(activeColor, CircleShape)) },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val y = size.height / 2
                    drawLine(inactiveColor, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                    val start = if (rtl) size.width else 0f
                    val end = if (rtl) size.width * (1f - state.value) else size.width * state.value
                    drawLine(activeColor, Offset(start, y), Offset(end, y), size.height, StrokeCap.Round)
                }
            },
        )
        if (!compact) {
            TextButton(onClick = {
                preview = 0.5f
                onOpacityChange(0.5f)
            }) {
                Text(stringResource(CommonR.string.action_reset_to_default))
            }
        }
    }
}
