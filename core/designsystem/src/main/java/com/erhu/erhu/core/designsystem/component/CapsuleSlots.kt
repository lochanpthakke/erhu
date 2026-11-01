package com.erhu.erhu.core.designsystem.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.floor

/**
 * Where each item of a row is, for [LiquidCapsule]: its cell (the touch target) and its content
 * (icon + label), as actually laid out. Measured by [capsuleSlot] / [capsuleContent]; nothing is
 * hard-coded, so the capsule fits any screen width and any label.
 */
@Stable
class CapsuleSlots(
    count: Int,
) {
    internal val cells = mutableStateListOf<Rect>().apply { repeat(count) { add(Rect.Zero) } }
    internal val contents = mutableStateListOf<Size>().apply { repeat(count) { add(Size.Zero) } }

    val count: Int get() = cells.size

    internal val measured: Boolean
        get() = cells.all { it.width > 0f } && contents.all { it.width > 0f }

    internal fun cell(
        index: Int,
        rect: Rect,
    ) {
        if (index in cells.indices && cells[index] != rect) cells[index] = rect
    }

    internal fun content(
        index: Int,
        size: IntSize,
    ) {
        val value = size.toSize()
        if (index in contents.indices && contents[index] != value) contents[index] = value
    }

    /** The capsule around item [index]: its content plus padding, centred in its cell, never wider than the cell. */
    internal fun capsuleAt(
        index: Int,
        padding: Float,
        inset: Float,
    ): Rect {
        val cell = cells[index]
        val width = (contents[index].width + padding * 2f).coerceAtMost(cell.width - inset * 2f)
        return Rect(cell.center.x - width / 2f, cell.top + inset, cell.center.x + width / 2f, cell.bottom - inset)
    }

    /** The capsule at fractional [position]: between two items, their capsules blended (null until measured). */
    internal fun capsuleBetween(
        position: Float,
        padding: Float,
        inset: Float,
    ): Rect? =
        when {
            !measured -> null
            count == 1 -> capsuleAt(0, padding, inset)
            else -> {
                val from = floor(position).toInt().coerceIn(0, count - 2)
                val t = position - from // outside 0..1 past the ends: the motion carries on a little
                val a = capsuleAt(from, padding, inset)
                val b = capsuleAt(from + 1, padding, inset)
                Rect(
                    left = a.left + (b.left - a.left) * t,
                    top = a.top + (b.top - a.top) * t,
                    right = a.right + (b.right - a.right) * t,
                    bottom = a.bottom + (b.bottom - a.bottom) * t,
                )
            }
        }

    /** Distance between neighbouring items' centres, px (for turning finger movement into position). */
    internal val step: Float
        get() = if (count < 2) 1f else ((cells.last().center.x - cells.first().center.x) / (count - 1)).coerceAtLeast(1f)
}

/** How selected item [index] is at [position]: 1 under the capsule, fading to 0 one item away. */
fun capsuleEmphasis(
    position: Float,
    index: Int,
): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

/** Item [index]'s cell (its full touch target, a child of the row the capsule is drawn over). */
fun Modifier.capsuleSlot(
    slots: CapsuleSlots,
    index: Int,
): Modifier = onPlaced { slots.cell(index, Rect(it.positionInParent(), it.size.toSize())) }

/** Item [index]'s visible content (icon and label): the capsule is sized around it. */
fun Modifier.capsuleContent(
    slots: CapsuleSlots,
    index: Int,
): Modifier = onSizeChanged { slots.content(index, it) }
