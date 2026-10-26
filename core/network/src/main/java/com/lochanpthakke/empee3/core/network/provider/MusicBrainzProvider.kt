@file:Suppress("detekt:All")

package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.network.api.MusicBrainzApi
import com.lochanpthakke.empee3.core.network.model.MusicBrainzGenreDto
import com.lochanpthakke.empee3.core.network.model.MusicBrainzRecordingDto
import com.lochanpthakke.empee3.core.network.model.MusicBrainzReleaseDto
import com.lochanpthakke.empee3.core.network.model.MusicBrainzSearchResponse
import com.lochanpthakke.empee3.core.network.scorer.artistSearchTerms
import com.lochanpthakke.empee3.core.network.scorer.escapePhrase
import com.lochanpthakke.empee3.core.network.tagger.ReleaseDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicBrainzProvider
    @Inject
    constructor(
        private val musicBrainzApi: MusicBrainzApi,
        private val logger: Logger,
    ) : MetadataProvider {
        override val providerName: String = "MusicBrainz"

        /** MusicBrainz allows one request per second per client; faster callers get 503s. */
        private val gate = Mutex()
        private var lastRequestAtMs = 0L
        private val genresByReleaseGroup = ConcurrentHashMap<String, List<MusicBrainzGenreDto>>()
        private val mbidCache = ConcurrentHashMap<String, String>()

        /**
         * Resolves all valid candidate MusicBrainz Artist IDs (MBIDs) for [artistName].
         * Sorts exact name matches first, then prefers Person type, then descending score.
         */
        suspend fun resolveArtistMbids(artistName: String): List<String> {
            val trimmed = artistName.trim()
            if (trimmed.isBlank() || trimmed.equals("Unknown Artist", ignoreCase = true)) return emptyList()

            val cacheKey = trimmed.lowercase()
            val cached = mbidCache[cacheKey]
            if (cached != null) return if (cached.isBlank()) emptyList() else cached.split(",")

            return try {
                val escaped = escapePhrase(trimmed)
                val query = "artist:\"$escaped\" OR alias:\"$escaped\""
                val response = throttled { musicBrainzApi.searchArtist(query = query, limit = 25) }
                val candidates = response.artists.filter { it.score >= 80 }

                fun matchesNameOrAlias(dto: com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistDto): Int {
                    val nameMatch = dto.name.trim().equals(trimmed, ignoreCase = true)
                    val aliasMatch = dto.aliases.any { it.name.trim().equals(trimmed, ignoreCase = true) }
                    val disambigMatch = dto.disambiguation?.contains(trimmed, ignoreCase = true) == true
                    return if (nameMatch || aliasMatch || disambigMatch) 0 else 1
                }

                fun richnessScore(dto: com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistDto): Int {
                    var score = 0
                    if (!dto.disambiguation.isNullOrBlank()) score += 3
                    if (!dto.country.isNullOrBlank()) score += 2
                    if (dto.aliases.isNotEmpty()) score += 2
                    if (dto.tags.isNotEmpty()) score += 2
                    if (!dto.type.isNullOrBlank()) score += 1
                    return score
                }

                val sorted =
                    candidates.sortedWith(
                        compareBy<com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistDto> { matchesNameOrAlias(it) }
                            .thenByDescending { richnessScore(it) }
                            .thenBy { if (it.name.trim().equals(trimmed, ignoreCase = true)) 0 else 1 }
                            .thenByDescending { it.score },
                    )
                val mbids =
                    sorted
                        .map { it.id }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(8)
                mbidCache[cacheKey] = mbids.joinToString(",")
                mbids
            } catch (e: Exception) {
                logger.w(TAG, "MusicBrainz artist MBID lookup failed for $artistName: ${e.message}", e)
                emptyList()
            }
        }

        suspend fun resolveArtistMbid(artistName: String): String? = resolveArtistMbids(artistName).firstOrNull()

        private val urlRelsCache = ConcurrentHashMap<String, com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistDto>()

        suspend fun lookupArtistUrlRels(mbid: String): com.lochanpthakke.empee3.core.network.model.MusicBrainzArtistDto? {
            if (mbid.isBlank()) return null
            urlRelsCache[mbid]?.let { return it }
            return try {
                val dto = throttled { musicBrainzApi.lookupArtistUrlRels(artistId = mbid) }
                urlRelsCache[mbid] = dto
                dto
            } catch (e: Exception) {
                logger.w(TAG, "MusicBrainz artist url-rels lookup failed for $mbid: ${e.message}", e)
                null
            }
        }

        override suspend fun searchTrack(
            title: String,
            artist: String?,
            album: String?,
        ): List<MetadataCandidate> {
            val query = musicBrainzRecordingQuery(title, artist, album)
            // Failures propagate: the repository reports them as FAILED, not as "no results".
            val response = throttled { musicBrainzApi.searchRecording(query) }
            return musicBrainzCandidates(response, album, providerName)
        }

        /** Curated genres (with vote counts) of a release group; cached for the session. Throws on failure. */
        suspend fun fetchReleaseGroupGenres(releaseGroupId: String): List<MusicBrainzGenreDto> =
            genresByReleaseGroup[releaseGroupId]
                ?: throttled { musicBrainzApi.lookupReleaseGroupGenres(releaseGroupId) }.genres.also {
                    genresByReleaseGroup[releaseGroupId] = it
                    logger.d(TAG, "Genres for release group $releaseGroupId: ${it.size}")
                }

        /** Artwork comes from the Cover Art Archive URL on each candidate, or direct release group search. */
        override suspend fun fetchAlbumArt(
            albumTitle: String,
            artist: String?,
        ): String? = fetchReleaseGroupArtList(albumTitle, artist).firstOrNull()

        suspend fun fetchReleaseGroupArtList(
            albumTitle: String,
            artist: String?,
        ): List<String> {
            val query = musicBrainzReleaseGroupQuery(albumTitle, artist)
            return try {
                val response = throttled { musicBrainzApi.searchReleaseGroup(query) }
                response.releaseGroups
                    .mapNotNull { it.id }
                    .map { coverArtArchiveUrl(it) }
            } catch (e: Exception) {
                logger.w(TAG, "MusicBrainz release group art search failed for $albumTitle: ${e.message}", e)
                emptyList()
            }
        }

        private suspend fun <T> throttled(request: suspend () -> T): T =
            gate.withLock {
                val wait = lastRequestAtMs + MIN_REQUEST_INTERVAL_MS - nowMs()
                if (wait > 0) delay(wait)
                try {
                    request()
                } finally {
                    lastRequestAtMs = nowMs()
                }
            }

        private fun nowMs(): Long = System.nanoTime() / NANOS_PER_MS

        companion object {
            private const val TAG = "MusicBrainzProvider"
            private const val MIN_REQUEST_INTERVAL_MS = 1_100L
            private const val NANOS_PER_MS = 1_000_000L
        }
    }

