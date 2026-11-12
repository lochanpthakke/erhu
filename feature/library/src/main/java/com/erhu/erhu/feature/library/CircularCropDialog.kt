package com.erhu.erhu.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import com.erhu.erhu.core.common.R as CommonR

private const val MAX_ZOOM = 4f
private const val CROP_JPEG_QUALITY = 90
private const val MAX_CROP_SOURCE_DIMENSION = 2048

/**
 * WhatsApp / Instagram style circular profile picture cropper.
 *
 * Displays a dark translucent overlay with a transparent circular window in the center.
 * Users can pinch to zoom ($1\times$ to $4\times$) and drag/pan to align the artist's face or photo
 * inside the circular frame.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun CircularCropDialog(
    imageUri: Uri? = null,
    sourceBitmap: Bitmap? = null,
    onDismiss: () -> Unit,
    onCropped: (ByteArray, ImageBitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(imageUri, sourceBitmap) { mutableStateOf(sourceBitmap) }
    var failed by remember(imageUri, sourceBitmap) { mutableStateOf(false) }

    LaunchedEffect(imageUri, sourceBitmap) {
        if (sourceBitmap != null) {
            source = sourceBitmap
        } else if (imageUri != null) {
            source = withContext(Dispatchers.IO) { decodeForCircularCrop(context, imageUri) }
            failed = source == null
        }
    }

    var zoom by remember(imageUri, sourceBitmap) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUri, sourceBitmap) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUri, sourceBitmap) { mutableFloatStateOf(0f) }
    var framePx by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = Radius.largeShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(stringResource(CommonR.string.artist_crop_title), style = TextStyles.sectionTitle)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    stringResource(CommonR.string.artist_crop_hint),
                    style = TextStyles.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(Radius.mediumShape)
                            .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    val frame = with(LocalDensity.current) { maxWidth.toPx() }
                    framePx = frame
                    val bitmap = source
                    when {
                        bitmap != null -> {
                            val image = remember(bitmap) { bitmap.asImageBitmap() }
                            val strokeWidthPx = with(LocalDensity.current) { StrokeWidth.toPx() }
                            Canvas(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .pointerInput(bitmap, frame) {
                                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                                zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                                val (x, y) =
                                                    clampCropOffset(
                                                        bitmap.width,
                                                        bitmap.height,
                                                        frame,
                                                        zoom,
                                                        offsetX + pan.x,
                                                        offsetY + pan.y,
                                                    )
                                                offsetX = x
                                                offsetY = y
                                            }
                                        },
                            ) {
                                val scale = max(frame / bitmap.width, frame / bitmap.height) * zoom
                                val width = bitmap.width * scale
                                val height = bitmap.height * scale

                                // Draw transformed source image
                                drawImage(
                                    image = image,
                                    dstOffset =
                                        IntOffset(
                                            ((frame - width) / 2f + offsetX).roundToInt(),
                                            ((frame - height) / 2f + offsetY).roundToInt(),
                                        ),
                                    dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                                )

                                // Draw circular mask overlay (dark scrim outside circular viewport)
                                val rectPath =
                                    Path().apply {
                                        addRect(
                                            androidx.compose.ui.geometry
                                                .Rect(0f, 0f, frame, frame),
                                        )
                                    }
                                val circlePath =
                                    Path().apply {
                                        addOval(
                                            androidx.compose.ui.geometry
                                                .Rect(0f, 0f, frame, frame),
                                        )
                                    }
                                val maskPath =
                                    Path.combine(
                                        PathOperation.Difference,
                                        rectPath,
                                        circlePath,
                                    )

                                drawPath(
                                    path = maskPath,
                                    color = MaskOverlayColor,
                                )

                                // White circular guide ring
                                drawCircle(
                                    color = RingGuideColor,
                                    radius = frame / 2f - strokeWidthPx / 2f,
                                    style = Stroke(width = strokeWidthPx),
                                )
                            }
                        }
                        failed -> Text(stringResource(CommonR.string.image_load_failed), color = MaterialTheme.colorScheme.error)
                        else -> CircularProgressIndicator()
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.action_cancel)) }
                    TextButton(
                        enabled = source != null,
                        onClick = {
                            val bitmap = source ?: return@TextButton
                            val rect = squareCropRect(bitmap.width, bitmap.height, framePx, zoom, offsetX, offsetY)
                            scope.launch {
                                val (bytes, preview) = withContext(Dispatchers.Default) { encodeCircularCover(bitmap, rect) }
                                onCropped(bytes, preview)
                            }
                        },
                    ) {
                        Text(stringResource(CommonR.string.action_apply))
                    }
                }
            }
        }
    }
}

private val MaskOverlayColor = Color.Black.copy(alpha = 0.65f)
private val RingGuideColor = Color.White.copy(alpha = 0.85f)
private val StrokeWidth =
    androidx.compose.ui.unit
        .Dp(2f)

@Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
internal fun decodeForCircularCrop(
    context: Context,
    uri: Uri,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val decoded =
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val longest = max(info.size.width, info.size.height)
                    if (longest > MAX_CROP_SOURCE_DIMENSION) {
                        decoder.setTargetSampleSize((longest + MAX_CROP_SOURCE_DIMENSION - 1) / MAX_CROP_SOURCE_DIMENSION)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Exception) {
                null // Some document providers cannot supply the descriptor ImageDecoder needs.
            }
        if (decoded != null) return decoded
    }
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_CROP_SOURCE_DIMENSION || bounds.outHeight / sample > MAX_CROP_SOURCE_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) {
        null
    }
}

private fun encodeCircularCover(
    source: Bitmap,
    rect: CropRect,
): Pair<ByteArray, ImageBitmap> {
    val cropped = Bitmap.createBitmap(source, rect.left, rect.top, rect.size, rect.size)
    val stream = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, CROP_JPEG_QUALITY, stream)
    val bytes = stream.toByteArray()
    val preview = cropped.asImageBitmap()
    return bytes to preview
}
