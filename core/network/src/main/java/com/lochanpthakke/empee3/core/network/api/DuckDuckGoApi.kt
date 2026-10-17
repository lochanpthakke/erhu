package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.DuckDuckGoImageResultDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface DuckDuckGoApi {
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.9",
    )
    @GET
    suspend fun getSearchPage(
        @Url url: String,
    ): ResponseBody

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept: application/json, text/javascript, */*; q=0.01",
        "Accept-Language: en-US,en;q=0.9",
        "Referer: https://duckduckgo.com/",
    )
    @GET("i.js")
    suspend fun searchImages(
        @Query("q") query: String,
        @Query("vqd") vqd: String,
        @Query("o") output: String = "json",
        @Query("p") page: Int = 1,
    ): DuckDuckGoImageResultDto

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.9",
    )
    @GET
    suspend fun fetchHtml(
        @Url url: String,
    ): ResponseBody
}
