package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.theme.LocalGlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.LocalMenuGlassTokens
import com.lochanpthakke.empee3.core.designsystem.theme.LocalPlaybackArtworkColors

/**
 * Shared anchored action menu. Place inside the same Box as the trigger.
 * One interruptible surface spring moves every row together; the popup stays mounted for exit.
 * A focusable platform popup owns Back/outside dismissal, clipping and accessibility focus.
 *
 * Popup windows cannot safely sample positionInRoot-based GlassBackdrop layers from another
 * window. Reuse layered, playback-tinted glass instead of blurring text or copying the app every frame.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Options",
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    val dismiss by rememberUpdatedState(onDismissRequest)
    var actionTaken by remember(expanded) { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<PendingMenuConfirmation?>(null) }
    val interaction =
        GlassMenuInteraction(expanded && !actionTaken) { prompt, action ->
            if (expanded && !actionTaken) {
                actionTaken = true
                dismiss()
                if (prompt == null) action() else confirmation = PendingMenuConfirmation(prompt, action)
            }
        }
    if (visible.currentState || visible.targetState) {
        GlassMenuPopup(visible, { if (expanded) dismiss() }, modifier, label, interaction, content)
    }
    confirmation?.let { pending ->
        GlassAlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(pending.prompt.title) },
            text = { Text(pending.prompt.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        pending.action()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(pending.prompt.confirmLabel) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmation = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GlassMenuPopup(
    visible: MutableTransitionState<Boolean>,
    dismiss: () -> Unit,
    modifier: Modifier,
    label: String,
    interaction: GlassMenuInteraction,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(TransformOrigin(1f, 0f)) }
    val position =
        remember(density) {
            GlassMenuPositionProvider(
                margin = with(density) { 12.dp.roundToPx() },
                gap = with(density) { 4.dp.roundToPx() },
                contentInset = with(density) { 16.dp.roundToPx() },
            ) { origin = it }
        }
    val transition = rememberTransition(visible, label = "Glass menu")
    val scale by transition.animateFloat(
        // A tighter threshold lets this small scale change actually overshoot before settling.
        transitionSpec = { if (targetState) Motion.expressive(visibilityThreshold = 0.001f) else Motion.fade() },
        label = "Menu spring",
    ) { if (it) 1f else 0.9f }
    val alpha by transition.animateFloat(transitionSpec = { Motion.fade() }, label = "Menu fade") { if (it) 1f else 0f }
    val travel by transition.animateFloat(
        transitionSpec = { if (targetState) Motion.press() else Motion.fade() },
        label = "Menu settling",
    ) { if (it) 0f else 6f }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.onSurface.luminance() > scheme.surface.luminance()
    val glass = LocalMenuGlassTokens.current ?: LocalGlassTokens.current
    val colors =
        glassMenuColors(dark, glass?.panelFillColor(strong = true)?.alpha, LocalPlaybackArtworkColors.current.value, scheme.surface)
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 80.dp).coerceIn(48.dp, 600.dp)
    Popup(popupPositionProvider = position, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        val focusManager = LocalFocusManager.current
        // Keep shadow and the small spring overshoot inside the platform popup's clipped window.
        Box(Modifier.padding(16.dp)) {
            LiquidMenuSurface(
                modifier =
                    modifier
                        .widthIn(min = 240.dp, max = 320.dp)
                        .width(IntrinsicSize.Max)
                        .heightIn(max = maxHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = origin
                            translationY = travel.dp.toPx() * if (origin.pivotFractionY <= 0.5f) -1f else 1f
                        }.semantics { paneTitle = label }
                        .menuKeys(focusManager, dismiss),
                glass = glass,
                colors = colors,
            ) {
                CompositionLocalProvider(LocalGlassMenuInteraction provides interaction, LocalContentColor provides colors.content) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = content)
                }
            }
        }
    }
}

private fun Modifier.menuKeys(
    focusManager: FocusManager,
    dismiss: () -> Unit,
): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Next)
                Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Previous)
                Key.Escape -> {
                    dismiss()
                    true
                }
                else -> false
            }
        }
    }

internal data class GlassMenuColors(
    val surface: Color,
    val content: Color,
    val reflection: Color = Color.Transparent,
    val edge: Color = Color.Transparent,
)

private data class PendingMenuConfirmation(
    val prompt: GlassMenuConfirmation,
    val action: () -> Unit,
)
