package com.erhu.erhu.core.datastore.repository

import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.designsystem.theme.BuiltInThemes

/**
 * Migrates selections of the removed Glassmorphism theme, or returns null when
 * there is nothing to migrate. The app theme becomes System Default with the
 * Glass Effect on (the closest match to what the user chose); a widget using it
 * becomes Default Dark, since widgets have no System Default. Everything else in
 * the proto is kept as is.
 */
internal fun PlaybackStateProto.migratedThemeSelection(): PlaybackStateProto? {
    val legacy = BuiltInThemes.LEGACY_GLASSMORPHISM_ID
    val appWasGlass = appThemeId == legacy
    val widgetWasGlass = widgetThemeId == legacy
    val stableWasGlass = lastVerifiedStableThemeId == legacy
    if (!appWasGlass && !widgetWasGlass && !stableWasGlass) return null
    return toBuilder()
        .apply {
            if (appWasGlass) {
                setAppThemeId(BuiltInThemes.SYSTEM_DEFAULT_ID)
                if (!hasGlassEffectEnabled()) setGlassEffectEnabled(true)
            }
            if (widgetWasGlass) setWidgetThemeId(BuiltInThemes.DEFAULT_DARK.id)
            if (stableWasGlass) setLastVerifiedStableThemeId(BuiltInThemes.SYSTEM_DEFAULT_ID)
        }.build()
}
