package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.network.apple.model.AppleCatalogResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppleCatalogSerializationTest {

    @Test
    fun `deserialize sza catalog json`() {
        val jsonString = javaClass.getResourceAsStream("/apple/catalog_with_motion_sza.json")
            ?.bufferedReader()
            ?.readText() ?: throw Exception("Fixture not found")

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val response = json.decodeFromString<AppleCatalogResponse>(jsonString)

        val video = response.data.firstOrNull()?.attributes?.editorialVideo
        assertNotNull(video)
        assertNotNull(video?.motionDetailSquare?.video)
        assertNotNull(video?.motionDetailTall?.video)
    }
}
