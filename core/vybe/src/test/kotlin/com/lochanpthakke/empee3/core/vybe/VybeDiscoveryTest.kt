package com.lochanpthakke.empee3.core.vybe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VybeDiscoveryTest {
    /** Embeds by keyword: axis 0 "dark", 1 "party", 2 "soul". Enough to check the hybrid logic. */
    private object KeywordEmbedder : TextEmbedder {
        override val dimension = 3

        override fun embed(
            text: String,
            role: EmbeddingRole,
        ): FloatArray {
            val t = text.lowercase()
            val v =
                floatArrayOf(
                    if ("dark" in t || "cloud rap" in t || "memphis" in t) 1f else 0.1f,
                    if ("party" in t || "trap" in t) 1f else 0.1f,
                    if ("soul" in t || "r&b" in t || "mellow" in t) 1f else 0.1f,
                )
            return v
        }
    }

    private class MapVectors(
        val map: Map<Long, FloatArray>,
    ) : SongVectors {
        override fun rank(
            limit: Int,
            score: (Long, FloatArray) -> Float?,
        ) = map.entries
            .mapNotNull { (id, v) -> score(id, v)?.let { VybeMatch(id, it) } }
            .sortedWith(compareByDescending<VybeMatch> { it.score }.thenBy { it.songId })
            .take(limit)

        override fun vectorOf(songId: Long) = map[songId]
    }

    private val songs =
        listOf(
            SongText(1, "Nightmare", "\$uicideboy\$", "THY KINGDOM COME", genre = "cloud rap; memphis rap", bpm = 140),
            SongText(2, "Runaway", "Kanye West/Pusha T", "MBDTF", genre = "hip hop", bpm = 87),
            SongText(3, "OPPOSITE", "Don Toliver", "OCTANE", genre = "alternative r&b; hip hop; trap", bpm = 128),
            SongText(4, "Mystery", "Unknown Band", "Demos", genre = null, bpm = null),
            SongText(5, "Pink + White", "Frank Ocean", "Blonde", genre = "alternative r&b; soul", bpm = 80),
        )
    private val vectors = MapVectors(songs.associate { it.songId to KeywordEmbedder.embed(VybeText.describe(it), EmbeddingRole.DOCUMENT) })
    private val discovery = VybeDiscovery(KeywordEmbedder, vectors)

    @Test
    fun anExactArtistRequest_isAnsweredFromMetadata_notEmbeddings() {
        val answer = discovery.search("songs by Kanye West", songs, 10) as VybeAnswer.Named
        assertEquals(EntityKind.ARTIST, answer.kind)
        assertEquals(listOf(2L), answer.songIds)
        assertTrue(answer.similar.isEmpty())
        val like = discovery.search("songs like Runaway", songs, 2) as VybeAnswer.Named
        assertEquals(listOf(2L), like.songIds)
        assertFalse("the named song isn't its own neighbour", like.similar.any { it.songId == 2L })
    }

    @Test
    fun aMood_isMatchedSemantically_andKeepsSongsWithoutTags() {
        val answer = discovery.search("dark atmospheric stuff", songs, 10) as VybeAnswer.Described
        assertEquals(1L, answer.hits.first().songId)
        assertTrue("songs without genre or BPM aren't dropped", answer.hits.any { it.songId == 4L })
        assertEquals(songs.size, answer.hits.size)
    }

    @Test
    fun anArtistNamedInsideAMood_wins_andSaysWhy() {
        val answer = discovery.search("late-night Frank Ocean vibes", songs, 3) as VybeAnswer.Described
        assertEquals(5L, answer.hits.first().songId)
        assertEquals("Artist: Frank Ocean", answer.hits.first().reasons.first())
    }

    @Test
    fun aLibraryGenreNamedOutright_isBoosted() {
        val boosts = VybeDiscovery.metadataBoosts("some trap for the gym", songs)
        assertTrue(boosts.getValue(3L).any { it.first == "Genre: trap" })
        // Tempo only nudges, and only songs whose BPM tag fits.
        assertTrue(boosts.getValue(1L).any { it.first == "Tempo: 140 BPM (tag)" })
        assertFalse(boosts.containsKey(2L))
        assertFalse("no tag, no boost, still searchable", boosts.containsKey(4L))
    }

    @Test
    fun aQueryThatDoesntSeparateTheLibrary_isFlaggedWeak() {
        assertTrue(VybeDiscovery.isWeak(listOf(0.51f, 0.50f, 0.50f, 0.49f)))
        assertFalse(VybeDiscovery.isWeak(listOf(0.70f, 0.50f, 0.48f, 0.40f)))
        // "sad but beautiful" on the real library: nothing is described like it.
        assertTrue(VybeDiscovery.isWeak(listOf(0.215f, 0.209f, 0.15f, 0.10f)))
    }

    @Test
    fun similarToTheCurrentSong_excludesIt() {
        val answer = discovery.similarToSong(5L, 2)
        assertFalse(answer.hits.any { it.songId == 5L })
        assertEquals(2, answer.hits.size)
    }

    @Test
    fun descriptions_leaveTitlesAndAlbumsOut_andSplitGenres() {
        assertEquals(
            "Artist: Don Toliver. Genres: alternative r&b, hip hop, trap. Tempo: 128 BPM",
            VybeText.describe(songs[2]),
        )
        assertFalse(VybeText.describe(songs[0]).contains("Nightmare"))
        assertEquals("Artist: Unknown Band", VybeText.describe(songs[3]))
    }
}
