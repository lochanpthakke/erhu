package com.erhu.erhu.onboarding

import androidx.annotation.StringRes
import com.erhu.erhu.R
import com.erhu.erhu.core.common.R as CommonR

internal enum class IntroductionPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    LANGUAGE(CommonR.string.onboarding_page_language_title, CommonR.string.onboarding_page_language_desc),
    WELCOME(CommonR.string.onboarding_page_welcome_title, CommonR.string.onboarding_page_welcome_desc),
    LIBRARY(CommonR.string.onboarding_page_library_title, CommonR.string.onboarding_page_library_desc),
    ARTWORK(CommonR.string.onboarding_page_artwork_title, CommonR.string.onboarding_page_artwork_desc),
    TAGS(CommonR.string.onboarding_page_tags_title, CommonR.string.onboarding_page_tags_desc),
    BUILDER(CommonR.string.onboarding_page_builder_title, CommonR.string.onboarding_page_builder_desc),
    LISTENING(CommonR.string.onboarding_page_listening_title, CommonR.string.onboarding_page_listening_desc),
    LYRICS(CommonR.string.onboarding_page_lyrics_title, CommonR.string.onboarding_page_lyrics_desc),
    SOUND(CommonR.string.onboarding_page_sound_title, CommonR.string.onboarding_page_sound_desc),
    TRANSITIONS(CommonR.string.onboarding_page_transitions_title, CommonR.string.onboarding_page_transitions_desc),
    VYBE(CommonR.string.onboarding_page_vybe_title, CommonR.string.onboarding_page_vybe_desc),
    WIDGETS(CommonR.string.onboarding_page_widgets_title, CommonR.string.onboarding_page_widgets_desc),
    READY(CommonR.string.onboarding_page_ready_title, CommonR.string.onboarding_page_ready_desc),
}

internal fun introductionPages(
    vybeEnabled: Boolean,
    widgetsAvailable: Boolean,
): List<IntroductionPage> =
    IntroductionPage.entries.filter {
        (it != IntroductionPage.VYBE || vybeEnabled) && (it != IntroductionPage.WIDGETS || widgetsAvailable)
    }

/** Keep identity across changing page counts and process restoration; hidden VYBE advances naturally. */
internal fun restoredIntroductionPage(
    saved: String,
    pages: List<IntroductionPage>,
): Int {
    val previous = IntroductionPage.entries.firstOrNull { it.name == saved } ?: IntroductionPage.LANGUAGE
    return pages.indexOf(previous).takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.ordinal > previous.ordinal }.takeIf { it >= 0 }
        ?: 0
}

internal data class IntroductionPreview(
    @StringRes val labelRes: Int,
    val image: Int,
)

internal fun IntroductionPage.previews(): List<IntroductionPreview> =
    when (this) {
        IntroductionPage.WELCOME -> listOf(IntroductionPreview(CommonR.string.onboarding_preview_now_playing, R.drawable.onb_welcome))
        IntroductionPage.LIBRARY ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_library, R.drawable.onb_library),
                IntroductionPreview(CommonR.string.onboarding_preview_albums, R.drawable.onb_albums),
            )
        IntroductionPage.LISTENING ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_now_playing, R.drawable.onb_player),
                IntroductionPreview(CommonR.string.onboarding_preview_queue, R.drawable.onb_queue),
            )
        IntroductionPage.LYRICS -> listOf(IntroductionPreview(CommonR.string.onboarding_preview_lyrics, R.drawable.onb_lyrics))
        IntroductionPage.SOUND -> listOf(IntroductionPreview(CommonR.string.onboarding_preview_equalizer, R.drawable.onb_equalizer))
        IntroductionPage.VYBE ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_vybe, R.drawable.onb_vybe),
                IntroductionPreview(CommonR.string.onboarding_preview_radio, R.drawable.onb_radio),
            )
        IntroductionPage.WIDGETS ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_widget_music, R.drawable.onb_widget_music),
                IntroductionPreview(CommonR.string.onboarding_preview_widget_glass, R.drawable.onb_widget_glass),
                IntroductionPreview(CommonR.string.onboarding_preview_widget_vinyl, R.drawable.onb_widget_vinyl),
            )
        IntroductionPage.ARTWORK ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_artwork_fetcher, R.drawable.onb_artwork_fetcher),
            )
        IntroductionPage.TAGS -> listOf(IntroductionPreview(CommonR.string.onboarding_preview_auto_fix_tags, R.drawable.onb_auto_fix_tags))
        IntroductionPage.BUILDER ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_library_builder, R.drawable.onb_library_builder),
            )
        IntroductionPage.TRANSITIONS ->
            listOf(
                IntroductionPreview(CommonR.string.onboarding_preview_studio, R.drawable.onb_transition_studio),
                IntroductionPreview(CommonR.string.onboarding_preview_volume_panel, R.drawable.onb_transition_volume),
            )
        IntroductionPage.LANGUAGE,
        IntroductionPage.READY,
        -> emptyList()
    }
