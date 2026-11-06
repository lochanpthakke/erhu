package com.erhu.erhu.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erhu.erhu.core.designsystem.component.GlassAlertDialog
import com.erhu.erhu.core.designsystem.foundation.Radius
import com.erhu.erhu.core.designsystem.foundation.Spacing
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest side a picked image is decoded at; plenty for a square cover. */
private const val DECODE_MAX_PX = 2048

/** Side of the saved cover. */
private const val COVER_PX = 1024
private const val COVER_JPEG_QUALITY = 90
private const val MAX_ZOOM = 5f
private val EDITOR_PREVIEW_SIZE = 160.dp

/** What the user decided in the editor; applied only on Save. */
sealed interface CoverChange {
    data object None : CoverChange

    data object Remove : CoverChange

    class Replace(
        val jpegBytes: ByteArray,
        val preview: ImageBitmap,
    ) : CoverChange
}

/**
 * Edit a playlist's name and its own cover. "Choose image" opens the system
 * photo picker (no storage permission needed), then [SquareCropDialog]; the
 * cropped cover is saved with the playlist, independent of its songs.
 */
@Composable
@Suppress("LongMethod")
fun EditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (name: String, cover: CoverChange) -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.name) }
    var cover by remember(playlist.id) { mutableStateOf<CoverChange>(CoverChange.None) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) pickedUri = uri
        }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit playlist") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val previewModifier = Modifier.size(EDITOR_PREVIEW_SIZE).clip(Radius.mediumShape)
                when (val change = cover) {
                    is CoverChange.Replace ->
                        Image(
                            bitmap = change.preview,
                            contentDescription = "New cover",
                            contentScale = ContentScale.Crop,
                            modifier = previewModifier,
                        )
                    CoverChange.Remove -> PlaylistCover(playlist = playlist.copy(artworkUri = null), modifier = previewModifier)
                    CoverChange.None -> PlaylistCover(playlist = playlist, modifier = previewModifier)
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = { picker.launch("image/*") }) {
                        Text("Choose image")
                    }
                    val hasCover = cover is CoverChange.Replace || (cover == CoverChange.None && playlist.artworkUri != null)
                    if (hasCover) {
                        TextButton(onClick = { cover = CoverChange.Remove }) { Text("Remove image") }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, cover) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    pickedUri?.let { uri ->
        SquareCropDialog(
            imageUri = uri,
            onDismiss = { pickedUri = null },
            onCropped = { bytes, preview ->
                cover = CoverChange.Replace(bytes, preview)
                pickedUri = null
            },
        )
    }
}

/**
 * Square crop: the picked image fills a square frame; pinch to zoom and drag to
 * choose what is inside. The result is encoded once on Done, never per frame.
 */
@Composable
@Suppress("LongMethod")
internal fun SquareCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (ByteArray, ImageBitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(imageUri) { mutableStateOf(false) }
    LaunchedEffect(imageUri) {
        source = withContext(Dispatchers.IO) { decodeForCrop(context, imageUri) }
        failed = source == null
    }
    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUri) { mutableFloatStateOf(0f) }
    var framePx by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = Radius.largeShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("Crop cover", style = TextStyles.sectionTitle)
                Text(
                    "Pinch to zoom, drag to position.",
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
                                drawImage(
                                    image = image,
                                    dstOffset =
                                        IntOffset(
                                            ((frame - width) / 2f + offsetX).roundToInt(),
                                            ((frame - height) / 2f + offsetY).roundToInt(),
                                        ),
                                    dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                                )
                            }
                        }
                        failed -> Text("This image could not be opened.", color = MaterialTheme.colorScheme.error)
                        else -> CircularProgressIndicator()
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        enabled = source != null,
                        onClick = {
                            val bitmap = source ?: return@TextButton
                            val rect = squareCropRect(bitmap.width, bitmap.height, framePx, zoom, offsetX, offsetY)
                            scope.launch {
                                val (bytes, preview) = withContext(Dispatchers.Default) { encodeCover(bitmap, rect) }
                                onCropped(bytes, preview.asImageBitmap())
                            }
                        },
                    ) { Text("Done") }
                }
            }
        }
    }
}

/** Decodes [uri] with its rotation applied, at most [DECODE_MAX_PX] on the longest side; null if unreadable. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun decodeForCrop(
    context: Context,
    uri: Uri,
): Bitmap? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > DECODE_MAX_PX) decoder.setTargetSampleSize((longest + DECODE_MAX_PX - 1) / DECODE_MAX_PX)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = max(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = max(1, (longest + DECODE_MAX_PX - 1) / DECODE_MAX_PX) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (e: Exception) {
        null
    }

/** Cuts [rect] out of [source], scales it to at most [COVER_PX] and encodes it as JPEG. */
private fun encodeCover(
    source: Bitmap,
    rect: CropRect,
): Pair<ByteArray, Bitmap> {
    val square = Bitmap.createBitmap(source, rect.left, rect.top, rect.size, rect.size)
    val cover = if (rect.size > COVER_PX) Bitmap.createScaledBitmap(square, COVER_PX, COVER_PX, true) else square
    val bytes =
        ByteArrayOutputStream().use { out ->
            cover.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, out)
            out.toByteArray()
        }
    return bytes to cover
}
