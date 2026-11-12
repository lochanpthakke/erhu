package com.erhu.erhu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erhu.erhu.core.common.R
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.model.LyricsTimingAdjustment
import com.erhu.erhu.core.model.LyricsTimingContext
import com.erhu.erhu.core.model.LyricsTimingOffset
import com.erhu.erhu.core.network.lyrics.CachedLyrics
import com.erhu.erhu.core.network.lyrics.LyricsTiming
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The lyrics of the playing song. Timed (LRC) lyrics follow playback: the current
 * line is emphasised and kept around the upper middle of the screen, and with word
 * timing the current word lights up too. Tapping a line (or a timed word) seeks
 * there. Dragging the lyrics stops the following until "back to current" is tapped.
 * Plain lyrics are shown as before, without any synchronisation.
 *
 * [lyrics] is the lyrics text of song [songId]; while it belongs to another song
 * (the new song's lyrics are still loading) nothing is shown, so a previous song's
 * lyrics never appear under the new one.
 */
@Composable
internal fun LyricsOverlay(
    songId: Long,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.sm, top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.queue_tab_lyrics),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.lyrics_cd_close))
            }
        }
        // Everything below is keyed by the song: a track change starts from a fresh state.
        key(songId) {
            val current = lyrics?.takeIf { it.songId == songId }
            if (current != null) LyricsContent(current, position, isPlaying, onSeek)
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun SongLyricsView(
    songId: Long,
    lyrics: SongLyrics?,
    position: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    actions: LyricsActions? = null,
    searchQuery: String = "",
    song: com.erhu.erhu.core.model.Song? = null,
    onPlayPause: (() -> Unit)? = null,
) {
    // Showing the page is what asks for lyrics: once per song, never per position update.
    if (actions != null) LaunchedEffect(songId) { actions.onShown(songId) }
    Column(modifier = modifier.fillMaxSize()) {
        key(songId) {
            val current = lyrics?.takeIf { it.songId == songId }
            var searching by remember { mutableStateOf(false) }
            var adjusting by remember(current?.timingContext?.id) { mutableStateOf(false) }
            val canAdjust = current?.timingContext != null && song?.id == songId && actions?.saveTiming != null
            val onAdjust: (() -> Unit)? =
                if (canAdjust) {
                    { adjusting = true }
                } else {
                    null
                }
            if (current != null && actions != null) LyricsSourceBar(current, actions, onAdjust) { searching = true }
            if (canAdjust) {
                val locale = AppLocaleManager.activeLocale(LocalContext.current)
                TextButton(onClick = { adjusting = true }, modifier = Modifier.padding(start = Spacing.lg)) {
                    Text(
                        if (current.offsetMs == 0L) {
                            stringResource(R.string.lyrics_adjust_timing_prompt)
                        } else {
                            val sign = if (current.offsetMs > 0) "+" else ""
                            stringResource(
                                R.string.lyrics_adjust_timing_offset,
                                sign + LocaleFormatters.formatInteger(current.offsetMs, locale),
                            )
                        },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    adjusting -> Unit // Only the visible editor runs the lyrics frame clock.
                    current?.text != null -> LyricsContent(current, position, isPlaying, onSeek)
                    current != null -> LyricsLookupStatus(current.lookup, actions, onSearch = { searching = true })
                    else -> NoLyrics()
                }
            }
            if (adjusting && canAdjust) {
                LyricsTimingEditor(song, current, position, isPlaying, onSeek, onPlayPause, actions.saveTiming) { adjusting = false }
            }
            if (searching && current != null && actions != null) {
                ManualLyricsSearchDialog(
                    initialQuery = searchQuery,
                    search = current.search,
                    actions =
                        LyricsActions(
                            onShown = actions.onShown,
                            refresh = actions.refresh,
                            search = actions.search,
                            choose = {
                                actions.choose(it)
                                searching = false
                            },
                            useFileLyrics = actions.useFileLyrics,
                            closeSearch = actions.closeSearch,
                        ),
                    onDismiss = {
                        searching = false
                        actions.closeSearch()
                    },
                )
            }
        }
    }
}

/**
 * How lyrics are set: [compact] for the panel inside Now Playing (smaller lines, the current one
 * nearer the top), [alignStart] to read from the left like the panel's heading. The defaults are
 * the full-page lyrics.
 */
internal data class LyricsLook(
    val compact: Boolean = false,
    val alignStart: Boolean = false,
)

internal val LocalLyricsLook = staticCompositionLocalOf { LyricsLook() }

@Composable
internal fun LyricsContent(
    lyrics: SongLyrics,
    position: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
) {
    val parsed = remember(lyrics.text) { LyricsParser.parse(lyrics.text) }
    when (parsed) {
        null -> NoLyrics()
        is Lyrics.Plain -> PlainLyrics(parsed)
        is Lyrics.Synced ->
            SyncedLyrics(parsed, position, isPlaying, { time ->
                onSeek(LyricsTimingOffset.playbackPosition(time, lyrics.offsetMs, lyrics.timingContext?.durationMs ?: 0))
            }, lyrics.offsetMs)
    }
}

/**
 * The lyrics of one song as shown (null [text]: none yet), where they come from ([origin],
 * [provider], [timing]), the online lookup state and any manual search.
 */
data class SongLyrics(
    val songId: Long,
    val text: String?,
    val origin: LyricsOrigin = LyricsOrigin.FILE,
    val provider: String? = null,
    val timing: LyricsTiming? = null,
    val lookup: LyricsLookup = LyricsLookup.Idle,
    /** The file has readable lyrics of its own. */
    val hasFileLyrics: Boolean = false,
    val search: ManualLyricsSearch = ManualLyricsSearch.Idle,
    val saved: CachedLyrics? = null,
    val downloaded: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    val sourceId: String? = null,
    val timingContext: LyricsTimingContext? = null,
    val offsetMs: Long = 0,
    val previousTiming: List<LyricsTimingAdjustment> = emptyList(),
)

@Composable
private fun NoLyrics() {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.lyrics_no_embedded),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics.Plain) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (LocalLyricsLook.current.compact) Spacing.lg else Spacing.xl, vertical = Spacing.lg),
    ) {
        itemsIndexed(lyrics.lines) { _, line ->
            val look = LocalLyricsLook.current
            Text(
                text = line,
                style = if (look.compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                textAlign = if (look.alignStart) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics.Synced,
    position: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    offsetMs: Long,
) {
    val smooth = rememberSmoothPosition(position, isPlaying)
    val lyricPosition = remember(smooth, offsetMs) { derivedStateOf { LyricsTimingOffset.lyricsPosition(smooth.value, offsetMs) } }
    // Only these two values change as the song plays; they change a few times per line.
    val currentLine by remember(lyrics, lyricPosition) { derivedStateOf { lyrics.lineAt(lyricPosition.value) } }
    val currentWord by remember(lyrics, lyricPosition) { derivedStateOf { lyrics.wordAt(currentLine, lyricPosition.value) } }
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }

    // A finger dragging the lyrics takes over; the following resumes from the button.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false }
    }

    val look = LocalLyricsLook.current
    val gutter = if (look.compact) Spacing.lg else Spacing.xl
    val anchor = if (look.compact) COMPACT_ANCHOR_FRACTION else ANCHOR_FRACTION
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The current line rests a little above the middle (the list's top padding), leaving room for what comes next.
        FollowCurrentLine(listState, currentLine, following)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = gutter,
                    end = gutter,
                    top = maxHeight * anchor,
                    bottom =
                        maxHeight * TAIL_FRACTION,
                ),
        ) {
            itemsIndexed(lyrics.lines, key = { index, _ -> index }) { index, line ->
                val distance = if (currentLine < 0) index + 1 else abs(index - currentLine)
                LyricLineItem(
                    line = line,
                    isCurrent = index == currentLine,
                    distance = distance,
                    activeWord = if (index == currentLine) currentWord else -1,
                    onSeek = { time ->
                        following = true
                        onSeek(time)
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = !following,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
        ) {
            SmallFloatingActionButton(onClick = { following = true }) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = stringResource(R.string.lyrics_cd_back_to_current))
            }
        }
    }
}

