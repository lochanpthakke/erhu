package com.lochanpthakke.empee3.core.database.library

/** Deterministic 64-bit FNV-1a, identical to the hash the app already uses for artist ids. */
object IdentityHash {
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L

    fun of(key: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (char in key) {
            hash = hash xor char.code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }
}

/**
 * Layer B. An artist is its canonical key; [aliases] (alias key → canonical key)
 * come only from explicit manual/external evidence. No fuzzy matching, no chains.
 * [canonicalNames] gives an aliased artist its approved display name.
 */
class ArtistIdentityResolver(
    private val aliases: Map<String, String>,
    private val canonicalNames: Map<String, String> = emptyMap(),
) {
    fun canonicalKey(creditKey: String): String = aliases[creditKey] ?: creditKey

    fun idOf(creditKey: String): Long = IdentityHash.of(canonicalKey(creditKey))

    /** True when explicit evidence names the artist behind this credit text. */
    fun isAliased(creditKey: String): Boolean = creditKey in aliases

    fun approvedName(canonicalKey: String): String? = canonicalNames[canonicalKey]

    /**
     * Whether a credit denotes an artist: a resolved credit does; an unresolved one
     * (text spanning an unproven boundary) only through explicit alias evidence.
     */
    fun hasIdentity(credit: ResolvedCredit): Boolean = !credit.isUnresolved || isAliased(credit.key)
}

fun chooseDisplayName(variants: Map<String, Int>): String =
    variants.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .first()
        .key
