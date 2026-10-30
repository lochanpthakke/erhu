package com.lochanpthakke.empee3.feature.library

import com.lochanpthakke.empee3.core.model.Song
import com.lochanpthakke.empee3.core.network.lyrics.CachedLyrics
import com.lochanpthakke.empee3.core.network.lyrics.LyricsCandidate
import com.lochanpthakke.empee3.core.network.lyrics.LyricsQuery
import com.lochanpthakke.empee3.core.network.lyrics.LyricsTiming
import com.lochanpthakke.empee3.core.network.lyrics.LyricsTrack
import com.lochanpthakke.empee3.core.network.lyrics.OnlineLyricsResult

/** Where the shown lyrics come from. */
enum class LyricsOrigin {
    /** The file's own tags. */
    FILE,

    /** Fetched online earlier and kept in the app. */
    ONLINE_SAVED,

    /** Fetched online just now. */
    ONLINE,
}

/** The online lookup for the playing song, as the lyrics page shows it. */
sealed interface LyricsLookup {
    data object Idle : LyricsLookup

    data object Searching : LyricsLookup

    data object NotFound : LyricsLookup

    data object Instrumental : LyricsLookup

    data object Offline : LyricsLookup

    data class RateLimited(
        val retryAtMs: Long,
    ) : LyricsLookup

    data class Failed(
        val reason: String,
    ) : LyricsLookup

    /** Possible matches, none certain: the user picks one (or none). */
    data class NeedsChoice(
        val candidates: List<LyricsCandidate>,
    ) : LyricsLookup
}

/** A manual online search started from the lyrics page. */
sealed interface ManualLyricsSearch {
    data object Idle : ManualLyricsSearch

    data object Searching : ManualLyricsSearch

    data class Results(
        val candidates: List<LyricsCandidate>,
    ) : ManualLyricsSearch

    data class Error(
        val message: String,
    ) : ManualLyricsSearch
}

/** What the lyrics page can ask for. */
class LyricsActions(
    /** The lyrics page is showing [songId]: look its lyrics up if needed (once per song). */
    val onShown: (Long) -> Unit,
    val refresh: () -> Unit,
    val search: (String) -> Unit,
    val choose: (LyricsCandidate) -> Unit,
    val useFileLyrics: () -> Unit,
    val closeSearch: () -> Unit,
    val download: () -> Unit = {},
    val removeDownload: (CachedLyrics) -> Unit = {},
)

/** The online lookup state of one song. */
internal data class OnlineLyricsView(
    val songId: Long,
    val lookup: LyricsLookup,
    /** The online result to show (kept while a refresh runs). */
    val candidate: LyricsCandidate? = null,
    val fromCache: Boolean = false,
    val userChosen: Boolean = false,
    val saved: CachedLyrics? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
)

internal fun Song.toLyricsTrack() = LyricsTrack(id, LyricsQuery(title, artist, album.takeIf { it.isNotBlank() }, durationMs), uri)

internal fun OnlineLyricsResult.toView(
    songId: Long,
    previous: LyricsCandidate?,
): OnlineLyricsView =
    when (this) {
        is OnlineLyricsResult.Found -> OnlineLyricsView(songId, LyricsLookup.Idle, candidate, fromCache, userChosen, saved)
        OnlineLyricsResult.NotFound -> OnlineLyricsView(songId, LyricsLookup.NotFound, previous)
        OnlineLyricsResult.Instrumental -> OnlineLyricsView(songId, LyricsLookup.Instrumental)
        OnlineLyricsResult.Offline -> OnlineLyricsView(songId, LyricsLookup.Offline, previous)
        is OnlineLyricsResult.RateLimited -> OnlineLyricsView(songId, LyricsLookup.RateLimited(retryAtMs), previous)
        is OnlineLyricsResult.Failed -> OnlineLyricsView(songId, LyricsLookup.Failed(reason), previous)
        is OnlineLyricsResult.NeedsChoice -> OnlineLyricsView(songId, LyricsLookup.NeedsChoice(candidates), previous)
    }

/**
 * What the page shows for [songId]: the file's own lyrics when it has readable ones (unless the user
 * picked an online result for this song), otherwise the online result, otherwise the lookup state.
 */
internal fun chooseLyrics(
    songId: Long,
    embedded: String?,
    online: OnlineLyricsView?,
    search: ManualLyricsSearch,
): SongLyrics {
    val view = online?.takeIf { it.songId == songId }
    val fileUsable = LyricsParser.parse(embedded) != null
    val onlineText = view?.candidate?.bestText
    val result =
        when {
            onlineText != null && (view.userChosen || !fileUsable) ->
                SongLyrics(
                    songId = songId,
                    text = onlineText,
                    origin =
                        if (view.saved != null &&
                            view.saved.candidate == view.candidate
                        ) {
                            LyricsOrigin.ONLINE_SAVED
                        } else {
                            LyricsOrigin.ONLINE
                        },
                    provider = view.candidate.provider,
                    timing = view.candidate.timing,
                    lookup = view.lookup,
                    hasFileLyrics = fileUsable,
                    search = search,
                )
            fileUsable ->
                SongLyrics(
                    songId,
                    embedded,
                    LyricsOrigin.FILE,
                    lookup = view?.lookup ?: LyricsLookup.Idle,
                    hasFileLyrics = true,
                    search = search,
                )
            else -> SongLyrics(songId, null, LyricsOrigin.FILE, lookup = view?.lookup ?: LyricsLookup.Idle, search = search)
        }
    return result.copy(
        saved = view?.saved,
        downloaded = view?.saved?.explicitDownload == true && view.saved.candidate == view.candidate && result.origin != LyricsOrigin.FILE,
        saving = view?.saving == true,
        saveError = view?.saveError,
    )
}

/** Short label of a result's timing. */
internal fun LyricsTiming.label(): String =
    when (this) {
        LyricsTiming.WORD -> "word-synced"
        LyricsTiming.LINE -> "line-synced"
        LyricsTiming.PLAIN -> "plain text"
        LyricsTiming.NONE -> "no text"
    }
