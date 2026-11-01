package com.erhu.erhu.core.network.provider

import com.erhu.erhu.core.network.model.DeezerArtistDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Candidate lists are the real Deezer `search/artist?limit=100` results observed on 2026-10-04. */
class DeezerArtistMatchTest {
    private fun dto(
        id: Long,
        name: String,
        fans: Long,
        photo: Boolean = true,
    ) = DeezerArtistDto(
        id = id,
        name = name,
        pictureBig = if (photo) "https://cdn-images.dzcdn.net/images/artist/h$id/500x500-000000-80-0-0.jpg" else BLANK,
        nbFan = fans,
    )

    private fun selected(
        name: String,
        candidates: List<DeezerArtistDto>,
    ): Long? = (selectDeezerArtist(name, candidates) as? DeezerArtistMatch.Selected)?.artistId

    private fun rejected(
        name: String,
        candidates: List<DeezerArtistDto>,
    ): Boolean = selectDeezerArtist(name, candidates) is DeezerArtistMatch.Rejected

    // A. normalization

    @Test
    fun `normalization folds only harmless differences`() {
        assertEquals("tyler, the creator", normalizeArtistName("  Tyler,   The CREATOR "))
        assertEquals(normalizeArtistName("Lil' Wayne"), normalizeArtistName("Lil\u2019 Wayne"))
        assertEquals(normalizeArtistName("\"Weird Al\""), normalizeArtistName("\u201CWeird Al\u201D"))
        assertEquals(normalizeArtistName("Beyonc\u00E9"), normalizeArtistName("Beyonce\u0301"))
        assertEquals("a\$ap rocky", normalizeArtistName("A\$AP Rocky"))
        assertNotEquals(normalizeArtistName("Too \$hort"), normalizeArtistName("Too Short"))
        assertNotEquals(normalizeArtistName("\$uicideboy\$"), normalizeArtistName("Suicideboy\$"))
        assertNotEquals(normalizeArtistName("Metro Boomin'"), normalizeArtistName("Metro Boomin"))
        assertNotEquals(normalizeArtistName("Tyler, The Creator"), normalizeArtistName("Tyler The Creator"))
    }

    // B. one exact candidate

    @Test
    fun `a single exact candidate with a photo is accepted whatever its fan count`() {
        assertEquals(12060520L, selected("Che Ecru", listOf(dto(12060520, "Che Ecru", 4331), dto(125377, "Cherry Pie", 16322))))
        assertEquals(11329046L, selected("Teezo Touchdown", listOf(dto(11329046, "Teezo Touchdown", 9950))))
        assertEquals(230L, selected("Kanye West", listOf(dto(230, "Kanye West", 4550285), dto(11198106, "Kanye West & Nas", 60))))
        assertEquals(48947681L, selected("Baby Keem", listOf(dto(48947681, "Baby Keem", 155351), dto(116696422, "Baby Zeek", 8))))
    }

    // C + D. several exact candidates, 10x dominance

    @Test
    fun `the dominant same-name profile wins over Deezer's duplicates`() {
        val drake =
            listOf(
                dto(67927442, "Drake", 166),
                dto(67926762, "Drake", 141),
                dto(246791, "Drake", 24098891),
                dto(5215664, "Drake", 3824),
                dto(303396411, "Drake", 9),
                dto(2062, "Nick Drake", 103112),
            )
        assertEquals(246791L, selected("Drake", drake))
        assertEquals(6043160L, selected("Kali Uchis", listOf(dto(6043160, "Kali Uchis", 531252), dto(276492471, "Kali Uchis", 7))))
        val rema = listOf(dto(152097442, "R.E.M.A", 17), dto(264639682, "Rema", 44), dto(301870001, "Rema", 26), dto(5259966, "Rema", 574637))
        assertEquals(5259966L, selected("Rema", rema))
        assertEquals(68334L, selected("Offset", listOf(dto(68334, "Offset", 851194), dto(260907541, "Offset", 19), dto(15095793, "Offset", 36))))
        val estelle = listOf(dto(155713022, "Estelle", 4), dto(213297307, "ESTELLE", 7), dto(16745, "Estelle", 117766))
        assertEquals(16745L, selected("Estelle", estelle))
        assertEquals(277893L, selected("BONES", listOf(dto(72966492, "Bones", 62), dto(73146042, "Bones", 6), dto(277893, "Bones", 144227))))
    }

    @Test
    fun `dominance needs ten times the next profile`() {
        assertEquals(1L, selected("X", listOf(dto(1, "X", 1000), dto(2, "X", 100))))
        assertTrue(rejected("X", listOf(dto(1, "X", 999), dto(2, "X", 100))))
    }

    // E. ambiguous

    @Test
    fun `comparable same-name profiles are ambiguous, so no photo`() {
        assertTrue(rejected("Drake", listOf(dto(1, "Drake", 166), dto(2, "Drake", 141))))
        assertTrue(rejected("X", listOf(dto(1, "X", 5), dto(2, "X", 5))))
    }

    // F. zero fans

    @Test
    fun `zero-fan duplicates count as one fan`() {
        val boys = listOf(dto(10159994, "\$uicideboy\$", 699601), dto(413032511, "\$uicideboy\$", 0, photo = false))
        assertEquals(10159994L, selected("\$uicideboy\$", boys))
        assertEquals(1L, selected("X", listOf(dto(1, "X", 10), dto(2, "X", 0))))
        assertTrue(rejected("X", listOf(dto(1, "X", 9), dto(2, "X", 0))))
        assertTrue(rejected("X", listOf(dto(1, "X", 0), dto(2, "X", 0))))
    }

    // G. candidate without photo

    @Test
    fun `a single exact candidate without a photo is not found`() {
        assertTrue(rejected("X", listOf(dto(1, "X", 50000, photo = false))))
        assertTrue(rejected("X", listOf(DeezerArtistDto(id = 1, name = "X", nbFan = 5))))
    }

    @Test
    fun `a photo-less small duplicate does not block the main profile`() {
        val metro = listOf(dto(4968870, "Metro Boomin", 758437), dto(412932831, "Metro Boomin", 1, photo = false))
        assertEquals(4968870L, selected("Metro Boomin", metro))
    }

    // H. dominant candidate without photo

    @Test
    fun `a dominant profile without a photo never falls back to a smaller duplicate`() {
        assertTrue(rejected("Metro Boomin", listOf(dto(1, "Metro Boomin", 758437, photo = false), dto(2, "Metro Boomin", 1))))
    }

    // I. blank Deezer image URL

    @Test
    fun `Deezer's blank picture url is not a photo`() {
        val blank = DeezerArtistDto(id = 1, name = "X", pictureBig = BLANK, pictureXl = BLANK.replace("500x500", "1000x1000"), nbFan = 5)
        assertTrue(rejected("X", listOf(blank)))
    }

    @Test
    fun `no exact name means not found`() {
        assertTrue(rejected("Too \$hort", listOf(dto(504125, "Too Short", 151079), dto(514404, "Too \$hort & Friends", 27))))
        assertTrue(rejected("Metro Boomin", listOf(dto(13590885, "Metro Boomin'", 15), dto(154102992, "Nav Metro Boomin", 647))))
        assertTrue(rejected("X", emptyList()))
    }

    private companion object {
        const val BLANK = "https://cdn-images.dzcdn.net/images/artist//500x500-000000-80-0-0.jpg"
    }
}
