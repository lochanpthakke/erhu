package com.erhu.erhu.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.LocalPopupGlassBackdrop
import com.erhu.erhu.core.designsystem.theme.PopupGlassSurface
import com.erhu.erhu.core.designsystem.theme.glassBackdropSource
import com.erhu.erhu.core.designsystem.theme.rememberGlassBackdrop
import com.erhu.erhu.core.common.R as CommonR

@Composable
fun PopupGlassPreview(modifier: Modifier = Modifier) {
    val backdrop = rememberGlassBackdrop("popup-preview", screenCoordinates = true)
    Box(modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))) {
        Canvas(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
            drawRect(Brush.linearGradient(listOf(Color(0xFFE9DBBE), Color(0xFF274D64), Color(0xFF122332))))
            repeat(12) { index ->
                val x = size.width * index / 11f
                drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Offset(x - size.height, size.height), 7.dp.toPx())
            }
            drawCircle(Color(0xFFD6A56C), size.height * 0.4f, Offset(size.width * 0.25f, size.height * 0.55f))
        }
        CompositionLocalProvider(LocalPopupGlassBackdrop provides backdrop) {
            PopupGlassSurface(Modifier.align(Alignment.CenterEnd).padding(16.dp).width(200.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(CommonR.string.theme_popup_preview_title))
                    Text(stringResource(CommonR.string.theme_popup_preview_play_next), Modifier.padding(top = 12.dp))
                    Text(stringResource(CommonR.string.theme_popup_preview_add_to_queue), Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
