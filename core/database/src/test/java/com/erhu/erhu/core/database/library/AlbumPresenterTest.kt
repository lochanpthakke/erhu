package com.erhu.erhu.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPresenterTest {
    private val identity = ArtistIdentityResolver(emptyMap())
    private val names =
        mapOf(
            "drake" to "Drake",
            "21 savage" to "21 Savage",
            "kanye west" to "Kanye West",
            "kanye west/jay-z" to "Kanye West/JAY-Z",
            "travis scott" to "Travis Scott",
        )

    private fun credit(
        text: String,
        role: CreditRole = CreditRole.PRIMARY,
        unresolved: Boolean = false,
    ) = ResolvedCredit(text, CreditText.canonicalKey(text), role, 0, unresolved, CreditText.segmentsOf(text).map { it.key })

    private fun group(
        n: Int,
        aa: String? = null,
        compilation: Boolean = false,
    ) = AlbumGroup(1, "X", "dir:/x", aa?.let(CreditText::canonicalKey), aa, (1L..n).toList(), compilation, false)

    private fun present(
        g: AlbumGroup,
        credits: Map<Long, List<ResolvedCredit>>,
        standalone: Set<String> = emptySet(),
    ) = AlbumPresenter.present(g, credits, standalone, identity) { names[it] ?: it }

    @Test
    fun `core artists on half the tracks or more, features ignored`() {
        val credits =
            (1L..16L).associateWith { i ->
                when {
                    i == 1L -> listOf(credit("21 Savage"))
                    i <= 5L -> listOf(credit("Drake"))
                    else -> listOf(credit("Drake"), credit("21 Savage"), credit("Travis Scott", CreditRole.FEATURED))
                }
            }
        val p = present(group(16), credits)
        assertEquals("Drake & 21 Savage", p.displayArtist)
        assertEquals(listOf("drake", "21 savage"), p.linkedArtistKeys)
    }

    @Test
    fun `tagged album artist wins`() {
        assertEquals("Drake", present(group(2, "Drake"), mapOf(1L to listOf(credit("21 Savage")))).displayArtist)
    }

    @Test
    fun `compilation flag and scattered artists become Various Artists`() {
        assertEquals(
            AlbumPresenter.VARIOUS_ARTISTS,
            present(group(2, compilation = true), mapOf(1L to listOf(credit("Drake")))).displayArtist,
        )
        val scattered = (1L..4L).associateWith { listOf(credit("Artist $it")) }
        val p = present(group(4), scattered)
        assertEquals(AlbumPresenter.VARIOUS_ARTISTS, p.displayArtist)
        assertEquals(emptyList<String>(), p.linkedArtistKeys)
    }

    @Test
    fun `unresolved credit counts toward a standalone leading artist only`() {
        val credits = mapOf(1L to listOf(credit("Kanye West")), 2L to listOf(credit("Kanye West/JAY-Z", unresolved = true)))
        assertEquals("Kanye West", present(group(2), credits, setOf("kanye west")).displayArtist)
        // Without evidence the credit is text only: shown as written, linked to no artist.
        val tyler = mapOf(1L to listOf(credit("Tyler/ The Creator", unresolved = true)))
        val unaliased = present(group(1), tyler)
        assertEquals("Tyler/ The Creator", unaliased.displayArtist)
        assertEquals(emptyList<String>(), unaliased.linkedArtistKeys)
        // With the approved alias it is the artist Tyler, The Creator.
        val aliased =
            AlbumPresenter.present(group(1), tyler, emptySet(), ArtistIdentityResolver(ManualArtistAliases.aliasKeys, ManualArtistAliases.artistNames)) {
                ManualArtistAliases.artistNames[it] ?: it
            }
        assertEquals("Tyler, The Creator", aliased.displayArtist)
        assertEquals(listOf("tyler, the creator"), aliased.linkedArtistKeys)
    }
}
