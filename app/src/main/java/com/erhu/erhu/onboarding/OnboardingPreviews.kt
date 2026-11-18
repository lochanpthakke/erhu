package com.erhu.erhu.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.erhu.erhu.R
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.feature.library.components.OnboardingPreviewVideo
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import com.erhu.erhu.core.common.R as CommonR

private const val PREVIEW_ASPECT_RATIO = 1080f / 2424f
private const val SLIDE_DURATION_MILLIS = 3000L

/** Original portrait assets; images also provide first-frame posters for inactive video cards. */
internal enum class PreviewRecording(
    val labelRes: Int,
    val imageRes: Int,
    val video: String? = null,
    val summaryRes: Int? = null,
) {
    NOW_PLAYING(CommonR.string.onboarding_preview_now_playing, R.drawable.onboarding_now_playing, "now_playing.mp4"),
    HOME(R.string.onboarding_home_preview, R.drawable.onboarding_home, "home.mp4"),
    ARTWORK(
        CommonR.string.onboarding_preview_artwork_fetcher,
        R.drawable.onboarding_artwork,
        summaryRes = R.string.onboarding_artwork_summary,
    ),
    TAGS(CommonR.string.onboarding_preview_auto_fix_tags, R.drawable.onboarding_tags, summaryRes = R.string.onboarding_tags_summary),
    BUILDER(
        CommonR.string.onboarding_preview_library_builder,
        R.drawable.onboarding_builder,
        summaryRes = R.string.onboarding_builder_summary,
    ),
    LYRICS(CommonR.string.onboarding_preview_lyrics, R.drawable.onboarding_lyrics, "lyrics.mp4"),
    QUEUE(CommonR.string.onboarding_preview_queue, R.drawable.onboarding_queue, "queue.mp4"),
    EQUALIZER(CommonR.string.onboarding_preview_equalizer, R.drawable.onboarding_equalizer),
    TRANSITIONS(CommonR.string.onboarding_preview_studio, R.drawable.onboarding_transitions),
}

