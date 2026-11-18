package com.erhu.erhu.onboarding

import androidx.annotation.StringRes
import com.erhu.erhu.R
import com.erhu.erhu.core.common.R as CommonR

internal enum class IntroductionPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    LANGUAGE(CommonR.string.onboarding_page_language_title, CommonR.string.onboarding_page_language_desc),
    WELCOME(CommonR.string.onboarding_page_welcome_title, R.string.onboarding_welcome_summary),
    ARTWORK(R.string.onboarding_tools_title, R.string.onboarding_tools_summary),
    LYRICS(R.string.onboarding_lyrics_queue_title, R.string.onboarding_lyrics_queue_summary),
    SOUND(R.string.onboarding_sound_title, R.string.onboarding_sound_summary),
    VYBE(CommonR.string.onboarding_page_vybe_title, CommonR.string.onboarding_page_vybe_desc),
    QUICK_ACCESS(R.string.onboarding_navigation_widgets_title, CommonR.string.onboarding_page_widgets_desc),
    READY(CommonR.string.onboarding_page_ready_title, CommonR.string.onboarding_page_ready_desc),
}

internal fun introductionPages(
    vybeEnabled: Boolean,
    initialSetup: Boolean = false,
): List<IntroductionPage> = IntroductionPage.entries.filter { it != IntroductionPage.VYBE || vybeEnabled || initialSetup }

/** Resolve old identities before finding an index in the current sequence. */
internal fun restoredIntroductionPage(
    saved: String,
    pages: List<IntroductionPage>,
): Int {
    val identity =
        when (saved) {
            "LIBRARY", "LISTENING" -> "WELCOME"
            "TAGS", "BUILDER" -> "ARTWORK"
            "TRANSITIONS" -> "SOUND"
            "WIDGETS" -> "QUICK_ACCESS"
            else -> saved
        }
    val previous = IntroductionPage.entries.firstOrNull { it.name == identity } ?: IntroductionPage.LANGUAGE
    return pages.indexOf(previous).takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.ordinal > previous.ordinal }.takeIf { it >= 0 }
        ?: 0
}

internal data class IntroductionPreview(
    @StringRes val labelRes: Int,
    val image: Int,
)

internal fun vybePreviews(): List<IntroductionPreview> =
    listOf(
        IntroductionPreview(CommonR.string.onboarding_preview_vybe, R.drawable.onb_vybe),
        IntroductionPreview(CommonR.string.onboarding_preview_radio, R.drawable.onb_radio),
    )

internal fun widgetPreviews(): List<IntroductionPreview> =
    listOf(
        IntroductionPreview(CommonR.string.onboarding_preview_widget_music, R.drawable.onb_widget_music),
        IntroductionPreview(CommonR.string.onboarding_preview_widget_glass, R.drawable.onb_widget_glass),
        IntroductionPreview(CommonR.string.onboarding_preview_widget_vinyl, R.drawable.onb_widget_vinyl),
    )
