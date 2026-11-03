package com.erhu.erhu.onboarding

/** Usage guidance for existing tools; these pages never enqueue repairs or modify music files. */
internal data class IntroductionToolGuide(
    val entryPoint: String,
    val steps: List<Pair<String, String>>,
    val details: List<Pair<String, String>>,
)

internal fun IntroductionPage.toolGuide(): IntroductionToolGuide? =
    when (this) {
        IntroductionPage.ARTWORK ->
            IntroductionToolGuide(
                entryPoint = "Navigation drawer → Artwork Fetcher",
                steps =
                    listOf(
                        "Find an album or artist" to
                            "Choose Albums or Artists. Search your library and add results with +, or scan for missing covers and photos.",
                        "Search and inspect" to
                            "Choose a source provider and resolution target, then tap Search for Artwork. Tap a candidate to inspect it at a larger size.",
                        "Apply the image you want" to
                            "Confirm Apply Artwork for the selected cover or artist photo. You choose the result before it is applied.",
                    ),
                details =
                    listOf(
                        "Album covers and artist photos" to
                            "Applying an album cover embeds it into the album's tracks. Artist photos update the artist's profile image in erhu.",
                        "Use a local cover" to
                            "Open an album's three-dot menu and choose Set Custom Cover (Local File). Fetch Cover Online in that menu adds the album to Artwork Fetcher.",
                        "Image quality and availability" to
                            "The resolution target helps narrow the search. Available images depend on the provider and internet connection; not every album has a matching high-resolution cover.",
                    ),
            )
        IntroductionPage.TAGS ->
            IntroductionToolGuide(
                entryPoint = "Navigation drawer → Auto Fix Tags",
                steps =
                    listOf(
                        "Choose what needs fixing" to
                            "Search tracks, albums or artists and add them to the repair queue. You can also find tracks with bad tags or select a larger batch.",
                        "Set the scope" to
                            "Choose a metadata provider and the fields to change: title, artist, album, year, genre, BPM or Filename Only. Artwork has its own fetcher.",
                        "Choose a matching mode" to
                            "Automatic Tagging handles matching for you. Manual Selection lets you choose the matching result. Tap Start Fixing Queue / Fix Tags to begin.",
                    ),
                details =
                    listOf(
                        "Fill missing tags or replace selected fields" to
                            "All Fields fills missing tag values while retaining valid ones. A smaller field selection replaces those selected fields. Filename Only renames the file using its title.",
                        "Progress and corrections" to
                            "Watch each queued track's status. Pause after the current track and resume later in the queue. Completed tracks with saved originals can be selected for Revert Changes.",
                        "File access and sound" to
                            "Tag writes and renaming may need Android's file-access approval. These tools update metadata; they do not repair damaged audio or improve recording quality. Online matches can be unavailable or incorrect, so use Manual Selection when needed.",
                    ),
            )
        IntroductionPage.BUILDER ->
            IntroductionToolGuide(
                entryPoint = "Navigation drawer → Library Builder",
                steps =
                    listOf(
                        "Choose the folder to organize" to
                            "Start Scanning Files uses your configured scan location. Scan another folder lets you choose a folder for this run without changing the library's scan location.",
                        "Review proposed moves" to
                            "Check each destination before continuing. Missing metadata and filename collisions are marked; choose Resolve to edit a destination or Skip to leave that file out.",
                        "Execute when the plan is right" to
                            "Tap Execute Moves to organize the files. The result lists successful, failed, skipped and already-organized files.",
                    ),
                details =
                    listOf(
                        "Folders follow your tags" to
                            "The proposed structure is Artist / Album / Track title.ext. The builder prefers the primary album artist, falling back to the track artist. Correct incomplete tags first for better folder names.",
                        "Actual files are moved" to
                            "This changes file locations on your device, rather than only sorting the library view. Choose a folder you can grant write access to and review the destinations before executing.",
                        "Force Re-organize" to
                            "Normally, previously organized files can be skipped. Enable Force Re-organize to include them in a new scan. Scanning and reviewing alone do not execute the proposed moves.",
                    ),
            )
        else -> null
    }
