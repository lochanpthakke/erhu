package com.erhu.erhu.core.common.util

import java.text.Normalizer
import java.util.Locale

object SearchNormalizer {
    /**
     * Normalizes text for search matching by translating leetspeak symbols, stripping
     * diacritics/accents, and removing punctuation.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val leetspeak =
            text
                .replace("$", "s")
                .replace("@", "a")
                .replace("!", "i")
                .replace("3", "e")
                .replace("0", "o")
                .replace("&", "and")
        val decomposed = Normalizer.normalize(leetspeak.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val accentFree = decomposed.replace(Regex("\\p{M}"), "")
        return accentFree.replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Normalizes text keeping spaces for word boundary / token matching.
     */
    fun normalizeWords(text: String): String {
        if (text.isBlank()) return ""
        val leetspeak =
            text
                .replace("$", "s")
                .replace("@", "a")
                .replace("!", "i")
                .replace("3", "e")
                .replace("0", "o")
                .replace("&", "and")
        val decomposed = Normalizer.normalize(leetspeak.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val accentFree = decomposed.replace(Regex("\\p{M}"), "")
        return accentFree.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Returns true if [target] matches [query] via raw substring, normalized leetspeak/accent-free
     * substring, or all query token matches.
     */
    fun matches(
        target: String,
        query: String,
    ): Boolean {
        if (query.isBlank() || target.isBlank()) return false
        if (target.contains(query, ignoreCase = true)) return true

        val normTarget = normalize(target)
        val normQuery = normalize(query)
        if (normTarget.contains(normQuery) || normQuery.contains(normTarget)) return true

        val wordsTarget = normalizeWords(target)
        val wordsQuery = normalizeWords(query)
        val tokens = wordsQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        return tokens.all { token ->
            wordsTarget.contains(token) || normTarget.contains(token)
        }
    }
}
