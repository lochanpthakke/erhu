package com.lochanpthakke.empee3.core.database.scanner

data class ParsedArtist(
    val name: String,
    val normalizedName: String,
    val id: Long,
)

data class ParsedArtistResult(
    val primaryArtists: List<ParsedArtist>,
    val featuredArtists: List<ParsedArtist>,
    val displayArtist: String,
)

object ArtistParser {
    private val FEAT_REGEX =
        Regex(
            "(?i)(?:\\s*[(\\[{]?\\s*(?:feat\\.?|ft\\.|featuring|with)\\s+([^)\\]}]+)[)\\]}]?|\\s+(?:feat\\.?|ft\\.|featuring|with)\\s+(.+)$)",
        )

    private val DELIMITER_REGEX = Regex("[,;&/|]|\\s+and\\s+")
    private val EXTRA_WHITESPACE_REGEX = Regex("\\s+")

    private val PROTECTED_ARTISTS =
        setOf(
            "tyler, the creator",
            "earth, wind & fire",
            "crosby, stills, nash & young",
            "emerson, lake & palmer",
            "sly & the family stone",
            "simon & garfunkel",
            "hall & oates",
            "ac/dc",
            "florence + the machine",
            "blood, sweat & tears",
            "panic! at the disco",
        )

    fun parse(rawArtist: String?): ParsedArtistResult {
        val trimmed = rawArtist?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("Unknown Artist", ignoreCase = true)) {
            val unknown = createParsedArtist("Unknown Artist")
            return ParsedArtistResult(
                primaryArtists = listOf(unknown),
                featuredArtists = emptyList(),
                displayArtist = "Unknown Artist",
            )
        }

        var primaryPart = trimmed
        var featuredPart = ""

        val featMatch = FEAT_REGEX.find(trimmed)
        if (featMatch != null) {
            featuredPart =
                featMatch.groupValues
                    .drop(1)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
            primaryPart = trimmed.substring(0, featMatch.range.first).trim()
        }

        val primaryNames = splitArtists(primaryPart, isPrimary = true)
        val featuredNames = splitArtists(featuredPart, isPrimary = false)

        val primaryList = primaryNames.map { createParsedArtist(it) }.distinctBy { it.id }
        val featuredList =
            featuredNames
                .map { createParsedArtist(it) }
                .filter { feat -> primaryList.none { it.id == feat.id } }
                .distinctBy { it.id }

        val finalPrimary = if (primaryList.isEmpty()) listOf(createParsedArtist(trimmed)) else primaryList

        return ParsedArtistResult(
            primaryArtists = finalPrimary,
            featuredArtists = featuredList,
            displayArtist = trimmed,
        )
    }

    private fun splitArtists(
        part: String,
        isPrimary: Boolean,
    ): List<String> {
        if (part.isBlank()) return emptyList()
        val lower = part.lowercase().trim()
        if (isPrimary && (lower in PROTECTED_ARTISTS || lower.contains(", the "))) {
            return listOf(part.trim())
        }
        return part
            .split(DELIMITER_REGEX)
            .map { it.trim(' ', '(', ')', '[', ']', '{', '}') }
            .filter { it.isNotBlank() }
    }

    fun createParsedArtist(name: String): ParsedArtist {
        val cleanName = name.trim()
        val normalized = cleanName.lowercase()
        return ParsedArtist(
            name = cleanName,
            normalizedName = normalized,
            id = generateArtistId(normalized),
        )
    }

    fun normalizeAlbumTitle(rawTitle: String?): String {
        val trimmed = rawTitle?.trim().orEmpty()
        if (trimmed.isEmpty()) return "unknown album"
        return trimmed.lowercase().replace(EXTRA_WHITESPACE_REGEX, " ")
    }

    /**
     * Deterministic 64-bit FNV-1a hash of normalized album title + album artist ID.
     * Prevents MediaStore from splitting tracks of the same album into duplicate entries.
     */
    fun generateAlbumId(
        albumTitle: String,
        albumArtistId: Long,
    ): Long = generateArtistId("${normalizeAlbumTitle(albumTitle)}:$albumArtistId")

    /**
     * Deterministic 64-bit FNV-1a hash of the normalized artist name.
     * Guarantees the same artist ID across scans and devices.
     */
    @Suppress("MagicNumber")
    fun generateArtistId(normalizedName: String): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
        for (char in normalizedName) {
            hash = hash xor char.code.toLong()
            hash *= 1099511628211L // FNV-1a 64-bit prime
        }
        return hash
    }
}
