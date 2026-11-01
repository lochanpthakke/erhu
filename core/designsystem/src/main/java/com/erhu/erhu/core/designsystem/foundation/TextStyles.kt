package com.erhu.erhu.core.designsystem.foundation

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

    /** Large title in a page header (album page). */
    val pageTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)

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

    /** Title line of a library list row (tracks, artists): semibold, lighter than [itemTitle]. */
    val listTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Secondary line of a library list row; one step larger than [itemSubtitle] for legibility. */
    val listSubtitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium

    /** Title under artwork in a grid tile (albums). */
    val tileTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    /** Secondary line under a grid tile title. */
    val tileSubtitle: TextStyle
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
