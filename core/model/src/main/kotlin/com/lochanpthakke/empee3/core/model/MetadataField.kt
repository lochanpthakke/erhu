package com.lochanpthakke.empee3.core.model

/**
 * What a metadata fix may change, chosen individually. Selecting every tag field means "All
 * Fields" (conservative: missing values are filled, valid ones kept); a smaller selection replaces
 * exactly those fields. [FILENAME] renames the file after its title. [unsupportedReason] marks a
 * field no provider can honestly serve: kept so stored selections still parse, never offered.
 */
enum class MetadataField(
    val displayName: String,
    val unsupportedReason: String? = null,
) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    YEAR("Year"),
    GENRE("Genre"),
    BPM("BPM", "No connected provider supplies BPM: Deezer reports 0 for the tracks checked, MusicBrainz has none."),
    ARTWORK("Artwork", "Artwork is managed separately via Artwork Fetcher."),
    FILENAME("Filename Only"),
    ;

    val isSupported: Boolean get() = unsupportedReason == null

    /** A tag inside the file (everything but the file name). */
    val isTag: Boolean get() = this != FILENAME

    companion object {
        val supported: List<MetadataField> get() = entries.filter { it.isSupported }

        /** The supported tag fields: selecting all of them is "All Fields". */
        val supportedTags: Set<MetadataField> get() = supported.filter { it.isTag }.toSet()

        /** Every supported field: the default selection. */
        val all: Set<MetadataField> get() = supported.toSet()

        /**
         * A stored selection back to fields: "TITLE,GENRE,FILENAME", or a single-action name stored by
         * earlier versions ("ALL_FIELDS", "GENRE_ONLY", "YEAR_ONLY"…). Unknown or empty means [all].
         */
        @Suppress("ReturnCount")
        fun fromStored(stored: String?): Set<MetadataField> {
            val text = stored?.trim().orEmpty()
            if (text.isEmpty() || text == "ALL_FIELDS") return all
            LEGACY_ACTIONS[text]?.let { return setOf(it) }
            val fields =
                text
                    .split(',')
                    .mapNotNull { name -> entries.firstOrNull { it.name == name.trim() && it.isSupported } }
                    .toSet()
            return fields.ifEmpty { all }
        }

        fun toStored(fields: Set<MetadataField>): String = entries.filter { it in fields }.joinToString(",") { it.name }

        private val LEGACY_ACTIONS =
            mapOf(
                "TITLE_ONLY" to TITLE,
                "ARTIST_ONLY" to ARTIST,
                "ALBUM_ONLY" to ALBUM,
                "YEAR_ONLY" to YEAR,
                "GENRE_ONLY" to GENRE,
                "ARTWORK_ONLY" to ARTWORK,
            )
    }
}
