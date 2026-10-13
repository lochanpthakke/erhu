package com.lochanpthakke.empee3.core.vybe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VybeTest {
    // ---- vector similarity

    @Test
    fun `cosine similarity of identical, opposite and orthogonal vectors`() {
        assertEquals(1f, cosineSimilarity(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f))!!, 1e-6f)
        assertEquals(-1f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-3f, 0f))!!, 1e-6f)
        assertEquals(0f, cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 5f))!!, 1e-6f)
    }

    @Test
    fun `invalid vectors cannot be compared`() {
        assertNull(cosineSimilarity(floatArrayOf(), floatArrayOf()))
        assertNull(cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f)))
        assertNull(cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 2f)))
        assertNull(cosineSimilarity(floatArrayOf(Float.NaN, 1f), floatArrayOf(1f, 1f)))
        assertNull(cosineSimilarity(floatArrayOf(Float.POSITIVE_INFINITY, 1f), floatArrayOf(1f, 1f)))
    }

    // ---- ranking

    @Test
    fun `ranking is best first, limited, and skips invalid vectors`() {
        val query = floatArrayOf(1f, 0f)
        val songs =
            listOf(
                EmbeddedSong(1, floatArrayOf(0f, 1f)),
                EmbeddedSong(2, floatArrayOf(1f, 0.1f)),
                EmbeddedSong(3, floatArrayOf(1f, 1f)),
                EmbeddedSong(4, floatArrayOf(1f, 0f, 0f)),
                EmbeddedSong(5, floatArrayOf(0f, 0f)),
            )
        assertEquals(listOf(2L, 3L, 1L), VybeRanker.rank(query, songs, limit = 10).map { it.songId })
        assertEquals(listOf(2L), VybeRanker.rank(query, songs, limit = 1).map { it.songId })
    }

    @Test
    fun `equal scores are ordered by song id whatever the input order`() {
        val query = floatArrayOf(1f, 1f)
        val songs = listOf(EmbeddedSong(9, floatArrayOf(2f, 2f)), EmbeddedSong(3, floatArrayOf(1f, 1f)), EmbeddedSong(5, floatArrayOf(4f, 4f)))
        val expected = listOf(3L, 5L, 9L)
        assertEquals(expected, VybeRanker.rank(query, songs, limit = 3).map { it.songId })
        assertEquals(expected, VybeRanker.rank(query, songs.reversed(), limit = 3).map { it.songId })
    }

    @Test
    fun `empty or invalid input gives no matches`() {
        assertEquals(emptyList<VybeMatch>(), VybeRanker.rank(floatArrayOf(1f), emptyList(), limit = 5))
        assertEquals(emptyList<VybeMatch>(), VybeRanker.rank(floatArrayOf(0f, 0f), listOf(EmbeddedSong(1, floatArrayOf(1f, 1f))), limit = 5))
        assertEquals(emptyList<VybeMatch>(), VybeRanker.rank(floatArrayOf(1f), listOf(EmbeddedSong(1, floatArrayOf(1f))), limit = 0))
    }

    // ---- song descriptions

    @Test
    fun `description uses the known fields only and leaves the album name out`() {
        val song = SongText(1, "Lost In The World", "Kanye West", "My Beautiful Dark Twisted Fantasy", genre = "Hip-Hop", year = 2010)
        assertEquals(
            "Title: Lost In The World. Artist: Kanye West. Genre: Hip-Hop. Year: 2010",
            SongDescriber.describe(song),
        )
        val sparse = SongText(2, "Track 01", "<unknown>", " ", genre = null, year = 0)
        assertEquals("Title: Track 01", SongDescriber.describe(sparse))
    }

    @Test
    fun `lyrics become a short excerpt without timestamps`() {
        val lrc = "[ar:Someone]\n[00:01.00]<00:01.00>Late <00:01.40>night\n[00:05.00]city lights"
        assertEquals("Late night city lights", SongDescriber.lyricsExcerpt(lrc))
        val long = List(200) { "word" }.joinToString(" ")
        val excerpt = SongDescriber.lyricsExcerpt(long)!!
        assertTrue(excerpt.length <= SongDescriber.LYRICS_EXCERPT_CHARS)
        assertTrue(excerpt.endsWith("word"))
        assertNull(SongDescriber.lyricsExcerpt("  [00:01.00]  "))
        assertNull(SongDescriber.lyricsExcerpt(null))
    }

    // ---- end to end with a mocked embedder (no model)

    @Test
    fun `search ranks songs whose description shares the query's meaning`() {
        val songs =
            listOf(
                SongText(1, "Morning Sun", "A", "Daylight", genre = "Pop"),
                SongText(2, "Midnight Drive", "B", "After Dark", genre = "Synthwave", lyrics = "late night city lights"),
                SongText(3, "Gym Hype", "C", "Workout", genre = "EDM"),
            )
        val search = VybeSearch(FakeEmbedder())
        val results = search.search("late night music", songs, limit = 2)
        assertEquals(2L, results.first().songId)
        assertEquals(results, search.search("late night music", songs.reversed(), limit = 2))
    }

    @Test
    fun `blank query or empty library finds nothing`() {
        val search = VybeSearch(FakeEmbedder())
        assertEquals(emptyList<VybeMatch>(), search.search("   ", listOf(SongText(1, "A", "B", "C")), limit = 5))
        assertEquals(emptyList<VybeMatch>(), search.search("night", emptyList(), limit = 5))
    }
}

/**
 * A stand-in for a real model: a deterministic bag of words hashed into a small vector, so
 * texts sharing words point the same way. Lets VYBE's ranking be tested without any model.
 */
private class FakeEmbedder : TextEmbedder {
    override val dimension = 64

    override fun embed(
        text: String,
        role: EmbeddingRole,
    ): FloatArray {
        val vector = FloatArray(dimension)
        text
            .lowercase()
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .forEach { vector[Math.floorMod(it.hashCode(), dimension)] += 1f }
        return vector
    }

    private companion object {
        val STOP_WORDS = setOf("title", "artist", "album", "genre", "year", "lyrics", "the", "and", "music")
    }
}
