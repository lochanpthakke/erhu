package com.lochanpthakke.empee3.core.database.library

const val UNKNOWN_ALBUM = "Unknown Album"

data class AlbumGroup(
    val id: Long,
    val title: String,
    val groupingKey: String,
    val albumArtistKey: String?,
    val albumArtistText: String?,
    val songIds: List<Long>,
    val isCompilationTagged: Boolean,
    val albumArtistInconsistent: Boolean,
)

/**
 * Layer C. Album identity from album-level raw fields only: title, folder, the
 * Album Artist tag (whole string, never split) and the compilation flag. It
 * receives [AlbumInput], which has no track-artist field, so track artists
 * cannot influence grouping.
 */
object AlbumGrouper {
    fun group(
        inputs: List<AlbumInput>,
        identity: ArtistIdentityResolver,
    ): List<AlbumGroup> =
        inputs
            .groupBy {
                val t = titleKey(it)
                if (t == UNKNOWN_ALBUM_KEY) t to it.folder else t to ""
            }.toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .values
            .flatMap { reconcile(it, identity) }
            .groupBy { it.titleKey to it.groupingKey }
            .values
            .map { merge(it) }
            .sortedBy { it.id }

    private class Partial(
        val titleKey: String,
        val groupingKey: String,
        val albumArtistKey: String?,
        val members: List<AlbumInput>,
        val inconsistent: Boolean,
    )

    private val UNKNOWN_ALBUM_KEY = CreditText.canonicalKey(UNKNOWN_ALBUM)

    private fun titleKey(input: AlbumInput): String = CreditText.canonicalKey(input.album.ifBlank { UNKNOWN_ALBUM })

    private fun reconcile(
        members: List<AlbumInput>,
        identity: ArtistIdentityResolver,
    ): List<Partial> {
        val keyOf = { m: AlbumInput ->
            m.rawAlbumArtist?.takeIf { it.isNotBlank() }?.let { identity.canonicalKey(CreditText.canonicalKey(it)) }
        }
        val counts = members.mapNotNull(keyOf).groupingBy { it }.eachCount()
        val majority =
            counts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.key
        val collides =
            counts.size > 1 &&
                members
                    .filter { it.trackNumber > 0 && keyOf(it) != null }
                    .groupBy { it.discNumber to it.trackNumber }
                    .values
                    .any { slot -> slot.mapNotNull(keyOf).toSet().size > 1 }
        val buckets = if (collides) members.groupBy { keyOf(it) ?: majority } else mapOf(majority to members)
        val folder = members.first().folder
        return buckets.map { (key, bucket) ->
            val title = titleKey(bucket.first())
            // An untitled album is a folder of loose tracks: never merge it across folders, even by Album Artist.
            val anchor =
                if (title == UNKNOWN_ALBUM_KEY) {
                    "dir:$folder"
                } else if (key != null) {
                    "aa:$key"
                } else {
                    "title:$title"
                }
            Partial(title, anchor, key, bucket, !collides && counts.size > 1)
        }
    }

    private fun merge(parts: List<Partial>): AlbumGroup {
        val members = parts.flatMap { it.members }
        val first = parts.first()
        val albumArtistTexts =
            members
                .mapNotNull { it.rawAlbumArtist?.takeIf { text -> text.isNotBlank() }?.trim() }
                .groupingBy { it }
                .eachCount()
        return AlbumGroup(
            id = IdentityHash.of("album|${first.titleKey}|${first.groupingKey}"),
            title = chooseDisplayName(members.map { it.album.trim().ifBlank { UNKNOWN_ALBUM } }.groupingBy { it }.eachCount()),
            groupingKey = first.groupingKey,
            albumArtistKey = first.albumArtistKey,
            albumArtistText = albumArtistTexts.takeIf { it.isNotEmpty() }?.let(::chooseDisplayName),
            songIds = members.map { it.songId }.sorted(),
            isCompilationTagged = members.any { it.isCompilation },
            albumArtistInconsistent = parts.any { it.inconsistent },
        )
    }
}
