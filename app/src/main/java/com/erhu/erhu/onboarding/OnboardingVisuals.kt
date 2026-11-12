@file:Suppress("MagicNumber") // Normalized drawing coordinates and decorative geometry.

package com.erhu.erhu.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erhu.erhu.R
import com.erhu.erhu.core.designsystem.foundation.Motion
import kotlin.math.PI
import kotlin.math.sin
import com.erhu.erhu.core.common.R as CommonR

private val Pearl = Color(0xffd5dedf)
private val Ocean = Color(0xff547586)

/** Native infinite-animation policy keeps tests idle; disposing a hidden page removes its clock. */
@Composable
private fun rememberPreviewPhase(
    page: IntroductionPage,
    running: Boolean,
): State<Float> {
    var position by rememberSaveable(page.name) { mutableFloatStateOf(0f) }
    if (!running) return rememberUpdatedState(position)
    val transition = rememberInfiniteTransition(label = "introduction-${page.name}")
    val phase =
        transition.animateFloat(
            initialValue = position,
            targetValue = position + 1f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
            label = "previewPhase",
        )
    DisposableEffect(phase) {
        onDispose { position = phase.value % 1f }
    }
    return phase
}

/** One clock coordinates the feature motif, artwork and light; only drawing layers read its value. */
@Composable
internal fun IntroductionVisual(
    page: IntroductionPage,
    preview: IntroductionPreview?,
    running: Boolean,
    motionEnabled: Boolean,
) {
    var visible by remember { mutableStateOf(true) }
    val phase = rememberPreviewPhase(page, running && motionEnabled && visible)
    val path = remember { Path() }
    val density = LocalDensity.current
    val hairline = remember(density) { Stroke(with(density) { 1.dp.toPx() }) }
    val curveStroke = remember(density) { Stroke(with(density) { 1.5.dp.toPx() }) }
    val glow = remember { Brush.radialGradient(listOf(Ocean.copy(alpha = .16f), Color.Transparent)) }
    val imageEdge = remember { Brush.linearGradient(listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .02f))) }
    val hero = page == IntroductionPage.WELCOME || page == IntroductionPage.READY
    Box(
        Modifier.fillMaxWidth().height(if (hero) 272.dp else 232.dp).testTag("introduction-visual-${page.name}").onGloballyPositioned {
            val bounds = it.boundsInWindow()
            visible = bounds.width > 0f && bounds.height > 0f
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val angle = phase.value * (2 * PI).toFloat()
            drawRect(glow)
            with(IntroductionMotifs) { drawFeature(page, angle, path, hairline, curveStroke) }
        }
        if (page == IntroductionPage.READY) {
            IntroductionLogo(phase)
        } else if (preview != null) {
            Crossfade(
                targetState = preview,
                animationSpec = tween(if (motionEnabled) Motion.CONTENT else 0),
                label = "introductionPreview",
                modifier = Modifier.fillMaxSize().padding(horizontal = if (hero) 42.dp else 28.dp, vertical = 12.dp),
            ) { shown ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { IntroductionArtwork(page, shown, phase) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(.54f)
                    .height(2.dp)
                    .background(imageEdge, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun IntroductionLogo(phase: State<Float>) {
    val logoShape = remember { RoundedCornerShape(28.dp) }
    Image(
        painterResource(R.drawable.erhu_logo),
        stringResource(R.string.app_name),
        Modifier.fillMaxSize(.46f).graphicsLayer {
            val light = sin(phase.value * (2 * PI).toFloat())
            translationY = light * 2.dp.toPx()
            shadowElevation = 18.dp.toPx()
            shape = logoShape
            clip = true
        },
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun IntroductionArtwork(
    page: IntroductionPage,
    preview: IntroductionPreview,
    phase: State<Float>,
) {
    val shape = remember { RoundedCornerShape(18.dp) }
    val painter = painterResource(preview.image)
    val label = stringResource(preview.labelRes)
    val ratio = (painter.intrinsicSize.width / painter.intrinsicSize.height).takeIf { it.isFinite() && it > 0f } ?: 1f
    Image(
        painter,
        stringResource(CommonR.string.onboarding_cd_preview, label),
        Modifier
            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                val floats = page == IntroductionPage.WELCOME || page == IntroductionPage.LISTENING || page == IntroductionPage.WIDGETS
                translationY = if (floats) sin(phase.value * (2 * PI).toFloat()) * 3.dp.toPx() else 0f
                shadowElevation = 12.dp.toPx()
                this.shape = shape
            }.clip(shape),
        contentScale = ContentScale.Fit,
    )
}

private object IntroductionMotifs {
    fun DrawScope.drawFeature(
        page: IntroductionPage,
        angle: Float,
        path: Path,
        hairline: Stroke,
        curveStroke: Stroke,
    ) {
        when (page) {
            IntroductionPage.LANGUAGE, IntroductionPage.WELCOME, IntroductionPage.LISTENING -> drawWaves(path, angle, hairline)
            IntroductionPage.LIBRARY -> drawLibraryTiles(angle)
            IntroductionPage.ARTWORK -> drawArtworkLight(angle)
            IntroductionPage.TAGS -> drawDocuments(angle, false)
            IntroductionPage.BUILDER -> drawDocuments(angle, true)
            IntroductionPage.LYRICS -> drawLyricLines(angle)
            IntroductionPage.SOUND -> drawFrequencyBars(angle)
            IntroductionPage.TRANSITIONS -> drawCrossfade(path, angle, curveStroke)
            IntroductionPage.VYBE, IntroductionPage.READY -> drawDiscoveryRings(angle, hairline)
            IntroductionPage.WIDGETS -> drawWidgetLayers(angle)
        }
    }

    private fun DrawScope.drawWaves(
        path: Path,
        angle: Float,
        stroke: Stroke,
    ) {
        repeat(6) { layer ->
            path.reset()
            repeat(65) { step ->
                val x = size.width * step / 64f
                val y = size.height * (.25f + layer * .10f) + sin(step / 64f * 5f + angle + layer * .6f) * size.height * .07f
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, if (layer % 2 == 0) Ocean.copy(alpha = .30f) else Pearl.copy(alpha = .10f), style = stroke)
        }
    }

    private fun DrawScope.drawLibraryTiles(angle: Float) {
        repeat(6) { index ->
            val column = index % 3
            val row = index / 3
            val offset = sin(angle + index * .45f) * 3.dp.toPx()
            drawRoundRect(
                Pearl.copy(alpha = .055f + index * .006f),
                Offset(size.width * (.03f + column * .34f), size.height * (.12f + row * .52f) + offset),
                Size(size.width * .26f, size.height * .34f),
                CornerRadius(12.dp.toPx()),
            )
        }
    }

    private fun DrawScope.drawArtworkLight(angle: Float) {
        val y = size.height * (.5f + sin(angle) * .34f)
        drawLine(Pearl.copy(alpha = .22f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        repeat(2) { edge ->
            val x = if (edge == 0) .04f else .96f
            drawLine(
                Pearl.copy(alpha = .16f),
                Offset(size.width * x, size.height * .20f),
                Offset(size.width * x, size.height * .8f),
                1.dp.toPx(),
            )
        }
    }

    private fun DrawScope.drawDocuments(
        angle: Float,
        folders: Boolean,
    ) {
        repeat(3) { index ->
            val x = size.width * (.02f + index * .36f)
            val y = size.height * (.17f + index * .14f) + sin(angle + index) * 2.dp.toPx()
            val width = size.width * .23f
            drawRoundRect(Pearl.copy(alpha = .07f), Offset(x, y), Size(width, size.height * .35f), CornerRadius(8.dp.toPx()))
            if (folders) {
                drawRoundRect(
                    Pearl.copy(alpha = .10f),
                    Offset(x, y - 4.dp.toPx()),
                    Size(width * .45f, 8.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                )
            }
            repeat(3) { line ->
                val lineY = y + 14.dp.toPx() + line * 9.dp.toPx()
                drawLine(Pearl.copy(alpha = .16f), Offset(x + 8.dp.toPx(), lineY), Offset(x + width - 8.dp.toPx(), lineY), 2.dp.toPx())
            }
        }
    }

    private fun DrawScope.drawLyricLines(angle: Float) {
        repeat(5) { index ->
            val light = (.5f + .5f * sin(angle - index * .8f))
            val y = size.height * (.15f + index * .17f)
            drawRoundRect(Pearl.copy(alpha = .035f + light * .07f), Offset(0f, y), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
    }

    private fun DrawScope.drawFrequencyBars(angle: Float) {
        repeat(8) { index ->
            val height = size.height * (.10f + (.5f + .5f * sin(angle + index * .85f)) * .32f)
            val x = size.width * (.02f + index * .135f)
            drawRoundRect(
                Pearl.copy(alpha = .13f),
                Offset(x, size.height * .73f - height / 2),
                Size(5.dp.toPx(), height),
                CornerRadius(3.dp.toPx()),
            )
        }
    }

    private fun DrawScope.drawCrossfade(
        path: Path,
        angle: Float,
        stroke: Stroke,
    ) {
        repeat(2) { curve ->
            path.reset()
            repeat(49) { step ->
                val t = step / 48f
                val rise = if (curve == 0) t else 1f - t
                val y = size.height * (.75f - rise * .5f) + sin(angle) * 2.dp.toPx()
                if (step == 0) path.moveTo(0f, y) else path.lineTo(size.width * t, y)
            }
            drawPath(path, if (curve == 0) Pearl.copy(alpha = .22f) else Ocean.copy(alpha = .4f), style = stroke)
        }
    }

    private fun DrawScope.drawDiscoveryRings(
        angle: Float,
        stroke: Stroke,
    ) {
        repeat(3) { index ->
            drawCircle(
                Pearl.copy(alpha = .05f + index * .018f),
                radius = size.minDimension * (.20f + index * .12f) + sin(angle) * 2.dp.toPx(),
                center = center,
                style = stroke,
            )
        }
    }

    private fun DrawScope.drawWidgetLayers(angle: Float) {
        repeat(3) { index ->
            drawRoundRect(
                Pearl.copy(alpha = .045f + index * .015f),
                Offset(size.width * .04f, size.height * (.15f + index * .28f) + sin(angle + index) * 2.dp.toPx()),
                Size(size.width * .92f, size.height * .19f),
                CornerRadius(22.dp.toPx()),
            )
        }
    }
}
