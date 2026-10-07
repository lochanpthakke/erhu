package com.lochanpthakke.empee3.feature.library

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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lochanpthakke.empee3.core.designsystem.foundation.Motion
import com.lochanpthakke.empee3.core.designsystem.foundation.Spacing
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
            Text(text = "Lyrics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close lyrics")
            }
        }
        // Everything below is keyed by the song: a track change starts from a fresh state.
        key(songId) {
            val current = lyrics?.takeIf { it.songId == songId }
            if (current != null) LyricsContent(current, position, isPlaying, onSeek)
        }
    }
}

@Composable
private fun LyricsContent(
    lyrics: SongLyrics,
    position: State<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
) {
    val parsed = remember(lyrics.text) { LyricsParser.parse(lyrics.text) }
    when (parsed) {
        null -> NoLyrics()
        is Lyrics.Plain -> PlainLyrics(parsed)
        is Lyrics.Synced -> SyncedLyrics(parsed, position, isPlaying, onSeek)
    }
}

/** The lyrics text of one song (null when the file has none). */
data class SongLyrics(
    val songId: Long,
    val text: String?,
)

@Composable
private fun NoLyrics() {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Text(
            text = "This file has no embedded lyrics.",
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
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.lg),
    ) {
        itemsIndexed(lyrics.lines) { _, line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
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
) {
    val smooth = rememberSmoothPosition(position, isPlaying)
    // Only these two values change as the song plays; they change a few times per line.
    val currentLine by remember(lyrics) { derivedStateOf { lyrics.lineAt(smooth.value) } }
    val currentWord by remember(lyrics) { derivedStateOf { lyrics.wordAt(currentLine, smooth.value) } }
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }

    // A finger dragging the lyrics takes over; the following resumes from the button.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The current line rests a little above the middle (the list's top padding), leaving room for what comes next.
        FollowCurrentLine(listState, currentLine, following)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.xl,
                    end = Spacing.xl,
                    top = maxHeight * ANCHOR_FRACTION,
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
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Back to current lyric")
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
private fun rememberSmoothPosition(
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
    // Lines fade with their distance from the current one; the current line is full size.
    val restingAlpha = (1f - distance * DISTANCE_FADE).coerceAtLeast(MIN_ALPHA)
    val base = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val words = line.words
    val modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .graphicsLayer {
                val scale = INACTIVE_SCALE + (1f - INACTIVE_SCALE) * emphasis
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                alpha = restingAlpha + (1f - restingAlpha) * emphasis
            }
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    if (words == null || !isCurrent) {
        Text(
            text = line.text.ifBlank { "♪" },
            style = style,
            color = lerp(dim, base, emphasis),
            textAlign = TextAlign.Center,
            modifier = modifier.clickable { onSeek(line.timeMs) },
        )
    } else {
        TimedWordsLine(words, activeWord, style.color.takeIf { it != Color.Unspecified } ?: base, dim, onSeek, modifier, style)
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
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
        modifier =
            modifier.pointerInput(starts) {
                detectTapGestures { tap ->
                    val offset = layout?.getOffsetForPosition(tap) ?: return@detectTapGestures
                    val index = (starts.indexOfLast { it <= offset }).coerceIn(0, latestWords.lastIndex)
                    onSeek(latestWords[index].timeMs)
                }
            },
    )
}

private const val ANCHOR_FRACTION = 0.35f
private const val TAIL_FRACTION = 0.6f
private const val INACTIVE_SCALE = 0.9f
private const val DISTANCE_FADE = 0.18f
private const val MIN_ALPHA = 0.3f
private const val WORD_FADE_MS = 140
private const val SEEK_JUMP_MS = 600L
private const val MAX_EXTRAPOLATION_MS = 350L
private const val NANOS_PER_MS = 1_000_000L
