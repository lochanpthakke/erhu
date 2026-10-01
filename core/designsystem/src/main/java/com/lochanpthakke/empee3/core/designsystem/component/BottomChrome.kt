package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Glass
import com.lochanpthakke.empee3.core.designsystem.theme.EmPee3Theme

/**
 * Shared geometry for the floating surfaces of the shell's bottom chrome (mini
 * player, library navigation), so they read as one stacked control system:
 * same margins, same corner radius, a small gap between layers.
 */
object BottomChrome {
    /** Outer side margin; tighter than the screen gutter on purpose (floating surfaces). */
    val horizontalMargin = Spacing.sm

    /** Space between two stacked chrome surfaces. */
    val gap = Spacing.xs

    /** Shadow depth of chrome surfaces in non-glass themes. */
    val elevation = 6.dp

    /** Corner shape: the theme's mini-player radius under glass, the medium radius otherwise. */
    @Composable
    @ReadOnlyComposable
    fun shape(): Shape =
        if (EmPee3Glass.isActive) {
            RoundedCornerShape(EmPee3Theme.shapes.miniPlayerCornerRadiusDp.dp)
        } else {
            Radius.mediumShape
        }
}
