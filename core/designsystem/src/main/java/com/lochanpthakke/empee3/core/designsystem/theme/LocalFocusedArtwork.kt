package com.lochanpthakke.empee3.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Holds the artwork URI of the page currently in focus (e.g. an Album or Artist
 * detail screen). When present, dynamic themes (Dominant Color / Gradient UI) adapt
 * to the viewed album/artist rather than the playing track.
 */
val LocalFocusedArtworkUri = staticCompositionLocalOf<String?> { null }

val LocalSetFocusedArtworkUri = staticCompositionLocalOf<(String?) -> Unit> { {} }
