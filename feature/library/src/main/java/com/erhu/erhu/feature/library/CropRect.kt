package com.erhu.erhu.feature.library

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square region of the source image, in source pixels. */
data class CropRect(
    val left: Int,
    val top: Int,
    val size: Int,
)

/**
 * The crop frame is a square of [frame] px. The image is scaled to just cover it
 * (zoom 1), then by [zoom], and moved by an offset from the centred position.
 */
private fun coverScale(
    imageWidth: Int,
    imageHeight: Int,
    frame: Float,
    zoom: Float,
): Float = max(frame / imageWidth, frame / imageHeight) * zoom

/** Keeps the frame inside the image: the furthest the image may be moved from centre on each axis. */
fun clampCropOffset(
    imageWidth: Int,
    imageHeight: Int,
    frame: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): Pair<Float, Float> {
    val scale = coverScale(imageWidth, imageHeight, frame, zoom)
    val maxX = max(0f, (imageWidth * scale - frame) / 2f)
    val maxY = max(0f, (imageHeight * scale - frame) / 2f)
    // "+ 0f" turns a clamped -0.0 into 0.0
    return offsetX.coerceIn(-maxX, maxX) + 0f to offsetY.coerceIn(-maxY, maxY) + 0f
}

/** The source pixels shown inside the frame (offsets are clamped first). */
fun squareCropRect(
    imageWidth: Int,
    imageHeight: Int,
    frame: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): CropRect {
    val scale = coverScale(imageWidth, imageHeight, frame, zoom)
    val (x, y) = clampCropOffset(imageWidth, imageHeight, frame, zoom, offsetX, offsetY)
    val size = min((frame / scale).roundToInt(), min(imageWidth, imageHeight)).coerceAtLeast(1)
    val left = (((imageWidth * scale - frame) / 2f - x) / scale).roundToInt().coerceIn(0, imageWidth - size)
    val top = (((imageHeight * scale - frame) / 2f - y) / scale).roundToInt().coerceIn(0, imageHeight - size)
    return CropRect(left, top, size)
}
