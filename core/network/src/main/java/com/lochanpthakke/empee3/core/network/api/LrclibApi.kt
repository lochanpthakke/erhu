package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.LrclibLyricsDto
import retrofit2.http.GET
import retrofit2.http.Query

interface LrclibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationInSeconds: Int? = null,
    ): LrclibLyricsDto

    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String,
    ): List<LrclibLyricsDto>
}
