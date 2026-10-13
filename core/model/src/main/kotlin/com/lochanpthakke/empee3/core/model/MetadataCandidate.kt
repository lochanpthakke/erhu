package com.lochanpthakke.empee3.core.model

data class MetadataCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int = 0,
    val genre: String? = null,
    val trackNumber: Int = 0,
    val bpm: Int? = null,
    val durationMs: Long? = null,
    val coverArtUrl: String? = null,
    val confidenceScore: Float = 0f,
    val sourceName: String = "",
    /** MusicBrainz release group of the chosen release: genre and Cover Art Archive lookups. */
    val releaseGroupId: String? = null,
    /** Deezer album id: genre lookup (Deezer gives genres per album only). */
    val deezerAlbumId: Long? = null,
    /** Version note kept out of the title, e.g. MusicBrainz "live, 2011-04-17: Coachella". */
    val versionNote: String? = null,
    /**
     * Validated original release date as precise as the source gives it ("2010-11-19", "2010-11" or
     * "2010"); null when the source has none it can vouch for, with the reason in [releaseDateIssue].
     */
    val releaseDate: String? = null,
    val releaseDateIssue: String? = null,
)
