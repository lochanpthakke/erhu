package com.erhu.erhu.core.database.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtistIdentityTest {
    @Test
    fun `identity hash keeps the artist ids already stored in v5 databases`() {
        // ids read from a real v5 library database (artists table) before the upgrade
        assertEquals(1616113631558986022L, IdentityHash.of("drake"))
        assertEquals(6800762372324799937L, IdentityHash.of("21 savage"))
        assertEquals(-484836757766393360L, IdentityHash.of("\$uicideboy\$"))
        assertEquals(6684406489965805378L, IdentityHash.of("kanye west"))
    }

    @Test
    fun `exact canonical names share one identity, similar names never do`() {
        val identity = ArtistIdentityResolver(emptyMap())
        assertEquals(identity.idOf(CreditText.canonicalKey("Drake")), identity.idOf(CreditText.canonicalKey("ＤＲＡＫＥ ")))
        assertNotEquals(identity.idOf("yameii"), identity.idOf("yameii online"))
        assertNotEquals(identity.idOf("future"), identity.idOf("future islands"))
    }

    @Test
    fun `an alias row maps one key onto a canonical key (single hop)`() {
        val identity = ArtistIdentityResolver(mapOf("tyler/ the creator" to "tyler, the creator"))
        assertEquals(identity.idOf("tyler, the creator"), identity.idOf("tyler/ the creator"))
        assertEquals("tyler, the creator", identity.canonicalKey("tyler/ the creator"))
    }

    @Test
    fun `display name is the most frequent spelling, ties broken alphabetically`() {
        assertEquals("Drake", chooseDisplayName(mapOf("Drake" to 5, "DRAKE" to 1)))
        assertEquals("AAA", chooseDisplayName(mapOf("aaa" to 2, "AAA" to 2)))
    }
}
