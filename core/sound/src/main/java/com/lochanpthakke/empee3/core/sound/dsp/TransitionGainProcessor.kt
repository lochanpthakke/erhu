package com.lochanpthakke.empee3.core.sound.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.lochanpthakke.empee3.core.model.FilterSetting
import com.lochanpthakke.empee3.core.model.TransitionRole
import com.lochanpthakke.empee3.core.model.TransitionSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * One track's side of a transition, in content time from the start of the audio that follows a
 * flush, so it lines up with the music itself whatever the output latency is.
 *
 * [leadInMs] of audio play before the transition starts (used by previews): the outgoing track at
 * its starting level, the incoming one silent. [entryRampMs] rises from silence at the very start
 * (the outgoing tail taking over from the main player, so the hand-over does not click). After the
 * transition the outgoing track stays silent; the incoming one plays on at full level unchanged.
 */
data class TransitionAutomation(
    val role: TransitionRole,
    val spec: TransitionSpec,
    /** Effective duration (the spec's, shortened to fit both tracks). */
    val durationMs: Int,
    val entryRampMs: Int = 0,
    val leadInMs: Int = 0,
) {
    private fun progress(ms: Double): Float = ((ms - leadInMs) / durationMs.coerceAtLeast(1)).toFloat()

    fun gainAt(ms: Double): Float {
        val t = progress(ms)
        val shaped =
            when {
                t < 0f -> if (role == TransitionRole.OUTGOING) spec.gain(role, 0f) else 0f
                else -> spec.gain(role, t)
            }
        val entry = if (entryRampMs > 0) (ms / entryRampMs).toFloat().coerceIn(0f, 1f) else 1f
        return (shaped * entry).coerceIn(0f, 1f)
    }

    /** Low-shelf gain in dB at [ms]; 0 without a bass swap. */
    fun bassDbAt(ms: Double): Float = if (!spec.bassSwap) 0f else spec.bassDb(role, progress(ms).coerceIn(0f, 1f))

    /** The filter sweep on this track at [ms], or null when this track is not filtered. */
    fun filterAt(ms: Double): FilterSetting? = spec.filter.settingAt(role, progress(ms))

    /** True once an incoming automation is over and the audio plays unchanged again. */
    fun isFinished(ms: Double): Boolean = role == TransitionRole.INCOMING && progress(ms) >= 1f
}

