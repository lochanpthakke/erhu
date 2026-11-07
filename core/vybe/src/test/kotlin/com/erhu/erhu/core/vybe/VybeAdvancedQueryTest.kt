package com.erhu.erhu.core.vybe

import org.junit.Assert.*
import org.junit.Test

class VybeAdvancedQueryTest {
    private val songs = listOf(
        SongText(1,"Dramatic Girl (feat. Guest)","Baby Keem","The Melodic Blue",genre="hip-hop",year=2021,bpm=90),
        SongText(2,"Bound 2","Kanye West","Yeezus",genre="rap",year=2013,bpm=120),
        SongText(3,"Both styles","Other","Third",genre="R&B",year=2016,bpm=95),
        SongText(4,"Only first","Other","Fourth",genre="soul",year=1994,bpm=140),
        SongText(5,"Bound 2","Other Artist","Other Album",genre="jazz",year=1995,bpm=85),
        SongText(6,"Unknown tags","Unknown","Unknown"),
        SongText(7,"Pink + White","Frank Ocean","Blonde",genre="rhythm and blues",year=2016,bpm=80),
        SongText(8,"And More, Too","Earth, Wind & Fire","Classic",genre="soul",year=1990,bpm=99),
    )
    private class Vectors(val data: Map<Long,FloatArray>):SongVectors {
        override fun vectorOf(songId:Long)=data[songId]
        override fun rank(limit:Int,score:(Long,FloatArray)->Float?)=data.mapNotNull { (id,v)->score(id,v)?.let{VybeMatch(id,it)} }
            .sortedWith(compareByDescending<VybeMatch>{it.score}.thenBy{it.songId}).take(limit)
    }
    private val data=mapOf(1L to floatArrayOf(10f,0f),2L to floatArrayOf(0f,1f),
        3L to floatArrayOf(1f,1f),4L to floatArrayOf(1f,0f),5L to floatArrayOf(0f,1f),
        6L to floatArrayOf(0.2f,0.8f),7L to floatArrayOf(0.5f,0.5f),8L to floatArrayOf(1f,0f))
    private val model=object:TextEmbedder {
        override val dimension=2
        override fun embed(text:String,role:EmbeddingRole)=floatArrayOf(1f,1f)
    }
    private fun search(q:String,selections:Map<Int,String> = emptyMap(),vectors:Map<Long,FloatArray> = data)=
        VybeDiscovery(model,Vectors(vectors)).search(q,songs,20,VybeNameIndex(songs),selections)

    @Test fun aliasesApplyToSemanticArtistBoostsAndExplicitArtistRequests() {
        val names=VybeNameIndex(songs + SongText(9,"Track","Other","Ye"))
        val route=VybeQueryRouter.route("songs by Ye",names) as VybeRoute.Named
        assertEquals(listOf(2L),route.songIds)
        val expanded=names.expandArtistAliases("late-night Ye vibes")
        assertTrue(expanded.contains("kanye west"))
        assertTrue(VybeDiscovery.metadataBoosts(expanded,songs).getValue(2).any{it.first.startsWith("Artist:")})
        assertTrue((search("x".repeat(1025)) as VybeAnswer.Described).error!=null)
    }

    @Test fun staleIndexRowsAndInvalidBpmTagsNeverSatisfyFilteredSearch() {
        val answer=search("under 100 bpm",vectors=data+(999L to floatArrayOf(1f,1f))) as VybeAnswer.Described
        assertFalse(answer.hits.any{it.songId==999L || it.songId==6L})
        val noTags=VybeNameIndex(songs)
        val filter=VybeQueryIntentParser.parse("under 100 bpm",noTags).constraints
        assertFalse(filter.allows(songs.first().copy(bpm=-10),noTags))
    }

    @Test fun normalizedCatalogDuplicatesKeepAllOccurrencesAndCachedChoicesRemainStable() {
        val names=VybeNameIndex(songs + SongText(10,"bound 2","kanye west","Yeezus"))
        val match=names.find("Bound 2 by Kanye").single()
        assertEquals(listOf(2L,10L),match.songIds)
        repeat(3) { assertEquals(match,names.find("Bound 2 by Kanye").single()) }
    }

    @Test fun catalogTitlesThatLookLikeFiltersStaySearchableWithOtherFilters() {
        val library=songs+listOf(
            SongText(20,"Without Me","Eminem","Album",bpm=90),
            SongText(21,"No Role Modelz","J. Cole","Album",bpm=90),
            SongText(22,"Track","Drake","Album",bpm=90))
        val names=VybeNameIndex(library)
        for(title in listOf("Without Me","No Role Modelz")) {
            val parsed=VybeQueryIntentParser.parse("songs like " + title + " under 100 BPM without Drake",names)
            assertNull(parsed.constraints.error)
            assertEquals(99,parsed.constraints.maxBpm)
            assertEquals(setOf("Drake"),parsed.constraints.excludedArtists)
            assertTrue(VybeQueryRouter.route(parsed.text,names) is VybeRoute.Named)
        }
        val multiple=VybeQueryIntentParser.parse("like No Role Modelz and Bound 2 by Kanye under 100 BPM without Drake",names)
        assertNull(multiple.constraints.error)
        assertTrue(VybeQueryRouter.route(multiple.text,names) is VybeRoute.References)
    }

