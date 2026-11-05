package com.erhu.erhu.feature.library

import androidx.compose.runtime.staticCompositionLocalOf
import com.erhu.erhu.feature.library.mix.MixKind
import com.erhu.erhu.feature.library.mix.SmartMix

/** Published by the app shell from its one persisted preference; previews retain the enabled default. */
val LocalVybeEnabled = staticCompositionLocalOf { true }

/** A hidden saved/requested section is never rendered, including while preferences are loading. */
fun LibrarySection.visibleWithVybe(enabled: Boolean): LibrarySection =
    if (!enabled && this == LibrarySection.VYBE) LibrarySection.TRACKS else this

fun isVybeMix(key: String): Boolean = key == MixKind.YOUR_SOUND.key || key == MixKind.A_LITTLE_DIFFERENT.key

internal fun visibleHomeMixes(
    mixes: List<SmartMix>,
    enabled: Boolean,
): List<SmartMix> = if (enabled) mixes else mixes.filterNot { isVybeMix(it.kind.key) }
