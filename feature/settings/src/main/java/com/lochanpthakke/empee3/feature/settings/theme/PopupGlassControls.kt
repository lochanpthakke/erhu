package com.lochanpthakke.empee3.feature.settings.theme

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
import com.lochanpthakke.empee3.core.designsystem.component.PopupGlassPreview
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.LocalPopupGlassOpacity
import com.lochanpthakke.empee3.feature.settings.component.SettingsGroup
import com.lochanpthakke.empee3.feature.settings.component.SettingsSection
import kotlin.math.roundToInt

@Composable
internal fun PopupGlassControls(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    var preview by remember { mutableFloatStateOf(opacity) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(opacity) { if (!dragging) preview = opacity }
    SettingsSection(title = "Liquid Glass", footer = "Adjust clear glass on navigation, Search and popup surfaces.") {
        SettingsGroup {
            row {
                Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
                    CompositionLocalProvider(LocalPopupGlassOpacity provides preview) { PopupGlassPreview() }
                    Text("Glass Strength: ${(preview * 100).roundToInt()}%", Modifier.padding(top = Spacing.md))
                    Text("Less Glass · More Glass", style = MaterialTheme.typography.bodySmall)
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
                    }) { Text("Reset to default") }
                }
            }
        }
    }
}
