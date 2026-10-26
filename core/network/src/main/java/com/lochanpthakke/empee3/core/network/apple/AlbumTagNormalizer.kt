package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.network.scorer.foldForMatch

object AlbumTagNormalizer {
    fun fold(input: String): String = foldForMatch(input)

    fun cleanAlbumTitle(title: String): String {
        var cleaned = title
        val noiseWords =
            listOf(
                "deluxe",
                "remaster",
                "remastered",
                "expanded",
                "anniversary",
                "bonus",
                "explicit",
                "clean",
                "edition",
                "version",
                "special",
                "collector",
                "disc \\d+",
                "cd ?\\d+",
                "feat\\.",
                "ft\\.",
            )
        val noisePatternStr = noiseWords.joinToString("|")

        val bracketPattern = "(?i)[(\\[][^)\\]]*?\\b($noisePatternStr)\\b[^)\\]]*?[)\\]]".toRegex()
        cleaned = cleaned.replace(bracketPattern, "")

        val dashPattern = "(?i)\\s+-\\s+.*?\\b($noisePatternStr)\\b.*$".toRegex()
        cleaned = cleaned.replace(dashPattern, "")

        val barePattern = "(?i)\\b(deluxe|expanded|remastered)( edition| version)?$".toRegex()
        cleaned = cleaned.replace(barePattern, "")

        return cleaned.trim()
    }

    fun cleanArtist(artist: String): List<String> {
        val noFeat = artist.replace("(?i)\\s+(feat\\.|ft\\.|featuring)\\s+.*$".toRegex(), "").trim()
        val splits = noFeat.split(Regex("\\s*(?:&|,|;| x |/)\\s*"))
        val foldedFull = foldForMatch(noFeat)
        val foldedSplits = splits.map { foldForMatch(it) }.filter { it.isNotBlank() }

        return (listOf(foldedFull) + foldedSplits).distinct().filter { it.isNotBlank() }
    }
}
