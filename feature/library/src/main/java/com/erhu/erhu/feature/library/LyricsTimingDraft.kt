package com.erhu.erhu.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.erhu.erhu.core.model.LyricsTimingOffset
import kotlin.math.roundToLong

/** A local editor draft: no persistence or player changes until the user explicitly requests them. */
internal class LyricsTimingDraft(
    initialMs: Long,
) {
    var offsetMs by mutableLongStateOf(LyricsTimingOffset.clamp(initialMs))
        private set
    var input by mutableStateOf(offsetMs.toString())
        private set
    val valid: Boolean get() = input.toLongOrNull() != null

    fun set(value: Long) {
        offsetMs = LyricsTimingOffset.clamp(value)
        input = offsetMs.toString()
    }

    fun input(value: String) {
        if (value.length > MAX_INPUT_LENGTH || !value.matches(Regex("[+-]?[0-9]*"))) return
        input = value
        value.toLongOrNull()?.let {
            offsetMs = LyricsTimingOffset.clamp(it)
            if (offsetMs != it) input = offsetMs.toString()
        }
    }

    fun slide(value: Float) {
        if (value.isFinite()) set((value / LyricsTimingOffset.STEP_MS).roundToLong() * LyricsTimingOffset.STEP_MS)
    }

    fun step(direction: Int) = set(offsetMs + direction.coerceIn(-1, 1) * LyricsTimingOffset.STEP_MS)
}

private const val MAX_INPUT_LENGTH = 10
