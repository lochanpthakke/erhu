package com.lochanpthakke.empee3.core.database.library

/**
 * Explicitly approved manual identity evidence: each entry maps one exact credit
 * text to one artist and was approved individually by the user. This is not a
 * protected-name list and implies nothing about any other credit containing the
 * same separators. Database aliases (`artist_alias`) are applied on top of these.
 */
object ManualArtistAliases {
    private class Approved(
        val creditText: String,
        val artistName: String,
    )

    private val APPROVED =
        listOf(
            // Approved 2026-10-04: the files' tag writes this artist as "Tyler/ The Creator".
            Approved(creditText = "Tyler/ The Creator", artistName = "Tyler, The Creator"),
        )

    /** Alias credit key → canonical artist key. */
    val aliasKeys: Map<String, String> =
        APPROVED.associate { CreditText.canonicalKey(it.creditText) to CreditText.canonicalKey(it.artistName) }

    /** Canonical artist key → the artist's approved display name. */
    val artistNames: Map<String, String> = APPROVED.associate { CreditText.canonicalKey(it.artistName) to it.artistName }
}
