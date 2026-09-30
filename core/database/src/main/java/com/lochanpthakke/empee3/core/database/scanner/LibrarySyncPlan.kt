package com.lochanpthakke.empee3.core.database.scanner

import com.lochanpthakke.empee3.core.database.entity.SongEntity

internal data class LibrarySyncPlan(
    val upserts: List<SongEntity>,
    val deletedIds: List<Long>,
)

/**
 * Reconciles a fresh MediaStore scan with what is already stored.
 *
 * - User-owned state (favorites) always survives a rescan.
 * - Tag enrichment (lyrics, disc, composer, genre) survives when the file is
 *   unchanged, so it is not re-read on every scan; a modified file is
 *   re-enriched.
 * - Songs no longer present in MediaStore are deleted.
 */
internal fun planLibrarySync(
    scanned: List<SongEntity>,
    existing: List<SongEntity>,
): LibrarySyncPlan {
    val existingById = existing.associateBy { it.id }
    val upserts =
        scanned.map { fresh ->
            val old = existingById[fresh.id] ?: return@map fresh
            val unchangedFile = old.dateModified == fresh.dateModified && old.filePath == fresh.filePath
            if (unchangedFile) {
                fresh.copy(
                    isFavorite = old.isFavorite,
                    isEnriched = old.isEnriched,
                    lyrics = old.lyrics,
                    discNumber = old.discNumber,
                    composer = old.composer,
                    genre = old.genre,
                )
            } else {
                fresh.copy(isFavorite = old.isFavorite)
            }
        }
    val scannedIds = scanned.mapTo(HashSet(scanned.size)) { it.id }
    val deletedIds = existing.map { it.id }.filterNot { it in scannedIds }
    return LibrarySyncPlan(upserts = upserts, deletedIds = deletedIds)
}

private val EXCLUDED_PATH_SEGMENTS =
    listOf(
        // WhatsApp (and WhatsApp Business) voice notes and audio messages. "WhatsApp
        // Documents" is deliberately kept: songs shared as files end up there.
        "/whatsapp audio/",
        "/whatsapp voice notes/",
        "/whatsapp business audio/",
        "/whatsapp business voice notes/",
    )

/** Chat voice notes / audio messages are not music and are kept out of the library. */
internal fun isExcludedFromLibrary(filePath: String): Boolean {
    val normalized = filePath.lowercase()
    return EXCLUDED_PATH_SEGMENTS.any { normalized.contains(it) }
}
