package com.lochanpthakke.empee3.core.network.repository

import com.lochanpthakke.empee3.core.model.MetadataCandidate
import com.lochanpthakke.empee3.core.model.MetadataProvider
import com.lochanpthakke.empee3.core.model.ProviderStatus
import com.lochanpthakke.empee3.core.network.api.MusicBrainzApi
import com.lochanpthakke.empee3.core.network.cleaner.FilenameCleaner
import com.lochanpthakke.empee3.core.network.cleaner.TitleCandidateGenerator
import com.lochanpthakke.empee3.core.network.model.MusicBrainzSearchResponse
import com.lochanpthakke.empee3.core.network.provider.ProviderUnavailableException
import com.lochanpthakke.empee3.core.network.provider.deezerTrackQuery
import com.lochanpthakke.empee3.core.network.provider.musicBrainzCandidates
import com.lochanpthakke.empee3.core.network.provider.musicBrainzRecordingQuery
import com.lochanpthakke.empee3.core.network.retry.RetryPolicy
import com.lochanpthakke.empee3.core.network.scorer.HIGH_CONFIDENCE_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.scorer.UNCERTAIN_THRESHOLD
import com.lochanpthakke.empee3.core.network.scorer.creditSimilarity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

/**
 * "Mr. Lonely (feat. Frank Ocean & Steve Lacy).mp3" on the S23 (2026-10-14). Its tags: title
 * "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)", artist "Tyler/ The Creator/Frank Ocean/
 * Steve Lacy", album "Flower Boy", 4:15. What the app sent (from the phone's log), every query
 * answered with 0 results:
 *   recording:"911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)" AND artist:"Tyler/ The Creator/Frank Ocean/Steve Lacy"
 *   recording:"Mr. Lonely" AND artist:"…same…"
 *   recording:"Mr. Lonely (feat. Frank Ocean & Steve Lacy" AND artist:"…same…"   (closing ")" lost)
 * MusicBrainz finds the song as `recording:"911 / Mr. Lonely" AND artist:(Tyler The Creator Frank
 * Ocean Steve Lacy)`; the fixtures are its real answers (trimmed).
 */
class MrLonelyMatchTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val today = LocalDate.of(2026, 10, 14)
    private val scorer = MetadataScorer()

    private val fileName = "Mr. Lonely (feat. Frank Ocean & Steve Lacy).mp3"
    private val taggedTitle = "911 / Mr. Lonely (feat. Frank Ocean & Steve Lacy)"
    private val taggedArtist = "Tyler/ The Creator/Frank Ocean/ Steve Lacy"

    private fun fixture(name: String): List<MetadataCandidate> =
        musicBrainzCandidates(
            json.decodeFromString<MusicBrainzSearchResponse>(requireNotNull(javaClass.classLoader?.getResource(name)).readText()),
            "Flower Boy",
            "MusicBrainz",
            today,
        )

    /** Everything MusicBrainz offers for "Mr. Lonely": the three Tyler recordings, other "Mr Lonely" songs and Bobby Vinton's. */
    private val found by lazy { fixture("musicbrainz_mr_lonely_search.json") + fixture("musicbrainz_mr_lonely_bobby_vinton_search.json") }

    private val fileTitles = TitleCandidateGenerator(FilenameCleaner()).generateCandidates(fileName)

    private val identity by lazy {
        TrackIdentity(
            title = taggedTitle,
            artist = taggedArtist,
            album = "Flower Boy",
            durationMs = 255_000L,
            fileTitles = fileTitles,
        )
    }

    private fun match(id: TrackIdentity): MatchResult =
        runBlocking {
            TrackMatcher(scorer).match(id) { query ->
                val provider =
                    object : MetadataProvider {
                        override val providerName = "MusicBrainz"

                        override suspend fun searchTrack(
                            title: String,
                            artist: String?,
                            album: String?,
                        ) = found

                        override suspend fun fetchAlbumArt(
                            albumTitle: String,
                            artist: String?,
                        ): String? = null
                    }
                val (candidates, outcome) =
                    searchProviderScored(
                        provider,
                        MetadataSources.ALL,
                        ProviderAvailability.Available,
                        scorer,
                        query.title,
                        query.artist,
                        query.album,
                        id.durationMs,
                        retry = RetryPolicy(maxAttempts = 1),
                    )
                DetailedSearchResponse(candidates, listOf(outcome))
            }
        }

    // ---- query construction

    @Test
    fun theFileNameTitleKeepsItsClosingBracket() {
        assertTrue(fileTitles.toString(), "Mr. Lonely (feat. Frank Ocean & Steve Lacy)" in fileTitles)
        assertFalse(fileTitles.toString(), fileTitles.any { it.count { c -> c == '(' } != it.count { c -> c == ')' } })
    }

    @Test
    fun theFailingTrack_getsCleanSeparateQueries() {
        val queries = planQueries(identity)
        assertEquals(
            listOf(
                TrackQuery("911 / Mr. Lonely", taggedArtist, TrackQuery.Source.TAG),
                TrackQuery("911 / Mr. Lonely", null, TrackQuery.Source.TAG, album = "Flower Boy"),
                TrackQuery("Mr. Lonely", taggedArtist, TrackQuery.Source.FILE_NAME),
                TrackQuery("Mr. Lonely", null, TrackQuery.Source.FILE_NAME, album = "Flower Boy"),
                // Lochan's fallback: the title alone, last.
                TrackQuery("911 / Mr. Lonely", null, TrackQuery.Source.TAG),
            ),
            queries,
        )
        for (query in queries) {
            assertFalse(query.title, query.title.contains("feat", ignoreCase = true))
            assertFalse(query.title, query.title.contains('"') || query.title.contains("Search"))
        }
    }

    @Test
    fun musicBrainzQueries_useTheArtistWords_notTheFlattenedCreditAsAPhrase() {
        assertEquals(
            "recording:\"911 / Mr. Lonely\" AND artist:(tyler the creator frank ocean steve lacy)",
            musicBrainzRecordingQuery("911 / Mr. Lonely", taggedArtist, "Flower Boy"),
        )
        assertEquals("recording:\"Mr. Lonely\" AND release:\"Flower Boy\"", musicBrainzRecordingQuery("Mr. Lonely", null, "Flower Boy"))
        assertEquals("recording:\"Say \\\"Hi\\\" \\\\ Bye\"", musicBrainzRecordingQuery("Say \"Hi\" \\ Bye", "Unknown Artist", null))
        assertEquals("album:\"Flower Boy\" track:\"911 / Mr. Lonely\"", deezerTrackQuery("911 / Mr. Lonely", taggedArtist, "Flower Boy"))
        assertEquals("artist:\"Kanye West\" track:\"Runaway\"", deezerTrackQuery("Runaway", "Kanye West", null))
    }

    @Test
    fun theRequestUrl_encodesAmpersandsSlashesApostrophesAndParentheses() {
        var sent: okhttp3.HttpUrl? = null
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    sent = chain.request().url
                    okhttp3.Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"recordings":[]}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        val api =
            Retrofit
                .Builder()
                .baseUrl("https://musicbrainz.org/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MusicBrainzApi::class.java)
        val query = musicBrainzRecordingQuery("Don't Stop (Rock & Roll) / Part 2", "Tyler/ The Creator", null)

        runBlocking { api.searchRecording(query) }

        val url = requireNotNull(sent)
        assertEquals("the query arrives intact", query, url.queryParameter("query"))
        val raw = url.encodedQuery.orEmpty()
        assertTrue(raw, raw.contains("%26") && raw.contains("%2F") && raw.contains("%28") && raw.contains("%29"))
        assertFalse("an & must not split the parameter", url.queryParameterNames.any { it.startsWith(" Roll") || it.startsWith("Roll") })
    }

    // ---- discovery and choice

    @Test
    fun theAlbumTrackIsFound_fromTheFirstQuery_andChosenOverEveryOtherMrLonely() {
        val result = match(identity)
        val chosen = (result.decision as MatchDecision.Confident).match
        assertEquals(255_000L, chosen.durationMs)
        assertEquals("911 / Mr. Lonely", chosen.title)
        assertEquals("Flower Boy", chosen.album)
        assertEquals("2017-07-21", chosen.releaseDate)
        assertEquals("a confident answer ends the search", 1, result.attempts.size)

        val vinton = result.candidates.filter { it.artist == "Bobby Vinton" }
        assertTrue(vinton.isNotEmpty())
        assertTrue(vinton.all { it.confidenceScore < UNCERTAIN_THRESHOLD })
        assertTrue(result.candidates.filter { it.artist.startsWith("Frank") }.all { it.confidenceScore < UNCERTAIN_THRESHOLD })
    }

    @Test
    fun theLiveBootlegIsRejected_andTheAtmosMixRanksBelowTheStudioRecording() {
        val ranked = match(identity).candidates
        val studio = ranked.single { it.durationMs == 255_000L }
        val atmos = ranked.single { it.durationMs == 255_653L }
        val live = ranked.single { it.durationMs == 295_000L }
        assertTrue(atmos.confidenceScore < studio.confidenceScore)
        assertTrue("same song, not rejected", atmos.confidenceScore >= HIGH_CONFIDENCE_THRESHOLD)
        assertTrue(live.confidenceScore < UNCERTAIN_THRESHOLD)
    }

    @Test
    fun withOnlyTheShortFileName_theAlbumTrackIsStillFound() {
        val tagless = identity.copy(title = null, fileTitles = listOf("Mr. Lonely"))
        val chosen = (match(tagless).decision as MatchDecision.Confident).match
        assertEquals(255_000L, chosen.durationMs)
    }

    @Test
    fun durationIsWeighed_notAnAbsoluteRejection() {
        val offByAMinute = identity.copy(durationMs = 315_000L)
        val studio = match(offByAMinute).candidates.single { it.durationMs == 255_000L }
        assertTrue(studio.confidenceScore < HIGH_CONFIDENCE_THRESHOLD)
        assertTrue("still a candidate to review", studio.confidenceScore >= UNCERTAIN_THRESHOLD)
    }

    @Test
    fun featuredArtists_dontBreakArtistMatching() {
        assertEquals(1f, creditSimilarity(taggedArtist, "Tyler, The Creator ft. Frank Ocean & Steve Lacy"), 0.0001f)
        assertTrue(creditSimilarity(taggedArtist, "Bobby Vinton") < 0.1f)
    }

    // ---- provider restrictions stay distinct from "no match"

    @Test
    fun deezersCountryRestriction_isReportedOnce_thenNotAskedAgain() {
        val health = ProviderHealth()
        var calls = 0
        val deezer =
            object : MetadataProvider {
                override val providerName = "Deezer"

                override suspend fun searchTrack(
                    title: String,
                    artist: String?,
                    album: String?,
                ): List<MetadataCandidate> {
                    calls++
                    throw ProviderUnavailableException("track search isn't available in this country", sessionWide = true)
                }

                override suspend fun fetchAlbumArt(
                    albumTitle: String,
                    artist: String?,
                ): String? = null
            }

        fun ask() =
            runBlocking {
                searchProviderScored(
                    deezer,
                    MetadataSources.ALL,
                    health.availabilityOf("Deezer"),
                    scorer,
                    "911 / Mr. Lonely",
                    null,
                    "Flower Boy",
                    255_000L,
                    retry = RetryPolicy(maxAttempts = 3, baseDelayMs = 1),
                    onSessionFailure = { status, reason -> health.markUnusable("Deezer", status, reason) },
                ).second
            }

        assertEquals(ProviderStatus.UNAVAILABLE, ask().status)
        val second = ask()
        assertEquals(ProviderStatus.UNAVAILABLE, second.status)
        assertTrue(second.errorMessage!!.contains("not asked again"))
        assertEquals("one request in total, no retries", 1, calls)
        assertNull(health.availabilityOf("MusicBrainz").let { if (it == ProviderAvailability.Available) null else it })
    }
}
