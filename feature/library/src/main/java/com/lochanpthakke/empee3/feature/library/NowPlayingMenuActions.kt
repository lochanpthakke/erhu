package com.lochanpthakke.empee3.feature.library

/** What Now Playing's overflow menu can do for the current track. */
internal class NowPlayingMenuActions(
    val onMusicInfo: () -> Unit,
    val onAudioDetails: () -> Unit,
    val onAddBookmark: () -> Unit,
    val onBookmarks: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    /** Null when hiding isn't available (no mixes source); otherwise toggles the song's hidden state. */
    val onToggleHidden: (() -> Unit)?,
    val onQueue: () -> Unit,
    val onVisualizer: () -> Unit,
    val onEqualizer: () -> Unit,
)
