package com.erhu.erhu.core.network.bpm

import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.network.api.AcousticBrainzApi
import com.erhu.erhu.core.network.tagger.PLAUSIBLE_BPM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/** Outcome of looking for a track's tempo. */
sealed interface BpmLookup {
    /**
     * [estimated] values are never written over a BPM the file already has. Every source is one
     * today: tempo detection can land on half or double time, and AcousticBrainz's analyses do
     * (EARFQUAKE 155.7, See You Again 155.4 where the songs are about 78-80 BPM).
     */
    data class Found(
        val bpm: Int,
        val source: String,
        val estimated: Boolean,
    ) : BpmLookup

    /** Every source answered, none with a dependable tempo; [reason] says why. */
    data class NotFound(
        val reason: String,
    ) : BpmLookup

    /** No source could be asked (network down and the audio unreadable); [reason] says why. */
    data class Failed(
        val reason: String,
    ) : BpmLookup
}

/** What AcousticBrainz has for one recording. */
internal sealed interface AcousticBrainzBpm {
    data class Measured(
        val bpm: Int,
        val analyses: Int,
    ) : AcousticBrainzBpm

    data class None(
        val reason: String,
    ) : AcousticBrainzBpm
}

private const val AGREEMENT = 0.03

/**
 * The tempo AcousticBrainz measured for [recordingId]: the median of its analyses (one per
 * submitted copy of the recording) when they all agree within 3 %. Copies that disagree (often
 * by half / double time) give none, rather than a pick.
 */
@Suppress("ReturnCount")
internal fun bpmFromAcousticBrainz(
    body: JsonObject,
    recordingId: String,
): AcousticBrainzBpm {
    val analyses = body[recordingId] as? JsonObject ?: return AcousticBrainzBpm.None("AcousticBrainz has no analysis of this recording")
    val values =
        analyses.values
            .mapNotNull { analysis ->
                val rhythm = (analysis as? JsonObject)?.get("rhythm") as? JsonObject
                rhythm?.get("bpm")?.jsonPrimitive?.doubleOrNull
            }.filter { it.roundToInt() in PLAUSIBLE_BPM }
    if (values.isEmpty()) return AcousticBrainzBpm.None("AcousticBrainz has no usable tempo for this recording")
    val median = values.sorted()[values.size / 2]
    if (values.any { abs(it - median) > AGREEMENT * median }) {
        val shown = values.joinToString(", ") { it.roundToInt().toString() }
        return AcousticBrainzBpm.None("AcousticBrainz's analyses disagree ($shown BPM)")
    }
    return AcousticBrainzBpm.Measured(median.roundToInt(), values.size)
}

/**
 * Finds a track's tempo, most trustworthy source first:
 * 1. AcousticBrainz, for a MusicBrainz match: computed from the recording itself (only the
 *    recording id is sent). Its data stops in 2022, so newer releases aren't there. It is an
 *    algorithm's reading too, and is sometimes double time, so it is treated as an estimate.
 * 2. An estimate from the file's own audio, decoded and analysed on this device ([TempoEstimator]);
 *    reported only when the song's sections agree.
 * Deezer isn't used: its search often returns nothing here, most tracks report 0 BPM, and some
 * report double time (I Feel It Coming: 186, checked 2026-10-15). Last.fm has no tempo.
 * (The file's own BPM tag comes first of all, but that is the planner's call, not a lookup.)
 */
@Singleton
class BpmFinder
    @Inject
    constructor(
        private val acousticBrainz: AcousticBrainzApi,
        private val pcmSource: PcmSource,
        private val logger: Logger,
    ) {
        private val byRecording = ConcurrentHashMap<String, AcousticBrainzBpm>()

        @Suppress("TooGenericExceptionCaught", "ReturnCount")
        suspend fun find(
            match: MetadataCandidate,
            filePath: String,
        ): BpmLookup {
            val reasons = mutableListOf<String>()
            var failures = 0
            val recordingId = match.id.takeIf { it.startsWith(MUSICBRAINZ_PREFIX) }?.removePrefix(MUSICBRAINZ_PREFIX)
            if (recordingId == null) {
                reasons += "AcousticBrainz needs a MusicBrainz match"
            } else {
                try {
                    when (val measured = measuredTempo(recordingId)) {
                        is AcousticBrainzBpm.Measured -> return BpmLookup.Found(measured.bpm, "AcousticBrainz", estimated = true)
                        is AcousticBrainzBpm.None -> reasons += measured.reason
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "AcousticBrainz lookup failed: ${e.message}")
                    failures++
                    reasons += "AcousticBrainz lookup failed (${e.message ?: e.javaClass.simpleName})"
                }
            }

            try {
                val pcm = pcmSource.decode(filePath)
                val estimate = withContext(Dispatchers.Default) { TempoEstimator.estimate(pcm.samples, pcm.sampleRate) }
                when (estimate) {
                    is TempoEstimate.Found -> {
                        val agreement = "${estimate.sectionsAgreeing} of ${estimate.sections} sections agree"
                        logger.i(TAG, "Estimated ${estimate.bpm} BPM on device for $filePath ($agreement)")
                        return BpmLookup.Found(estimate.bpm, "on-device estimate, $agreement", estimated = true)
                    }
                    is TempoEstimate.Uncertain -> reasons += "on-device estimate: ${estimate.reason}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "On-device tempo analysis failed for $filePath: ${e.message}")
                failures++
                reasons += "the audio couldn't be analysed (${e.message ?: e.javaClass.simpleName})"
            }
            val reason = reasons.joinToString("; ")
            val attempted = if (recordingId == null) 1 else 2
            return if (failures == attempted) BpmLookup.Failed(reason) else BpmLookup.NotFound(reason)
        }

        /** Cached per recording for the session; network errors are not cached. */
        private suspend fun measuredTempo(recordingId: String): AcousticBrainzBpm =
            byRecording[recordingId]
                ?: bpmFromAcousticBrainz(acousticBrainz.lowLevel(recordingId), recordingId).also { byRecording[recordingId] = it }

        private companion object {
            const val TAG = "BpmFinder"
            const val MUSICBRAINZ_PREFIX = "mb_"
        }
    }
