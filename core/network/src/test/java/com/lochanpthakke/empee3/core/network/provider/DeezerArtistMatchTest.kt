package com.lochanpthakke.empee3.core.network.provider

import com.lochanpthakke.empee3.core.network.model.DeezerArtistDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeezerArtistMatchTest {
    private fun dto(
        id: Long,
        name: String,
    ) = DeezerArtistDto(id = id, name = name)

    @Test
    fun `only an exact canonical name match is accepted`() {
        assertEquals(1L, pickExactArtist("Drake", listOf(dto(2, "Drake Bell"), dto(1, "DRAKE")))?.id)
        assertNull(pickExactArtist("Yameii Online", listOf(dto(3, "Yameii"))))
        assertNull(pickExactArtist("The Creator", listOf(dto(4, "Tyler, The Creator"))))
    }
}
