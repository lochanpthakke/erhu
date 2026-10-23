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
    /** Smart Radio from the current song (it keeps playing; the radio replaces what comes next). */
    val onStartRadio: (() -> Unit)?,
    /** Fresh picks for the radio playing now; null when the current song isn't part of one. */
    val onRefreshRadio: (() -> Unit)?,
    val onQueue: () -> Unit,
    val onVisualizer: () -> Unit,
    val onEqualizer: () -> Unit,
)
