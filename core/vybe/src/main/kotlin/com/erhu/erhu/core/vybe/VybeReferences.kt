// Numeric bounds here describe token edit distance, catalog segmentation, BPM and calendar ranges.
@file:Suppress("MagicNumber")

package com.erhu.erhu.core.vybe

data class VybeReference(
    val id: Int,
    val text: String,
    val matches: List<VybeNameMatch>,
    val selected: VybeNameMatch? = null,
    val filteredOut: Boolean = false,
)

/** Bounded catalog-aware segmentation preserves commas/and/& inside known names and quotes. */
internal object VybeReferences {
    @Suppress("ReturnCount")
    fun parse(
        text: String,
        index: VybeNameIndex,
        preferred: EntityKind?,
    ): List<VybeReference>? {
        val quotes = Regex("\"[^\"]*\"|“[^”]*”").findAll(text).map { it.range }.toList()
        val separators =
            Regex("""\s+(?:and|plus|&)\s+|[,;]""", RegexOption.IGNORE_CASE)
                .findAll(text)
                .filter { m -> quotes.none { m.range.first in it } }
                .toList()
        if (separators.isEmpty()) return null
        if (separators.size >= 16) return listOf(VybeReference(0, text, emptyList()))
        val starts = listOf(0) + separators.map { it.range.last + 1 }
        val ends = separators.map { it.range.first } + listOf(text.length)

        data class Plan(
            val refs: List<VybeReference>,
            val score: Int,
        )
        val memo = mutableMapOf<Pair<Int, Int>, Plan?>()
        val spans = mutableMapOf<Pair<Int, Int>, Pair<String, List<VybeNameMatch>>>()

        fun solve(
            start: Int,
            slots: Int,
        ): Plan? {
            if (start == starts.size) return Plan(emptyList(), 0)
            if (slots == 0) return null
            val key = start to slots
            if (key in memo) return memo[key]
            var best: Plan? = null
            for (end in start until starts.size) {
                val (phrase, matches) =
                    spans.getOrPut(start to end) {
                        val phrase = text.substring(starts[start], ends[end]).trim()
                        phrase to index.find(phrase, preferred)
                    }
                val tail = solve(end + 1, slots - 1) ?: continue
                val words = nameWords(phrase).size
                val score = tail.score + if (matches.isEmpty()) -words * 100 - 1 else words * 1000 - matches.size - 1
                val plan = Plan(listOf(VybeReference(start, phrase, matches)) + tail.refs, score)
                if (best == null || plan.score > best!!.score) best = plan
            }
            memo[key] = best
            return best
        }
        val found = solve(0, 8)?.refs ?: return listOf(VybeReference(0, text, emptyList()))
        return found.takeIf { it.size > 1 }
    }
}