/**
 * The Lucene query for a release group (album) search: the release title as an escaped phrase,
 * plus artist terms.
 */
internal fun musicBrainzReleaseGroupQuery(
    albumTitle: String,
    artist: String?,
): String {
    val release = "release:\"${escapePhrase(albumTitle.trim())}\""
    val terms = artistSearchTerms(artist?.takeUnless { it.equals("Unknown Artist", ignoreCase = true) })
    return if (terms != null) "$release AND artist:($terms)" else release
}

/**
 * The Lucene query for a recording search: the title as an escaped phrase, plus the artist credit
 * as words (`artist:(…)`, so "Tyler/ The Creator/Frank Ocean/ Steve Lacy" still finds "Tyler, The
 * Creator ft. Frank Ocean & Steve Lacy"), or the album as a phrase when there is no artist.
 */
internal fun musicBrainzRecordingQuery(
    title: String,
    artist: String?,
    album: String?,
): String {
    val terms = artistSearchTerms(artist?.takeUnless { it.equals("Unknown Artist", ignoreCase = true) })
    val release = album?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Unknown Album", ignoreCase = true) }
    val recording = "recording:\"${escapePhrase(title.trim())}\""
    return when {
        terms != null -> "$recording AND artist:($terms)"
        release != null -> "$recording AND release:\"${escapePhrase(release)}\""
        else -> recording
    }
}

/** Front cover of a release group from the Cover Art Archive (MusicBrainz's image store); 404 when it has none. */
fun coverArtArchiveUrl(releaseGroupId: String): String = "https://coverartarchive.org/release-group/$releaseGroupId/front-500"

/**
 * Maps a recording search. A recording appears on many releases (bootlegs, live sets, radio
 * compilations), so each candidate takes its album, year and artwork from the release that best
 * fits: one titled like [targetAlbum], then official over promotional or bootleg, then a plain
 * album over compilations / live / mixtapes, then the earliest. Recordings whose best release is
 * an official, plain one come first, so the studio version wins ties with copies on bootlegs.
 */