@Composable
private fun RecordingPreview(
    recording: PreviewRecording,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    loop: Boolean = true,
    onEnded: () -> Unit = {},
) {
    val description = stringResource(recording.labelRes)
    Box(
        modifier
            .aspectRatio(PREVIEW_ASPECT_RATIO)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("recording-${recording.name}")
            .semantics { contentDescription = description },
    ) {
        Image(painterResource(recording.imageRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (active && recording.video != null) {
            key(recording) {
                OnboardingPreviewVideo("asset:///onboarding/${recording.video}", active, loop, onEnded)
            }
        }
    }
}

@Composable
internal fun PairedRecordingPreviews(
    first: PreviewRecording,
    second: PreviewRecording,
    active: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(first, second).forEach { recording ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingPreview(recording, Modifier.fillMaxWidth(), active = active)
                Text(stringResource(recording.labelRes), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Uses the Now Playing depth treatment without involving the music playback controller. */
@Composable
internal fun UtilityPreviewCarousel(
    active: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    RecordingCarousel(
        listOf(PreviewRecording.ARTWORK, PreviewRecording.TAGS, PreviewRecording.BUILDER),
        active,
        motionEnabled,
        modifier,
    )
}

@Composable
internal fun RecordingCarousel(
    slides: List<PreviewRecording>,
    active: Boolean,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var target by rememberSaveable { mutableIntStateOf(0) }
    var interaction by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val position by animateFloatAsState(
        targetValue = target + if (dragging && motionEnabled) dragOffset else 0f,
        animationSpec = if (!motionEnabled || dragging || !active) snap() else tween(550, easing = Motion.Standard),
        label = "previewCardPosition",
    )
    val selectedSlide = Math.floorMod(target, slides.size)
    val settled = abs(position - target) < 0.001f
    LaunchedEffect(active, motionEnabled, dragging, target, interaction, settled) {
        if (active && motionEnabled && !dragging && settled) {
            delay(SLIDE_DURATION_MILLIS)
            target = if (slides.size == 2) 1 - target else target + 1
        }
    }
    LaunchedEffect(active) {
        if (!active) {
            dragging = false
            dragOffset = 0f
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cardWidth = minOf(maxWidth * 0.64f, maxHeight * PREVIEW_ASPECT_RATIO)
            val cardHeight = cardWidth / PREVIEW_ASPECT_RATIO
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .testTag("preview-carousel")
                    .pointerInput(rtl, slides.size) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                distance = 0f
                                interaction++
                            },
                            onDragEnd = {
                                if (abs(distance) >= viewConfiguration.touchSlop) {
                                    val forward = if (rtl) distance > 0 else distance < 0
                                    target = if (slides.size == 2) 1 - target else target + if (forward) 1 else -1
                                }
                                dragging = false
                                dragOffset = 0f
                                interaction++
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffset = 0f
                                interaction++
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                distance += amount
                                dragOffset = ((if (rtl) distance else -distance) / (size.width * 0.65f)).coerceIn(-1f, 1f)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Two-item pages render each preview once; Tools retains its circular neighbours.
                val centerSlot = floor(position).toInt()
                val slots = if (slides.size == 2) 0..1 else centerSlot - 2..centerSlot + 2
                slots.forEach { slot ->
                    val index = Math.floorMod(slot, slides.size)
                    val recording = slides[index]
                    val distance = slot - position
                    val away = abs(distance)
                    val depth = away.coerceIn(0f, 1f)
                    val smoothDepth = depth * depth * (3f - 2f * depth)
                    val selected = slot == target
                    RecordingPreview(
                        recording,
                        Modifier
                            .width(cardWidth)
                            .zIndex(-away)
                            .graphicsLayer {
                                // Neighbours settle behind the active card and sweep outwards during a change.
                                val scale = 1f - 0.14f * smoothDepth
                                scaleX = scale
                                scaleY = scale
                                translationX =
                                    (if (motionEnabled) sin(distance * PI).toFloat() * size.width * 0.48f else 0f) * (if (rtl) -1 else 1) +
                                    distance.coerceIn(-1f, 1f) * size.width * 0.24f * (if (rtl) -1 else 1)
                                translationY = 0f
                                rotationY = if (motionEnabled) -sin(distance * PI).toFloat() * 12f else 0f
                                cameraDistance = 12f * density
                                shadowElevation = (if (selected) 12.dp else 4.dp).toPx()
                                shape = RoundedCornerShape(16.dp)
                                clip = true
                                alpha = (1.0f - (away - 1f).coerceAtLeast(0f) * 2f).coerceIn(0f, 1f)
                            }.then(if (selected) Modifier else Modifier.clearAndSetSemantics {}),
                        active = active && selected && settled && !dragging,
                        loop = true,
                    )
                }
            }
        }
        Text(stringResource(slides[selectedSlide].labelRes), style = MaterialTheme.typography.titleLarge)
        slides[selectedSlide].summaryRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            slides.indices.forEach { index ->
                val description = stringResource(R.string.onboarding_slide_position, index + 1, slides.size)
                IconButton(
                    onClick = {
                        target = if (slides.size == 2) index else target + index - selectedSlide
                        interaction++
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = description
                            selected = selectedSlide == index
                        },
                ) {
                    Box(
                        Modifier.size(if (index == selectedSlide) 12.dp else 8.dp).background(
                            if (index == selectedSlide) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun WidgetPreviewGrid() {
    val previews = remember { widgetPreviews() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ExistingPreviewImages(listOf(previews[1], previews[2]))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                ExistingPreviewImages(listOf(previews[0].copy(labelRes = R.string.onboarding_gradient_widget)))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ExistingPreviewImages(previews: List<IntroductionPreview>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        previews.forEach { preview ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val painter = painterResource(preview.image)
                val ratio = (painter.intrinsicSize.width / painter.intrinsicSize.height).takeIf { it.isFinite() && it > 0f } ?: 1f
                Image(
                    painter,
                    stringResource(preview.labelRes),
                    Modifier.fillMaxWidth().aspectRatio(ratio),
                    contentScale = ContentScale.Fit,
                )
                Text(stringResource(preview.labelRes), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
