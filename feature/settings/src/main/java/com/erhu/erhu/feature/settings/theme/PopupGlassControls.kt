package com.erhu.erhu.feature.settings.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.PopupGlassPreview
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.theme.LocalPopupGlassOpacity
import com.erhu.erhu.feature.settings.component.SettingsGroup
import com.erhu.erhu.feature.settings.component.SettingsSection
import com.erhu.erhu.core.common.R as CommonR

@Composable
internal fun PopupGlassControls(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val locale = AppLocaleManager.activeLocale(context)
    var preview by remember { mutableFloatStateOf(opacity) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(opacity) { if (!dragging) preview = opacity }
    SettingsSection(
        title = stringResource(CommonR.string.theme_liquid_glass_title),
        footer = stringResource(CommonR.string.theme_liquid_glass_footer),
    ) {
        SettingsGroup {
            row {
                Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                    CompositionLocalProvider(LocalPopupGlassOpacity provides preview) { PopupGlassPreview() }
                    Text(
                        stringResource(
                            CommonR.string.theme_liquid_glass_strength,
                            LocaleFormatters.formatPercent(preview.toDouble(), locale),
                        ),
                        Modifier.padding(top = Spacing.md),
                    )
                    Text(
                        stringResource(CommonR.string.theme_liquid_glass_scale),
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                    )
                    TextButton(onClick = {
                        preview = 0.5f
                        onOpacityChange(0.5f)
                    }) { Text(stringResource(CommonR.string.action_reset_to_default)) }
                }
            }
        }
    }
}
