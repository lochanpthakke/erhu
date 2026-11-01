package com.erhu.erhu.core.network.tagger

import kotlin.math.roundToInt

/**
 * Tempos written by a metadata fix, and accepted as a valid existing tag. Wide on purpose: slow
 * ambient pieces sit near 40, speedcore well above 250; outside this a value is a broken tag
 * (0, a year, a track number) rather than music.
 */
val PLAUSIBLE_BPM = 20..400

private val BPM_NUMBER = Regex("""^\s*(\d{1,4}(?:[.,]\d+)?)\s*(?:bpm)?\s*$""", RegexOption.IGNORE_CASE)

/** A BPM tag value ("128", "127.9", "128 BPM") as a whole number, or null when it isn't one. */
fun parseBpmTag(text: String?): Int? =
    text
        ?.let { BPM_NUMBER.find(it) }
        ?.groupValues
        ?.get(1)
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.roundToInt()

/** The file's BPM if it is a plausible tempo, else null (missing, 0 or junk count as no BPM). */
fun usableBpm(text: String?): Int? = parseBpmTag(text)?.takeIf { it in PLAUSIBLE_BPM }

/** A manual tempo correction for a BPM read at the wrong octave. */
enum class BpmCorrection(
    val label: String,
    private val factor: Double,
) {
    HALF_TIME("Half time", 0.5),
    DOUBLE_TIME("Double time", 2.0),
    ;

    /** [bpm] corrected (rounded: 155 -> 78), or null when the result isn't a plausible tempo. */
    fun applyTo(bpm: Int): Int? = (bpm * factor).roundToInt().takeIf { it in PLAUSIBLE_BPM }
}
