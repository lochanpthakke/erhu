package com.erhu.erhu.onboarding

import com.erhu.erhu.R

internal enum class IntroductionPage(
    val title: String,
    val description: String,
) {
    WELCOME("Your music. Your space.", "A beautiful home for the music you own. Listen, explore and make every sound yours."),
    LIBRARY("Your music library.", "Browse albums, artists and tracks. Search the music stored on your device."),
    ARTWORK(
        "Give every album its cover.",
        "Find album covers and artist photos with Artwork Fetcher, or choose a cover from your own files.",
    ),
    TAGS("Put the right names to your music.", "Use Auto Fix Tags to correct incomplete metadata and bring order to your collection."),
    BUILDER(
        "A library, down to the folders.",
        "Library Builder uses your music tags to propose an organized folder structure on your device.",
    ),
    LISTENING("Your listening experience.", "Swipe the artwork to change tracks. Open Queue to reorder what plays next."),
    LYRICS(
        "Stay with the words.",
        "Follow synchronized lyrics on supported tracks. Availability depends on the file and online providers.",
    ),
    SOUND("Make sound yours.", "Adjust the eight-band equalizer and choose a preset for your listening experience."),
    TRANSITIONS(
        "Make the next track flow.",
        "Use Transition Studio to shape the handover between tracks, with defaults and individual transitions.",
    ),
    VYBE("Discover VYBE.", "Search your own collection and browse generated radio stations. Playback starts when you choose."),
    WIDGETS("Music on your Home screen.", "Touch and hold your Android Home screen, open Widgets, then choose an erhu player."),
    READY("Everything is in place.", "Your music is ready. Make yourself at home."),
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
    val previous = IntroductionPage.entries.firstOrNull { it.name == saved } ?: IntroductionPage.WELCOME
    return pages.indexOf(previous).takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.ordinal > previous.ordinal }.takeIf { it >= 0 }
        ?: 0
}

internal data class IntroductionPreview(
    val label: String,
    val image: Int,
)

internal fun IntroductionPage.previews(): List<IntroductionPreview> =
    when (this) {
        IntroductionPage.WELCOME -> listOf(IntroductionPreview("Now Playing", R.drawable.onb_welcome))
        IntroductionPage.LIBRARY ->
            listOf(
                IntroductionPreview("Library", R.drawable.onb_library),
                IntroductionPreview("Albums", R.drawable.onb_albums),
            )
        IntroductionPage.LISTENING ->
            listOf(
                IntroductionPreview("Now Playing", R.drawable.onb_player),
                IntroductionPreview("Queue", R.drawable.onb_queue),
            )
        IntroductionPage.LYRICS -> listOf(IntroductionPreview("Lyrics", R.drawable.onb_lyrics))
        IntroductionPage.SOUND -> listOf(IntroductionPreview("Equalizer", R.drawable.onb_equalizer))
        IntroductionPage.VYBE ->
            listOf(
                IntroductionPreview("VYBE", R.drawable.onb_vybe),
                IntroductionPreview("Radio", R.drawable.onb_radio),
            )
        IntroductionPage.WIDGETS ->
            listOf(
                IntroductionPreview("Music", R.drawable.onb_widget_music),
                IntroductionPreview("Glass", R.drawable.onb_widget_glass),
                IntroductionPreview("Vinyl", R.drawable.onb_widget_vinyl),
            )
        IntroductionPage.ARTWORK -> listOf(IntroductionPreview("Artwork Fetcher", R.drawable.onb_artwork_fetcher))
        IntroductionPage.TAGS -> listOf(IntroductionPreview("Auto Fix Tags", R.drawable.onb_auto_fix_tags))
        IntroductionPage.BUILDER -> listOf(IntroductionPreview("Library Builder", R.drawable.onb_library_builder))
        IntroductionPage.TRANSITIONS ->
            listOf(
                IntroductionPreview("Studio", R.drawable.onb_transition_studio),
                IntroductionPreview("Volume panel", R.drawable.onb_transition_volume),
            )
        IntroductionPage.READY -> emptyList()
    }
