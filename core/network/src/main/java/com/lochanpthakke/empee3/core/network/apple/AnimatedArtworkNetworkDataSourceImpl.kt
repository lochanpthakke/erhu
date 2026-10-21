package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.getOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimatedArtworkNetworkDataSourceImpl @Inject constructor(
    private val itunesApi: ItunesSearchApi,
    private val catalogApi: AppleMusicCatalogApi,
    private val tokenProvider: AppleMusicTokenProvider,
    private val gate: AppleRequestGate,
    private val scorer: CatalogCandidateScorer,
    private val assembler: HlsMp4Assembler,
    private val client: OkHttpClient
) : AnimatedArtworkNetworkDataSource {

    override suspend fun resolveAlbum(artist: String, album: String, trackCount: Int?): AppResult<ResolveOutcome> = appResultOf {
        val term = "${AlbumTagNormalizer.cleanArtist(artist).firstOrNull() ?: artist} ${AlbumTagNormalizer.cleanAlbumTitle(album)}"
        val defaultLocaleCountry = Locale.getDefault().country.lowercase(Locale.ROOT)
        val storefronts = mutableListOf("us")
        if (defaultLocaleCountry.isNotBlank() && defaultLocaleCountry != "us") {
            storefronts.add(defaultLocaleCountry)
        }
        
        var bestCandidate: com.lochanpthakke.empee3.core.network.apple.model.ItunesAlbumDto? = null
        var bestStorefront: String? = null
        
        for (storefront in storefronts) {
            val response = gate.runItunes {
                itunesApi.search(term = term, country = storefront)
            }
            if (response.results.isNotEmpty()) {
                val candidate = scorer.score(artist, album, response.results, trackCount)
                if (candidate != null) {
                    bestCandidate = candidate
                    bestStorefront = storefront
                    break
                }
            }
        }
        
        if (bestCandidate == null || bestCandidate.collectionId == null || bestStorefront == null) {
            return@appResultOf ResolveOutcome.NoMatch
        }
        
        val catalogAlbumId = bestCandidate.collectionId.toString()
        val urls = fetchMasterUrls(catalogAlbumId, bestStorefront)
        
        if (urls is AppResult.Success) {
            ResolveOutcome.Matched(
                catalogAlbumId = catalogAlbumId,
                storefront = bestStorefront,
                squareMasterUrl = urls.data.squareMasterUrl,
                tallMasterUrl = urls.data.tallMasterUrl
            )
        } else {
            // Assume 404 or missing means no animation
            ResolveOutcome.Matched(
                catalogAlbumId = catalogAlbumId,
                storefront = bestStorefront,
                squareMasterUrl = null,
                tallMasterUrl = null
            )
        }
    }

    override suspend fun fetchMasterUrls(catalogAlbumId: String, storefront: String): AppResult<EditorialVideoUrls> = appResultOf {
        var token = tokenProvider.getToken().getOrNull() ?: throw Exception("No token")
        
        var response: com.lochanpthakke.empee3.core.network.apple.model.AppleCatalogResponse? = null
        try {
            response = gate.runAppleMusic {
                catalogApi.getAlbum(storefront = storefront, id = catalogAlbumId, auth = "Bearer $token")
            }
        } catch (e: Exception) {
            if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                tokenProvider.invalidate()
                token = tokenProvider.getToken(forceRefresh = true).getOrNull() ?: throw e
                
                try {
                    response = gate.runAppleMusic {
                        catalogApi.getAlbum(storefront = storefront, id = catalogAlbumId, auth = "Bearer $token")
                    }
                } catch (e2: Exception) {
                    if (e2.message?.contains("429") == true || e2.message?.contains("403") == true) {
                        gate.tripBreaker()
                    }
                    throw e2
                }
            } else if (e.message?.contains("429") == true) {
                gate.tripBreaker()
                throw e
            } else {
                throw e
            }
        }
        
        val editorial = response?.data?.firstOrNull()?.attributes?.editorialVideo
        EditorialVideoUrls(
            squareMasterUrl = editorial?.motionDetailSquare?.video,
            tallMasterUrl = editorial?.motionDetailTall?.video
        )
    }

    override suspend fun downloadToFile(masterUrl: String, destination: File, tmp: File): AppResult<Unit> = appResultOf {
        val req = Request.Builder().url(masterUrl).build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw Exception("Failed to fetch master m3u8: ${res.code}")
        val masterContent = res.body?.string() ?: throw Exception("Empty master")
        res.close()
        
        val variantUrl = HlsPlaylistParser.selectBestVariantUrl(masterUrl, masterContent, AnimatedArtworkConfig.MAX_SHORT_EDGE_PX)
            ?: throw Exception("No suitable variant")
            
        val varReq = Request.Builder().url(variantUrl).build()
        val varRes = client.newCall(varReq).execute()
        if (!varRes.isSuccessful) throw Exception("Failed to fetch media m3u8: ${varRes.code}")
        val varContent = varRes.body?.string() ?: throw Exception("Empty media")
        varRes.close()
        
        val playlist = HlsPlaylistParser.parseMediaPlaylist(variantUrl, varContent)
        
        val result = assembler.assemble(playlist, tmp, destination)
        if (result is AppResult.Error) {
            throw Exception(result.error.message, result.error.cause)
        }
    }
}
