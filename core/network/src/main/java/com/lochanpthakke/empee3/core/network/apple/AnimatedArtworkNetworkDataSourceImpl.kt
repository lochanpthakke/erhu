package com.lochanpthakke.empee3.core.network.apple

import com.lochanpthakke.empee3.core.common.dispatcher.DispatcherProvider
import com.lochanpthakke.empee3.core.common.result.AppResult
import com.lochanpthakke.empee3.core.common.result.appResultOf
import com.lochanpthakke.empee3.core.common.result.getOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnimatedArtworkNetworkDataSourceImpl
    @Inject
    constructor(
        private val itunesApi: ItunesSearchApi,
        private val catalogApi: AppleMusicCatalogApi,
        private val tokenProvider: AppleMusicTokenProvider,
        private val gate: AppleRequestGate,
        private val scorer: CatalogCandidateScorer,
        private val assembler: HlsMp4Assembler,
        @AppleWebClient private val client: OkHttpClient,
        private val dispatchers: DispatcherProvider,
    ) : AnimatedArtworkNetworkDataSource {
        override suspend fun resolveAlbum(
            artist: String,
            album: String,
            trackCount: Int?,
        ): AppResult<ResolveOutcome> =
            withContext(dispatchers.io) {
                appResultOf {
                    val term = "${AlbumTagNormalizer
                        .cleanArtist(
                            artist,
                        ).firstOrNull() ?: artist} ${AlbumTagNormalizer.cleanAlbumTitle(album)}"
                    val defaultLocaleCountry = Locale.getDefault().country.lowercase(Locale.ROOT)
                    val storefronts = mutableListOf("us")
                    if (defaultLocaleCountry.isNotBlank() && defaultLocaleCountry != "us") {
                        storefronts.add(defaultLocaleCountry)
                    }

                    var bestCandidate: com.lochanpthakke.empee3.core.network.apple.model.ItunesAlbumDto? = null
                    var bestStorefront: String? = null

                    for (storefront in storefronts) {
                        val response =
                            gate.runItunes {
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

                    // Search can return just one edition (often the clean one, which has no motion cover):
                    // try the album's other editions too, explicit first. Motion covers are also per
                    // storefront: every edition in the first storefront, then the preferred edition in the others.
                    val editions = scorer.editionOrder(bestCandidate, editionSiblings(bestCandidate, bestStorefront))
                    val motionStorefronts = AnimatedArtworkConfig.motionStorefronts(defaultLocaleCountry, bestStorefront)
                    val attempts =
                        editions.map { it to motionStorefronts.first() } +
                            motionStorefronts.drop(1).map { editions.first() to it }
                    var fallback: ResolveOutcome.Matched? = null
                    var firstError: AppResult.Error? = null
                    for ((edition, storefront) in attempts) {
                        val catalogAlbumId = edition.collectionId?.toString() ?: continue
                        when (val urlsResult = fetchMasterUrls(catalogAlbumId, storefront)) {
                            is AppResult.Success -> {
                                val matched =
                                    ResolveOutcome.Matched(
                                        catalogAlbumId = catalogAlbumId,
                                        storefront = storefront,
                                        squareMasterUrl = urlsResult.data.squareMasterUrl,
                                        tallMasterUrl = urlsResult.data.tallMasterUrl,
                                    )
                                if (matched.squareMasterUrl != null || matched.tallMasterUrl != null) return@appResultOf matched
                                if (fallback == null) fallback = matched
                            }
                            is AppResult.Error -> {
                                // Apple is limiting requests: every further attempt would be refused too.
                                (urlsResult.error.cause as? RateLimitException)?.let { throw it }
                                if (firstError == null) firstError = urlsResult
                            }
                        }
                    }
                    fallback ?: firstError!!.let { throw Exception(it.error.message, it.error.cause) }
                }
            }

        /**
         * The artist's albums, to find [match]'s other editions. Only needed for a clean match; a
         * failed lookup just leaves the match on its own.
         */
        private suspend fun editionSiblings(
            match: com.lochanpthakke.empee3.core.network.apple.model.ItunesAlbumDto,
            storefront: String,
        ): List<com.lochanpthakke.empee3.core.network.apple.model.ItunesAlbumDto> {
            val artistId = match.artistId ?: return emptyList()
            if (match.collectionExplicitness != "cleaned") return emptyList()
            return try {
                gate.runItunes { itunesApi.lookupArtistAlbums(artistId = artistId, country = storefront) }.results
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }

        override suspend fun fetchMasterUrls(
            catalogAlbumId: String,
            storefront: String,
        ): AppResult<EditorialVideoUrls> =
            withContext(dispatchers.io) {
                appResultOf {
                    var token = tokenProvider.getToken().getOrNull() ?: throw Exception("Failed to acquire token")

                    var response: com.lochanpthakke.empee3.core.network.apple.model.AppleCatalogResponse? = null
                    try {
                        response =
                            gate.runAppleMusic {
                                catalogApi.getAlbum(storefront = storefront, id = catalogAlbumId, auth = "Bearer $token")
                            }
                    } catch (e: RateLimitException) {
                        throw e
                    } catch (e: Exception) {
                        val statusCode = (e as? HttpException)?.code() ?: -1
                        if (statusCode == 401 ||
                            statusCode == 403 ||
                            e.message?.contains("401") == true ||
                            e.message?.contains("403") == true
                        ) {
                            tokenProvider.invalidate()
                            token = tokenProvider.getToken(forceRefresh = true).getOrNull() ?: throw e

                            try {
                                response =
                                    gate.runAppleMusic {
                                        catalogApi.getAlbum(storefront = storefront, id = catalogAlbumId, auth = "Bearer $token")
                                    }
                            } catch (e2: RateLimitException) {
                                throw e2
                            } catch (e2: Exception) {
                                val statusCode2 = (e2 as? HttpException)?.code() ?: -1
                                if (statusCode2 == 429 || statusCode2 == 403 || e2.message?.contains("429") == true) {
                                    gate.tripBreaker()
                                }
                                if (statusCode2 == 404) {
                                    return@appResultOf EditorialVideoUrls(null, null)
                                }
                                throw e2
                            }
                        } else if (statusCode == 429 || e.message?.contains("429") == true) {
                            gate.tripBreaker()
                            throw e
                        } else if (statusCode == 404) {
                            return@appResultOf EditorialVideoUrls(null, null)
                        } else {
                            throw e
                        }
                    }

                    val editorial =
                        response
                            ?.data
                            ?.firstOrNull()
                            ?.attributes
                            ?.editorialVideo
                    EditorialVideoUrls(
                        squareMasterUrl = editorial?.motionDetailSquare?.video ?: editorial?.motionSquareVideo1x1?.video,
                        tallMasterUrl = editorial?.motionDetailTall?.video ?: editorial?.motionTallVideo3x4?.video,
                    )
                }
            }

        override suspend fun fetchVariantStreamsForMasterUrl(masterUrl: String): AppResult<List<HlsVariantStream>> =
            withContext(dispatchers.io) {
                appResultOf {
                    val req = Request.Builder().url(masterUrl).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("Failed to fetch master m3u8: ${res.code}")
                    val masterContent = res.body?.string() ?: throw Exception("Empty master")
                    res.close()

                    HlsPlaylistParser.parseMasterPlaylistVariants(masterUrl, masterContent)
                }
            }

        override suspend fun fetchVariantByteSize(variantUrl: String): AppResult<Long?> =
            withContext(dispatchers.io) {
                appResultOf {
                    val res = client.newCall(Request.Builder().url(variantUrl).build()).execute()
                    res.use {
                        if (!it.isSuccessful) throw Exception("Failed to fetch media m3u8: ${it.code}")
                        val content = it.body?.string() ?: throw Exception("Empty media")
                        HlsPlaylistParser.parseMediaPlaylist(variantUrl, content).statedBytes
                    }
                }
            }

        override suspend fun downloadToFile(
            masterUrl: String,
            destination: File,
            tmp: File,
        ): AppResult<Unit> =
            withContext(dispatchers.io) {
                appResultOf {
                    val req = Request.Builder().url(masterUrl).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("Failed to fetch master m3u8: ${res.code}")
                    val masterContent = res.body?.string() ?: throw Exception("Empty master")
                    res.close()

                    val variantUrl =
                        HlsPlaylistParser.selectBestVariantUrl(masterUrl, masterContent, AnimatedArtworkConfig.maxShortEdgePx)
                            ?: throw Exception("No suitable variant")

                    downloadVariantToFile(variantUrl, destination, tmp).let { result ->
                        if (result is AppResult.Error) {
                            throw Exception(result.error.message, result.error.cause)
                        }
                    }
                }
            }

        override suspend fun downloadVariantToFile(
            variantUrl: String,
            destination: File,
            tmp: File,
        ): AppResult<Unit> =
            withContext(dispatchers.io) {
                appResultOf {
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
    }