/**
 * Scrolls the current line to the list's top padding (the anchor) when it changes: one
 * animation per line, not per position update. Item offsets are measured from that anchor.
 */
@Composable
private fun FollowCurrentLine(
    listState: LazyListState,
    currentLine: Int,
    following: Boolean,
) {
    LaunchedEffect(currentLine, following) {
        if (!following) return@LaunchedEffect
        val target = currentLine.coerceAtLeast(0)
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (visible != null) {
            if (visible.offset != 0) listState.animateScrollBy(visible.offset.toFloat(), tween(Motion.NAVIGATION, easing = Motion.Standard))
        } else {
            listState.animateScrollToItem(target)
        }
    }
}

/**
 * The playback position between player updates (they come four times a second):
 * moved on with the frame clock while playing, re-anchored at every real update, never
 * running more than [MAX_EXTRAPOLATION_MS] ahead. The player stays the only source of time.
 */
@Composable
internal fun rememberSmoothPosition(
    position: State<Long>,
    isPlaying: Boolean,
): State<Long> {
    val smooth = remember { mutableLongStateOf(position.value) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            snapshotFlow { position.value }.collect { smooth.longValue = it }
            return@LaunchedEffect
        }
        var anchorMs = position.value
        var anchorNanos = System.nanoTime()
        launch {
            snapshotFlow { position.value }.collectLatest {
                anchorMs = it
                anchorNanos = System.nanoTime()
                // A seek moves straight there; small differences keep the smooth value (no jitter back).
                if (abs(it - smooth.longValue) > SEEK_JUMP_MS || it > smooth.longValue) smooth.longValue = it
            }
        }
        while (true) {
            withFrameNanos { now ->
                val ahead = ((now - anchorNanos) / NANOS_PER_MS).coerceIn(0L, MAX_EXTRAPOLATION_MS)
                val next = anchorMs + ahead
                if (next > smooth.longValue) smooth.longValue = next
            }
        }
    }
    return smooth
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isCurrent: Boolean,
    distance: Int,
    activeWord: Int,
    onSeek: (Long) -> Unit,
) {
    val emphasis by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(Motion.CONTENT, easing = Motion.Standard),
        label = "lyricEmphasis",
    )
    val look = LocalLyricsLook.current
    // Lines fade with their distance from the current one; the current line is full size. In the
    // compact panel every line keeps its size and stays readable: only the colour marks the current one.
    val restingAlpha = if (look.compact) 1f else (1f - distance * DISTANCE_FADE).coerceAtLeast(MIN_ALPHA)
    val inactiveScale = if (look.compact) 1f else INACTIVE_SCALE
    val base = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val words = line.words
    val align = if (look.alignStart) TextAlign.Start else TextAlign.Center
    val modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (look.compact) Spacing.xs else Spacing.sm)
            .graphicsLayer {
                val scale = inactiveScale + (1f - inactiveScale) * emphasis
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(if (look.alignStart) 0f else 0.5f, 0.5f)
                alpha = restingAlpha + (1f - restingAlpha) * emphasis
            }
    val style =
        if (look.compact) {
            MaterialTheme.typography.titleMedium.copy(
                fontSize = COMPACT_LINE_SIZE,
                lineHeight = COMPACT_LINE_HEIGHT,
                fontWeight = FontWeight.Bold,
            )
        } else {
            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        }
    if (words == null || !isCurrent) {
        Text(
            text = line.text.ifBlank { "♪" },
            style = style,
            color = lerp(dim, base, emphasis),
            textAlign = align,
            modifier = modifier.clickable { onSeek(line.timeMs) },
        )
    } else {
        TimedWordsLine(words, activeWord, style.color.takeIf { it != Color.Unspecified } ?: base, dim, onSeek, modifier, style, align)
    }
}

