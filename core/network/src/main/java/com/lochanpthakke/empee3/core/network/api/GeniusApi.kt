package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.GeniusSearchResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface GeniusApi {
    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Accept: application/json, text/plain, */*",
        "Accept-Language: en-US,en;q=0.9",
        "Referer: https://genius.com/",
        "X-Requested-With: XMLHttpRequest",
    )
    @GET("api/search/artist")
    suspend fun searchArtist(
        @Query("q") query: String,
    ): GeniusSearchResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Accept: application/json, text/plain, */*",
        "Accept-Language: en-US,en;q=0.9",
        "Referer: https://genius.com/",
        "X-Requested-With: XMLHttpRequest",
    )
    @GET("api/search/multi")
    suspend fun searchMulti(
        @Query("q") query: String,
    ): GeniusSearchResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Accept: application/json, text/plain, */*",
        "Accept-Language: en-US,en;q=0.9",
        "Referer: https://genius.com/",
        "X-Requested-With: XMLHttpRequest",
    )
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
    ): GeniusSearchResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.9",
    )
    @GET
    suspend fun fetchRawPage(
        @Url url: String,
    ): ResponseBody
}
