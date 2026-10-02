package com.lochanpthakke.empee3.core.designsystem.component

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Radius
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing

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

    /**
     * Capsule for every bottom-chrome surface, in every theme and with or without
     * glass: a fixed corner radius reads as a rounded rectangle on these short,
     * wide surfaces (and as a near-square on Cyberpunk's 2 dp radius).
     */
    fun shape(): Shape = Radius.pillShape
}
