package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.GlassLevel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPanel
import com.lochanpthakke.empee3.core.designsystem.theme.glassPressEffect
import com.lochanpthakke.empee3.core.designsystem.theme.glassRippleConfiguration

/** Visible size of the round header button (Material icon-button container; touch target stays 48 dp). */
private val HEADER_BUTTON_SIZE = 40.dp

/** Scrim opacity behind the status bar: keeps the system icons legible over any content. */
private const val SCRIM_TOP_ALPHA = 0.92f

/** Scrim opacity around the middle of the header, behind the button. */
private const val SCRIM_MID_ALPHA = 0.6f

/** Where (0..1 of the header height) the scrim reaches [SCRIM_MID_ALPHA]. */
private const val SCRIM_MID_STOP = 0.5f

/**
 * Minimal edge-to-edge header that floats over scrolling content: only a round
 * navigation button, no title and no bar. Behind it a scrim in the screen's own
 * background colour fades from nearly opaque (status bar) to fully transparent,
 * so content scrolls underneath without a hard edge. Under a glass theme the
 * scrim uses the glass environment colour and the button is a glass disc;
 * other themes use their background and surface colours.
 *
 * Owns the status-bar inset like [AppTopBar], so [AppScaffold] reports its full
 * height as top padding. Pair it with `forScrollingContent(topOverlay = true)`.
 */
@Composable
fun OverlayHeader(
    navigation: TopBarNavigation,
    modifier: Modifier = Modifier,
) {
    val scrim = EmPee3Glass.backdropColor(MaterialTheme.colorScheme.background)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to scrim.copy(alpha = SCRIM_TOP_ALPHA),
                            SCRIM_MID_STOP to scrim.copy(alpha = SCRIM_MID_ALPHA),
                            1f to Color.Transparent,
                        ),
                    )
                }.windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        val colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = EmPee3Glass.containerColor(MaterialTheme.colorScheme.surfaceVariant),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        // Glass on: a frosted glass disc behind a transparent button. Glass off:
        // the button's own surface-variant container, exactly as before.
        val interactionSource = remember { MutableInteractionSource() }
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(HEADER_BUTTON_SIZE)
                        .glassPressEffect(interactionSource, CircleShape)
                        .glassPanel(CircleShape, level = GlassLevel.Strong, elevated = false),
            )
            // Under glass the disc shows the press; the button's ripple is switched off.
            CompositionLocalProvider(LocalRippleConfiguration provides glassRippleConfiguration()) {
                when (navigation) {
                    is TopBarNavigation.TopLevel ->
                        IconButton(onClick = navigation.onOpenDrawer, colors = colors, interactionSource = interactionSource) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open navigation drawer")
                        }
                    is TopBarNavigation.Detail ->
                        IconButton(onClick = navigation.onBack, colors = colors, interactionSource = interactionSource) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                }
            }
        }
    }
}