/** The current line with word timing: words already sung are full, the active word fades in. */
@Composable
private fun TimedWordsLine(
    words: List<LyricWord>,
    activeWord: Int,
    sung: Color,
    upcoming: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    style: androidx.compose.ui.text.TextStyle,
    align: TextAlign,
) {
    val accent = MaterialTheme.colorScheme.primary
    // One short animation per word change (not per position update).
    val wordProgress = remember { Animatable(1f) }
    LaunchedEffect(activeWord) {
        wordProgress.snapTo(0f)
        wordProgress.animateTo(1f, tween(WORD_FADE_MS, easing = Motion.Decelerate))
    }
    val progress = wordProgress.value
    val text: AnnotatedString =
        buildAnnotatedString {
            words.forEachIndexed { index, word ->
                val color =
                    when {
                        index < activeWord -> sung
                        index == activeWord -> lerp(upcoming, accent, progress)
                        else -> upcoming
                    }
                withStyle(SpanStyle(color = color)) { append(word.text) }
            }
        }
    val starts = remember(words) { words.runningFold(0) { at, word -> at + word.text.length } }
    val latestWords by rememberUpdatedState(words)
    val latestSeek by rememberUpdatedState(onSeek)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        textAlign = align,
        onTextLayout = { layout = it },
        modifier =
            modifier.pointerInput(starts) {
                detectTapGestures { tap ->
                    val offset = layout?.getOffsetForPosition(tap) ?: return@detectTapGestures
                    val index = (starts.indexOfLast { it <= offset }).coerceIn(0, latestWords.lastIndex)
                    latestSeek(latestWords[index].timeMs)
                }
            },
    )
}

private const val ANCHOR_FRACTION = 0.35f

/** In the compact panel the current line sits nearer the top, leaving most of the panel for what comes next. */
private const val COMPACT_ANCHOR_FRACTION = 0.06f
private val COMPACT_LINE_SIZE = 19.sp
private val COMPACT_LINE_HEIGHT = 26.sp
private const val TAIL_FRACTION = 0.6f
private const val INACTIVE_SCALE = 0.9f
private const val DISTANCE_FADE = 0.18f
private const val MIN_ALPHA = 0.3f
private const val WORD_FADE_MS = 140
private const val SEEK_JUMP_MS = 600L
private const val MAX_EXTRAPOLATION_MS = 350L
private const val NANOS_PER_MS = 1_000_000L
