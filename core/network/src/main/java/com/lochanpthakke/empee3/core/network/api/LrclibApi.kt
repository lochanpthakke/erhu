package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.LrclibLyricsDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * LRCLIB (https://lrclib.net). Requests identify the app, as LRCLIB asks. Responses are wrapped so
 * a 404 (no lyrics) and a 429 (with Retry-After) can be told apart from failures.
 */
interface LrclibApi {
    /** The record matching this exact track (title, artist, album, duration within about 2 s); 404 when none. */
    @Headers(USER_AGENT)
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationInSeconds: Int? = null,
    ): Response<LrclibLyricsDto>

    /** Records matching the fields (or the free-text [query] when given). */
    @Headers(USER_AGENT)
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null,
    ): Response<List<LrclibLyricsDto>>

    companion object {
        const val USER_AGENT = "User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)"
    }
}
