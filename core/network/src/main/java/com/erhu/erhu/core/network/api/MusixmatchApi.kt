package com.erhu.erhu.core.network.api

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Official licensed API only; no consumer tokens or website scraping. */
interface MusixmatchApi {
    @GET("track.search")
    suspend fun tracks(
        @Query("apikey") key: String,
        @Query("q_track") title: String,
        @Query("q_artist") artist: String,
        @Query("page_size") limit: Int = 10,
    ): Response<JsonObject>

    @GET("track.subtitle.get")
    suspend fun subtitle(
        @Query("apikey") key: String,
        @Query("track_id") trackId: Long,
        @Query("subtitle_format") format: String = "lrc",
        @Query("f_subtitle_length") duration: Long?,
        @Query("f_subtitle_length_max_deviation") tolerance: Int = 3,
    ): Response<JsonObject>
}
