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
 */
class ArtistIdentityResolver(
    private val aliases: Map<String, String>,
) {
    fun canonicalKey(creditKey: String): String = aliases[creditKey] ?: creditKey

    fun idOf(creditKey: String): Long = IdentityHash.of(canonicalKey(creditKey))
}

fun chooseDisplayName(variants: Map<String, Int>): String =
    variants.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .first()
        .key
