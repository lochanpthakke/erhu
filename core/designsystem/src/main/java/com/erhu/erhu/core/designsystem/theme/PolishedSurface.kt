package com.erhu.erhu.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Opaque neutral gloss shared by playback chrome. No backdrop consumer or optical filter. */
@Composable
fun Modifier.polishedSurface(shape: Shape): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val reflectionHeight = size.height.coerceAtMost(220.dp.toPx())
        val backing = Brush.verticalGradient(listOf(Color(0xFF292929), Color(0xFF111111)), endY = reflectionHeight)
        val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = reflectionHeight)
        onDrawBehind {
            drawOutline(outline, backing)
            drawOutline(outline, edge, style = Stroke(1.dp.toPx()))
        }
    }
