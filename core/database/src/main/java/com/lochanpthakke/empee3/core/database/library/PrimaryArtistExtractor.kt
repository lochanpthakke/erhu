package com.lochanpthakke.empee3.core.database.library

object PrimaryArtistExtractor {
    fun extractPrimaryArtist(rawArtist: String?): String? {
        if (rawArtist.isNullOrBlank() || rawArtist.equals("Unknown Artist", ignoreCase = true)) return null
        val firstRun = CreditText.runs(rawArtist).firstOrNull()?.raw ?: rawArtist
        val segments = CreditText.segmentsOf(firstRun)
        val firstSegment = segments.firstOrNull()?.text?.trim()
        return if (!firstSegment.isNullOrBlank() && !firstSegment.equals("Unknown Artist", ignoreCase = true)) {
            firstSegment
        } else {
            rawArtist.trim()
        }
    }
}