internal fun musicBrainzCandidates(
    response: MusicBrainzSearchResponse,
    targetAlbum: String?,
    providerName: String,
    today: LocalDate = LocalDate.now(),
): List<MetadataCandidate> =
    response.recordings
        .map { recording -> recording to bestRelease(recording.releases, targetAlbum) }
        .sortedBy { (_, release) -> if (release != null && isPlainOfficial(release)) 0 else 1 }
        .map { (recording, release) ->
            val (date, issue) = releaseDateOf(recording, release, targetAlbum, today)
            recording
                .toCandidate(release, providerName)
                .copy(year = date?.year ?: 0, releaseDate = date?.tagValue, releaseDateIssue = issue)
        }

/**
 * The original release date of [chosen]'s album for this recording, or the reason there is none.
 * Only official releases count: a bootleg, promo or radio compilation date is not the song's date,
 * and a compilation / live release counts only when it is the album the file is tagged with.
 * Among the official releases of the same album (deluxe, clean, regional editions) the earliest
 * wins, as precise as MusicBrainz knows it. Malformed or impossible dates are ignored.
 */
internal fun releaseDateOf(
    recording: MusicBrainzRecordingDto,
    chosen: MusicBrainzReleaseDto?,
    targetAlbum: String?,
    today: LocalDate,
): Pair<ReleaseDate?, String?> {
    if (chosen == null) return null to "MusicBrainz lists no release for this recording"
    if (!chosen.status.equals("Official", ignoreCase = true)) {
        return null to "MusicBrainz lists this recording only on unofficial releases (${chosen.status ?: "status unknown"})"
    }
    val secondary = chosen.releaseGroup?.secondaryTypes.orEmpty()
    val isTaggedAlbum = targetAlbum != null && chosen.title.trim().equals(targetAlbum.trim(), ignoreCase = true)
    if (secondary.isNotEmpty() && !isTaggedAlbum) {
        return null to "MusicBrainz lists this recording only on ${secondary.joinToString("/").lowercase()} releases"
    }
    val groupId = chosen.releaseGroup?.id
    val raw =
        recording.releases
            .filter { it.status.equals("Official", ignoreCase = true) }
            .filter { if (groupId == null) it === chosen else it.releaseGroup?.id == groupId }
            .mapNotNull { it.date?.trim()?.takeIf { date -> date.isNotEmpty() } }
    if (raw.isEmpty()) return null to "MusicBrainz has no release date for \"${chosen.title}\""
    val valid = raw.mapNotNull { ReleaseDate.parse(it, today) }
    val earliest = ReleaseDate.earliest(valid) ?: return null to "MusicBrainz's release date isn't a valid date (\"${raw.first()}\")"
    return earliest to null
}

internal fun bestRelease(
    releases: List<MusicBrainzReleaseDto>,
    targetAlbum: String?,
): MusicBrainzReleaseDto? {
    val wanted = targetAlbum?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "unknown album" }
    return releases.minWithOrNull(
        compareBy<MusicBrainzReleaseDto> { if (wanted != null && it.title.trim().lowercase() == wanted) 0 else 1 }
            .thenBy { statusRank(it.status) }
            .thenBy { if (it.releaseGroup?.secondaryTypes.isNullOrEmpty()) 0 else 1 }
            .thenBy { primaryTypeRank(it.releaseGroup?.primaryType) }
            .thenBy { it.date?.takeIf { date -> date.isNotBlank() } ?: "9999" },
    )
}

private fun isPlainOfficial(release: MusicBrainzReleaseDto): Boolean =
    release.status.equals("Official", ignoreCase = true) && release.releaseGroup?.secondaryTypes.isNullOrEmpty()

private fun statusRank(status: String?): Int =
    when (status?.lowercase()) {
        "official" -> 0
        null -> 1
        "promotion" -> 2
        else -> 3
    }

private fun primaryTypeRank(type: String?): Int =
    when (type?.lowercase()) {
        "album" -> 0
        "ep" -> 1
        "single" -> 2
        else -> 3
    }

private fun MusicBrainzRecordingDto.toCandidate(
    release: MusicBrainzReleaseDto?,
    providerName: String,
): MetadataCandidate {
    val artistName =
        if (artistCredit.any { !it.joinPhrase.isNullOrBlank() }) {
            artistCredit.joinToString("") { credit -> credit.name + (credit.joinPhrase ?: "") }.trim()
        } else {
            artistCredit.joinToString(", ") { it.name }.trim()
        }
    val releaseGroupId = release?.releaseGroup?.id
    return MetadataCandidate(
        id = "mb_$id",
        title = title,
        artist = artistName,
        album = release?.title.orEmpty(),
        durationMs = length,
        coverArtUrl = releaseGroupId?.let(::coverArtArchiveUrl),
        sourceName = providerName,
        releaseGroupId = releaseGroupId,
        versionNote = disambiguation?.trim()?.takeIf { it.isNotEmpty() },
    )
}
