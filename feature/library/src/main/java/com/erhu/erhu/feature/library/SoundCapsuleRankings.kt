package com.erhu.erhu.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.common.R as CommonR

/** "1 play" / "12 plays" for a ranked song, album or artist, in the app's language. */
@Composable
internal fun playsText(plays: Int): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return pluralStringResource(CommonR.plurals.capsule_plays_count, plays, LocaleFormatters.formatInteger(plays, locale))
}
