package com.erhu.erhu.core.network.artwork

import com.erhu.erhu.core.model.ProviderOutcome
import com.erhu.erhu.core.model.ProviderStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** A single music-only pipeline used by previews, manual application and legacy artwork callers. */
class AlbumArtworkFinder(
    client: OkHttpClient,
    private val json: Json,
    val cache: AlbumArtworkCache,
    private val inspector: ArtworkImageInspector,
    private val audioDbKey: String = "",
    private val endpoints: Endpoints = Endpoints(),
    private val musicBrainzRequest: suspend (suspend () -> JsonObject?) -> JsonObject? = { block -> pacedMusicBrainz(block) },
    private val budgetMs: Long = 45000,
) {
    data class Endpoints(
        val musicBrainz: String = "https://musicbrainz.org/",
        val coverArt: String = "https://coverartarchive.org/",
        val deezer: String = "https://api.deezer.com/",
        val itunes: String = "https://itunes.apple.com/",
        val audioDb: String = "https://www.theaudiodb.com/api/v1/json/",
    )

    private val http =
        client
            .newBuilder()
            .callTimeout(
                6,
                TimeUnit.SECONDS,
            ).connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    private val searchLock = Mutex()
    private val cooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var lastAppleAt = 0L
    private var lastAudioDbAt = 0L
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    suspend fun search(query: AlbumArtworkQuery): AlbumArtworkResult =
        withContext(Dispatchers.IO) {
            searchLock.withLock {
                val key = cache.key(query, "music-catalogues-v3")
                cache.read(key)?.let {
                    return@withLock AlbumArtworkResult(
                        it,
                        listOf(ProviderOutcome("Validated artwork cache", it.size, ProviderStatus.SUCCESS)),
                        0,
                        true,
                    )
                }
                val started = System.nanoTime()

                fun elapsed() = (System.nanoTime() - started) / 1000000
                val outcomes = mutableListOf<ProviderOutcome>()
                val candidates = mutableListOf<AlbumArtworkCandidate>()
                val releases = mutableListOf<JsonObject>()
                val validated = mutableListOf<AlbumArtworkCandidate>()
                val attemptedImages = mutableSetOf<String>()
                val imageErrors = mutableSetOf<String>()

                suspend fun validate(items: List<AlbumArtworkCandidate>) {
                    for (candidate in items.distinctBy { it.imageUrl }.sortedByDescending { it.score }.take(10)) {
                        if (elapsed() >= budgetMs || !attemptedImages.add(candidate.imageUrl)) continue
                        currentCoroutineContext().ensureActive()
                        try {
                            val bytes =
                                cache.bytes(candidate.imageUrl)
                                    ?: withTimeout(minOf(4500, budgetMs - elapsed()).coerceAtLeast(1)) { download(candidate.imageUrl) }
                            val facts =
                                inspector.inspect(bytes) ?: run {
                                    imageErrors += "Unsupported image data"
                                    continue
                                }
                            cache.saveImage(candidate.imageUrl, bytes)
                            validated +=
                                candidate.copy(
                                    width = facts.width,
                                    height = facts.height,
                                    verifiedImage = true,
                                    contentHash = AlbumArtworkCache.hash(bytes),
                                    visualHash = facts.visualHash,
                                    reviewReasons =
                                        candidate.reviewReasons +
                                            if (minOf(facts.width, facts.height) < 500) listOf("Low-resolution image") else emptyList(),
                                )
                        } catch (
                            e: TimeoutCancellationException,
                        ) {
                            currentCoroutineContext().ensureActive()
                        } catch (
                            e: CancellationException,
                        ) {
                            throw e
                        } catch (error: Exception) {
                            imageErrors += "${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}"
                            // Try the next independently validated image.
                        }
                    }
                }

                suspend fun stage(
                    name: String,
                    maxMs: Long = 6000,
                    block: suspend () -> List<AlbumArtworkCandidate>,
                ) {
                    currentCoroutineContext().ensureActive()
                    val remaining = budgetMs - elapsed()
                    if (remaining <
                        250
                    ) {
                        outcomes += ProviderOutcome(name, status = ProviderStatus.TIMED_OUT, errorMessage = "Search time budget reached")
                        return
                    }
                    try {
                        val found = withTimeout(minOf(maxMs, remaining)) { block() }.mapNotNull { AlbumArtworkRanking.rank(query, it) }
                        candidates += found
                        outcomes +=
                            ProviderOutcome(name, found.size, if (found.isEmpty()) ProviderStatus.NO_RESULTS else ProviderStatus.SUCCESS)
                    } catch (e: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        outcomes +=
                            ProviderOutcome(name, status = ProviderStatus.TIMED_OUT, errorMessage = "Provider timed out")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val status =
                            if (e is HttpFailure &&
                                e.code in setOf(429, 503)
                            ) {
                                ProviderStatus.RATE_LIMITED
                            } else {
                                ProviderStatus.FAILED
                            }
                        outcomes +=
                            ProviderOutcome(
                                name,
                                status = status,
                                errorMessage = if (e is HttpFailure) "HTTP ${e.code}" else "Provider response unavailable or malformed",
                            )
                    }
                }
                stage("MusicBrainz release identification", 7500) {
                    val releaseId = query.releaseId?.takeIf(uuid::matches)
                    val recordingId = query.recordingId?.takeIf(uuid::matches)
                    when {
                        releaseId != null ->
                            musicBrainzRequest {
                                get(
                                    endpoints.musicBrainz,
                                    "ws/2/release/$releaseId",
                                    mapOf(
                                        "fmt" to "json",
                                        "inc" to "artist-credits+release-groups",
                                    ),
                                )
                            }?.let(releases::add)
                        recordingId != null -> {
                            val recording =
                                musicBrainzRequest {
                                    get(
                                        endpoints.musicBrainz,
                                        "ws/2/recording/$recordingId",
                                        mapOf(
                                            "fmt" to "json",
                                            "inc" to "releases+artist-credits",
                                        ),
                                    )
                                }
                            recording?.objects("releases")?.forEach {
                                val releaseCredits = it["artist-credit"] ?: recording["artist-credit"] ?: JsonArray(emptyList())
                                releases += JsonObject(it + ("artist-credit" to releaseCredits))
                            }
                        }
                        else -> {
                            val artistKnown =
                                AlbumArtworkRanking.normalized(query.artist) !in setOf("", "unknown artist", "various artists", "unknown")
                            val search =
                                "release:\"${escape(AlbumArtworkRanking.searchTitle(query.album))}\"" +
                                    if (artistKnown) " AND artist:\"${escape(query.artist)}\"" else ""
                            var result =
                                musicBrainzRequest {
                                    get(
                                        endpoints.musicBrainz,
                                        "ws/2/release/",
                                        mapOf(
                                            "query" to (search + if (query.year > 0) " AND date:${query.year}*" else ""),
                                            "fmt" to "json",
                                            "limit" to "12",
                                        ),
                                    )
                                }
                            if (result?.objects("releases").isNullOrEmpty() &&
                                query.year > 0
                            ) {
                                result =
                                    musicBrainzRequest {
                                        get(
                                            endpoints.musicBrainz,
                                            "ws/2/release/",
                                            mapOf(
                                                "query" to search,
                                                "fmt" to "json",
                                                "limit" to "12",
                                            ),
                                        )
                                    }
                            }
                            releases += result?.objects("releases").orEmpty()
                        }
                    }
                    // Identification is evidence, not an image candidate.
                    emptyList()
                }
                val identified =
                    releases
                        .filter { it.text("status").lowercase() !in setOf("bootleg", "pseudo-release") }
                        .mapNotNull { release -> releaseCandidate(release)?.let { AlbumArtworkRanking.rank(query, it) } }
                        .sortedByDescending { it.score }
                val identificationIndex = outcomes.indexOfFirst { it.providerName == "MusicBrainz release identification" }
                if (identified.isNotEmpty() &&
                    identificationIndex >= 0
                ) {
                    outcomes[identificationIndex] =
                        ProviderOutcome("MusicBrainz release identification", identified.size, ProviderStatus.SUCCESS)
                }
                stage("Cover Art Archive exact release", 7500) {
                    identified.take(2).flatMap { release -> archive(release, "release", release.releaseId.orEmpty()) }
                }
                validate(candidates.filter { it.source == "Cover Art Archive release" })
                if (validated.none { it.source == "Cover Art Archive release" && it.reviewReasons.isEmpty() }) {
                    stage("Cover Art Archive release-group fallback", 6000) {
                        val group = query.releaseGroupId?.takeIf(uuid::matches) ?: identified.firstNotNullOfOrNull { it.releaseGroupId }
                        if (group !=
                            null
                        ) {
                            archive(
                                identified.firstOrNull() ?: AlbumArtworkCandidate("", "", query.album, query.artist),
                                "release-group",
                                group,
                            )
                        } else {
                            val response =
                                musicBrainzRequest {
                                    get(
                                        endpoints.musicBrainz,
                                        "ws/2/release-group/",
                                        mapOf(
                                            "query" to
                                                "releasegroup:\"${escape(
                                                    AlbumArtworkRanking.searchTitle(query.album),
                                                )}\" AND artist:\"${escape(query.artist)}\"",
                                            "fmt" to "json",
                                            "limit" to "3",
                                        ),
                                    )
                                }
                            response
                                ?.objects("release-groups")
                                .orEmpty()
                                .mapNotNull { groupObject ->
                                    val id = groupObject.text("id").takeIf(uuid::matches) ?: return@mapNotNull null
                                    val release =
                                        AlbumArtworkCandidate(
                                            "",
                                            "",
                                            groupObject.text("title"),
                                            credits(groupObject),
                                            kind = kind(groupObject.text("primary-type")),
                                            releaseGroupId = id,
                                        )
                                    AlbumArtworkRanking.rank(query, release)
                                }.sortedByDescending { it.score }
                                .take(1)
                                .flatMap { archive(it, "release-group", it.releaseGroupId.orEmpty()) }
                        }
                    }
                }
                stage("Deezer") {
                    val response =
                        get(
                            endpoints.deezer,
                            "search/album",
                            mapOf(
                                "q" to
                                    "artist:\"${escape(query.artist)}\" album:\"${escape(AlbumArtworkRanking.searchTitle(query.album))}\"",
                                "limit" to "12",
                            ),
                        )
                    if (response?.get("error") is JsonObject) throw IOException("Deezer unavailable")
                    response?.objects("data").orEmpty().mapNotNull { item ->
                        val url = item.text("cover_xl").ifBlank { item.text("cover_big") }
                        if (url.isBlank()) return@mapNotNull null
                        AlbumArtworkCandidate(
                            url,
                            "Deezer",
                            item.text("title"),
                            item.obj("artist")?.text("name").orEmpty(),
                            item.text("release_date").take(4).toIntOrNull() ?: 0,
                            kind = kind(item.text("record_type")),
                            catalogId = item.text("id"),
                        )
                    }
                }
                stage("iTunes / Apple Search") {
                    val wait = lastAppleAt + 3500 - System.currentTimeMillis()
                    if (wait > 0) kotlinx.coroutines.delay(wait)
                    lastAppleAt = System.currentTimeMillis()
                    val response =
                        get(
                            endpoints.itunes,
                            "search",
                            mapOf(
                                "term" to AlbumArtworkRanking.searchTitle(query.album),
                                "attribute" to "albumTerm",
                                "entity" to "album",
                                "limit" to "50",
                            ),
                        )
                    response?.objects("results").orEmpty().mapNotNull { item ->
                        if (item.text("collectionType").isNotEmpty() && item.text("collectionType") != "Album") return@mapNotNull null
                        val raw = item.text("artworkUrl100").ifBlank { item.text("artworkUrl60") }
                        if (raw.isBlank()) return@mapNotNull null
                        val title = item.text("collectionName")
                        val type =
                            when {
                                Regex("(?i)(?:-|–)\\s*single$").containsMatchIn(title) -> ArtworkReleaseKind.SINGLE
                                Regex("(?i)(?:-|–)\\s*ep$").containsMatchIn(title) -> ArtworkReleaseKind.EP
                                else -> ArtworkReleaseKind.ALBUM
                            }
                        AlbumArtworkCandidate(
                            raw.replace(Regex("\\d+x\\d+(bb)?"), "1000x1000bb"),
                            "iTunes / Apple Search",
                            title,
                            item.text("artistName"),
                            item.text("releaseDate").take(4).toIntOrNull() ?: 0,
                            kind = type,
                            catalogId = item.text("collectionId"),
                        )
                    }
                }
                if (audioDbKey.isNotBlank()) {
                    stage("TheAudioDB") {
                        val wait = lastAudioDbAt + 2100 - System.currentTimeMillis()
                        if (wait > 0) kotlinx.coroutines.delay(wait)
                        lastAudioDbAt = System.currentTimeMillis()
                        val response = get(endpoints.audioDb, "$audioDbKey/searchalbum.php", mapOf("s" to query.artist, "a" to query.album))
                        response?.objects("album").orEmpty().mapNotNull { item ->
                            val url = item.text("strAlbumThumb")
                            if (url.isBlank()) return@mapNotNull null
                            AlbumArtworkCandidate(
                                url,
                                "TheAudioDB",
                                item.text("strAlbum"),
                                item.text("strArtist"),
                                item.text("intYearReleased").toIntOrNull() ?: 0,
                                kind = kind(item.text("strReleaseFormat")),
                                catalogId = item.text("idAlbum"),
                                releaseGroupId = item.text("strMusicBrainzID").takeIf(uuid::matches),
                            )
                        }
                    }
                } else {
                    outcomes +=
                        ProviderOutcome(
                            "TheAudioDB",
                            status = ProviderStatus.UNAVAILABLE,
                            errorMessage = "Production access is not configured; other catalogues remain available",
                        )
                }

                validate(candidates)
                outcomes +=
                    ProviderOutcome(
                        "Artwork image verification",
                        validated.size,
                        if (validated.isEmpty()) ProviderStatus.NO_RESULTS else ProviderStatus.SUCCESS,
                        imageErrors.takeIf { it.isNotEmpty() }?.joinToString("; "),
                    )
                val ranked = AlbumArtworkRanking.deduplicate(validated)
                if (ranked.isNotEmpty()) cache.write(key, ranked)
                val fallback = if (ranked.isEmpty()) cache.read(key, allowExpired = true) else null
                AlbumArtworkResult(fallback ?: ranked, outcomes, elapsed(), fallback != null)
            }
        }

    private fun releaseCandidate(item: JsonObject): AlbumArtworkCandidate? {
        val id = item.text("id").takeIf(uuid::matches) ?: return null
        val group = item.obj("release-group")
        return AlbumArtworkCandidate(
            "",
            "MusicBrainz",
            item.text("title"),
            credits(item),
            item.text("date").take(4).toIntOrNull() ?: 0,
            listOf(
                item.text("disambiguation"),
                item
                    .text("status")
                    .takeIf {
                        it.equals("Promotion", true)
                    }.orEmpty(),
            ).filter(String::isNotBlank).joinToString("; "),
            kind(group?.text("primary-type").orEmpty()),
            id,
            group?.text("id"),
        )
    }

    private suspend fun archive(
        release: AlbumArtworkCandidate,
        type: String,
        id: String,
    ): List<AlbumArtworkCandidate> {
        if (!uuid.matches(id)) return emptyList()
        val response = get(endpoints.coverArt, "$type/$id", missingIsEmpty = true) ?: return emptyList()
        return response
            .objects("images")
            .filter { it.flag("front") && !it.flag("back") && it.flag("approved") }
            .take(2)
            .mapNotNull { image ->
                val thumbs = image.obj("thumbnails")
                val url =
                    thumbs
                        ?.text("1200")
                        .orEmpty()
                        .ifBlank { thumbs?.text("large").orEmpty() }
                        .ifBlank { image.text("image") }
                if (url.isBlank()) {
                    null
                } else {
                    release.copy(
                        imageUrl = if (endpoints.coverArt.startsWith("https://")) url.replace("http://", "https://") else url,
                        source = "Cover Art Archive $type",
                        releaseId = if (type == "release-group") null else release.releaseId,
                        year = if (type == "release-group") 0 else release.year,
                        edition = if (type == "release-group") "Release-group artwork" else release.edition,
                        reviewReasons =
                            if (type ==
                                "release-group"
                            ) {
                                listOf("Release-group cover: confirm edition")
                            } else {
                                release.reviewReasons
                            },
                    )
                }
            }
    }

    private suspend fun get(
        base: String,
        path: String,
        params: Map<String, String> = emptyMap(),
        missingIsEmpty: Boolean = false,
    ): JsonObject? {
        val url =
            (base.trimEnd('/') + "/" + path)
                .toHttpUrl()
                .newBuilder()
                .apply {
                    params.forEach { (key, value) ->
                        addQueryParameter(key, value)
                    }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
        response(request).use { response ->
            if (response.code == 404 && missingIsEmpty) return null
            if (!response.isSuccessful) throw HttpFailure(response.code)
            val bytes = bounded(response.body?.byteStream() ?: throw IOException("No response body"), 2 * 1024 * 1024)
            return json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: throw IOException("Invalid JSON object")
        }
    }

    private suspend fun download(url: String): ByteArray {
        val parsed = url.toHttpUrl()
        val configuredImageHost = endpoints.itunes.toHttpUrl()
        require(parsed.isHttps || (!configuredImageHost.isHttps && parsed.host == configuredImageHost.host))
        response(
            Request
                .Builder()
                .url(parsed)
                .header("User-Agent", USER_AGENT)
                .build(),
        ).use { response ->
            if (!response.isSuccessful) throw HttpFailure(response.code)
            val body = response.body ?: throw IOException("Empty image")
            if (body.contentType()?.type != "image") throw IOException("Not an image")
            return bounded(body.byteStream(), 3 * 1024 * 1024)
        }
    }

    private suspend fun response(request: Request): Response {
        if ((cooldowns[request.url.host] ?: 0L) > System.currentTimeMillis()) throw HttpFailure(429)
        val response =
            suspendCancellableCoroutine<Response> { continuation ->
                val call = http.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            error: IOException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            continuation.resume(response) { _, value, _ -> value.close() }
                        }
                    },
                )
            }
        if (response.code == 429 || response.code == 503) {
            val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 3600) ?: 60
            cooldowns[request.url.host] = System.currentTimeMillis() + seconds * 1000
        }
        return response
    }

    private fun bounded(
        input: java.io.InputStream,
        maximum: Int,
    ): ByteArray =
        input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = it.read(buffer)
                if (n <
                    0
                ) {
                    break
                }
                if (output.size() + n > maximum) throw IOException("Response too large")
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }

    private class HttpFailure(
        val code: Int,
    ) : IOException("HTTP $code")

    private fun credits(item: JsonObject) =
        item.objects("artist-credit").joinToString("") {
            it.text("name").ifBlank { it.obj("artist")?.text("name").orEmpty() } +
                it.text("joinphrase")
        }

    private fun escape(value: String) = value.replace("\\", " ").replace("\"", " ")

    private fun kind(value: String) =
        when (value.lowercase()) {
            "album", "lp" -> ArtworkReleaseKind.ALBUM
            "ep" -> ArtworkReleaseKind.EP
            "single" -> ArtworkReleaseKind.SINGLE
            else -> ArtworkReleaseKind.UNKNOWN
        }

    companion object {
        const val USER_AGENT = "erhu/1.0.2 (https://github.com/lochanpthakke/erhu)"
        private val gate = Mutex()
        private var lastMb = 0L

        private suspend fun pacedMusicBrainz(block: suspend () -> JsonObject?): JsonObject? =
            gate.withLock {
                val wait = lastMb + 1100 - System.currentTimeMillis()
                if (wait > 0) kotlinx.coroutines.delay(wait)
                try {
                    block()
                } finally {
                    lastMb = System.currentTimeMillis()
                }
            }
    }
}

internal fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun JsonObject.flag(key: String): Boolean = text(key) == "true"
