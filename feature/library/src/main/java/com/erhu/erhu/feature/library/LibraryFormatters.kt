package com.erhu.erhu.feature.library

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.feature.library.mix.MixKind
import com.erhu.erhu.feature.library.mix.MixNoteKey
import com.erhu.erhu.feature.library.mix.SmartMix
import com.erhu.erhu.core.common.R as CommonR

private const val MINUTES_PER_HOUR = 60L

@Composable
internal fun songCountText(count: Int): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return pluralStringResource(CommonR.plurals.library_songs_count, count, LocaleFormatters.formatInteger(count, locale))
}

internal fun songCountText(
    context: Context,
    count: Int,
): String {
    val localized = AppLocaleManager.localizedContext(context)
    val locale = AppLocaleManager.activeLocale(localized)
    return localized.resources.getQuantityString(
        CommonR.plurals.library_songs_count,
        count,
        LocaleFormatters.formatInteger(count, locale),
    )
}

@Composable
internal fun albumCountText(count: Int): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return pluralStringResource(CommonR.plurals.library_albums_count, count, LocaleFormatters.formatInteger(count, locale))
}

@Composable
internal fun trackCountText(count: Int): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return pluralStringResource(CommonR.plurals.library_tracks_count, count, LocaleFormatters.formatInteger(count, locale))
}

@Composable
internal fun minutesDurationText(minutes: Long): String {
    val locale = AppLocaleManager.activeLocale(LocalContext.current)
    return if (minutes < MINUTES_PER_HOUR) {
        stringResource(CommonR.string.library_duration_minutes, LocaleFormatters.formatInteger(minutes, locale))
    } else {
        stringResource(
            CommonR.string.library_duration_hours_minutes,
            LocaleFormatters.formatInteger(minutes / MINUTES_PER_HOUR, locale),
            LocaleFormatters.formatInteger(minutes % MINUTES_PER_HOUR, locale),
        )
    }
}

private val MIX_KIND_TITLE_RES =
    mapOf(
        MixKind.ROTATION to CommonR.string.mix_title_rotation,
        MixKind.YOUR_SOUND to CommonR.string.mix_title_your_sound,
        MixKind.FORGOTTEN to CommonR.string.mix_title_forgotten,
        MixKind.DEEP_CUTS to CommonR.string.mix_title_deep_cuts,
        MixKind.HIGH_ENERGY to CommonR.string.mix_title_high_energy,
        MixKind.CHILL to CommonR.string.mix_title_chill,
        MixKind.LATE_NIGHT to CommonR.string.mix_title_late_night,
        MixKind.A_LITTLE_DIFFERENT to CommonR.string.mix_title_a_little_different,
        MixKind.SPOTLIGHT to CommonR.string.mix_title_spotlight,
        MixKind.THROWBACKS to CommonR.string.mix_title_throwbacks,
        MixKind.QUICK_HITS to CommonR.string.mix_title_quick_hits,
        MixKind.LONG_LISTENS to CommonR.string.mix_title_long_listens,
        MixKind.YET_TO_HEAR to CommonR.string.mix_title_yet_to_hear,
        MixKind.FAVOURITES to CommonR.string.mix_title_favourites,
        MixKind.FRESH to CommonR.string.mix_title_fresh,
    )

private val MIX_NOTE_DESC_RES =
    mapOf(
        MixNoteKey.ROTATION to CommonR.string.mix_desc_rotation,
        MixNoteKey.YOUR_SOUND_VYBE to CommonR.string.mix_desc_your_sound_vybe,
        MixNoteKey.YOUR_SOUND_TAGS to CommonR.string.mix_desc_your_sound_tags,
        MixNoteKey.FORGOTTEN to CommonR.string.mix_desc_forgotten,
        MixNoteKey.DEEP_CUTS to CommonR.string.mix_desc_deep_cuts,
        MixNoteKey.HIGH_ENERGY to CommonR.string.mix_desc_high_energy,
        MixNoteKey.CHILL to CommonR.string.mix_desc_chill,
        MixNoteKey.LATE_NIGHT to CommonR.string.mix_desc_late_night,
        MixNoteKey.A_LITTLE_DIFFERENT to CommonR.string.mix_desc_a_little_different,
        MixNoteKey.SPOTLIGHT_PLAYS to CommonR.string.mix_desc_spotlight_plays,
        MixNoteKey.SPOTLIGHT_SONGS to CommonR.string.mix_desc_spotlight_songs,
        MixNoteKey.THROWBACKS to CommonR.string.mix_desc_throwbacks,
        MixNoteKey.QUICK_HITS to CommonR.string.mix_desc_quick_hits,
        MixNoteKey.LONG_LISTENS to CommonR.string.mix_desc_long_listens,
        MixNoteKey.YET_TO_HEAR to CommonR.string.mix_desc_yet_to_hear,
        MixNoteKey.FAVOURITES to CommonR.string.mix_desc_favourites,
        MixNoteKey.FRESH to CommonR.string.mix_desc_fresh,
    )

@StringRes
internal fun MixKind.titleRes(): Int = MIX_KIND_TITLE_RES.getValue(this)

@StringRes
internal fun MixNoteKey.descriptionRes(): Int = MIX_NOTE_DESC_RES.getValue(this)

@Composable
internal fun SmartMix.localizedTitle(): String =
    if (kind == MixKind.SPOTLIGHT && !spotlightArtist.isNullOrBlank()) {
        stringResource(CommonR.string.mix_title_spotlight_artist, spotlightArtist)
    } else {
        stringResource(kind.titleRes())
    }

@Composable
internal fun SmartMix.localizedDescription(): String = stringResource(noteKey.descriptionRes())