    @Test fun filtersAreHardConstraintsAndMissingTagsNeverPassThem() {
        val names=VybeNameIndex(songs)
        val intent=VybeQueryIntentParser.parse("chill R&B under 100 BPM released in 2016, without Frank Ocean",names)
        assertEquals(listOf(3L),songs.filter{intent.constraints.allows(it,names)}.map{it.songId})
        val answer=search("under 100 bpm released in 2016 without Frank Ocean") as VybeAnswer.Described
        assertEquals(listOf(3L),answer.hits.map{it.songId})
        assertTrue((search("120-80 BPM") as VybeAnswer.Described).error!=null)
        assertTrue((search("above 400 BPM") as VybeAnswer.Described).error!=null)
    }

    @Test fun decadesRangesExclusionsAndAliasesDoNotRewriteMetadata() {
        val names=VybeNameIndex(songs)
        fun ids(q:String)=VybeQueryIntentParser.parse(q,names).constraints.let{c->songs.filter{c.allows(it,names)}.map{it.songId}}
        assertEquals(listOf(4L,5L,8L),ids("music from the 90s"))
        assertEquals(listOf(1L,3L),ids("around 90 bpm from 2010 to 2022"))
        assertEquals(listOf(3L,4L,5L,6L,7L,8L),ids("no rap"))
        assertFalse(2L in ids("without Ye"))
        assertEquals("Kanye West",songs[1].artist)
        val quoted=VybeQueryIntentParser.parse("like \"Under 100 BPM\"",names)
        assertNull(quoted.constraints.maxBpm)
    }

    @Test fun qualifiedNamesAliasesPartialTyposAndAmbiguityUseCatalogFacts() {
        val names=VybeNameIndex(songs)
        assertEquals(listOf(1L),names.find("Baby Keem’s Dramatic Girl").single().songIds)
        assertEquals(listOf(1L),names.find("baby keems dramatic girl").single().songIds)
        assertEquals(listOf(2L),names.find("Bound 2 by Ye").single().songIds)
        assertEquals(2,names.find("Bound 2",EntityKind.TITLE).size)
        assertEquals(listOf(7L),names.find("Blond").single().songIds)
        assertEquals(listOf(7L),names.find("Blodne").single().songIds)
        assertTrue(names.find("Bound 3").isEmpty())
        val alias=VybeNameIndex(songs,mapOf("Mr Ocean" to "Frank Ocean"))
        assertEquals(listOf(7L),alias.find("Mr Ocean").single().songIds)
    }

    @Test fun knownSeparatorsAndQuotedTitlesAreNotSplitIntoFakeReferences() {
        val route=VybeQueryRouter.route("songs like \"And More, Too\" and Bound 2 by Kanye",VybeNameIndex(songs)) as VybeRoute.References
        assertEquals(2,route.references.size)
        assertEquals(listOf(8L),route.references[0].matches.single().songIds)
        assertEquals(listOf(2L),route.references[1].matches.single().songIds)
        assertTrue(VybeQueryRouter.route("songs by Earth, Wind & Fire",songs) is VybeRoute.Named)
    }

    @Test fun multipleReferencesAreBalancedAndSeedsAreExcluded() {
        val answer=search("like Baby Keem’s Dramatic Girl and Bound 2 by Kanye") as VybeAnswer.References
        assertEquals(listOf(3L,7L),answer.hits.take(2).map{it.songId})
        assertFalse(answer.waitingForIndex)
        assertFalse(answer.hits.any{it.songId in setOf(1L,2L)})
        assertEquals(2,answer.references.size)
    }

    @Test fun ambiguousChoicesRetainOtherReferencesAndFiltersAndRejectStaleSelections() {
        val query="like Baby Keem’s Dramatic Girl and Bound 2 under 100 bpm"
        val unresolved=search(query) as VybeAnswer.References
        assertTrue(unresolved.hits.isEmpty())
        val ambiguous=unresolved.references.first{it.matches.size>1}
        val choice=ambiguous.matches.first{it.songIds==listOf(5L)}
        val resolved=search(query,mapOf(ambiguous.id to choice.id)) as VybeAnswer.References
        assertTrue(resolved.references.all{it.selected!=null})
        assertTrue(resolved.hits.all{songs.first{s->s.songId==it.songId}.bpm!!<100})
        assertTrue((search(query,mapOf(ambiguous.id to "stale")) as VybeAnswer.References).hits.isEmpty())
        val excluded=ambiguous.matches.first{it.songIds==listOf(2L)}
        assertTrue((search(query,mapOf(ambiguous.id to excluded.id)) as VybeAnswer.References).references.any{it.filteredOut})
    }

    @Test fun missingReferencesAndIncompleteIndexNeverSilentlyUseOnlyOneSeed() {
        val missing=search("like Bound 2 by Kanye and Not A Real Song") as VybeAnswer.References
        assertTrue(missing.references.any{it.matches.isEmpty()})
        assertTrue(missing.hits.isEmpty())
        val waiting=search("like Baby Keem’s Dramatic Girl and Bound 2 by Kanye",vectors=data-2L) as VybeAnswer.References
        assertTrue(waiting.waitingForIndex)
        assertTrue(waiting.hits.isEmpty())
    }
}
