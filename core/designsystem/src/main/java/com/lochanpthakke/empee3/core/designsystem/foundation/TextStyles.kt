package com.lochanpthakke.empee3.core.designsystem.foundation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Semantic text roles. Each maps onto the current Material type scale with
 * the weights the screens already used, so this names the hierarchy without
 * changing how text looks. Phase 2 can retune the scale in one place.
 */
object TextStyles {
    /** Top bar titles. */
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge

    /** Headings above groups of content ("Equalizer Bands", settings sections). */
    val sectionTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    /** Primary line of a row or card (song title, setting name). */
    val itemTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    /** Secondary line of a row or card (artist • album, descriptions). */
    val itemSubtitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall

    /** Small factual values (timestamps, counts, dB values). */
    val metadata: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall

    /** Explanatory paragraphs and empty-state messages. */
    val supporting: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge
}
