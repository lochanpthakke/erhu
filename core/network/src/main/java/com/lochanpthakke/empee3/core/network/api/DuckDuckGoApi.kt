package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.DuckDuckGoImageSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface DuckDuckGoApi {
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.5",
    )
    @GET("/")
    suspend fun getSearchPage(
        @Query("q") query: String,
        @Query("t") t: String = "h_",
        @Query("iax") iax: String = "images",
        @Query("ia") ia: String = "images",
    ): okhttp3.ResponseBody

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language: en-US,en;q=0.5",
    )
    @GET
    suspend fun getHtmlPage(
        @retrofit2.http.Url url: String,
    ): okhttp3.ResponseBody

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://duckduckgo.com/",
        "Accept: application/json, text/javascript, */*; q=0.01",
    )
    @GET("i.js")
    suspend fun searchImages(
        @Query("l") l: String = "us-en",
        @Query("o") o: String = "json",
        @Query("q") query: String,
        @Query("vqd") vqdToken: String,
        @Query("f") f: String = ",,,,,",
        @Query("p") p: String = "1",
    ): DuckDuckGoImageSearchResponse
}
