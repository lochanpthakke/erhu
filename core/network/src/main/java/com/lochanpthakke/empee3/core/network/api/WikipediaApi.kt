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
}