/**
 * Applies one [TransitionAutomation] to 16-bit or float PCM, sample-accurately in content time:
 * the volume curve, and for a bass swap a low-shelf filter whose gain follows the swap (its
 * coefficients are updated every few frames from the same running filter, so the change is smooth).
 * Idle (the normal case) it copies audio unchanged. Each player has its own instance, so two
 * overlapping tracks never share filter state; it never touches the player's volume or the user's
 * equalizer settings.
 *
 * [arm] from any thread; the automation starts at the next flush (the seek that starts the new
 * track, or the start of a prepared player). A later flush cancels a running automation (the user
 * skipped or seeked), except within the first [restartWindowMs] of it, where the player may flush
 * again while starting the same track; then it restarts from zero.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class TransitionGainProcessor(
    private val restartWindowMs: Int = DEFAULT_RESTART_WINDOW_MS,
    private val cancelOnLaterFlush: Boolean = true,
) : BaseAudioProcessor() {
    private sealed interface State {
        data object Idle : State

        data class Armed(
            val automation: TransitionAutomation,
        ) : State

        data class Running(
            val automation: TransitionAutomation,
        ) : State
    }

    private val state = AtomicReference<State>(State.Idle)

    /** Frames processed in the running automation (playback thread only). */
    private var frame = 0L
    private var filters: Array<BiquadFilter> = emptyArray()
    private var filterDb = 0f
    private var passFilters: Array<PassFilter> = emptyArray()
    private var passSetting: FilterSetting? = null

    /** True while an automation is waiting for its flush or still shaping the audio. */
    val isEngaged: Boolean get() = state.get() != State.Idle

    /** True once the armed automation has started (its flush arrived). */
    val hasStarted: Boolean get() = state.get() is State.Running

    fun arm(automation: TransitionAutomation) {
        state.set(State.Armed(automation))
    }

    /** Back to unity gain at once. */
    fun cancel() {
        state.set(State.Idle)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    override fun onFlush() {
        val current = state.get()
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        val startingUp = frame * MS_PER_S < restartWindowMs.toLong() * sampleRate
        val next =
            when (current) {
                is State.Armed -> State.Running(current.automation)
                is State.Running -> if (startingUp || !cancelOnLaterFlush) current else State.Idle
                State.Idle -> State.Idle
            }
        frame = 0L
        filters = emptyArray()
        filterDb = 0f
        passFilters = emptyArray()
        passSetting = null
        state.compareAndSet(current, next)
    }

    override fun onReset() {
        state.set(State.Idle)
        frame = 0L
        filters = emptyArray()
        passFilters = emptyArray()
        passSetting = null
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())
        val running = state.get() as? State.Running
        if (running == null) {
            output.put(inputBuffer)
        } else {
            apply(running, inputBuffer, output)
        }
        output.flip()
    }

    @Suppress("LongMethod")
    private fun apply(
        running: State.Running,
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val sampleRate = format.sampleRate.coerceAtLeast(1)
        val msPerFrame = MS_PER_S.toDouble() / sampleRate
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerFrame = channels * if (float) Float.SIZE_BYTES else Short.SIZE_BYTES
        val automation = running.automation
        val bass = automation.spec.bassSwap
        val sweep = automation.spec.filter.affects(automation.role)
        prepareFilters(channels, bass, sweep)
        val source = input.order(ByteOrder.nativeOrder())
        while (source.remaining() >= bytesPerFrame) {
            val ms = frame * msPerFrame
            val gain = automation.gainAt(ms)
            if (frame % FILTER_UPDATE_FRAMES == 0L) {
                if (bass) updateFilters(automation.bassDbAt(ms), sampleRate)
                if (sweep) updatePassFilters(automation.filterAt(ms), sampleRate)
            }
            for (channel in 0 until channels) {
                val x = if (float) source.float else source.short / PCM16_SCALE
                write(output, float, shape(channel, x, sweep, bass) * gain)
            }
            frame++
        }
        // A partial frame cannot occur with valid PCM; pass any stray bytes through unchanged.
        while (source.hasRemaining()) output.put(source.get())
        if (automation.isFinished(frame * msPerFrame)) state.compareAndSet(running, State.Idle)
    }

    /** One filter (and bass shelf) per channel, made once per automation and format. */
    private fun prepareFilters(
        channels: Int,
        bass: Boolean,
        sweep: Boolean,
    ) {
        if (bass && filters.size != channels) {
            filters = Array(channels) { BiquadFilter() }
            filterDb = Float.NaN
        }
        if (sweep && passFilters.size != channels) {
            passFilters = Array(channels) { PassFilter() }
            passSetting = null
        }
    }

    /** Filter sweep first, then the bass shelf: each player has its own filters, so no state is shared. */
    private fun shape(
        channel: Int,
        x: Float,
        sweep: Boolean,
        bass: Boolean,
    ): Float {
        val swept = if (sweep) passFilters[channel].process(x) else x
        return if (bass) filters[channel].processSample(swept) else swept
    }

    private fun write(
        output: ByteBuffer,
        float: Boolean,
        y: Float,
    ) {
        if (float) {
            output.putFloat(y)
        } else {
            output.putShort(Math.round(y * PCM16_SCALE).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
    }

    /** Moves the sweep's cutoff in steps under 1 % (inaudible as steps), keeping the filter state. */
    private fun updatePassFilters(
        setting: FilterSetting?,
        sampleRate: Int,
    ) {
        if (setting == null || isSmallStep(passSetting, setting)) return
        passSetting = setting
        passFilters.forEach { it.configure(setting.type, setting.hz, sampleRate) }
    }

    private fun isSmallStep(
        current: FilterSetting?,
        next: FilterSetting,
    ): Boolean = current != null && current.type == next.type && abs(next.hz / current.hz - 1f) < CUTOFF_STEP

    private fun updateFilters(
        db: Float,
        sampleRate: Int,
    ) {
        if (!filterDb.isNaN() && abs(db - filterDb) < FILTER_STEP_DB) return
        filterDb = db
        filters.forEach { it.configure(TransitionSpec.BASS_SHELF_HZ, sampleRate, db, SHELF_SLOPE, FilterType.LOW_SHELF) }
    }

    private companion object {
        const val DEFAULT_RESTART_WINDOW_MS = 60
        const val MS_PER_S = 1_000L
        const val PCM16_SCALE = 32_768f

        /** Coefficients follow the automation every 32 frames (under 1 ms) in steps of at most 0.05 dB. */
        const val FILTER_UPDATE_FRAMES = 32L
        const val FILTER_STEP_DB = 0.05f
        const val SHELF_SLOPE = 0.707f
        const val CUTOFF_STEP = 0.005f
    }
}
