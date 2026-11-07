package com.erhu.erhu.core.network.scorer
import com.erhu.erhu.core.model.MetadataCandidate
import com.erhu.erhu.core.network.bpm.candidateTempo
import com.erhu.erhu.core.network.tagger.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MetadataSiblingsTest {
 private val match=MetadataCandidate("a","Song","Artist","Album",durationMs=180000,confidenceScore=0.98f,sourceName="MusicBrainz")
 private val sibling=match.copy(id="b",confidenceScore=0.97f,sourceName="iTunes",releaseDate="2016-05-06",bpm=90,genre="R&B")
 @Test fun onlyConfidentSameEditionCandidatesCanSupplyMissingFields() {
  val wrong=listOf(sibling.copy(id="low",confidenceScore=0.3f),sibling.copy(id="live",title="Song (Live)"),
   sibling.copy(id="artist",artist="Other"),sibling.copy(id="album",album="Compilation"),
   sibling.copy(id="duration",durationMs=210000))
  assertEquals(listOf(sibling),metadataSiblings(match,wrong+sibling))
 }
 @Test fun releaseDateFallbackKeepsConflictingYearAndWrongEditionSafeguards() {
  val date=releaseDateFor(match,listOf(sibling),LocalDate.of(2026,11,7),0.9f) as ReleaseDateDecision.Found
  assertEquals("2016-05-06",date.date.tagValue)
  assertTrue(releaseDateFor(match,listOf(sibling.copy(title="Song (Live)")),LocalDate.of(2026,11,7),0.9f) is ReleaseDateDecision.Rejected)
  assertTrue(releaseDateFor(match,listOf(sibling,sibling.copy(id="c",releaseDate="2020")),LocalDate.of(2026,11,7),0.9f) is ReleaseDateDecision.Rejected)
 }
 @Test fun candidateBpmRequiresAgreementAndNeverTurnsAmbiguityIntoAnEstimate() {
  assertEquals(90,candidateTempo(match,listOf(sibling))!!.bpm)
  assertFalse(candidateTempo(match,listOf(sibling))!!.estimated)
  assertNull(candidateTempo(match,listOf(sibling,sibling.copy(id="c",bpm=180))))
  assertNull(candidateTempo(match,listOf(sibling.copy(title="Song (Live)"))))
  assertEquals(100,candidateTempo(match.copy(bpm=100),listOf(sibling))!!.bpm)
 }
}
