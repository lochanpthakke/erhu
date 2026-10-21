package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.network.scorer.MetadataScorer
import com.lochanpthakke.empee3.core.network.apple.model.ItunesAlbumDto
import javax.inject.Inject

class CatalogCandidateScorer @Inject constructor(
    private val metadataScorer: MetadataScorer
) {
    fun score(localArtist: String, localAlbum: String, candidates: List<ItunesAlbumDto>, localTrackCount: Int? = null): ItunesAlbumDto? {
        val cleanedLocalAlbum = AlbumTagNormalizer.cleanAlbumTitle(localAlbum)
        val foldedCleanedLocalAlbum = AlbumTagNormalizer.fold(cleanedLocalAlbum)
        
        val localArtistCandidates = AlbumTagNormalizer.cleanArtist(localArtist)
        
        val localSequelTokens = extractSequelTokens(cleanedLocalAlbum)

        var bestCandidate: ItunesAlbumDto? = null
        var bestScore = 0.0f
        
        for (candidate in candidates) {
            val candidateArtist = candidate.artistName ?: ""
            val candidateAlbum = candidate.collectionName ?: ""
            
            val cleanedCandidateAlbum = AlbumTagNormalizer.cleanAlbumTitle(candidateAlbum)
            val foldedCleanedCandidateAlbum = AlbumTagNormalizer.fold(cleanedCandidateAlbum)
            
            val candidateSequelTokens = extractSequelTokens(cleanedCandidateAlbum)
            
            val artistScore = localArtistCandidates.maxOfOrNull { 
                metadataScorer.jaroWinklerDistance(it, AlbumTagNormalizer.fold(candidateArtist))
            } ?: 0.0f
            
            if (artistScore < 0.75f) continue
            
            var albumScore = metadataScorer.jaroWinklerDistance(foldedCleanedLocalAlbum, foldedCleanedCandidateAlbum)
            
            if (localSequelTokens != candidateSequelTokens) {
                albumScore = 0.0f
            }
            
            if (albumScore < 0.80f) continue
            
            var editionScore = 1.0f
            val rawCandidateAlbumLower = candidateAlbum.lowercase()
            val rawLocalAlbumLower = localAlbum.lowercase()
            
            val instrWords = listOf("instrumental", "a cappella", "acappella", "karaoke", "commentary")
            if (instrWords.any { it in rawCandidateAlbumLower } && instrWords.none { it in rawLocalAlbumLower }) {
                editionScore = 0.3f
            } else if (rawCandidateAlbumLower.length > cleanedCandidateAlbum.length && rawLocalAlbumLower.length == cleanedLocalAlbum.length) {
                editionScore = 0.9f
            }
            
            var totalScore = (artistScore * 0.40f) + (albumScore * 0.45f) + (editionScore * 0.15f)
            totalScore = totalScore.coerceIn(0.0f, 1.0f)
            
            if (totalScore >= 0.80f) {
                if (totalScore > bestScore || (totalScore >= bestScore - 0.02f && localTrackCount != null && candidate.trackCount == localTrackCount)) {
                    bestScore = totalScore
                    bestCandidate = candidate
                }
            }
        }
        
        return bestCandidate
    }
    
    private fun extractSequelTokens(title: String): Set<String> {
        val tokens = mutableSetOf<String>()
        val words = title.lowercase().split(Regex("\\s+"))
        for (word in words) {
            if (word.matches(Regex("\\d+"))) {
                tokens.add(word)
            } else if (word.matches(Regex("^(ii|iii|iv|v|vi|vii|viii|ix|x)$"))) {
                val digit = when(word) {
                    "ii" -> "2"; "iii" -> "3"; "iv" -> "4"; "v" -> "5"
                    "vi" -> "6"; "vii" -> "7"; "viii" -> "8"; "ix" -> "9"; "x" -> "10"
                    else -> ""
                }
                tokens.add(digit)
            }
        }
        return tokens
    }
}
