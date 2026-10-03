package com.lochanpthakke.empee3.core.database.library

/**
 * Builds the derived library in a fixed order:
 * raw rows → evidence → credits (A) → identity (B) → album groups (C, artist-free) → presentation (sink).
 */
object LibraryModelBuilder {
    fun build(
        tracks: List<RawTrack>,
        aliases: Map<String, String>,
    ): LibraryModel {
        val ordered = tracks.sortedBy { it.songId }
        val evidence = EvidenceIndex.build(ordered)
        val identity = ArtistIdentityResolver(aliases)
        val resolver = CreditResolver(evidence)
        val credits = ordered.associate { it.songId to resolver.resolve(it) }
        val groups = AlbumGrouper.group(ordered.map { it.toAlbumInput() }, identity)
        val names = displayNames(credits.values.flatten(), groups, identity)
        val presentations =
            groups.associateWith { group ->
                AlbumPresenter.present(group, credits, evidence.standaloneKeys, identity) { key -> names[key] ?: key }
            }
        val creditRows = creditRows(credits, identity)
        val albumArtists =
            presentations.flatMap { (group, presentation) ->
                presentation.linkedArtistKeys.mapIndexed { i, key -> AlbumArtistRow(group.id, identity.idOf(key), i) }
            }
        return LibraryModel(
            songs = songAssignments(groups, credits, identity),
            credits = creditRows,
            albums = presentations.map { (group, presentation) -> albumRow(group, presentation, identity) },
            albumArtists = albumArtists,
            artists = artistRows(creditRows, albumArtists, credits, names, identity),
            unverifiedSegments = unverified(credits, evidence.standaloneKeys, identity),
        )
    }

    private fun displayNames(
        credits: List<ResolvedCredit>,
        groups: List<AlbumGroup>,
        identity: ArtistIdentityResolver,
    ): Map<String, String> {
        val variants = HashMap<String, MutableMap<String, Int>>()
        credits.forEach { c -> variants.getOrPut(identity.canonicalKey(c.key)) { mutableMapOf() }.merge(c.text, 1, Int::plus) }
        groups
            .mapNotNull { group -> group.albumArtistKey?.let { key -> group.albumArtistText?.let { key to it } } }
            .forEach { (key, text) -> variants.getOrPut(key) { mutableMapOf() }.merge(text, 1, Int::plus) }
        return variants.mapValues { chooseDisplayName(it.value) }
    }

    private fun creditRows(
        credits: Map<Long, List<ResolvedCredit>>,
        identity: ArtistIdentityResolver,
    ): List<CreditRow> =
        credits.flatMap { (songId, list) ->
            list.distinctBy { identity.canonicalKey(it.key) }.map {
                CreditRow(songId, identity.idOf(it.key), it.role, it.position, it.text, it.isUnresolved)
            }
        }

    private fun songAssignments(
        groups: List<AlbumGroup>,
        credits: Map<Long, List<ResolvedCredit>>,
        identity: ArtistIdentityResolver,
    ): Map<Long, SongAssignment> {
        val albumOf = groups.flatMap { g -> g.songIds.map { it to g.id } }.toMap()
        return credits.mapValues { (songId, list) ->
            val lead = list.firstOrNull { it.role == CreditRole.PRIMARY } ?: list.first()
            SongAssignment(albumOf.getValue(songId), identity.idOf(lead.key))
        }
    }

    private fun albumRow(
        group: AlbumGroup,
        presentation: AlbumPresentation,
        identity: ArtistIdentityResolver,
    ) = AlbumRow(
        id = group.id,
        title = group.title,
        displayArtist = presentation.displayArtist,
        leadArtistId = presentation.linkedArtistKeys.firstOrNull()?.let(identity::idOf) ?: 0L,
        songIds = group.songIds,
        isCompilation = presentation.isCompilation,
        albumArtistInconsistent = group.albumArtistInconsistent,
        groupingKey = group.groupingKey,
    )

    private fun artistRows(
        creditRows: List<CreditRow>,
        albumArtists: List<AlbumArtistRow>,
        credits: Map<Long, List<ResolvedCredit>>,
        names: Map<String, String>,
        identity: ArtistIdentityResolver,
    ): List<ArtistRow> {
        val keyOfId = HashMap<Long, String>()
        val unresolved = HashSet<Long>()
        credits.values.flatten().forEach { c ->
            val id = identity.idOf(c.key)
            keyOfId[id] = identity.canonicalKey(c.key)
            if (c.isUnresolved) unresolved += id
        }
        names.keys.forEach { key -> keyOfId.putIfAbsent(identity.idOf(key), key) }
        val songCounts = creditRows.groupBy { it.artistId }.mapValues { (_, rows) -> rows.map { it.songId }.toSet().size }
        val albumCounts = albumArtists.groupBy { it.artistId }.mapValues { (_, rows) -> rows.map { it.albumId }.toSet().size }
        return (songCounts.keys + albumCounts.keys).toSortedSet().map { id ->
            val key = keyOfId.getValue(id)
            ArtistRow(id, key, names[key] ?: key, songCounts[id] ?: 0, albumCounts[id] ?: 0, id in unresolved)
        }
    }

    private fun unverified(
        credits: Map<Long, List<ResolvedCredit>>,
        standaloneKeys: Set<String>,
        identity: ArtistIdentityResolver,
    ): List<UnverifiedSegmentRow> =
        credits.flatMap { (songId, list) ->
            list.filter { it.isUnresolved }.flatMap { c ->
                c.segmentKeys
                    .withIndex()
                    .filter { it.value in standaloneKeys }
                    .map { (i, key) -> UnverifiedSegmentRow(songId, identity.idOf(c.key), key, i) }
            }
        }
}
