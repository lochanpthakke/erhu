package com.lochanpthakke.empee3.core.network.api

import com.lochanpthakke.empee3.core.network.model.WikipediaQueryResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface WikipediaApi {
    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("w/api.php?action=query&prop=pageimages&format=json&pithumbsize=1000")
    suspend fun getPageImage(
        @Query("titles") titles: String,
    ): WikipediaQueryResponse

    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET("w/api.php?action=query&generator=search&gsrnamespace=0&gsrlimit=10&prop=pageimages&pithumbsize=1000&format=json")
    suspend fun searchPageImages(
        @Query("gsrsearch") query: String,
    ): WikipediaQueryResponse

    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET(
        "w/api.php?action=query&generator=categorymembers&gcmnamespace=6&gcmlimit=15&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1000&format=json",
    )
    suspend fun listCategoryMembers(
        @Query("gcmtitle") categoryTitle: String,
    ): WikipediaQueryResponse

    @Headers("User-Agent: empee3/1.0.0 (https://github.com/lochanpthakke/empee3)")
    @GET
    suspend fun listCommonsCategoryMembers(
        @retrofit2.http.Url url: String,
    ): WikipediaQueryResponse
}
